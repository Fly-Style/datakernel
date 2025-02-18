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

package io.datakernel.rpc.client.sender;

import io.datakernel.async.Callback;
import io.datakernel.rpc.client.RpcClientConnectionPool;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;

import static io.datakernel.util.Preconditions.checkArgument;

public final class RpcStrategyRoundRobin implements RpcStrategy {
	private final RpcStrategyList list;
	private final int minActiveSubStrategies;

	private RpcStrategyRoundRobin(RpcStrategyList list, int minActiveSubStrategies) {
		this.list = list;
		this.minActiveSubStrategies = minActiveSubStrategies;
	}

	public static RpcStrategyRoundRobin create(RpcStrategyList list) {
		return new RpcStrategyRoundRobin(list, 0);
	}

	public RpcStrategyRoundRobin withMinActiveSubStrategies(int minActiveSubStrategies) {
		return new RpcStrategyRoundRobin(list, minActiveSubStrategies);
	}

	@Override
	public Set<InetSocketAddress> getAddresses() {
		return list.getAddresses();
	}

	@Nullable
	@Override
	public RpcSender createSender(RpcClientConnectionPool pool) {
		List<RpcSender> subSenders = list.listOfSenders(pool);
		if (subSenders.size() < minActiveSubStrategies)
			return null;
		if (subSenders.size() == 0)
			return null;
		if (subSenders.size() == 1)
			return subSenders.get(0);
		return new Sender(subSenders);
	}

	static final class Sender implements RpcSender {
		private int nextSender;
		private RpcSender[] subSenders;

		Sender(List<RpcSender> senders) {
			checkArgument(senders != null && senders.size() > 0, "List of senders should not be null and should contain at least one sender");
			this.subSenders = senders.toArray(new RpcSender[0]);
			this.nextSender = 0;
		}

		@Override
		public <I, O> void sendRequest(I request, int timeout, Callback<O> cb) {
			RpcSender sender = subSenders[nextSender];
			nextSender = (nextSender + 1) % subSenders.length;
			sender.sendRequest(request, timeout, cb);
		}

	}
}
