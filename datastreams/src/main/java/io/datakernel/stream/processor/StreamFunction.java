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

import io.datakernel.async.Stage;
import io.datakernel.stream.*;

import java.util.function.Function;

/**
 * Provides you apply function before sending data to the destination. It is a {@link StreamFunction}
 * which receives specified type and streams set of function's result  to the destination .
 *
 * @param <I> type of input data
 * @param <O> type of output data
 */
public final class StreamFunction<I, O> implements StreamTransformer<I, O> {
	private final Function<I, O> function;
	private final Input input;
	private final Output output;

	// region creators
	private StreamFunction(Function<I, O> function) {
		this.function = function;
		this.input = new Input();
		this.output = new Output();
	}

	public static <I, O> StreamFunction<I, O> create(Function<I, O> function) {
		return new StreamFunction<>(function);
	}

	@Override
	public StreamConsumer<I> getInput() {
		return input;
	}

	@Override
	public StreamProducer<O> getOutput() {
		return output;
	}
	// endregion

	protected final class Input extends AbstractStreamConsumer<I> {
		@Override
		protected Stage<Void> onProducerEndOfStream() {
			return output.sendEndOfStream();
		}

		@Override
		protected void onError(Throwable t) {
			output.closeWithError(t);
		}
	}

	protected final class Output extends AbstractStreamProducer<O> {
		@Override
		protected void onSuspended() {
			input.getProducer().suspend();
		}

		@Override
		protected void onError(Throwable t) {
			input.closeWithError(t);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void onProduce(StreamDataAcceptor<O> dataAcceptor) {
			input.getProducer().produce(
					function == Function.identity() ?
							(StreamDataAcceptor<I>) dataAcceptor :
							item -> dataAcceptor.accept(function.apply(item)));
		}
	}
}
