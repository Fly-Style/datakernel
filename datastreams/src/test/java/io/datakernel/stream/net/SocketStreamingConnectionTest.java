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

package io.datakernel.stream.net;

import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.eventloop.AsyncTcpSocketImpl;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.SimpleServer;
import io.datakernel.stream.StreamConsumerToList;
import io.datakernel.stream.StreamProducers;
import io.datakernel.stream.processor.StreamBinaryDeserializer;
import io.datakernel.stream.processor.StreamBinarySerializer;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.datakernel.bytebuf.ByteBufPool.*;
import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.serializer.asm.BufferSerializers.intSerializer;
import static io.datakernel.stream.DataStreams.stream;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("unchecked")
public final class SocketStreamingConnectionTest {
	private static final int LISTEN_PORT = 1234;
	private static final InetSocketAddress address;
	static {
		try {
			address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), LISTEN_PORT);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	@Before
	public void setUp() {
		ByteBufPool.clear();
		ByteBufPool.setSizes(0, Integer.MAX_VALUE);
	}

	@Test
	public void test() throws Exception {
		CompletableFuture<?> future;
		List<Integer> list = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			list.add(i);
		}

		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		StreamConsumerToList<Integer> consumerToList = StreamConsumerToList.create();

		SimpleServer server = SimpleServer.create(eventloop,
				asyncTcpSocket -> {
					SocketStreamingConnection connection = SocketStreamingConnection.create(asyncTcpSocket);

					StreamBinaryDeserializer<Integer> streamDeserializer = StreamBinaryDeserializer.create(intSerializer());
					stream(streamDeserializer.getOutput(), consumerToList);
					stream(connection.getSocketReader(), streamDeserializer.getInput());

					return connection;
				})
				.withListenAddress(address)
				.withAcceptOnce();
		server.listen();

		future = eventloop.connect(address).thenAccept(socketChannel -> {
			StreamBinarySerializer<Integer> streamSerializer = StreamBinarySerializer.create(intSerializer())
					.withDefaultBufferSize(1);

			AsyncTcpSocketImpl asyncTcpSocket = AsyncTcpSocketImpl.wrapChannel(eventloop, socketChannel);
			SocketStreamingConnection connection = SocketStreamingConnection.create(asyncTcpSocket);
			stream(StreamProducers.ofIterable(list), streamSerializer.getInput());
			stream(streamSerializer.getOutput(), connection.getSocketWriter());
			asyncTcpSocket.setEventHandler(connection);
			asyncTcpSocket.register();
		}).toCompletableFuture();

		eventloop.run();

		future.get();
		assertEquals(list, consumerToList.getList());
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}

	@Test
	public void testLoopback() throws Exception {
		CompletableFuture<?> future;
		List<Integer> source = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			source.add(i);
		}

		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		StreamConsumerToList<Integer> consumerToList = StreamConsumerToList.create();

		SimpleServer server = SimpleServer.create(eventloop,
				asyncTcpSocket -> {
					SocketStreamingConnection connection = SocketStreamingConnection.create(asyncTcpSocket);
					stream(connection.getSocketReader(), connection.getSocketWriter());
					return connection;
				})
				.withListenAddress(address)
				.withAcceptOnce();
		server.listen();

		future = eventloop.connect(address).thenAccept(socketChannel -> {
			StreamBinarySerializer<Integer> streamSerializer = StreamBinarySerializer.create(intSerializer())
					.withDefaultBufferSize(1);
			StreamBinaryDeserializer<Integer> streamDeserializer = StreamBinaryDeserializer.create(intSerializer());

			AsyncTcpSocketImpl asyncTcpSocket = AsyncTcpSocketImpl.wrapChannel(eventloop, socketChannel);
			SocketStreamingConnection connection = SocketStreamingConnection.create(asyncTcpSocket);
			stream(streamSerializer.getOutput(), connection.getSocketWriter());
			stream(connection.getSocketReader(), streamDeserializer.getInput());

			stream(StreamProducers.ofIterable(source), streamSerializer.getInput());
			stream(streamDeserializer.getOutput(), consumerToList);

			asyncTcpSocket.setEventHandler(connection);
			asyncTcpSocket.register();
		}).toCompletableFuture();

		eventloop.run();

		future.get();
		assertEquals(source, consumerToList.getList());
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}
}
