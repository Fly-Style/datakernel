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

package io.datakernel.datagraph.node;

import io.datakernel.datagraph.graph.StreamId;
import io.datakernel.datagraph.graph.TaskContext;
import io.datakernel.stream.processor.StreamUnion;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;

public final class NodeUnion<T> implements Node {
	private List<StreamId> inputs;
	private StreamId output;

	public NodeUnion() {
	}

	public NodeUnion(List<StreamId> inputs) {
		this.inputs = inputs;
		this.output = new StreamId();
	}

	@Override
	public Collection<StreamId> getOutputs() {
		return singletonList(output);
	}

	@Override
	public void createAndBind(TaskContext taskContext) {
		StreamUnion<T> streamUnion = StreamUnion.create();
		for(StreamId input : inputs) {
			taskContext.bindChannel(input, streamUnion.newInput());
		}
		taskContext.export(output, streamUnion.getOutput());
	}

	public List<StreamId> getInputs() {
		return inputs;
	}

	public void setInputs(List<StreamId> inputs) {
		this.inputs = inputs;
	}

	public StreamId getOutput() {
		return output;
	}

	public void setOutput(StreamId output) {
		this.output = output;
	}

	@Override
	public String toString() {
		return "NodeUnion{" +
				"inputs=" + inputs +
				", output=" + output +
				'}';
	}
}
