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

package io.datakernel.csp.process;

import io.datakernel.async.AsyncPredicate;
import io.datakernel.async.Promise;
import io.datakernel.async.Promises;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufQueue;
import io.datakernel.util.MemSize;

import static java.lang.Math.min;

public final class ChannelByteChunker extends AbstractChannelTransformer<ChannelByteChunker, ByteBuf, ByteBuf> {
	private final ByteBufQueue bufs = new ByteBufQueue();

	private final int minChunkSize;
	private final int maxChunkSize;

	private ChannelByteChunker(int minChunkSize, int maxChunkSize) {
		this.minChunkSize = minChunkSize;
		this.maxChunkSize = maxChunkSize;
	}

	public static ChannelByteChunker create(MemSize minChunkSize, MemSize maxChunkSize) {
		return new ChannelByteChunker(minChunkSize.toInt(), maxChunkSize.toInt());
	}

	@Override
	protected Promise<Void> onItem(ByteBuf item) {
		bufs.add(item);
		return Promises.loop(
				AsyncPredicate.of($ -> bufs.hasRemainingBytes(minChunkSize)),
				() -> {
					int exactSize = 0;
					for (int i = 0; i != bufs.remainingBufs(); i++) {
						exactSize += bufs.peekBuf(i).readRemaining();
						if (exactSize >= minChunkSize) {
							break;
						}
					}
					return send(bufs.takeExactSize(min(exactSize, maxChunkSize)));
				});
	}

	@Override
	protected Promise<Void> onProcessFinish() {
		return bufs.hasRemaining() ?
				send(bufs.takeRemaining())
						.then($ -> sendEndOfStream()) :
				sendEndOfStream();
	}
}
