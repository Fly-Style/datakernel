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

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufQueue;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.stream.*;
import io.datakernel.stream.file.StreamFileReader;
import io.datakernel.stream.file.StreamFileWriter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.datakernel.bytebuf.ByteBufPool.*;
import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.DataStreams.stream;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class StreamFileReaderWriterTest {
	Eventloop eventloop;
	ExecutorService executor;

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testStreamFileReader() throws IOException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		ExecutorService executor = Executors.newCachedThreadPool();

		byte[] fileBytes = Files.readAllBytes(Paths.get("test_data/in.dat"));
		StreamFileReader reader = StreamFileReader.readFileFully(executor,
				1, Paths.get("test_data/in.dat"));

		List<ByteBuf> list = new ArrayList<>();

		reader.streamTo(
				StreamConsumerToList.create(list));
		eventloop.run();

		ByteBufQueue byteQueue = ByteBufQueue.create();
		for (ByteBuf buf : list) {
			byteQueue.add(buf);
		}

		ByteBuf buf = ByteBuf.wrapForWriting(new byte[byteQueue.remainingBytes()]);
		byteQueue.drainTo(buf);

		assertArrayEquals(fileBytes, buf.array());
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}

	@Test
	public void testStreamFileReaderWithSuspends() throws IOException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		ExecutorService executor = Executors.newCachedThreadPool();

		byte[] fileBytes = Files.readAllBytes(Paths.get("test_data/in.dat"));
		StreamFileReader reader = StreamFileReader.readFileFully(executor,
				1, Paths.get("test_data/in.dat"));

		List<ByteBuf> list = new ArrayList<>();

		class MockConsumer extends AbstractStreamConsumer<ByteBuf> implements StreamDataReceiver<ByteBuf> {
			@Override
			protected void onStarted() {
				getProducer().suspend();
				eventloop.schedule(eventloop.currentTimeMillis() + 10, () -> getProducer().produce(this));
			}

			@Override
			protected void onEndOfStream() {
			}

			@Override
			protected void onError(Throwable t) {
			}

			@Override
			public void onData(ByteBuf item) {
				list.add(item);
				getProducer().suspend();
				eventloop.schedule(eventloop.currentTimeMillis() + 10, () -> getProducer().produce(this));
			}
		}

		StreamConsumer<ByteBuf> consumer = new MockConsumer();

		stream(reader, consumer);
		eventloop.run();

		ByteBufQueue byteQueue = ByteBufQueue.create();
		for (ByteBuf buf : list) {
			byteQueue.add(buf);
		}

		ByteBuf buf = ByteBuf.wrapForWriting(new byte[byteQueue.remainingBytes()]);
		byteQueue.drainTo(buf);

		assertArrayEquals(fileBytes, buf.array());
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}

	@Test
	public void testStreamFileWriter() throws IOException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		ExecutorService executor = Executors.newCachedThreadPool();
		File tempFile = tempFolder.newFile("out.dat");
		byte[] bytes = new byte[]{'T', 'e', 's', 't', '1', ' ', 'T', 'e', 's', 't', '2', ' ', 'T', 'e', 's', 't', '3', '\n', 'T', 'e', 's', 't', '\n'};

		StreamProducer<ByteBuf> producer = StreamProducer.of(ByteBuf.wrapForReading(bytes));

		StreamFileWriter writer = StreamFileWriter.create(executor, Paths.get(tempFile.getAbsolutePath()));

		stream(producer, writer);
		eventloop.run();


		byte[] fileBytes = Files.readAllBytes(tempFile.toPath());
		assertArrayEquals(bytes, fileBytes);
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}

	@Test
	public void testStreamFileWriterRecycle() throws IOException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		ExecutorService executor = Executors.newCachedThreadPool();
		File tempFile = tempFolder.newFile("out.dat");
		byte[] bytes = new byte[]{'T', 'e', 's', 't', '1', ' ', 'T', 'e', 's', 't', '2', ' ', 'T', 'e', 's', 't', '3', '\n', 'T', 'e', 's', 't', '\n'};

		StreamProducer<ByteBuf> producer = StreamProducer.concat(
				StreamProducer.of(ByteBuf.wrapForReading(bytes)),
				StreamProducer.closingWithError(new Exception("Test Exception")));

		StreamFileWriter writer = StreamFileWriter.create(executor, Paths.get(tempFile.getAbsolutePath()));

		stream(producer, writer);
		eventloop.run();

		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}

}