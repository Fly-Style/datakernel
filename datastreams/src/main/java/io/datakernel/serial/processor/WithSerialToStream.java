package io.datakernel.serial.processor;

import io.datakernel.serial.SerialSupplier;
import io.datakernel.serial.SerialSupplierModifier;
import io.datakernel.stream.StreamProducer;

public interface WithSerialToStream<B extends WithSerialToStream<B, I, O>, I, O> extends
		WithSerialInput<B, I>, StreamProducer<O>,
		SerialSupplierModifier<I, StreamProducer<O>> {

	@Override
	default StreamProducer<O> apply(SerialSupplier<I> supplier) {
		setInput(supplier);
		return this;
	}
}
