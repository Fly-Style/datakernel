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

import io.datakernel.async.Callback;
import io.datakernel.async.SettableStage;
import io.datakernel.async.Stage;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.eventloop.AsyncTcpSocket;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.exception.ParseException;
import io.datakernel.stream.StreamConsumer;
import io.datakernel.stream.StreamConsumerWithResult;
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.StreamProducerWithResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.datakernel.util.Preconditions.checkState;

/**
 * Represent the TCP connection which  processes received items with {@link StreamProducer} and {@link StreamConsumer},
 * which organized by binary protocol. It is created with socketChannel and sides exchange ByteBufs.
 */
public final class MessagingWithBinaryStreaming<I, O> implements AsyncTcpSocket.EventHandler, Messaging<I, O> {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final Eventloop eventloop = Eventloop.getCurrentEventloop();
	private final AsyncTcpSocket asyncTcpSocket;
	private final MessagingSerializer<I, O> serializer;

	private ByteBuf readBuf;
	private boolean readEndOfStream;
	private Callback<I> receiveMessageCallback;
	private List<SettableStage<Void>> writeCallbacks = new ArrayList<>();
	private boolean writeEndOfStreamRequest;
	private SocketStreamProducer socketReader;
	private SocketStreamConsumer socketWriter;

	private Exception closedException;

	private boolean readDone;
	private boolean writeDone;

	// region creators
	private MessagingWithBinaryStreaming(AsyncTcpSocket asyncTcpSocket, MessagingSerializer<I, O> serializer) {
		this.asyncTcpSocket = asyncTcpSocket;
		this.serializer = serializer;
	}

	public static <I, O> MessagingWithBinaryStreaming<I, O> create(AsyncTcpSocket asyncTcpSocket,
	                                                               MessagingSerializer<I, O> serializer) {
		return new MessagingWithBinaryStreaming<>(asyncTcpSocket, serializer);
	}
	// endregion

	@Override
	public Stage<I> receive() {
		checkState(socketReader == null && receiveMessageCallback == null);

		if (closedException != null) {
			return Stage.ofException(closedException);
		}

		SettableStage<I> result = SettableStage.create();
		this.receiveMessageCallback = result;
		if (readBuf != null || readEndOfStream) {
			eventloop.post(() -> {
				if (socketReader == null && this.receiveMessageCallback != null) {
					tryReadMessage();
				}
			});
		} else {
			asyncTcpSocket.read();
		}
		return result;
	}

	private void tryReadMessage() {
		if (readBuf != null && receiveMessageCallback != null) {
			try {
				I message = serializer.tryDeserialize(readBuf);
				if (message == null) {
					asyncTcpSocket.read();
				} else {
					if (!readBuf.canRead()) {
						readBuf.recycle();
						readBuf = null;
						if (!readEndOfStream) {
							asyncTcpSocket.read();
						}
					}
					takeReadCallback().set(message);
				}
			} catch (ParseException e) {
				takeReadCallback().setException(e);
			}
		}
		if (readBuf == null && readEndOfStream) {
			if (receiveMessageCallback != null) {
				takeReadCallback().set(null);
			}
		}
	}

	private Callback<I> takeReadCallback() {
		Callback<I> callback = this.receiveMessageCallback;
		receiveMessageCallback = null;
		return callback;
	}

	@Override
	public Stage<Void> send(O msg) {
		checkState(socketWriter == null && !writeEndOfStreamRequest);

		if (closedException != null) {
			return Stage.ofException(closedException);
		}

		SettableStage<Void> stage = SettableStage.create();
		writeCallbacks.add(stage);
		ByteBuf buf = serializer.serialize(msg);
		asyncTcpSocket.write(buf);

		return stage;
	}

	@Override
	public Stage<Void> sendEndOfStream() {
		checkState(socketWriter == null && !writeEndOfStreamRequest);

		if (closedException != null) {
			return Stage.ofException(closedException);
		}

		SettableStage<Void> stage = SettableStage.create();
		writeEndOfStreamRequest = true;
		writeCallbacks.add(stage);
		asyncTcpSocket.writeEndOfStream();

		return stage;
	}

