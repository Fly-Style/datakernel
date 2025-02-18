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

import io.datakernel.exception.ExpectedException;
import io.datakernel.stream.StreamConsumer;
import io.datakernel.stream.StreamConsumerToList;
import io.datakernel.stream.StreamSupplier;
import io.datakernel.test.rules.EventloopRule;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.datakernel.async.TestUtils.await;
import static io.datakernel.async.TestUtils.awaitException;
import static io.datakernel.stream.TestStreamConsumers.*;
import static io.datakernel.stream.TestUtils.*;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class StreamUnionTest {

	@ClassRule
	public static final EventloopRule eventloopRule = new EventloopRule();

	@Test
	public void test1() {
		StreamUnion<Integer> streamUnion = StreamUnion.create();

		StreamSupplier<Integer> source0 = StreamSupplier.of();
		StreamSupplier<Integer> source1 = StreamSupplier.of(1);
		StreamSupplier<Integer> source2 = StreamSupplier.of(2, 3);
		StreamSupplier<Integer> source3 = StreamSupplier.of();
		StreamSupplier<Integer> source4 = StreamSupplier.of(4, 5);
		StreamSupplier<Integer> source5 = StreamSupplier.of(6);
		StreamSupplier<Integer> source6 = StreamSupplier.of();

		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create();

		await(
				source0.streamTo(streamUnion.newInput()),
				source1.streamTo(streamUnion.newInput()),
				source2.streamTo(streamUnion.newInput()),
				source3.streamTo(streamUnion.newInput()),
				source4.streamTo(streamUnion.newInput()),
				source5.streamTo(streamUnion.newInput()),
				source6.streamTo(streamUnion.newInput()),

				streamUnion.getOutput()
						.streamTo(consumer.transformWith(randomlySuspending()))
		);

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
		StreamUnion<Integer> streamUnion = StreamUnion.create();

		StreamSupplier<Integer> source0 = StreamSupplier.of(1, 2, 3);
		StreamSupplier<Integer> source1 = StreamSupplier.of(4, 5);
		StreamSupplier<Integer> source2 = StreamSupplier.of(6, 7);

		List<Integer> list = new ArrayList<>();
		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create(list);
		ExpectedException exception = new ExpectedException("Test Exception");

		Throwable e = awaitException(
				source0.streamTo(streamUnion.newInput()),
				source1.streamTo(streamUnion.newInput()),
				source2.streamTo(streamUnion.newInput()),

				streamUnion.getOutput()
						.streamTo(consumer
								.transformWith(decorator((context, dataAcceptor) ->
										item -> {
											dataAcceptor.accept(item);
											if (item == 1) {
												context.closeWithError(exception);
											}
										})))
		);

		assertSame(exception, e);
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
	public void testSupplierWithError() {
		StreamUnion<Integer> streamUnion = StreamUnion.create();
		ExpectedException exception = new ExpectedException("Test Exception");

		StreamSupplier<Integer> source0 = StreamSupplier.concat(
				StreamSupplier.ofIterable(Arrays.asList(1, 2)),
				StreamSupplier.closingWithError(exception)
		);
		StreamSupplier<Integer> source1 = StreamSupplier.concat(
				StreamSupplier.ofIterable(Arrays.asList(7, 8, 9)),
				StreamSupplier.closingWithError(exception)
		);

		List<Integer> list = new ArrayList<>();
		StreamConsumer<Integer> consumer = StreamConsumerToList.create(list);

		Throwable e = awaitException(
				source0.streamTo(streamUnion.newInput()),
				source1.streamTo(streamUnion.newInput()),
				streamUnion.getOutput()
						.streamTo(consumer.transformWith(oneByOne()))
		);

		assertSame(exception, e);
		assertEquals(3, list.size());
		assertClosedWithError(streamUnion.getOutput());
		assertConsumersClosedWithError(streamUnion.getInputs());
	}

}
