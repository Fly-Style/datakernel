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
import io.datakernel.bytebuf.ByteBufQueue;
import io.datakernel.exception.ParseException;
import io.datakernel.exception.TruncatedDataException;
import io.datakernel.serializer.BufferSerializer;
import io.datakernel.stream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.datakernel.stream.StreamStatus.END_OF_STREAM;
import static java.lang.String.format;

/**
 * Represent deserializer which deserializes data from ByteBuffer to some type. Is a {@link AbstractStreamTransformer_1_1}
 * which receives ByteBufs and streams specified type.
 *
 * @param <T> original type of data
 */
public final class StreamBinaryDeserializer<T> implements HasLogging, StreamTransformer<ByteBuf, T> {
	private static final Logger logger = LoggerFactory.getLogger(StreamBinaryDeserializer.class);

	public static final ParseException HEADER_SIZE_EXCEPTION = new ParseException("Header size is too large");
	public static final ParseException DESERIALIZED_SIZE_EXCEPTION = new ParseException("Deserialized size != parsed data size");

	private final BufferSerializer<T> valueSerializer;

	private Input input;
	private Output output;

	// region creators
	private StreamBinaryDeserializer(BufferSerializer<T> valueSerializer) {
		this.valueSerializer = valueSerializer;
		this.input = new Input();
		this.output = new Output(valueSerializer);
		setLogger(StreamLogger.of(logger, this));
	}

	/**
	 * Creates a new instance of this class with default size of byte buffer pool - 16
	 *
	 * @param valueSerializer specified BufferSerializer for this type
	 */
	public static <T> StreamBinaryDeserializer<T> create(BufferSerializer<T> valueSerializer) {
		return new StreamBinaryDeserializer<>(valueSerializer);
	}
	// endregion

	@Override
	public StreamConsumer<ByteBuf> getInput() {
		return input;
	}

	@Override
	public StreamProducer<T> getOutput() {
		return output;
	}

	private final class Input extends AbstractStreamConsumer<ByteBuf> {

		@Override
		protected void onEndOfStream() {
			output.produce();
		}

		@Override
		protected void onError(Throwable t) {
			output.closeWithError(t);
		}
	}

	private final class Output extends AbstractStreamProducer<T> implements StreamDataReceiver<ByteBuf> {
		private final ByteBufQueue queue = ByteBufQueue.create();

		private final BufferSerializer<T> valueSerializer;

		private Output(BufferSerializer<T> valueSerializer) {
			this.valueSerializer = valueSerializer;
		}

		@Override
		public void onData(ByteBuf buf) {
			queue.add(buf);
			produce();
		}

		@Override
		protected void onWired() {
			super.onWired();
		}

		@Override
		protected void onSuspended() {
			input.getProducer().suspend();
		}

		@Override
		protected void produce() {
			try {
				while (isReceiverReady() && queue.hasRemaining()) {
					int dataSize = tryPeekSize(queue);
					int headerSize = dataSize >>> 24;
					int size = headerSize + (dataSize & 0xFFFFFF);

					if (headerSize == 0)
						break;

					if (!queue.hasRemainingBytes(size))
						break;

					ByteBuf buf = queue.takeExactSize(size);
					buf.moveReadPosition(headerSize);

					T item;
					try {
						item = valueSerializer.deserialize(buf);
					} catch (Exception e) {
						throw new ParseException("Deserialization error", e);
					}

					if (buf.canRead())
						throw DESERIALIZED_SIZE_EXCEPTION;
					buf.recycle();

					send(item);
				}

				if (isReceiverReady()) {
					input.getProducer().produce(this);

					if (input.getStatus() == END_OF_STREAM) {
						if (queue.isEmpty()) {
							output.sendEndOfStream();
						} else {
							throw new TruncatedDataException(format("Truncated serialized data stream, %s : %s", this, queue));
						}
					}
				}
			} catch (ParseException e) {
				closeWithError(e);
			}
		}

		@Override
		protected void onError(Throwable t) {
			input.closeWithError(t);
		}

		@Override
		protected void cleanup() {
			queue.clear();
		}
	}

	private static int tryPeekSize(ByteBufQueue queue) throws ParseException {
		assert queue.hasRemaining();
		int dataSize = 0;
		int headerSize = 0;
		byte b = queue.peekByte();
		if (b >= 0) {
			dataSize = b;
			headerSize = 1;
		} else if (queue.hasRemainingBytes(2)) {
			dataSize = b & 0x7f;
			b = queue.peekByte(1);
			if (b >= 0) {
				dataSize |= (b << 7);
				headerSize = 2;
			} else if (queue.hasRemainingBytes(3)) {
				dataSize |= ((b & 0x7f) << 7);
				b = queue.peekByte(2);
				if (b >= 0) {
					dataSize |= (b << 14);
					headerSize = 3;
				} else
					throw HEADER_SIZE_EXCEPTION;
			}
		}
		return (headerSize << 24) + dataSize;
	}
}
