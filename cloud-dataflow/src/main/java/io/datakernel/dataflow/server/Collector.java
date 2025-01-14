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

package io.datakernel.dataflow.server;

import io.datakernel.dataflow.dataset.Dataset;
import io.datakernel.dataflow.graph.DataGraph;
import io.datakernel.dataflow.graph.Partition;
import io.datakernel.dataflow.graph.StreamId;
import io.datakernel.dataflow.node.NodeUpload;
import io.datakernel.stream.StreamSupplier;

import java.util.ArrayList;
import java.util.List;

public final class Collector<T> {
	private final Dataset<T> input;
	private final Class<T> type;
	private final DatagraphClient client;

	public Collector(Dataset<T> input, Class<T> type, DatagraphClient client) {
		this.input = input;
		this.type = type;
		this.client = client;
	}

	public StreamSupplier<T> compile(DataGraph dataGraph) {
		List<StreamId> inputStreamIds = input.channels(dataGraph);
		List<StreamSupplier<T>> suppliers = new ArrayList<>();

		for (StreamId streamId : inputStreamIds) {
			NodeUpload<T> nodeUpload = new NodeUpload<>(type, streamId);
			Partition partition = dataGraph.getPartition(streamId);
			dataGraph.addNode(partition, nodeUpload);
			StreamSupplier<T> supplier = StreamSupplier.ofPromise(client.download(partition.getAddress(), streamId, type));
			suppliers.add(supplier);
		}

		return StreamSupplier.concat(suppliers);
	}
}
