/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
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
import io.datakernel.rpc.protocol.RpcException;

public interface RpcSender {
	RpcException NO_SENDER_AVAILABLE_EXCEPTION = new RpcException(RpcSender.class, "No senders available");

	<I, O> void sendRequest(I request, int timeout, Callback<O> cb);

	default <I, O> void sendRequest(I request, Callback<O> cb) {
		sendRequest(request, Integer.MAX_VALUE, cb);
	}
}
