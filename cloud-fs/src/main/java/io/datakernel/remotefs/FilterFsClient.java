/*
 * Copyright (C) 2015-2019 SoftIndex LLC.
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

package io.datakernel.remotefs;

import io.datakernel.async.Promise;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.csp.ChannelConsumer;
import io.datakernel.csp.ChannelConsumers;
import io.datakernel.csp.ChannelSupplier;

import java.util.List;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

final class FilterFsClient implements FsClient {
	private final FsClient parent;
	private final Predicate<String> predicate;

	FilterFsClient(FsClient parent, Predicate<String> predicate) {
		this.parent = parent;
		this.predicate = predicate;
	}

	@Override
	public Promise<ChannelConsumer<ByteBuf>> upload(String name, long offset) {
		if (!predicate.test(name)) {
			return Promise.of(ChannelConsumers.recycling());
		}
		return parent.upload(name, offset);
	}

	@Override
	public Promise<ChannelConsumer<ByteBuf>> upload(String name, long offset, long revision) {
		if (!predicate.test(name)) {
			return Promise.of(ChannelConsumers.recycling());
		}
		return parent.upload(name, offset, revision);
	}

	@Override
	public Promise<ChannelSupplier<ByteBuf>> download(String name, long offset, long length) {
		if (!predicate.test(name)) {
			return Promise.ofException(FILE_NOT_FOUND);
		}
		return parent.download(name, offset, length);
	}

	@Override
	public Promise<Void> move(String name, String target) {
		if (!predicate.test(name) || !predicate.test(target)) {
			return Promise.complete();
		}
		return parent.move(name, target);
	}


	@Override
	public Promise<Void> copy(String name, String target) {
		if (!predicate.test(name) || !predicate.test(target)) {
			return Promise.complete();
		}
		return parent.copy(name, target);
	}

	@Override
	public Promise<List<FileMetadata>> listEntities(String glob) {
		return parent.listEntities(glob)
				.map(list -> list.stream()
						.filter(meta -> predicate.test(meta.getName()))
						.collect(toList()));
	}

	@Override
	public Promise<Void> ping() {
		return parent.ping();
	}

	@Override
	public Promise<Void> delete(String name, long revision) {
		if (!predicate.test(name)) {
			return Promise.complete();
		}
		return parent.delete(name, revision);
	}
}
