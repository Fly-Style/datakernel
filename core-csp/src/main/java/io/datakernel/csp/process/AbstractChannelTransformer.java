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

package io.datakernel.csp.process;

import io.datakernel.async.Promise;
import io.datakernel.csp.*;
import io.datakernel.csp.dsl.WithChannelTransformer;

import static io.datakernel.util.Preconditions.checkState;

public abstract class AbstractChannelTransformer<S extends AbstractChannelTransformer<S, I, O>, I, O>
		extends AbstractCommunicatingProcess
		implements WithChannelTransformer<S, I, O> {
	protected ChannelSupplier<I> input;
	protected ChannelConsumer<O> output;

	protected final Promise<Void> send(O item) {
		return output.accept(item);
	}

	protected final Promise<Void> sendEndOfStream() {
		return output.accept(null);
	}

	protected abstract Promise<Void> onItem(I item);

	protected Promise<Void> onProcessFinish() {
		return sendEndOfStream();
	}

	protected Promise<Void> onProcessStart() {
		return Promise.complete();
	}

	@Override
	protected void beforeProcess() {
		checkState(input != null, "Input was not set");
		checkState(output != null, "Output was not set");
	}

	@Override
	protected void doProcess() {
		onProcessStart()
				.whenComplete(($, e) -> {
					if (e == null) {
						loop();
					} else {
						close(e);
					}
				});
	}

	private void loop() {
		input.get()
				.then(item ->
						item != null ?
								onItem(item)
										.whenResult($ -> loop()) :
								onProcessFinish()
										.whenResult($ -> completeProcess()))
				.whenException(this::close);
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public ChannelInput<I> getInput() {
		return input -> {
			this.input = sanitize(input);
			if (this.input != null && this.output != null) startProcess();
			return getProcessCompletion();
		};
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public ChannelOutput<O> getOutput() {
		return output -> {
			this.output = sanitize(output);
			if (this.input != null && this.output != null) startProcess();
		};
	}

	@Override
	protected final void doClose(Throwable e) {
		input.close(e);
		output.close(e);
	}
}
