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
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.TestStreamConsumers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.TestUtils.*;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class StreamUnionTest {
	@Test
	public void test1() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		StreamUnion<Integer> streamUnion = StreamUnion.create();

		StreamProducer<Integer> source0 = StreamProducer.of();
		StreamProducer<Integer> source1 = StreamProducer.of(1);
		StreamProducer<Integer> source2 = StreamProducer.of(2, 3);
		StreamProducer<Integer> source3 = StreamProducer.of();
		StreamProducer<Integer> source4 = StreamProducer.of(4, 5);
		StreamProducer<Integer> source5 = StreamProducer.of(6);
		StreamProducer<Integer> source6 = StreamProducer.of();

		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create();

		source0.streamTo(streamUnion.newInput());
		source1.streamTo(streamUnion.newInput());
		source2.streamTo(streamUnion.newInput());
		source3.streamTo(streamUnion.newInput());
		source4.streamTo(streamUnion.newInput());
		source5.streamTo(streamUnion.newInput());
		source6.streamTo(streamUnion.newInput());
		streamUnion.getOutput().streamTo(consumer.apply(TestStreamConsumers.randomlySuspending()));
		eventloop.run();

		List<Integer> result = consumer.getList();
		Collections.sort(result);
		assertEquals(asList(1, 2, 3, 4, 5, 6), result);

		assertEndOfStream(source0);
		assertEndOfStream(source1);
		assertEndOfStream(source2);
		assertEndOfStream(source3);
		assertEndOfStream(source4);
		assertEndOfStream(source5);
		assertEndOfStream(source6);

		assertEndOfStream(streamUnion.getOutput());
		assertConsumersEndOfStream(streamUnion.getInputs());
	}

	@Test
	public void testWithError() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		StreamUnion<Integer> streamUnion = StreamUnion.create();

		StreamProducer<Integer> source0 = StreamProducer.of(1, 2, 3);
		StreamProducer<Integer> source1 = StreamProducer.of(4, 5);
		StreamProducer<Integer> source2 = StreamProducer.of(6, 7);

		List<Integer> list = new ArrayList<>();
		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create(list);

		source0.streamTo(streamUnion.newInput());
		source1.streamTo(streamUnion.newInput());
		source2.streamTo(streamUnion.newInput());

		streamUnion.getOutput().streamTo(consumer.apply(TestStreamConsumers.decorator((context, dataAcceptor) ->
						item -> {
							dataAcceptor.accept(item);
							if (item == 1) {
								context.closeWithError(new ExpectedException("Test Exception"));
							}
						})));

		eventloop.run();

		assertEquals(Arrays.asList(6, 7, 4, 5, 1), list);
		assertClosedWithError(source0);
		assertEndOfStream(source1);
		assertEndOfStream(source2);

		assertClosedWithError(streamUnion.getOutput());
		assertClosedWithError(streamUnion.getInput(0));
		assertClosedWithError(streamUnion.getInput(1));
		assertClosedWithError(streamUnion.getInput(2));
	}

	@Test
	public void testProducerWithError() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		StreamUnion<Integer> streamUnion = StreamUnion.create();

		StreamProducer<Integer> source0 = StreamProducer.concat(
				StreamProducer.ofIterable(Arrays.asList(1, 2)),
				StreamProducer.closingWithError(new ExpectedException("Test Exception"))
		);
		StreamProducer<Integer> source1 = StreamProducer.concat(
				StreamProducer.ofIterable(Arrays.asList(7, 8, 9)),
				StreamProducer.closingWithError(new ExpectedException("Test Exception"))
		);

		List<Integer> list = new ArrayList<>();
		StreamConsumer<Integer> consumer = StreamConsumerToList.create(list);

		source0.streamTo(streamUnion.newInput());
		source1.streamTo(streamUnion.newInput());

		streamUnion.getOutput().streamTo(consumer.apply(TestStreamConsumers.oneByOne()));
		eventloop.run();

		assertTrue(list.size() == 3);
		assertClosedWithError(streamUnion.getOutput());
		assertConsumersClosedWithError(streamUnion.getInputs());
	}

}
