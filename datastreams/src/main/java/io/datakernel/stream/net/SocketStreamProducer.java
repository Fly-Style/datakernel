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

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufQueue;
import io.datakernel.eventloop.AsyncTcpSocket;
import io.datakernel.stream.AbstractStreamProducer;

final class SocketStreamProducer extends AbstractStreamProducer<ByteBuf> {
	private final AsyncTcpSocket asyncTcpSocket;
	protected final ByteBufQueue readQueue = new ByteBufQueue();
	private boolean readEndOfStream;

	// region creators
	private SocketStreamProducer(AsyncTcpSocket asyncTcpSocket) {
		this.asyncTcpSocket = asyncTcpSocket;
	}

	public static SocketStreamProducer create(AsyncTcpSocket asyncTcpSocket) {
		return new SocketStreamProducer(asyncTcpSocket);
	}
	// endregion

	@Override
	protected void produce(AsyncProduceController async) {
		while (isReceiverReady() && readQueue.hasRemaining()) {
			ByteBuf buf = readQueue.take();
			send(buf);
		}
		if (readEndOfStream) {
			if (isReceiverReady()) {
				if (readQueue.hasRemaining()) {
					ByteBuf buf = readQueue.takeRemaining();
					send(buf);
				}
				sendEndOfStream();
			}
		} else if (readQueue.remainingBufs() <= 1) {
			asyncTcpSocket.read();
		}
	}

	@Override
	protected void cleanup() {
		while (readQueue.hasRemaining()) {
			readQueue.take().recycle();
		}
	}

	@Override
	protected void onError(Throwable t) {
	}

	public void onRead(ByteBuf buf) {
		readQueue.add(buf);
		tryProduce();
	}

	public void onReadEndOfStream() {
		this.readEndOfStream = true;
		tryProduce();
	}

	public boolean isClosed() {
		return !isWired() || getStatus().isClosed();
	}
}
