package io.datakernel.stream.stats;

import io.datakernel.async.Promise;
import io.datakernel.stream.*;
import io.datakernel.stream.processor.StreamTransformer;

import java.util.Set;

import static java.util.Collections.emptySet;

public class StreamStatsForwarder<T> implements StreamTransformer<T, T> {
	private final Input input;
	private final Output output;

	private final StreamStats<T> stats;

	private StreamStatsForwarder(StreamStats<T> stats) {
		this.stats = stats;
		this.input = new Input();
		this.output = new Output();
	}

	public static <T> StreamStatsForwarder<T> create(StreamStats<T> stats) {
		return new StreamStatsForwarder<>(stats);
	}

	@Override
	public StreamConsumer<T> getInput() {
		return input;
	}

	@Override
	public StreamSupplier<T> getOutput() {
		return output;
	}

	private class Input extends AbstractStreamConsumer<T> {
		@Override
		protected void onStarted() {
			stats.onStarted();
		}

		@Override
		protected Promise<Void> onEndOfStream() {
			stats.onEndOfStream();
			return output.sendEndOfStream();
		}

		@Override
		protected void onError(Throwable e) {
			output.close(e);
		}

		@Override
		public Set<StreamCapability> getCapabilities() {
			StreamConsumer<T> consumer = output.getConsumer();
			return consumer != null ? consumer.getCapabilities() : emptySet();
		}
	}

	private class Output extends AbstractStreamSupplier<T> {
		@Override
		protected void onProduce(StreamDataAcceptor<T> dataAcceptor) {
			stats.onProduce();
			input.getSupplier().resume(stats.createDataAcceptor(dataAcceptor));
		}

		@Override
		protected void onSuspended() {
			stats.onSuspend();
			input.getSupplier().suspend();
		}

		@Override
		protected void onError(Throwable e) {
			stats.onError(e);
			input.close(e);
		}

		@Override
		public Set<StreamCapability> getCapabilities() {
			StreamSupplier<T> supplier = input.getSupplier();
			return supplier != null ? supplier.getCapabilities() : emptySet();
		}
	}

}
