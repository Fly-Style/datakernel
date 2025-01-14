package io.global.kv;

import io.datakernel.async.Promise;
import io.datakernel.async.Promises;
import io.datakernel.csp.ChannelConsumer;
import io.datakernel.csp.ChannelSupplier;
import io.global.common.PubKey;
import io.global.common.SignedData;
import io.global.common.api.AbstractGlobalNamespace;
import io.global.kv.api.GlobalKvNode;
import io.global.kv.api.KvStorage;
import io.global.kv.api.RawKvItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class GlobalKvNamespace extends AbstractGlobalNamespace<GlobalKvNamespace, GlobalKvNodeImpl, GlobalKvNode> {
	private final Map<String, Repo> repos = new HashMap<>();

	public GlobalKvNamespace(GlobalKvNodeImpl node, PubKey space) {
		super(node, space);
	}

	public Set<String> getRepoNames() {
		return repos.keySet();
	}

	public Repo ensureRepository(String table) {
		return repos.computeIfAbsent(table, t -> new Repo(node.getStorageFactory().apply(space, t), t));
	}

	public Promise<Void> fetch() {
		return ensureMasterNodes()
				.then(masters ->
						Promises.all(masters.stream()
								.map(node -> node
										.list(space)
										.then(tables ->
												Promises.all(tables.stream()
														.map(table ->
																ensureRepository(table)
																		.fetch(node)))))));
	}

	public Promise<Void> push() {
		return ensureMasterNodes()
				.then(masters ->
						Promises.all(masters
								.stream()
								.map(node ->
										Promises.all(repos
												.values()
												.stream()
												.map(repo -> repo.push(node))))));
	}

	public final class Repo {
		public final KvStorage storage;
		public final String table;

		long lastFetchTimestamp = 0;
		long cacheTimestamp = 0;

		private Repo(KvStorage storage, String table) {
			this.storage = storage;
			this.table = table;
		}

		public Promise<ChannelConsumer<SignedData<RawKvItem>>> upload() {
			return storage.upload()
					.map(consumer -> consumer
							.withAcknowledgement(ack -> ack
									.whenResult((Consumer<? super Void>) $ -> cacheTimestamp = node.getCurrentTimeProvider().currentTimeMillis())));
		}

		public Promise<ChannelSupplier<SignedData<RawKvItem>>> download(long timestamp) {
			return node.getCurrentTimeProvider().currentTimeMillis() - cacheTimestamp < node.getLatencyMargin().toMillis() ?
					storage.download(timestamp) :
					Promise.of(null);
		}

		public Promise<Void> fetch(GlobalKvNode from) {
			long timestamp = node.getCurrentTimeProvider().currentTimeMillis();
			return Promises.toTuple(from.download(space, table, lastFetchTimestamp), storage.upload())
					.then(tuple -> tuple.getValue1().streamTo(tuple.getValue2()))
					.whenResult((Consumer<? super Void>) $ -> lastFetchTimestamp = timestamp);
		}

		public Promise<Void> push(GlobalKvNode into) {
			return Promises.toTuple(storage.download(), into.upload(space, table))
					.then(tuple -> tuple.getValue1().streamTo(tuple.getValue2()));
		}
	}
}
