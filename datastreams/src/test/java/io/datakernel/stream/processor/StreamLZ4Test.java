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
import io.datakernel.stream.StreamConsumerToList;
import io.datakernel.stream.StreamProducer;
import io.datakernel.util.MemSize;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.StreamConsumers.randomlySuspending;
import static io.datakernel.stream.StreamStatus.END_OF_STREAM;
import static io.datakernel.stream.TestUtils.assertStatus;
import static org.junit.Assert.assertArrayEquals;

public class StreamLZ4Test {
	private static ByteBuf createRandomByteBuf(Random random) {
		int offset = random.nextInt(10);
		int tail = random.nextInt(10);
		int len = random.nextInt(100);
		ByteBuf result = ByteBuf.wrapForWriting(new byte[offset + len + tail]);
		int lenUnique = 1 + random.nextInt(len + 1);
		result.writePosition(offset);
		result.readPosition(offset);
		for (int i = 0; i < len; i++) {
			result.put((byte) (i % lenUnique));
		}
		return result;
	}

	private static byte[] byteBufsToByteArray(List<ByteBuf> byteBufs) {
		ByteBufQueue queue = ByteBufQueue.create();
		for (ByteBuf buf : byteBufs) {
			queue.add(buf.slice());
		}
		byte[] bytes = new byte[queue.remainingBytes()];
		queue.drainTo(bytes, 0, bytes.length);
		return bytes;
	}

	@Rule
	public ByteBufRule byteBufRule = new ByteBufRule();

	@Test
	public void test() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		List<ByteBuf> buffers = new ArrayList<>();
		Random random = new Random(123456);
		int buffersCount = 100;
		for (int i = 0; i < buffersCount; i++) {
			buffers.add(createRandomByteBuf(random));
		}
		byte[] expected = byteBufsToByteArray(buffers);

		StreamProducer<ByteBuf> producer = StreamProducer.ofIterable(buffers);
		StreamByteChunker preBuf = StreamByteChunker.create(MemSize.of(64), MemSize.of(128));
		StreamLZ4Compressor compressor = StreamLZ4Compressor.fastCompressor();
		StreamByteChunker postBuf = StreamByteChunker.create(MemSize.of(64), MemSize.of(128));
		StreamLZ4Decompressor decompressor = StreamLZ4Decompressor.create();
		StreamConsumerToList<ByteBuf> consumer = StreamConsumerToList.create();

		producer.with(preBuf).with(compressor).with(postBuf).with(decompressor).streamTo(
				consumer.with(randomlySuspending()));

		eventloop.run();

		byte[] actual = byteBufsToByteArray(consumer.getList());
		for (ByteBuf buf : consumer.getList()) {
			buf.recycle();
		}

		assertArrayEquals(expected, actual);
		assertStatus(END_OF_STREAM, producer);

//		assertStatus(END_OF_STREAM, preBuf.getInput());
//		assertStatus(END_OF_STREAM, preBuf.getOutput());

		assertStatus(END_OF_STREAM, compressor.getInput());
		assertStatus(END_OF_STREAM, compressor.getOutput());

//		assertStatus(END_OF_STREAM, postBuf.getInput());
//		assertStatus(END_OF_STREAM, postBuf.getOutput());

		assertStatus(END_OF_STREAM, decompressor.getInput());
		assertStatus(END_OF_STREAM, decompressor.getOutput());
	}

	@Test
	public void testRaw() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		StreamLZ4Compressor compressor = StreamLZ4Compressor.rawCompressor();

		doTest(eventloop, compressor);
	}

	@Test
	public void testLz4Fast() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		StreamLZ4Compressor compressor = StreamLZ4Compressor.fastCompressor();

		doTest(eventloop, compressor);
	}

	@Test
	public void testLz4High() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		StreamLZ4Compressor compressor = StreamLZ4Compressor.highCompressor();

		doTest(eventloop, compressor);
	}

	@Test
	public void testLz4High10() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		StreamLZ4Compressor compressor = StreamLZ4Compressor.highCompressor(10);

		doTest(eventloop, compressor);
	}

	private void doTest(Eventloop eventloop, StreamLZ4Compressor compressor) {
		byte data[] = "1".getBytes();
		ByteBuf buf = ByteBuf.wrapForReading(data);
		List<ByteBuf> buffers = new ArrayList<>();
		buffers.add(buf);

		StreamProducer<ByteBuf> producer = StreamProducer.ofIterable(buffers);
		StreamLZ4Decompressor decompressor = StreamLZ4Decompressor.create();
		StreamConsumerToList<ByteBuf> consumer = StreamConsumerToList.create();

		producer.with(compressor).with(decompressor).streamTo(consumer);

		eventloop.run();

		byte[] actual = byteBufsToByteArray(consumer.getList());
		byte[] expected = byteBufsToByteArray(buffers);
		for (ByteBuf b : consumer.getList()) {
			b.recycle();
		}
		assertArrayEquals(actual, expected);

		assertStatus(END_OF_STREAM, producer);
	}

}
