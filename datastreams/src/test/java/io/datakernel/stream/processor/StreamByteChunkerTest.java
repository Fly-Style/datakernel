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
import io.datakernel.eventloop.Eventloop;
import io.datakernel.stream.StreamProducer;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static org.junit.Assert.*;

public class StreamByteChunkerTest {

	@Rule
	public ByteBufRule byteBufRule = new ByteBufRule();

	private static ByteBuf createRandomByteBuf(Random random) {
		int len = random.nextInt(100);
		ByteBuf result = ByteBuf.wrapForWriting(new byte[len]);
		int lenUnique = 1 + random.nextInt(len + 1);
		for (int i = 0; i < len; i++) {
			result.put((byte) (i % lenUnique));
		}
		return result;
	}

	private static byte[] byteBufsToByteArray(List<ByteBuf> byteBufs) {
		int size = 0;
		for (ByteBuf byteBuf : byteBufs) {
			size += byteBuf.readRemaining();
		}
		byte[] result = new byte[size];
		int pos = 0;
		for (ByteBuf byteBuf : byteBufs) {
			System.arraycopy(byteBuf.array(), byteBuf.readPosition(), result, pos, byteBuf.readRemaining());
			pos += byteBuf.readRemaining();
		}
		return result;
	}

	@Test
	public void testResizer() throws Exception {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		List<ByteBuf> buffers = new ArrayList<>();
		Random random = new Random();
		int buffersCount = 1000;
		int totalLen = 0;
		for (int i = 0; i < buffersCount; i++) {
			ByteBuf buffer = createRandomByteBuf(random);
			buffers.add(buffer);
			totalLen += buffer.readRemaining();
		}
		byte[] expected = byteBufsToByteArray(buffers);

		int bufSize = 128;

		StreamProducer<ByteBuf> source = StreamProducer.ofIterable(buffers);
		StreamByteChunker chunker = StreamByteChunker.create(bufSize / 2, bufSize);

		CompletableFuture<List<ByteBuf>> listFuture = source.with(chunker).toList().toCompletableFuture();

		eventloop.run();

		List<ByteBuf> receivedBuffers = listFuture.get();
		byte[] received = byteBufsToByteArray(receivedBuffers);
		assertArrayEquals(received, expected);

		int actualLen = 0;
		for (int i = 0; i < receivedBuffers.size() - 1; i++) {
			ByteBuf buf = receivedBuffers.get(i);
			actualLen += buf.readRemaining();
			int receivedSize = buf.readRemaining();
			assertTrue(receivedSize >= bufSize / 2 && receivedSize <= bufSize);
			buf.recycle();
		}
		actualLen += receivedBuffers.get(receivedBuffers.size() - 1).readRemaining();
		receivedBuffers.get(receivedBuffers.size() - 1).recycle();

		assertEquals(totalLen, actualLen);
	}
}