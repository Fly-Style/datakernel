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
import io.datakernel.stream.StreamConsumer;
import io.datakernel.stream.StreamConsumerToList;
import io.datakernel.stream.StreamConsumers;
import io.datakernel.stream.StreamProducer;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.StreamConsumers.*;
import static io.datakernel.stream.StreamStatus.CLOSED_WITH_ERROR;
import static io.datakernel.stream.StreamStatus.END_OF_STREAM;
import static io.datakernel.stream.TestUtils.assertProducerStatuses;
import static io.datakernel.stream.TestUtils.assertStatus;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreamSplitterTest {
	private Eventloop eventloop;

	@Before
	public void before() {
		eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
	}

	@Test
	public void test1() {
		StreamProducer<Integer> source = StreamProducer.of(1, 2, 3);
		StreamSplitter<Integer> streamConcat = StreamSplitter.create();
		StreamConsumerToList<Integer> consumerToList1 = StreamConsumerToList.create();
		StreamConsumerToList<Integer> consumerToList2 = StreamConsumerToList.create();

		source.streamTo(streamConcat.getInput());
		streamConcat.newOutput().streamTo(consumerToList1.with(randomlySuspending()));
		streamConcat.newOutput().streamTo(consumerToList2.with(randomlySuspending()));
		eventloop.run();
		assertEquals(asList(1, 2, 3), consumerToList1.getList());
		assertEquals(asList(1, 2, 3), consumerToList2.getList());
		assertStatus(END_OF_STREAM, source);
		assertStatus(END_OF_STREAM, streamConcat.getInput());
		assertProducerStatuses(END_OF_STREAM, streamConcat.getOutputs());
	}

	@Test
	public void testConsumerDisconnectWithError() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		StreamProducer<Integer> source = StreamProducer.of(1, 2, 3, 4, 5);
		StreamSplitter<Integer> streamConcat = StreamSplitter.create();

		List<Integer> toList1 = new ArrayList<>();
		StreamConsumerToList<Integer> consumerToList1 = StreamConsumerToList.create(toList1);
		List<Integer> toList2 = new ArrayList<>();
		StreamConsumerToList<Integer> consumerToList2 = StreamConsumerToList.create(toList2);

		List<Integer> toBadList = new ArrayList<>();
		StreamConsumerToList<Integer> badConsumer = StreamConsumerToList.create(toBadList);

		source.streamTo(streamConcat.getInput());

		streamConcat.newOutput().streamTo(consumerToList1.with(StreamConsumers.oneByOne()));
		streamConcat.newOutput().streamTo(
				badConsumer.with(decorator((context, dataReceiver) ->
						item -> {
							dataReceiver.onData(item);
							if (item == 3) {
								context.closeWithError(new ExpectedException("Test Exception"));
							}
						})));
		streamConcat.newOutput().streamTo(consumerToList2.with(StreamConsumers.oneByOne()));

		eventloop.run();

		assertTrue(toList1.size() == 3);
		assertTrue(toList2.size() == 3);
		assertTrue(toBadList.size() == 3);

		assertStatus(CLOSED_WITH_ERROR, source);
		assertStatus(CLOSED_WITH_ERROR, streamConcat.getInput());
		assertProducerStatuses(CLOSED_WITH_ERROR, streamConcat.getOutputs());
	}

	@Test
	public void testProducerDisconnectWithError() {
		StreamProducer<Integer> source = StreamProducer.concat(
				StreamProducer.of(1),
				StreamProducer.of(2),
				StreamProducer.of(3),
				StreamProducer.closingWithError(new ExpectedException("Test Exception"))
		);

		StreamSplitter<Integer> splitter = StreamSplitter.create();

		List<Integer> list1 = new ArrayList<>();
		StreamConsumer<Integer> consumer1 = StreamConsumerToList.create(list1);
		List<Integer> list2 = new ArrayList<>();
		StreamConsumer<Integer> consumer2 = StreamConsumerToList.create(list2);
		List<Integer> list3 = new ArrayList<>();
		StreamConsumer<Integer> consumer3 = StreamConsumerToList.create(list3);

		source.streamTo(splitter.getInput());
		splitter.newOutput().streamTo(consumer1.with(oneByOne()));
		splitter.newOutput().streamTo(consumer2.with(oneByOne()));
		splitter.newOutput().streamTo(consumer3.with(oneByOne()));

		eventloop.run();

		assertTrue(list1.size() == 3);
		assertTrue(list2.size() == 3);
		assertTrue(list3.size() == 3);

		assertStatus(CLOSED_WITH_ERROR, splitter.getInput());
		assertProducerStatuses(CLOSED_WITH_ERROR, splitter.getOutputs());
	}

	@Test(expected = IllegalStateException.class)
	public void testNoOutputs() throws ExecutionException, InterruptedException {
		StreamSplitter<Integer> splitter = StreamSplitter.create();

		Future<Void> future = StreamProducer.of(1, 2, 3, 4).streamTo(splitter.getInput())
				.getEndOfStream()
				.toCompletableFuture();

		eventloop.run();
		future.get();
	}
}