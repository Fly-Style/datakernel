package io.datakernel.stream;

import java.util.Iterator;

import static io.datakernel.stream.DataStreams.bind;

/**
 * Represents {@link AbstractStreamTransformer_1_1}, which created with iterator with {@link AbstractStreamProducer}
 * which will stream to this
 *
 * @param <T> type of received data
 */
class StreamProducerConcat<T> extends AbstractStreamProducer<T> {
	private final Iterator<StreamProducer<T>> iterator;
	private StreamProducer<T> producer;
	private InternalConsumer internalConsumer;

	StreamProducerConcat(Iterator<StreamProducer<T>> iterator) {
		this.iterator = iterator;
	}

	private class InternalConsumer extends AbstractStreamConsumer<T> {
		@Override
		protected void onEndOfStream() {
			eventloop.post(() -> {
				producer = null;
				internalConsumer = null;
				if (isReceiverReady()) {
					onProduce(getCurrentDataReceiver());
				}
			});
		}

		@Override
		protected void onError(Throwable t) {
			StreamProducerConcat.this.closeWithError(t);
		}
	}

	@Override
	protected void onProduce(StreamDataReceiver<T> dataReceiver) {
		assert dataReceiver != null;
		if (producer == null) {
			if (!iterator.hasNext()) {
				eventloop.post(this::sendEndOfStream);
				return;
			}
			producer = iterator.next();
			internalConsumer = new InternalConsumer();
			bind(producer, internalConsumer);
		}
		producer.produce(dataReceiver);
	}

	@Override
	protected void onSuspended() {
		if (producer != null) {
			producer.suspend();
		}
	}

	@Override
	protected void onError(Throwable t) {
		if (producer != null) {
			assert internalConsumer != null;
			internalConsumer.closeWithError(t);
		} else {
			// TODO ?
		}
	}

	@Override
	protected void cleanup() {
		producer = null;
	}

}