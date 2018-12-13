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
import io.datakernel.stream.StreamSupplier;
import io.datakernel.stream.processor.StreamJoin.ValueJoiner;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.TestStreamConsumers.*;
import static io.datakernel.stream.TestUtils.assertClosedWithError;
import static io.datakernel.stream.TestUtils.assertEndOfStream;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class StreamJoinTest {
	private static final class DataItemMaster {
		int id;
		int detailId;
		String master;

		private DataItemMaster(int id, int detailId, String master) {
			this.id = id;
			this.detailId = detailId;
			this.master = master;
		}

		@Override
		public String toString() {
			return "DataItemMaster{" +
					"id=" + id +
					", detailId=" + detailId +
					", master='" + master + '\'' +
					'}';
		}
	}

	private static final class DataItemDetail {
		int id;
		String detail;

		private DataItemDetail(int id, String detail) {
			this.id = id;
			this.detail = detail;
		}

		@Override
		public String toString() {
			return "DataItemDetail{" +
					"id=" + id +
					", detail='" + detail + '\'' +
					'}';
		}
	}

	private static final class DataItemMasterDetail {
		int id;
		int detailId;
		String master;
		String detail;

		private DataItemMasterDetail(int id, int detailId, String master, String detail) {
			this.id = id;
			this.detailId = detailId;
			this.master = master;
			this.detail = detail;
		}

		@Override
		public boolean equals(Object o) {
			DataItemMasterDetail that = (DataItemMasterDetail) o;
			if (id != that.id) return false;
			if (detailId != that.detailId) return false;
			if (!Objects.equals(detail, that.detail)) return false;
			if (!Objects.equals(master, that.master)) return false;
			return true;
		}

		@Override
		public String toString() {
			return "DataItemMasterDetail{" +
					"id=" + id +
					", detailId=" + detailId +
					", master='" + master + '\'' +
					", detail='" + detail + '\'' +
					'}';
		}
	}

	@Test
	public void test1() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		StreamSupplier<DataItemMaster> source1 = StreamSupplier.of(
				new DataItemMaster(10, 10, "masterA"),
				new DataItemMaster(20, 10, "masterB"),
				new DataItemMaster(25, 15, "masterB+"),
				new DataItemMaster(30, 20, "masterC"),
				new DataItemMaster(40, 20, "masterD"));

		StreamSupplier<DataItemDetail> source2 = StreamSupplier.of(
				new DataItemDetail(10, "detailX"),
				new DataItemDetail(20, "detailY"));

		StreamJoin<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail> streamJoin =
				StreamJoin.create(Integer::compareTo,
						input -> input.detailId,
						input -> input.id,
						new ValueJoiner<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail>() {
							@Override
							public DataItemMasterDetail doInnerJoin(Integer key, DataItemMaster left, DataItemDetail right) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, right.detail);
							}

							@Override
							public DataItemMasterDetail doLeftJoin(Integer key, DataItemMaster left) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, null);
							}
						}
				);

		StreamConsumerToList<DataItemMasterDetail> consumer = StreamConsumerToList.create();

		source1.streamTo(streamJoin.getLeft());
		source2.streamTo(streamJoin.getRight());

		streamJoin.getOutput().streamTo(
				consumer.transformWith(randomlySuspending()));

		eventloop.run();

		List<DataItemMasterDetail> result = consumer.getList();
		assertArrayEquals(new DataItemMasterDetail[]{
						new DataItemMasterDetail(10, 10, "masterA", "detailX"),
						new DataItemMasterDetail(20, 10, "masterB", "detailX"),
						new DataItemMasterDetail(25, 15, "masterB+", null),
						new DataItemMasterDetail(30, 20, "masterC", "detailY"),
						new DataItemMasterDetail(40, 20, "masterD", "detailY")},
				result.toArray(new DataItemMasterDetail[0]));
		assertEndOfStream(source1);
		assertEndOfStream(source2);
	}

	@Test
	public void testWithError() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		List<DataItemMasterDetail> list = new ArrayList<>();

		StreamSupplier<DataItemMaster> source1 = StreamSupplier.of(
				new DataItemMaster(10, 10, "masterA"),
				new DataItemMaster(20, 10, "masterB"),
				new DataItemMaster(25, 15, "masterB+"),
				new DataItemMaster(30, 20, "masterC"),
				new DataItemMaster(40, 20, "masterD"));

		StreamSupplier<DataItemDetail> source2 = StreamSupplier.of(
				new DataItemDetail(10, "detailX"),
				new DataItemDetail(20, "detailY"));

		StreamJoin<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail> streamJoin =
				StreamJoin.create(Integer::compareTo,
						input -> input.detailId,
						input -> input.id,
						new ValueJoiner<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail>() {
							@Override
							public DataItemMasterDetail doInnerJoin(Integer key, DataItemMaster left, DataItemDetail right) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, right.detail);
							}

							@Override
							public DataItemMasterDetail doLeftJoin(Integer key, DataItemMaster left) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, null);
							}
						}
				);

		StreamConsumerToList<DataItemMasterDetail> consumer = StreamConsumerToList.create(list);

		source1.streamTo(streamJoin.getLeft());
		source2.streamTo(streamJoin.getRight());

		streamJoin.getOutput()
				.streamTo(consumer
						.transformWith(decorator((context, dataAcceptor) ->
								item -> {
									dataAcceptor.accept(item);
									if (list.size() == 1) {
										context.closeWithError(new ExpectedException("Test Exception"));
									}
								})));

		eventloop.run();
		assertEquals(1, list.size());
		assertClosedWithError(source1);
		assertEndOfStream(source2);
	}

	@Test
	public void testSupplierWithError() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		StreamSupplier<DataItemMaster> source1 = StreamSupplier.concat(
				StreamSupplier.of(new DataItemMaster(10, 10, "masterA")),
				StreamSupplier.closingWithError(new ExpectedException("Test Exception")),
				StreamSupplier.of(new DataItemMaster(20, 10, "masterB")),
				StreamSupplier.of(new DataItemMaster(25, 15, "masterB+")),
				StreamSupplier.of(new DataItemMaster(30, 20, "masterC")),
				StreamSupplier.of(new DataItemMaster(40, 20, "masterD"))
		);

		StreamSupplier<DataItemDetail> source2 = StreamSupplier.concat(
				StreamSupplier.of(new DataItemDetail(10, "detailX")),
				StreamSupplier.of(new DataItemDetail(20, "detailY")),
				StreamSupplier.closingWithError(new ExpectedException("Test Exception"))
		);

		StreamJoin<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail> streamJoin =
				StreamJoin.create(Integer::compareTo,
						input -> input.detailId,
						input -> input.id,
						new ValueJoiner<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail>() {
							@Override
							public DataItemMasterDetail doInnerJoin(Integer key, DataItemMaster left, DataItemDetail right) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, right.detail);
							}

							@Override
							public DataItemMasterDetail doLeftJoin(Integer key, DataItemMaster left) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, null);
							}
						}
				);

		List<DataItemMasterDetail> list = new ArrayList<>();
		StreamConsumer<DataItemMasterDetail> consumer = StreamConsumerToList.create(list);

		source1.streamTo(streamJoin.getLeft());
		source2.streamTo(streamJoin.getRight());

		streamJoin.getOutput().streamTo(consumer.transformWith(oneByOne()));

		eventloop.run();
		assertEquals(0, list.size());
		assertClosedWithError(source1);
		assertClosedWithError(source2);
	}
}