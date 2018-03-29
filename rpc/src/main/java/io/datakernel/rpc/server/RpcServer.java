/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.rpc.server;

import io.datakernel.async.SettableStage;
import io.datakernel.eventloop.AbstractServer;
import io.datakernel.eventloop.AsyncTcpSocket;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.jmx.*;
import io.datakernel.jmx.JmxReducers.JmxReducerSum;
import io.datakernel.net.ServerSocketSettings;
import io.datakernel.net.SocketSettings;
import io.datakernel.rpc.client.RpcClient;
import io.datakernel.rpc.protocol.RpcMessage;
import io.datakernel.rpc.protocol.RpcStream;
import io.datakernel.serializer.BufferSerializer;
import io.datakernel.serializer.SerializerBuilder;
import io.datakernel.stream.processor.StreamBinarySerializer;
import io.datakernel.util.MemSize;

import java.net.InetAddress;
import java.time.Duration;
import java.util.*;

import static io.datakernel.util.Preconditions.*;
import static java.util.Arrays.asList;

/**
 * An RPC server that works asynchronously. This server uses fast serializers
 * and custom optimized communication protocol, improving application
 * performance.
 * <p>
 * In order to set up a server it's mandatory to create it using
 * {@link #create(Eventloop)}, indicate a types of messages, and specify
 * an appropriate {@link RpcRequestHandler request handlers} for that types.
 * <p>
 * There are two ways of starting a server:
 * <ul>
 * <li>Manually: set up the server and call {@code listen()}</li>
 * <li>Create a module for your RPC server and pass it to a {@code Launcher}
 * along with {@code ServiceGraphModule}.</li>
 * </ul>
 * <p>
 * Example. Here are the steps, intended to supplement the example, listed in
 * {@link RpcClient}:
 * <ul>
 * <li>Create a {@code RequestHandler} for {@code RequestClass} and
 * {@code ResponseClass}</li>
 * <li>Create an {@code RpcServer}</li>
 * <li>Run the server</li>
 * </ul>
 * The implementation, which matches an example, listed in {@link RpcClient}
 * could be as follows:
 * <pre><code>
 * //create a request handler for RequestClass and ResponseClass
 * public class SimpleRequestHandler implements RpcRequestHandler&lt;RequestClass, ResponseClass&gt; {
 * 	public void run(RequestClass requestClass, ResultCallback&lt;ResponseClass&gt; resultCallback) {
 * 		int count = compute(requestClass.getInfo());
 * 		resultCallback.setResult(new ResponseClass(count));
 *    }
 *
 * 	private int compute(String info) {
 * 		return info.length();
 *    }
 * }</code></pre>
 * Next, instantiate an {@code RpcServer} capable for handling aforementioned
 * message types and run it:
 * <pre><code>
 * RpcServer server = RpcServer.create(eventloop)
 * 	.withHandler(RequestClass.class, ResponseClass.class, new SimpleRequestHandler())
 * 	.withMessageTypes(RequestClass.class, ResponseClass.class)
 * 	.withListenPort(40000);
 * </code></pre>
 *
 * @see RpcRequestHandler
 * @see RpcClient
 */
public final class RpcServer extends AbstractServer<RpcServer> {
	public static final ServerSocketSettings DEFAULT_SERVER_SOCKET_SETTINGS = ServerSocketSettings.create(16384);
	public static final SocketSettings DEFAULT_SOCKET_SETTINGS = SocketSettings.create().withTcpNoDelay(true);

	public static final MemSize DEFAULT_PACKET_SIZE = StreamBinarySerializer.DEFAULT_BUFFER_SIZE;
	public static final MemSize MAX_PACKET_SIZE = StreamBinarySerializer.MAX_SIZE;

	private int defaultPacketSize = DEFAULT_PACKET_SIZE.toInt();
	private int maxPacketSize = MAX_PACKET_SIZE.toInt();
	private boolean compression = false;
	private int flushDelayMillis = 0;

	private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
	private Map<Class<?>, RpcRequestHandler<?, ?>> handlers = new LinkedHashMap<>();
	private SerializerBuilder serializerBuilder = SerializerBuilder.create(classLoader);
	private List<Class<?>> messageTypes;

	private final List<RpcServerConnection> connections = new ArrayList<>();

	private BufferSerializer<RpcMessage> serializer;

	private SettableStage<Void> closeStage;

