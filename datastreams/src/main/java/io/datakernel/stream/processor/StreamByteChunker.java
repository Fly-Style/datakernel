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

package io.datakernel.stream.processor;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.stream.*;
import io.datakernel.util.MemSize;

import static io.datakernel.stream.StreamStatus.END_OF_STREAM;
import static java.lang.Math.max;
import static java.lang.Math.min;

public final class StreamByteChunker implements StreamTransformer<ByteBuf, ByteBuf> {
	private final Input input;
	private final Output output;

	// region creators
	private StreamByteChunker(MemSize minChunkSize, MemSize maxChunkSize) {
		this.input = new Input();
		this.output = new Output(minChunkSize, maxChunkSize);
	}

	public static StreamByteChunker create(MemSize minChunkSize, MemSize maxChunkSize) {
		return new StreamByteChunker(minChunkSize, maxChunkSize);
	}
	// endregion

	@Override
	public StreamConsumer<ByteBuf> getInput() {
		return input;
	}

	@Override
	public StreamProducer<ByteBuf> getOutput() {
		return output;
	}

	protected final class Input extends AbstractStreamConsumer<ByteBuf> {
		@Override
		protected void onEndOfStream() {
			output.tryFlushAndClose();
		}

		@Override
		protected void onError(Throwable t) {
			output.closeWithError(t);
		}
	}

	protected final class Output extends AbstractStreamProducer<ByteBuf> implements StreamDataReceiver<ByteBuf> {
		private final int minChunkSize;
		private final int maxChunkSize;
		private ByteBuf internalBuf = ByteBuf.empty();

		public Output(MemSize minChunkSize, MemSize maxChunkSize) {
			this.minChunkSize = minChunkSize.toInt();
			this.maxChunkSize = maxChunkSize.toInt();
		}

		@Override
		protected void produce() {
			tryFlushAndClose(); // chunk and send out any remaining data in internalBuf
			if (isReceiverReady()) { // if more data is needed
				input.getProducer().produce(this);
			}
		}

		@Override
		protected void onSuspended() {
			input.getProducer().suspend();
		}

		@Override
		public void onData(ByteBuf buf) {
			if (!internalBuf.canRead()) {
				while (isReceiverReady() && buf.readRemaining() >= minChunkSize) {
					int chunkSize = min(buf.readRemaining(), maxChunkSize);
					ByteBuf slice = buf.slice(chunkSize);
					send(slice);
					buf.moveReadPosition(chunkSize);
				}
			}

			if (buf.canRead()) {
				internalBuf = ByteBufPool.ensureWriteRemaining(internalBuf,
						max(maxChunkSize - internalBuf.writePosition(), buf.readRemaining()));
				internalBuf.put(buf);
			}
			buf.recycle();

			tryFlushAndClose();
		}

		private void tryFlushAndClose() {
			while (isReceiverReady() && internalBuf.readRemaining() >= minChunkSize) {
				int chunkSize = min(internalBuf.readRemaining(), maxChunkSize);
				assert chunkSize >= minChunkSize && chunkSize <= maxChunkSize;
				ByteBuf slice = internalBuf.slice(internalBuf.readPosition(), chunkSize);
				send(slice);
				internalBuf.moveReadPosition(chunkSize);
			}

			if (!isReceiverReady())
				return;

			if (input.getStatus() == END_OF_STREAM) {
				if (internalBuf.canRead()) {
					output.send(internalBuf);
					internalBuf = null;
				}
				output.sendEndOfStream();
			}
		}

		@Override
		protected void onError(Throwable t) {
			input.closeWithError(t);
		}

		@Override
		protected void cleanup() {
			if (internalBuf != null) {
				internalBuf.recycle();
				internalBuf = null;
			}
		}
	}
}