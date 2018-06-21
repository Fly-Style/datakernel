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
import io.datakernel.stream.StreamConsumerToList;
import io.datakernel.stream.StreamConsumerWithResult;
import io.datakernel.stream.StreamProducer;
import io.datakernel.util.MemSize;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.serializer.asm.BufferSerializers.intSerializer;
import static io.datakernel.stream.StreamConsumers.*;
import static io.datakernel.stream.StreamStatus.CLOSED_WITH_ERROR;
import static io.datakernel.stream.StreamStatus.END_OF_STREAM;
import static io.datakernel.stream.TestUtils.assertStatus;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreamSorterTest {
	@Rule
	public ByteBufRule byteBufRule = new ByteBufRule();

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testStreamStorage() throws Exception {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		ExecutorService executor = Executors.newCachedThreadPool();

		StreamProducer<Integer> source1 = StreamProducer.of(1, 2, 3, 4, 5, 6, 7);
		StreamProducer<Integer> source2 = StreamProducer.of(111);

		StreamSorterStorageImpl<Integer> storage = StreamSorterStorageImpl.create(executor, intSerializer(), tempFolder.getRoot().toPath())
				.withWriteBlockSize(MemSize.of(64));

		StreamConsumerWithResult<Integer, Integer> writer1 = storage.writeStream();
		StreamConsumerWithResult<Integer, Integer> writer2 = storage.writeStream();
		source1.streamTo(writer1);
		source2.streamTo(writer2);

		CompletableFuture<Integer> chunk1 = writer1.getResult().toCompletableFuture();
		CompletableFuture<Integer> chunk2 = writer2.getResult().toCompletableFuture();

		eventloop.run();

		assertStatus(END_OF_STREAM, source1);
		assertStatus(END_OF_STREAM, source2);

		StreamConsumerToList<Integer> consumer1 = StreamConsumerToList.create();
		StreamConsumerToList<Integer> consumer2 = StreamConsumerToList.create();
		storage.readStream(chunk1.get()).streamTo(consumer1.with(oneByOne()));
		storage.readStream(chunk2.get()).streamTo(consumer2.with(randomlySuspending()));
		eventloop.run();
		assertEquals(asList(1, 2, 3, 4, 5, 6, 7), consumer1.getList());
		assertEquals(asList(111), consumer2.getList());

		storage.cleanup(Arrays.asList(chunk1.get(), chunk2.get()));
		eventloop.run();
	}

	@Test
	public void test() throws Exception {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		ExecutorService executor = Executors.newCachedThreadPool();

		StreamProducer<Integer> source = StreamProducer.of(3, 1, 3, 2, 5, 1, 4, 3, 2);

		StreamSorterStorage<Integer> storage = StreamSorterStorageImpl.create(executor, intSerializer(), tempFolder.newFolder().toPath());
		StreamSorter<Integer, Integer> sorter = StreamSorter.create(
				storage, Function.identity(), Integer::compareTo, true, 2);

		StreamConsumerToList<Integer> consumerToList = StreamConsumerToList.create();

		source.streamTo(sorter.getInput());
		sorter.getOutput().streamTo(consumerToList.with(randomlySuspending()));

		eventloop.run();

		assertStatus(END_OF_STREAM, source);
//		assertStatus(END_OF_STREAM, sorter.getOutput());
		assertStatus(END_OF_STREAM, sorter.getInput());
		assertEquals(asList(1, 2, 3, 4, 5), consumerToList.getList());
	}

	@Test
	public void testErrorOnConsumer() throws IOException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		ExecutorService executor = Executors.newCachedThreadPool();

		StreamProducer<Integer> source = StreamProducer.of(3, 1, 3, 2, 5, 1, 4, 3, 2);

		StreamSorterStorage<Integer> storage = StreamSorterStorageImpl.create(executor, intSerializer(), tempFolder.newFolder().toPath());
		StreamSorter<Integer, Integer> sorter = StreamSorter.create(
				storage, Function.identity(), Integer::compareTo, true, 2);

		List<Integer> list = new ArrayList<>();
		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create(list);

		source.streamTo(sorter.getInput());
		sorter.getOutput().streamTo(
				consumer.with(decorator((context, dataReceiver) ->
						item -> {
							dataReceiver.onData(item);
							if (list.size() == 2) {
								context.closeWithError(new ExpectedException());
							}
						})));

		eventloop.run();

//		assertTrue(list.size() == 2);
		assertStatus(END_OF_STREAM, source);
//		assertStatus(CLOSED_WITH_ERROR, sorter.getOutput());
		assertStatus(END_OF_STREAM, sorter.getInput());
		assertStatus(CLOSED_WITH_ERROR, consumer);
	}

	@Test
	public void testErrorOnProducer() throws IOException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		ExecutorService executor = Executors.newCachedThreadPool();

		StreamProducer<Integer> source = StreamProducer.concat(
				StreamProducer.of(3, 1, 3, 2),
				StreamProducer.closingWithError(new ExpectedException())
		);

		StreamSorterStorage<Integer> storage = StreamSorterStorageImpl.create(executor, intSerializer(), tempFolder.newFolder().toPath());
		StreamSorter<Integer, Integer> sorter = StreamSorter.create(
				storage, Function.identity(), Integer::compareTo, true, 2);

		StreamConsumerToList<Integer> consumerToList = StreamConsumerToList.create();

		source.streamTo(sorter.getInput());
		sorter.getOutput().streamTo(consumerToList);

		eventloop.run();

		assertTrue(consumerToList.getList().size() == 0);

		assertStatus(CLOSED_WITH_ERROR, consumerToList);
//		assertStatus(CLOSED_WITH_ERROR, sorter.getOutput());
		assertStatus(CLOSED_WITH_ERROR, sorter.getInput());
	}
}