	// region JMX vars
	static final double SMOOTHING_WINDOW = ValueStats.SMOOTHING_WINDOW_1_MINUTE;
	private EventStats totalConnects = EventStats.create(SMOOTHING_WINDOW);
	private Map<InetAddress, EventStats> connectsPerAddress = new HashMap<>();
	private EventStats successfulRequests = EventStats.create(SMOOTHING_WINDOW);
	private EventStats failedRequests = EventStats.create(SMOOTHING_WINDOW);
	private ValueStats requestHandlingTime = ValueStats.create(SMOOTHING_WINDOW);
	private ExceptionStats lastRequestHandlingException = ExceptionStats.create();
	private ExceptionStats lastProtocolError = ExceptionStats.create();
	private boolean monitoring;
	// endregion

//	private final StreamBinarySerializer.JmxInspector statsSerializer = new StreamBinarySerializer.JmxInspector();
//	private final StreamBinaryDeserializer.JmxInspector statsDeserializer = new StreamBinaryDeserializer.JmxInspector();
//	private final StreamLZ4Compressor.JmxInspector statsCompressor = new StreamLZ4Compressor.JmxInspector();
//	private final StreamLZ4Decompressor.JmxInspector statsDecompressor = new StreamLZ4Decompressor.JmxInspector();

	// region builders
	private RpcServer(Eventloop eventloop) {
		super(eventloop);
	}

	public static RpcServer create(Eventloop eventloop) {
		return new RpcServer(eventloop)
				.withServerSocketSettings(DEFAULT_SERVER_SOCKET_SETTINGS)
				.withSocketSettings(DEFAULT_SOCKET_SETTINGS);
	}

