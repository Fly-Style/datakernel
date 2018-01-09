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
import io.datakernel.stream.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.DataStreams.stream;
import static io.datakernel.stream.StreamStatus.END_OF_STREAM;
import static org.junit.Assert.assertEquals;

public class ProducerOfIteratorTest {

	@Test
	public void test1() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		List<Integer> list = Arrays.asList(1, 2, 3);

		StreamProducer<Integer> producer = StreamProducers.ofIterable(list);
		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create();
		stream(producer, consumer);

		eventloop.run();

		assertEquals(list, consumer.getList());
		assertEquals(END_OF_STREAM, ((AbstractStreamProducer<?>) producer).getStatus());
		assertEquals(END_OF_STREAM, ((AbstractStreamConsumer<?>) consumer).getStatus());
	}

}


