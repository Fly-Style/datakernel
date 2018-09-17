package io.datakernel.stream;

import io.datakernel.async.Stage;
import io.datakernel.serial.SerialBuffer;

public final class StreamProducerEndpoint<T> extends AbstractStreamProducer<T> {
	public static final int DEFAULT_BUFFER_SIZE = 10;

	private final SerialBuffer<T> buffer;

	public StreamProducerEndpoint() {
		this(0, DEFAULT_BUFFER_SIZE);
	}

	public StreamProducerEndpoint(int bufferSize) {
		this(0, bufferSize);
	}

	private StreamProducerEndpoint(int bufferMinSize, int bufferMaxSize) {
		this.buffer = new SerialBuffer<>(bufferMinSize, bufferMaxSize);
	}

	public void add(T item) {
		postProduce();
		buffer.add(item);
	}

	public Stage<Void> put(T item) {
		postProduce();
		return buffer.put(item);
	}

	@SuppressWarnings({"unchecked", "ConstantConditions"})
	@Override
	protected void produce(AsyncProduceController async) {
		if (buffer.getException() == null) {
			while (!buffer.isEmpty()) {
				T item = buffer.poll();
				if (item != null) {
					send(item);
				} else {
					sendEndOfStream();
				}
			}
		} else {
			closeWithError(buffer.getException());
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void onError(Throwable e) {
		buffer.closeWithError(e);
	}
}