	public RpcServer withClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
		this.serializerBuilder = SerializerBuilder.create(classLoader);
		return this;
	}

	/**
	 * Creates a server, capable of specified message types processing.
	 *
	 * @param messageTypes classes of messages processed by a server
	 * @return server instance capable for handling provided message types
	 */
	public RpcServer withMessageTypes(Class<?>... messageTypes) {
		checkNotNull(messageTypes);
		return withMessageTypes(asList(messageTypes));
	}

	/**
	 * Creates a server, capable of specified message types processing.
	 *
	 * @param messageTypes a list of message types processed by a server
	 * @return server instance capable for handling provided message types
	 */
	public RpcServer withMessageTypes(List<Class<?>> messageTypes) {
		checkNotNull(messageTypes, "Message types should not be null");
		checkArgument(new HashSet<>(messageTypes).size() == messageTypes.size(), "Message types must be unique");
		this.messageTypes = messageTypes;
		return self();
	}

	public RpcServer withSerializerBuilder(SerializerBuilder serializerBuilder) {
		this.serializerBuilder = serializerBuilder;
		return self();
	}

	public RpcServer withStreamProtocol(int defaultPacketSize, int maxPacketSize, boolean compression) {
		this.defaultPacketSize = defaultPacketSize;
		this.maxPacketSize = maxPacketSize;
		this.compression = compression;
		return self();
	}

	public RpcServer withStreamProtocol(MemSize defaultPacketSize, MemSize maxPacketSize, boolean compression) {
		return withStreamProtocol(defaultPacketSize.toInt(), maxPacketSize.toInt(), compression);
	}

	public RpcServer withFlushDelay(Duration flushDelay) {
		return withFlushDelay((int) flushDelay.toMillis());
	}

	public RpcServer withFlushDelay(int flushDelayMillis) {
		checkArgument(flushDelayMillis >= 0, "Flush delay should not be less than zero");
		this.flushDelayMillis = flushDelayMillis;
		return this;
	}

	/**
	 * Adds a handler for a specified request-response pair.
	 *
	 * @param requestClass  a class representing a request structure
	 * @param responseClass a class representing a response structure
	 * @param handler       a class containing logic of request processing and
	 *                      creating a response
	 * @param <I>           class of request
	 * @param <O>           class of response
	 * @return server instance capable for handling requests of concrete types
	 */
	@SuppressWarnings("unchecked")
	public <I, O> RpcServer withHandler(Class<I> requestClass, Class<O> responseClass, RpcRequestHandler<I, O> handler) {
		handlers.put(requestClass, handler);
		return this;
	}

	// endregion

	@Override
	protected AsyncTcpSocket.EventHandler createSocketHandler(AsyncTcpSocket asyncTcpSocket) {
		RpcStream stream = new RpcStream(asyncTcpSocket, serializer, defaultPacketSize, maxPacketSize,
				flushDelayMillis, compression, true); // , statsSerializer, statsDeserializer, statsCompressor, statsDecompressor);
		RpcServerConnection connection = new RpcServerConnection(this, asyncTcpSocket.getRemoteSocketAddress(), handlers, stream);
		stream.setListener(connection);
		add(connection);

		// jmx
		ensureConnectStats(asyncTcpSocket.getRemoteSocketAddress().getAddress()).recordEvent();
		totalConnects.recordEvent();

		return stream.getSocketEventHandler();
	}

	@Override
	protected void onListen() {
		checkState(messageTypes != null, "Message types must be specified");
		serializer = serializerBuilder.withSubclasses(RpcMessage.MESSAGE_TYPES, messageTypes).build(RpcMessage.class);
	}

	@Override
	protected void onClose(SettableStage<Void> stage) {
		if (connections.size() == 0) {
			logger.info("RpcServer is closing. Active connections count: 0.");
			stage.set(null);
		} else {
			logger.info("RpcServer is closing. Active connections count: " + connections.size());
			for (RpcServerConnection connection : new ArrayList<>(connections)) {
				connection.close();
			}
			closeStage = stage;
		}
	}

	void add(RpcServerConnection connection) {
		if (logger.isInfoEnabled())
			logger.info("Client connected on {}", connection);

		if (monitoring) {
			connection.startMonitoring();
		}

		connections.add(connection);
	}

	void remove(RpcServerConnection connection) {
		if (logger.isInfoEnabled())
			logger.info("Client disconnected on {}", connection);
		connections.remove(connection);

		if (closeStage != null) {
			logger.info("RpcServer is closing. One more connection was closed. " +
					"Active connections count: " + connections.size());

			if (connections.size() == 0) {
				closeStage.set(null);
			}
		}
	}

	// region JMX
	@JmxOperation(description = "enable monitoring " +
			"[ when monitoring is enabled more stats are collected, but it causes more overhead " +
			"(for example, requestHandlingTime stats are collected only when monitoring is enabled) ]")
	public void startMonitoring() {
		monitoring = true;
		for (RpcServerConnection connection : connections) {
			connection.startMonitoring();
		}
	}

	@JmxOperation(description = "disable monitoring " +
			"[ when monitoring is enabled more stats are collected, but it causes more overhead " +
			"(for example, requestHandlingTime stats are collected only when monitoring is enabled) ]")
	public void stopMonitoring() {
		monitoring = false;
		for (RpcServerConnection connection : connections) {
			connection.stopMonitoring();
		}
	}

	@JmxAttribute(description = "when monitoring is enabled more stats are collected, but it causes more overhead " +
			"(for example, requestHandlingTime stats are collected only when monitoring is enabled)")
	public boolean isMonitoring() {
		return monitoring;
	}

	@JmxAttribute(description = "current number of connections", reducer = JmxReducerSum.class)
	public int getConnectionsCount() {
		return connections.size();
	}

	@JmxAttribute
	public EventStats getTotalConnects() {
		return totalConnects;
	}

	//	FIXME (vmykhalko) @JmxAttribute(description = "number of connects/reconnects per client address")
	public Map<InetAddress, EventStats> getConnectsPerAddress() {
		return connectsPerAddress;
	}

	private EventStats ensureConnectStats(InetAddress address) {
		EventStats stats = connectsPerAddress.get(address);
		if (stats == null) {
			stats = EventStats.create(SMOOTHING_WINDOW);
			connectsPerAddress.put(address, stats);
		}
		return stats;
	}

	@JmxAttribute(description = "detailed information about connections")
	public List<RpcServerConnection> getConnections() {
		return connections;
	}

	@JmxAttribute(extraSubAttributes = "totalCount", description = "number of requests which were processed correctly")
	public EventStats getSuccessfulRequests() {
		return successfulRequests;
	}

	@JmxAttribute(extraSubAttributes = "totalCount", description = "request with error responses (number of requests which were handled with error)")
	public EventStats getFailedRequests() {
		return failedRequests;
	}

	@JmxAttribute(description = "time for handling one request in milliseconds (both successful and failed)")
	public ValueStats getRequestHandlingTime() {
		return requestHandlingTime;
	}

	@JmxAttribute(description = "exception that occurred because of business logic error " +
			"(in RpcRequestHandler implementation)")
	public ExceptionStats getLastRequestHandlingException() {
		return lastRequestHandlingException;
	}

	@JmxAttribute(description = "exception that occurred because of protocol error " +
			"(serialization, deserialization, compression, decompression, etc)")
	public ExceptionStats getLastProtocolError() {
		return lastProtocolError;
	}

//	@JmxAttribute
//	public StreamBinarySerializer.JmxInspector getStatsSerializer() {
//		return statsSerializer;
//	}
//
//	@JmxAttribute
//	public StreamBinaryDeserializer.JmxInspector getStatsDeserializer() {
//		return statsDeserializer;
//	}
//
//	@JmxAttribute
//	public StreamLZ4Compressor.JmxInspector getStatsCompressor() {
//		return compression ? statsCompressor : null;
//	}
//
//	@JmxAttribute
//	public StreamLZ4Decompressor.JmxInspector getStatsDecompressor() {
//		return compression ? statsDecompressor : null;
//	}
	// endregion
}

