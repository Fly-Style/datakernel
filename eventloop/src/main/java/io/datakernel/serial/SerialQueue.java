package io.datakernel.serial;

import io.datakernel.annotation.Nullable;
import io.datakernel.async.Cancellable;
import io.datakernel.async.Stage;

public interface SerialQueue<T> extends Cancellable {
	Stage<Void> put(@Nullable T value);

	Stage<T> take();

	default SerialConsumer<T> getConsumer() {
		return new AbstractSerialConsumer<T>(this) {
			@Override
			public Stage<Void> accept(T value) {
				assert !isClosed();
				return put(value);
			}
		};
	}

	default SerialSupplier<T> getSupplier() {
		return new AbstractSerialSupplier<T>(this) {
			@Override
			public Stage<T> get() {
				assert !isClosed();
				return take();
			}
		};
	}

}
