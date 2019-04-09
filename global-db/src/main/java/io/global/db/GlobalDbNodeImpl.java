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

package io.global.db;

import io.datakernel.async.*;
import io.datakernel.csp.ChannelConsumer;
import io.datakernel.csp.ChannelOutput;
import io.datakernel.csp.ChannelSupplier;
import io.datakernel.csp.process.ChannelSplitter;
import io.datakernel.csp.queue.ChannelZeroBuffer;
import io.datakernel.util.Initializable;
import io.datakernel.util.ref.RefBoolean;
import io.global.common.NodeID;
import io.global.common.PubKey;
import io.global.common.SignedData;
import io.global.common.api.AbstractGlobalNode;
import io.global.common.api.DiscoveryService;
import io.global.db.GlobalDbNamespace.Repo;
import io.global.db.api.DbStorage;
import io.global.db.api.GlobalDbNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.datakernel.async.AsyncSuppliers.reuse;
import static io.global.util.Utils.nSuccessesOrLess;

public final class GlobalDbNodeImpl extends AbstractGlobalNode<GlobalDbNodeImpl, GlobalDbNamespace, GlobalDbNode> implements GlobalDbNode, Initializable<GlobalDbNodeImpl> {
	private int uploadCallNumber = 1;
	private int uploadSuccessNumber = 0;

	private boolean doesDownloadCaching = true;
	private boolean doesUploadCaching = false;

	private final BiFunction<PubKey, String, DbStorage> storageFactory;

	// region creators
	private GlobalDbNodeImpl(NodeID id, DiscoveryService discoveryService,
							 Function<NodeID, GlobalDbNode> nodeFactory,
							 BiFunction<PubKey, String, DbStorage> storageFactory) {
		super(id, discoveryService, nodeFactory);
		this.storageFactory = storageFactory;
	}

	public static GlobalDbNodeImpl create(NodeID id, DiscoveryService discoveryService,
										  Function<NodeID, GlobalDbNode> nodeFactory,
										  BiFunction<PubKey, String, DbStorage> storageFactory) {
		return new GlobalDbNodeImpl(id, discoveryService, nodeFactory, storageFactory);
	}
	// endregion

	@Override
	protected GlobalDbNamespace createNamespace(PubKey space) {
		return new GlobalDbNamespace(this, space);
	}

	public BiFunction<PubKey, String, DbStorage> getStorageFactory() {
		return storageFactory;
	}

	@Override
	public Promise<ChannelConsumer<SignedData<DbItem>>> upload(PubKey space, String table) {
		GlobalDbNamespace ns = ensureNamespace(space);
		return ns.ensureMasterNodes()
				.then(masters -> {
					Repo repo = ns.ensureRepository(table);
					if (isMasterFor(space)) {
						return repo.upload();
					}
					return nSuccessesOrLess(uploadCallNumber, masters
							.stream()
							.map(master -> AsyncSupplier.cast(() -> master.upload(space, table))))
							.map(consumers -> {
								ChannelZeroBuffer<SignedData<DbItem>> buffer = new ChannelZeroBuffer<>();

								ChannelSplitter<SignedData<DbItem>> splitter = ChannelSplitter.create(buffer.getSupplier())
										.lenient();

								RefBoolean localCompleted = new RefBoolean(false);
								if (doesUploadCaching || consumers.isEmpty()) {
									splitter.addOutput().set(ChannelConsumer.ofPromise(repo.upload())
											.withAcknowledgement(ack ->
													ack.whenComplete((Callback<? super Void>) ($, e) -> {
														if (e == null) {
															localCompleted.set(true);
														} else {
															splitter.close(e);
														}
													})));
								} else {
									localCompleted.set(true);
								}

								MaterializedPromise<Void> process = splitter.splitInto(consumers, uploadSuccessNumber, localCompleted);
								return buffer.getConsumer().withAcknowledgement(ack -> ack.both(process));
							});

				});
	}

	@Override
	public Promise<ChannelSupplier<SignedData<DbItem>>> download(PubKey space, String table, long timestamp) {
		GlobalDbNamespace ns = ensureNamespace(space);
		Repo repo = ns.ensureRepository(table);
		return repo.download(timestamp)
				.then(supplier -> {
					if (supplier != null) {
						return Promise.of(supplier);
					}
					return ns.ensureMasterNodes()
							.then(masters -> {
								if (isMasterFor(space) || masters.isEmpty()) {
									return repo.storage.download(timestamp);
								}
								if (!doesDownloadCaching) {
									return Promises.firstSuccessful(masters.stream()
											.map(node -> AsyncSupplier.cast(() ->
													node.download(space, table, timestamp))));
								}
								return Promises.firstSuccessful(masters.stream()
										.map(node -> AsyncSupplier.cast(() ->
												Promises.toTuple(node.download(space, table, timestamp), repo.upload())
														.map(t -> {
															ChannelSplitter<SignedData<DbItem>> splitter = ChannelSplitter.create();
															ChannelOutput<SignedData<DbItem>> output = splitter.addOutput();
															splitter.addOutput().set(t.getValue2());
															splitter.getInput().set(t.getValue1());
															return output.getSupplier();
														}))));
							});
				});
	}

	@Override
	public Promise<SignedData<DbItem>> get(PubKey space, String table, byte[] key) {
		GlobalDbNamespace ns = ensureNamespace(space);
		return ns.ensureMasterNodes()
				.then(masters -> {
					Repo repo = ns.ensureRepository(table);
					if (isMasterFor(space)) {
						return repo.storage.get(key);
					}
					return Promises.firstSuccessful(masters.stream()
							.map(node -> AsyncSupplier.cast(
									doesDownloadCaching ?
											() -> node.get(space, table, key)
													.then(item ->
															repo.storage.put(item)
																	.map($ -> item)) :
											() -> node.get(space, table, key))));
				});
	}

	@Override
	public Promise<List<String>> list(PubKey space) {
		GlobalDbNamespace ns = ensureNamespace(space);
		return ns.ensureMasterNodes()
				.then(masters -> {
					if (isMasterFor(space)) {
						return Promise.of(new ArrayList<>(ns.getRepoNames()));
					}
					return Promises.firstSuccessful(masters.stream()
							.map(node -> AsyncSupplier.cast(() -> node.list(space))));
				});
	}

	public Promise<Void> fetch() {
		return Promises.all(getManagedPublicKeys().stream().map(this::fetch));
	}

	public Promise<Void> push() {
		return Promises.all(namespaces.keySet().stream().map(this::push));
	}

	public Promise<Void> fetch(PubKey space) {
		return ensureNamespace(space).fetch();
	}

	public Promise<Void> push(PubKey space) {
		return ensureNamespace(space).push();
	}

	private final AsyncSupplier<Void> catchUp = reuse(this::doCatchUp);

	public Promise<Void> catchUp() {
		return catchUp.get();
	}

	private Promise<Void> doCatchUp() {
		return Promises.until(
				$1 -> {
					long timestampBegin = now.currentTimeMillis();
					return fetch()
							.map($2 ->
									now.currentTimeMillis() <= timestampBegin + latencyMargin.toMillis());

				});
	}

	@Override
	public String toString() {
		return "GlobalDbNodeImpl{id=" + id + '}';
	}
}
