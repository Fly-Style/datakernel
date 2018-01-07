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

import io.datakernel.eventloop.Eventloop;
import io.datakernel.exception.ExpectedException;
import io.datakernel.stream.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.DataStreams.stream;
import static io.datakernel.stream.StreamStatus.CLOSED_WITH_ERROR;
import static io.datakernel.stream.StreamStatus.END_OF_STREAM;
import static io.datakernel.stream.TestUtils.*;
import static io.datakernel.stream.processor.StreamReducers.mergeDeduplicateReducer;
import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;
import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class StreamReducerTest {
	@Test
	public void testEmpty() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		StreamProducer<Integer> source = StreamProducers.ofIterable(EMPTY_LIST);

		StreamReducer<Integer, Integer, Void> streamReducer = StreamReducer.<Integer, Integer, Void>create(Integer::compareTo)
				.withBufferSize(1);
		StreamReducers.Reducer<Integer, Integer, Integer, Void> reducer = mergeDeduplicateReducer();

		StreamConsumerToList<Integer> consumer = StreamConsumerToList.randomlySuspending();

		stream(source, streamReducer.newInput(Function.identity(), reducer));
		stream(streamReducer.getOutput(), consumer);

		eventloop.run();
		assertEquals(EMPTY_LIST, consumer.getList());
		assertStatus(END_OF_STREAM, source);
		assertStatus(END_OF_STREAM, streamReducer.getOutput());
		assertConsumerStatuses(END_OF_STREAM, streamReducer.getInputs());
	}

	@Test
	public void testDeduplicate() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		StreamProducer<Integer> source0 = StreamProducers.ofIterable(EMPTY_LIST);
		StreamProducer<Integer> source1 = StreamProducers.of(7);
		StreamProducer<Integer> source2 = StreamProducers.of(3, 4, 6);
		StreamProducer<Integer> source3 = StreamProducers.ofIterable(EMPTY_LIST);
		StreamProducer<Integer> source4 = StreamProducers.of(2, 3, 5);
		StreamProducer<Integer> source5 = StreamProducers.of(1, 3);
		StreamProducer<Integer> source6 = StreamProducers.of(1, 3);
		StreamProducer<Integer> source7 = StreamProducers.ofIterable(EMPTY_LIST);

		StreamReducer<Integer, Integer, Void> streamReducer = StreamReducer.<Integer, Integer, Void>create(Integer::compareTo)
				.withBufferSize(1);
		Function<Integer, Integer> keyFunction = Function.identity();
		StreamReducers.Reducer<Integer, Integer, Integer, Void> reducer = mergeDeduplicateReducer();

		StreamConsumerToList<Integer> consumer = StreamConsumerToList.randomlySuspending();

		stream(source0, streamReducer.newInput(keyFunction, reducer));
		stream(source1, streamReducer.newInput(keyFunction, reducer));
		stream(source2, streamReducer.newInput(keyFunction, reducer));
		stream(source3, streamReducer.newInput(keyFunction, reducer));
		stream(source4, streamReducer.newInput(keyFunction, reducer));
		stream(source5, streamReducer.newInput(keyFunction, reducer));
		stream(source6, streamReducer.newInput(keyFunction, reducer));
		stream(source7, streamReducer.newInput(keyFunction, reducer));
		stream(streamReducer.getOutput(), consumer);

		eventloop.run();
		assertEquals(asList(1, 2, 3, 4, 5, 6, 7), consumer.getList());
		assertStatus(END_OF_STREAM, source0);
		assertStatus(END_OF_STREAM, source1);
		assertStatus(END_OF_STREAM, source2);
		assertStatus(END_OF_STREAM, source3);
		assertStatus(END_OF_STREAM, source4);
		assertStatus(END_OF_STREAM, source5);
		assertStatus(END_OF_STREAM, source6);
		assertStatus(END_OF_STREAM, source7);

		assertStatus(END_OF_STREAM, streamReducer.getOutput());
		assertConsumerStatuses(END_OF_STREAM, streamReducer.getInputs());
	}

	@Test
	public void testWithError() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		StreamProducer<KeyValue1> source1 = StreamProducers.of(
				new KeyValue1(1, 10.0),
				new KeyValue1(3, 30.0));
		StreamProducer<KeyValue2> source2 = StreamProducers.of(
				new KeyValue2(1, 10.0),
				new KeyValue2(3, 30.0));
		StreamProducer<KeyValue3> source3 = StreamProducers.of(
				new KeyValue3(2, 10.0, 20.0),
				new KeyValue3(3, 10.0, 20.0));

		StreamReducer<Integer, KeyValueResult, KeyValueResult> streamReducer = StreamReducer.<Integer, KeyValueResult, KeyValueResult>create(Integer::compareTo)
				.withBufferSize(1);

		final List<KeyValueResult> list = new ArrayList<>();
		StreamConsumerToList<KeyValueResult> consumer = new StreamConsumerToList<KeyValueResult>(list) {
			@Override
			public void onData(KeyValueResult item) {
				list.add(item);
				if (list.size() == 1) {
					closeWithError(new ExpectedException("Test Exception"));
					return;
				}
				getProducer().suspend();
				eventloop.post(() -> getProducer().produce(this));
			}
		};

		StreamConsumer<KeyValue1> streamConsumer1 = streamReducer.newInput(input -> input.key, KeyValue1.REDUCER);
		stream(source1, streamConsumer1);

		StreamConsumer<KeyValue2> streamConsumer2 = streamReducer.newInput(input -> input.key, KeyValue2.REDUCER);
		stream(source2, streamConsumer2);

		StreamConsumer<KeyValue3> streamConsumer3 = streamReducer.newInput(input -> input.key, KeyValue3.REDUCER);
		stream(source3, streamConsumer3);

		stream(streamReducer.getOutput(), consumer);

		eventloop.run();

