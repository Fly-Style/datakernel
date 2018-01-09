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
import io.datakernel.stream.StreamConsumerToList;
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.StreamProducerDecorator;
import io.datakernel.stream.StreamProducers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.DataStreams.stream;
import static io.datakernel.stream.StreamStatus.CLOSED_WITH_ERROR;
import static io.datakernel.stream.StreamStatus.END_OF_STREAM;
import static io.datakernel.stream.TestUtils.assertStatus;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class StreamProducerDecoratorTest {
	@SuppressWarnings("unchecked")
	@Test
	public void test2() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		List<Integer> list = new ArrayList<>();

		StreamConsumerToList consumer = new StreamConsumerToList<Integer>(list) {
			@Override
			public void onData(Integer item) {
				if (item == 3) {
					closeWithError(new Exception("Test Exception"));
					return;
				}
				list.add(item);
				getProducer().suspend();
				eventloop.post(() -> getProducer().produce(this));
			}
		};

		StreamProducer<Integer> producer = StreamProducers.of(1, 2, 3, 4, 5);
		StreamProducerDecorator<Integer> producerDecorator = new StreamProducerDecorator<Integer>() {};
		producerDecorator.setActualProducer(producer);

		stream(producerDecorator, consumer);

		eventloop.run();

		assertEquals(list, asList(1, 2));
		assertStatus(CLOSED_WITH_ERROR, producer);
		assertStatus(CLOSED_WITH_ERROR, consumer);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void test1() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		List<Integer> list = new ArrayList<>();
		StreamConsumerToList consumer = StreamConsumerToList.oneByOne(list);
		StreamProducer<Integer> producer = StreamProducers.of(1, 2, 3, 4, 5);
		StreamProducerDecorator<Integer> producerDecorator = new StreamProducerDecorator<Integer>() {};
		producerDecorator.setActualProducer(producer);

		stream(producerDecorator, consumer);

		eventloop.run();

		assertEquals(consumer.getList(), asList(1, 2, 3, 4, 5));
		assertStatus(END_OF_STREAM, producer);
		assertStatus(END_OF_STREAM, consumer);
	}

	@Test
	public void testWithoutConsumer() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		List<Integer> list = new ArrayList<>();
		StreamConsumerToList<Integer> consumer = StreamConsumerToList.oneByOne(list);
		StreamProducer<Integer> producer = StreamProducers.of(1, 2, 3, 4, 5);
		StreamProducerDecorator<Integer> producerDecorator = new StreamProducerDecorator<Integer>() {};
		producerDecorator.setActualProducer(producer);
		StreamFunction<Integer, Integer> function = StreamFunction.create(Function.<Integer>identity());

		stream(producerDecorator, function.getInput());
		eventloop.run();

		stream(function.getOutput(), consumer);
		eventloop.run();

		assertEquals(consumer.getList(), asList(1, 2, 3, 4, 5));
		assertStatus(END_OF_STREAM, producer);
		assertStatus(END_OF_STREAM, consumer);
	}
}
