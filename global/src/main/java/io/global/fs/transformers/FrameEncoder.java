/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
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

package io.global.fs.transformers;

import io.datakernel.async.AbstractAsyncProcess;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.serial.SerialConsumer;
import io.datakernel.serial.SerialInput;
import io.datakernel.serial.SerialOutput;
import io.datakernel.serial.SerialSupplier;
import io.datakernel.serial.processor.WithSerialToSerial;
import io.global.fs.api.DataFrame;

/**
 * Encodes stream of frames into a stream of bytebufs to transmit or store those frames.
 * <p>
 * It's counterpart is the {@link FrameDecoder}.
 */
public final class FrameEncoder extends AbstractAsyncProcess implements WithSerialToSerial<FrameEncoder, DataFrame, ByteBuf> {
	private static final byte[] DATA_HEADER = new byte[]{0};
	private static final byte[] CHECKPOINT_HEADER = new byte[]{1};

	protected SerialSupplier<DataFrame> input;
	protected SerialConsumer<ByteBuf> output;

	@Override
	public SerialInput<DataFrame> getInput() {
		return input -> {
			this.input = sanitize(input);
			if (this.output != null) startProcess();
			return getProcessResult();
		};
	}

	@Override
	public SerialOutput<ByteBuf> getOutput() {
		return output -> {
			this.output = sanitize(output);
			if (this.input != null) startProcess();
		};
	}

	@Override
	protected final void doProcess() {
		input.get()
				.whenResult(item -> {
					if (item != null) {
						ByteBuf data = item.isBuf() ? item.getBuf() : ByteBuf.wrapForReading(item.getCheckpoint().toBytes());
						ByteBuf sizeBuf = ByteBufPool.allocate(5);
						sizeBuf.writeVarInt(data.readRemaining() + 1); // + 1 is for that header byte
						output.accept(sizeBuf)
								.thenCompose($ -> output.accept(ByteBuf.wrapForReading(item.isBuf() ? DATA_HEADER : CHECKPOINT_HEADER)))
								.thenCompose($ -> output.accept(data))
								.whenResult($ -> doProcess());
					} else {
						output.accept(null)
								.whenResult($ -> completeProcess());
					}
				});
	}

	@Override
	protected final void doCloseWithError(Throwable e) {
		input.close(e);
		output.close(e);
	}
}