//		assertEquals(1, list.size());

		assertStatus(CLOSED_WITH_ERROR, source1);
		assertStatus(END_OF_STREAM, source2);
		assertStatus(END_OF_STREAM, source3);

		assertStatus(CLOSED_WITH_ERROR, streamReducer.getOutput());
		assertArrayEquals(new StreamStatus[]{CLOSED_WITH_ERROR, END_OF_STREAM, END_OF_STREAM},
				consumerStatuses(streamReducer.getInputs()));
	}

	@Test
	public void testProducerDisconnectWithError() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		StreamProducer<KeyValue1> source1 = StreamProducers.ofIterable(
				asList(new KeyValue1(1, 10.0), new KeyValue1(3, 30.0)));

		StreamProducer<KeyValue2> source2 = StreamProducers.closingWithError(new Exception("Test Exception"));

		StreamProducer<KeyValue3> source3 = StreamProducers.ofIterable(
				asList(new KeyValue3(2, 10.0, 20.0), new KeyValue3(3, 10.0, 20.0)));

		StreamReducer<Integer, KeyValueResult, KeyValueResult> streamReducer = StreamReducer.<Integer, KeyValueResult, KeyValueResult>create(Integer::compareTo)
				.withBufferSize(1);

		List<KeyValueResult> list = new ArrayList<>();
		StreamConsumerToList<KeyValueResult> consumer = StreamConsumerToList.create(list);

		StreamConsumer<KeyValue1> streamConsumer1 = streamReducer.newInput(input -> input.key, KeyValue1.REDUCER);
		stream(source1, streamConsumer1);

		StreamConsumer<KeyValue2> streamConsumer2 = streamReducer.newInput(input -> input.key, KeyValue2.REDUCER);
		stream(source2, streamConsumer2);

		StreamConsumer<KeyValue3> streamConsumer3 = streamReducer.newInput(input -> input.key, KeyValue3.REDUCER);
		stream(source3, streamConsumer3);

		stream(streamReducer.getOutput(), consumer);

		eventloop.run();
		assertTrue(list.size() == 0);
		assertStatus(CLOSED_WITH_ERROR, source1);
		assertStatus(END_OF_STREAM, source3);
	}

	private static final class KeyValue1 {
		public int key;
		public double metric1;

		private KeyValue1(int key, double metric1) {
			this.key = key;
			this.metric1 = metric1;
		}

		public static final StreamReducers.ReducerToAccumulator<Integer, KeyValue1, KeyValueResult> REDUCER_TO_ACCUMULATOR = new StreamReducers.ReducerToAccumulator<Integer, KeyValue1, KeyValueResult>() {
			@Override
			public KeyValueResult createAccumulator(Integer key) {
				return new KeyValueResult(key, 0.0, 0.0, 0.0);
			}

			@Override
			public KeyValueResult accumulate(KeyValueResult accumulator, KeyValue1 value) {
				accumulator.metric1 += value.metric1;
				return accumulator;
			}
		};

		public static StreamReducers.Reducer<Integer, KeyValue1, KeyValueResult, KeyValueResult> REDUCER = new StreamReducers.Reducer<Integer, KeyValue1, KeyValueResult, KeyValueResult>() {
			@Override
			public KeyValueResult onFirstItem(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValue1 firstValue) {
				return new KeyValueResult(key, firstValue.metric1, 0.0, 0.0);
			}

			@Override
			public KeyValueResult onNextItem(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValue1 nextValue, KeyValueResult accumulator) {
				accumulator.metric1 += nextValue.metric1;
				return accumulator;
			}

			@Override
			public void onComplete(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValueResult accumulator) {
				stream.onData(accumulator);
			}

		};
	}

	private static final class KeyValue2 {
		public int key;
		public double metric2;

		private KeyValue2(int key, double metric2) {
			this.key = key;
			this.metric2 = metric2;
		}

		public static final StreamReducers.ReducerToAccumulator<Integer, KeyValue2, KeyValueResult> REDUCER_TO_ACCUMULATOR = new StreamReducers.ReducerToAccumulator<Integer, KeyValue2, KeyValueResult>() {
			@Override
			public KeyValueResult createAccumulator(Integer key) {
				return new KeyValueResult(key, 0.0, 0.0, 0.0);
			}

			@Override
			public KeyValueResult accumulate(KeyValueResult accumulator, KeyValue2 value) {
				accumulator.metric2 += value.metric2;
				return accumulator;
			}
		};

		public static StreamReducers.Reducer<Integer, KeyValue2, KeyValueResult, KeyValueResult> REDUCER = new StreamReducers.Reducer<Integer, KeyValue2, KeyValueResult, KeyValueResult>() {
			@Override
			public KeyValueResult onFirstItem(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValue2 firstValue) {
				return new KeyValueResult(key, 0.0, firstValue.metric2, 0.0);
			}

			@Override
			public KeyValueResult onNextItem(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValue2 nextValue, KeyValueResult accumulator) {
				accumulator.metric2 += nextValue.metric2;
				return accumulator;
			}

			@Override
			public void onComplete(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValueResult accumulator) {
				stream.onData(accumulator);
			}
		};
	}

	private static final class KeyValue3 {
		public int key;
		public double metric2;
		public double metric3;

		private KeyValue3(int key, double metric2, double metric3) {
			this.key = key;
			this.metric2 = metric2;
			this.metric3 = metric3;
		}

		public static final StreamReducers.ReducerToAccumulator<Integer, KeyValue3, KeyValueResult> REDUCER_TO_ACCUMULATOR = new StreamReducers.ReducerToAccumulator<Integer, KeyValue3, KeyValueResult>() {
			@Override
			public KeyValueResult createAccumulator(Integer key) {
				return new KeyValueResult(key, 0.0, 0.0, 0.0);
			}

			@Override
			public KeyValueResult accumulate(KeyValueResult accumulator, KeyValue3 value) {
				accumulator.metric2 += value.metric2;
				accumulator.metric3 += value.metric3;
				return accumulator;
			}
		};

		public static StreamReducers.Reducer<Integer, KeyValue3, KeyValueResult, KeyValueResult> REDUCER = new StreamReducers.Reducer<Integer, KeyValue3, KeyValueResult, KeyValueResult>() {
			@Override
			public KeyValueResult onFirstItem(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValue3 firstValue) {
				return new KeyValueResult(key, 0.0, firstValue.metric2, firstValue.metric3);
			}

			@Override
			public KeyValueResult onNextItem(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValue3 nextValue, KeyValueResult accumulator) {
				accumulator.metric2 += nextValue.metric2;
				accumulator.metric3 += nextValue.metric3;

				return accumulator;
			}

			@Override
			public void onComplete(StreamDataReceiver<KeyValueResult> stream, Integer key, KeyValueResult accumulator) {
				stream.onData(accumulator);
			}
		};
	}

	private static final class KeyValueResult {
		public int key;
		public double metric1;
		public double metric2;
		public double metric3;

		KeyValueResult(int key, double metric1, double metric2, double metric3) {
			this.key = key;
			this.metric1 = metric1;
			this.metric2 = metric2;
			this.metric3 = metric3;
		}

		@Override
		public boolean equals(Object o) {
			KeyValueResult that = (KeyValueResult) o;

			if (key != that.key) return false;
			if (Double.compare(that.metric1, metric1) != 0) return false;
			if (Double.compare(that.metric2, metric2) != 0) return false;
			if (Double.compare(that.metric3, metric3) != 0) return false;

			return true;
		}

		@Override
		public String toString() {
			return "KeyValueResult{" +
					"key=" + key +
					", metric1=" + metric1 +
					", metric2=" + metric2 +
					", metric3=" + metric3 +
					'}';
		}
	}

	@Test
	public void test2() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		StreamProducer<KeyValue1> source1 = StreamProducers.of(new KeyValue1(1, 10.0), new KeyValue1(3, 30.0));
		StreamProducer<KeyValue2> source2 = StreamProducers.of(new KeyValue2(1, 10.0), new KeyValue2(3, 30.0));
		StreamProducer<KeyValue3> source3 = StreamProducers.of(new KeyValue3(2, 10.0, 20.0), new KeyValue3(3, 10.0, 20.0));

		StreamReducer<Integer, KeyValueResult, KeyValueResult> streamReducer = StreamReducer.<Integer, KeyValueResult, KeyValueResult>create(Integer::compareTo)
				.withBufferSize(1);

		StreamConsumerToList<KeyValueResult> consumer = StreamConsumerToList.randomlySuspending(new ArrayList<>(), new Random(1));

		StreamConsumer<KeyValue1> streamConsumer1 = streamReducer.newInput(input -> input.key, KeyValue1.REDUCER_TO_ACCUMULATOR.inputToOutput());
		stream(source1, streamConsumer1);

		StreamConsumer<KeyValue2> streamConsumer2 = streamReducer.newInput(input -> input.key, KeyValue2.REDUCER_TO_ACCUMULATOR.inputToOutput());
		stream(source2, streamConsumer2);

		StreamConsumer<KeyValue3> streamConsumer3 = streamReducer.newInput(input -> input.key, KeyValue3.REDUCER_TO_ACCUMULATOR.inputToOutput());
		stream(source3, streamConsumer3);

		stream(streamReducer.getOutput(), consumer);

		eventloop.run();
		assertEquals(asList(
				new KeyValueResult(1, 10.0, 10.0, 0.0),
				new KeyValueResult(2, 0.0, 10.0, 20.0),
				new KeyValueResult(3, 30.0, 40.0, 20.0)),
				consumer.getList());
		assertStatus(END_OF_STREAM, source1);
		assertStatus(END_OF_STREAM, source2);
		assertStatus(END_OF_STREAM, source3);
	}

	@Test
	public void test3() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		StreamProducer<KeyValue1> source1 = StreamProducers.of(new KeyValue1(1, 10.0), new KeyValue1(3, 30.0));
		StreamProducer<KeyValue2> source2 = StreamProducers.of(new KeyValue2(1, 10.0), new KeyValue2(3, 30.0));
		StreamProducer<KeyValue3> source3 = StreamProducers.of(new KeyValue3(2, 10.0, 20.0), new KeyValue3(3, 10.0, 20.0));

		StreamReducer<Integer, KeyValueResult, KeyValueResult> streamReducer = StreamReducer.<Integer, KeyValueResult, KeyValueResult>create(Integer::compareTo)
				.withBufferSize(1);

		StreamConsumerToList<KeyValueResult> consumer = StreamConsumerToList.randomlySuspending();

		StreamConsumer<KeyValue1> streamConsumer1 = streamReducer.newInput(input -> input.key, KeyValue1.REDUCER);
		stream(source1, streamConsumer1);

		StreamConsumer<KeyValue2> streamConsumer2 = streamReducer.newInput(input -> input.key, KeyValue2.REDUCER);
		stream(source2, streamConsumer2);

		StreamConsumer<KeyValue3> streamConsumer3 = streamReducer.newInput(input -> input.key, KeyValue3.REDUCER);
		stream(source3, streamConsumer3);

		stream(streamReducer.getOutput(), consumer);

		eventloop.run();
		assertEquals(asList(
				new KeyValueResult(1, 10.0, 10.0, 0.0),
				new KeyValueResult(2, 0.0, 10.0, 20.0),
				new KeyValueResult(3, 30.0, 40.0, 20.0)),
				consumer.getList());
		assertStatus(END_OF_STREAM, source1);
		assertStatus(END_OF_STREAM, source2);
		assertStatus(END_OF_STREAM, source3);
	}

	@Test
	public void testWithoutConsumer() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		StreamProducer<Integer> source0 = StreamProducers.ofIterable(EMPTY_LIST);
		StreamProducer<Integer> source1 = StreamProducers.of(7);
		StreamProducer<Integer> source2 = StreamProducers.of(3, 4, 6);
		StreamProducer<Integer> source3 = StreamProducers.ofIterable(EMPTY_LIST);
		StreamProducer<Integer> source4 = StreamProducers.of(2, 3, 5);
		StreamProducer<Integer> source5 = StreamProducers.of(1, 3);
		StreamProducer<Integer> source6 = StreamProducers.of(1, 3);
		StreamProducer<Integer> source7 = StreamProducers.ofIterable(EMPTY_LIST);

		StreamReducer<Integer, Integer, Void> streamReducer = StreamReducer.<Integer, Integer, Void>create(Integer::compareTo)
				.withBufferSize(1);
		Function<Integer, Integer> keyFunction = Function.identity();
		StreamReducers.Reducer<Integer, Integer, Integer, Void> reducer = mergeDeduplicateReducer();

		StreamConsumerToList<Integer> consumer = StreamConsumerToList.randomlySuspending();

		stream(source0, streamReducer.newInput(keyFunction, reducer));
		stream(source1, streamReducer.newInput(keyFunction, reducer));
		stream(source2, streamReducer.newInput(keyFunction, reducer));
		stream(source3, streamReducer.newInput(keyFunction, reducer));
		stream(source4, streamReducer.newInput(keyFunction, reducer));
		stream(source5, streamReducer.newInput(keyFunction, reducer));
		stream(source6, streamReducer.newInput(keyFunction, reducer));
		stream(source7, streamReducer.newInput(keyFunction, reducer));
		eventloop.run();

		stream(streamReducer.getOutput(), consumer);
		eventloop.run();

		assertEquals(asList(1, 2, 3, 4, 5, 6, 7), consumer.getList());
		assertStatus(END_OF_STREAM, source0);
		assertStatus(END_OF_STREAM, source1);
		assertStatus(END_OF_STREAM, source2);
		assertStatus(END_OF_STREAM, source3);
		assertStatus(END_OF_STREAM, source4);
		assertStatus(END_OF_STREAM, source5);
		assertStatus(END_OF_STREAM, source6);
		assertStatus(END_OF_STREAM, source7);

		assertStatus(END_OF_STREAM, streamReducer.getOutput());
		assertConsumerStatuses(END_OF_STREAM, streamReducer.getInputs());
	}

	@Test
	public void testWithoutProducer() throws ExecutionException, InterruptedException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		StreamReducer<Integer, Integer, Void> streamReducer = StreamReducer.<Integer, Integer, Void>create(Integer::compareTo).withBufferSize(1);
		StreamConsumerToList<Integer> toList = StreamConsumerToList.create();
		stream(streamReducer.getOutput(), toList);
		CompletableFuture<Void> future = toList.getEndOfStream().toCompletableFuture();
		eventloop.run();
		future.get();
	}

}
