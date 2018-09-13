package io.datakernel.remotefs;

import io.datakernel.async.AbstractAsyncProcess;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.serial.SerialConsumer;
import io.datakernel.serial.SerialSupplier;
import io.datakernel.serial.processor.WithSerialToSerial;

import static io.datakernel.util.Preconditions.checkState;

public class SerialByteBufCutter extends AbstractAsyncProcess
		implements WithSerialToSerial<SerialByteBufCutter, ByteBuf, ByteBuf> {
	private final long offset;

	private long position = 0;

	private SerialSupplier<ByteBuf> input;
	private SerialConsumer<ByteBuf> output;

	// region creators
	private SerialByteBufCutter(long offset) {
		this.offset = offset;
	}

	public static SerialByteBufCutter create(long offset) {
		return new SerialByteBufCutter(offset);
	}

	@Override
	public void setInput(SerialSupplier<ByteBuf> input) {
		checkState(this.input == null, "Input already set");
		this.input = input;
	}

	@Override
	public void setOutput(SerialConsumer<ByteBuf> output) {
		checkState(this.output == null, "Output already set");
		this.output = output;
	}

	@Override
	protected void beforeProcess() {
		checkState(input != null, "Input was not set");
		checkState(output != null, "Output was not set");
	}

	@Override
	protected void doProcess() {
		input.get()
				.async()
				.whenComplete((item, e) -> {
					if (e == null) {
						if (item != null) {
							int size = item.readRemaining();
							position += size;
							if (position <= offset) {
								item.recycle();
								doProcess();
								return;
							}
							if (position - size < offset) {
								item.moveReadPosition(size - (int) (position - offset));
							}
							output.accept(item)
									.whenComplete(($, e2) -> {
										if (e2 == null) {
											doProcess();
										} else {
											closeWithError(e2);
										}
									});
						} else {
							output.accept(null)
									.whenComplete(($, e2) -> completeProcess(e));
						}
					} else {
						closeWithError(e);
					}
				});
	}

	@Override
	protected void doCloseWithError(Throwable e) {
		input.closeWithError(e);
		output.closeWithError(e);
	}
}