	@Override
	public StreamConsumerWithResult<ByteBuf, Void> sendBinaryStream() {
		checkState(socketWriter == null && !writeEndOfStreamRequest);

		writeCallbacks.clear();
		if (closedException != null) {
			return StreamConsumer.<ByteBuf>closingWithError(closedException).withEndOfStreamAsResult();
		}

		socketWriter = SocketStreamConsumer.create(asyncTcpSocket);
		return socketWriter.withResult(socketWriter.getSentStage());
	}

	@Override
	public StreamProducerWithResult<ByteBuf, Void> receiveBinaryStream() {
		checkState(this.socketReader == null && this.receiveMessageCallback == null);

		if (closedException != null) {
			StreamProducer<ByteBuf> producer = StreamProducer.closingWithError(closedException);
			return producer.withEndOfStreamAsResult();
		}

		socketReader = SocketStreamProducer.create(asyncTcpSocket);
		if (readBuf != null || readEndOfStream) {
			eventloop.post(() -> {
				if (readBuf != null) {
					readUnconsumedBuf();
				}
				if (readEndOfStream) {
					socketReader.onReadEndOfStream();
				}
			});
		}
		return socketReader.withEndOfStreamAsResult();
	}

	@Override
	public void close() {
		asyncTcpSocket.close();
		if (readBuf != null) {
			readBuf.recycle();
			readBuf = null;
		}
	}

	/**
	 * Is called after connection registration. Wires socketReader with StreamConsumer specified by,
	 * and socketWriter with StreamProducer, that are specified by overridden method {@code wire} of subclass.
	 * If StreamConsumer is null, items from socketReader are ignored. If StreamProducer is null, socketWriter
	 * gets EndOfStream signal.
	 */
	@Override
	public void onRegistered() {
		asyncTcpSocket.read();
	}

	private void readUnconsumedBuf() {
		assert readBuf != null;
		socketReader.onRead(readBuf);
		readBuf = null;
	}

	@Override
	public void onRead(ByteBuf buf) {
		logger.trace("onRead", this);
		assert eventloop.inEventloopThread();
		if (socketReader == null) {
			if (readBuf == null) {
				readBuf = ByteBufPool.allocate(Math.max(8192, buf.writeRemaining()));
			}
			readBuf = ByteBufPool.append(readBuf, buf);
			tryReadMessage();
		} else {
			if (readBuf != null) {
				readUnconsumedBuf();
			}
			socketReader.onRead(buf);
		}
	}

	@Override
	public void onReadEndOfStream() {
		logger.trace("onShutdownInput", this);
		readEndOfStream = true;
		if (socketReader == null) {
			tryReadMessage();
		} else {
			if (readBuf != null) {
				readUnconsumedBuf();
			}
			socketReader.onReadEndOfStream();
		}
		readDone = true;
		closeIfDone();
	}

	private void closeIfDone() {
		if (readDone && writeDone) {
			asyncTcpSocket.close();
		}
	}

	@Override
	public void onWrite() {
		logger.trace("onWrite", this);
		if (socketWriter == null) {
			List<SettableStage<Void>> callbacks = this.writeCallbacks;
			writeCallbacks = new ArrayList<>();
			for (SettableStage<Void> callback : callbacks) {
				callback.set(null);
			}
			if (writeEndOfStreamRequest)
				writeDone = true;
		} else {
			socketWriter.onWrite();
			if (socketWriter.getStatus().isClosed())
				writeDone = true;
		}
		closeIfDone();
	}

	@Override
	public void onClosedWithError(Exception e) {
		logger.trace("onClosedWithError", this);

		if (socketReader != null) {
			socketReader.closeWithError(e);
		} else if (socketWriter != null) {
			socketWriter.closeWithError(e);
		} else {
			closedException = e;
		}

		if (receiveMessageCallback != null) {
			receiveMessageCallback.setException(e);
		} else if (!writeCallbacks.isEmpty()) {
			for (SettableStage writeCallback : writeCallbacks) {
				writeCallback.setException(e);
			}
		}

		if (readBuf != null) {
			readBuf.recycle();
			readBuf = null;
		}
	}

	@Override
	public String toString() {
		return "{asyncTcpSocket=" + asyncTcpSocket + "}";
	}

}
