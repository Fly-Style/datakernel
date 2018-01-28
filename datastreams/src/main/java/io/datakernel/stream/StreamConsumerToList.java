package io.datakernel.stream;

import io.datakernel.async.SettableStage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.datakernel.stream.StreamCapability.LATE_BINDING;
import static io.datakernel.stream.StreamCapability.TERMINAL;

public final class StreamConsumerToList<T> extends AbstractStreamConsumer<T> implements StreamConsumerWithResult<T, List<T>>, StreamDataReceiver<T> {
	protected final List<T> list;
	private final SettableStage<List<T>> resultStage = SettableStage.create();

	private StreamConsumerToList() {
		this(new ArrayList<>());
	}

	private StreamConsumerToList(List<T> list) {
		this.list = list;
	}

	public static <T> StreamConsumerToList<T> create() {
		return new StreamConsumerToList<>();
	}

	public static <T> StreamConsumerToList<T> create(List<T> list) {
		return new StreamConsumerToList<>(list);
	}

	@Override
	public void onData(T item) {
		list.add(item);
	}

	@Override
	protected void onStarted() {
		getProducer().produce(this);
	}

	@Override
	protected void onEndOfStream() {
		if (resultStage != null) {
			resultStage.set(list);
		}
	}

	@Override
	protected void onError(Throwable t) {
		if (resultStage != null) {
			resultStage.setException(t);
		}
	}

	@Override
	public final CompletionStage<List<T>> getResult() {
		return resultStage;
	}

	public final List<T> getList() {
		return list;
	}

	@Override
	public Set<StreamCapability> getCapabilities() {
		return EnumSet.of(LATE_BINDING, TERMINAL);
	}
}
