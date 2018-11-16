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

package io.datakernel.remotefs;

import io.datakernel.annotation.Nullable;
import io.datakernel.async.MaterializedPromise;
import io.datakernel.async.Promise;
import io.datakernel.async.Promises;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.csp.ChannelConsumer;
import io.datakernel.csp.ChannelSupplier;
import io.datakernel.csp.process.ChannelSplitter;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.EventloopService;
import io.datakernel.functional.Try;
import io.datakernel.jmx.EventloopJmxMBeanEx;
import io.datakernel.jmx.JmxAttribute;
import io.datakernel.jmx.PromiseStats;
import io.datakernel.util.Initializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.datakernel.csp.ChannelConsumer.getAcknowledgement;
import static io.datakernel.remotefs.ServerSelector.RENDEZVOUS_HASH_SHARDER;
import static io.datakernel.util.LogUtils.toLogger;
import static io.datakernel.util.Preconditions.*;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.*;

/**
 * An implementation of {@link FsClient} which operates on a map of other clients as a cluster.
 * Contains some redundancy and fail-safety capabilities.
 */
public final class RemoteFsClusterClient implements FsClient, Initializable<RemoteFsClusterClient>, EventloopService, EventloopJmxMBeanEx {
	private static final Logger logger = LoggerFactory.getLogger(RemoteFsClusterClient.class);

	private final Eventloop eventloop;
	private final Map<Object, FsClient> clients;
	private final Map<Object, FsClient> aliveClients = new HashMap<>();
	private final Map<Object, FsClient> deadClients = new HashMap<>();

	private int replicationCount = 1;
	private ServerSelector serverSelector = RENDEZVOUS_HASH_SHARDER;

	// region JMX
	private final PromiseStats connectPromise = PromiseStats.create(Duration.ofMinutes(5));
	private final PromiseStats uploadStartPromise = PromiseStats.create(Duration.ofMinutes(5));
	private final PromiseStats uploadFinishPromise = PromiseStats.create(Duration.ofMinutes(5));
	private final PromiseStats downloadStartPromise = PromiseStats.create(Duration.ofMinutes(5));
	private final PromiseStats downloadFinishPromise = PromiseStats.create(Duration.ofMinutes(5));
	private final PromiseStats movePromise = PromiseStats.create(Duration.ofMinutes(5));
	private final PromiseStats copyPromise = PromiseStats.create(Duration.ofMinutes(5));
	private final PromiseStats listPromise = PromiseStats.create(Duration.ofMinutes(5));
	private final PromiseStats deletePromise = PromiseStats.create(Duration.ofMinutes(5));
	// endregion

	// region creators
	private RemoteFsClusterClient(Eventloop eventloop, Map<Object, FsClient> clients) {
		this.eventloop = eventloop;
		this.clients = clients;
		aliveClients.putAll(clients);
	}

	public static RemoteFsClusterClient create(Eventloop eventloop) {
		return new RemoteFsClusterClient(eventloop, new HashMap<>());
	}

	public static RemoteFsClusterClient create(Eventloop eventloop, Map<Object, FsClient> clients) {
		return new RemoteFsClusterClient(eventloop, clients);
	}

	/**
	 * Adds given client with given partition id to this cluster
	 */
	public RemoteFsClusterClient withPartition(Object id, FsClient client) {
		clients.put(id, client);
		aliveClients.put(id, client);
		return this;
	}

	/**
	 * Sets the replication count that determines how many copies of the file should persist over the cluster.
	 */
	public RemoteFsClusterClient withReplicationCount(int replicationCount) {
		checkArgument(1 <= replicationCount && replicationCount <= clients.size(), "Replication count cannot be less than one or more than number of clients");
		this.replicationCount = replicationCount;
		return this;
	}

	/**
	 * Sets the server selection strategy based on file name, alive partitions, and replication count.
	 */
	public RemoteFsClusterClient withServerSelector(ServerSelector serverSelector) {
		checkNotNull(serverSelector, "serverSelector");

		this.serverSelector = serverSelector;
		return this;
	}
	// endregion

	// region getters
	@Override
	public Eventloop getEventloop() {
		return eventloop;
	}

	public Map<Object, FsClient> getClients() {
		return Collections.unmodifiableMap(clients);
	}

	public Map<Object, FsClient> getAliveClients() {
		return Collections.unmodifiableMap(aliveClients);
	}

	public Map<Object, FsClient> getDeadClients() {
		return Collections.unmodifiableMap(deadClients);
	}

	public ServerSelector getServerSelector() {
		return serverSelector;
	}
	// endregion

	/**
	 * Starts a check process, which pings all partitions and marks them as dead or alive accordingly
	 *
	 * @return promise of the check
	 */
	public Promise<Void> checkAllPartitions() {
		return Promises.all(clients.entrySet().stream()
				.map(entry -> {
					Object id = entry.getKey();
					return entry.getValue()
							.ping()
							.thenApplyEx(($, e) -> {
								if (e == null) {
									markAlive(id);
								} else {
									markDead(id, e);
								}
								return null;
							});
				}))
				.whenComplete(toLogger(logger, "checkAllPartitions"));
	}

	/**
	 * Starts a check process, which pings all dead partitions to possibly mark them as alive.
	 * This is the preferred method as it does nothing when no clients are marked as dead,
	 * and RemoteFS operations themselves do mark nodes as dead on connection failures.
	 *
	 * @return promise of the check
	 */
	public Promise<Void> checkDeadPartitions() {
		return Promises.all(deadClients.entrySet().stream()
				.map(entry -> entry.getValue()
						.ping()
						.thenApplyEx(($, e) -> {
							if (e == null) {
								markAlive(entry.getKey());
							}
							return null;
						})))
				.whenComplete(toLogger(logger, "checkDeadPartitions"));
	}

	private void markAlive(Object partitionId) {
		FsClient client = deadClients.remove(partitionId);
		if (client != null) {
			logger.info("Partition " + partitionId + " is alive again!");
			aliveClients.put(partitionId, client);
		}
	}

	/**
	 * Mark partition as dead. It means that no operations will use it and it would not be given to the server selector.
	 * Next call of {@link #checkDeadPartitions()} or {@link #checkAllPartitions()} will ping this partition and possibly
	 * mark it as alive again.
	 *
	 * @param partitionId id of the partition to be marked
	 * @param e           optional exception for logging
	 * @return <code>true</code> if partition was alive and <code>false</code> otherwise
	 */
	public boolean markDead(Object partitionId, @Nullable Throwable e) {
		FsClient client = aliveClients.remove(partitionId);
		if (client != null) {
			logger.warn("marking " + partitionId + " as dead (" + e + ')');
			deadClients.put(partitionId, client);
			return true;
		}
		return false;
	}

	private void markIfDead(Object partitionId, Throwable e) {
		// marking as dead only on lower level connection and other I/O exceptions,
		// remotefs exceptions are the ones actually received with an ServerError response (so the node is obviously not dead)
		if (e.getClass() != RemoteFsException.class) {
			markDead(partitionId, e);
		}
	}

	private <T> BiFunction<T, Throwable, Promise<T>> wrapDeath(Object partitionId) {
		return (res, e) -> {
			if (e == null) {
				return Promise.of(res);
			}
			markIfDead(partitionId, e);
			return Promise.ofException(new RemoteFsException(RemoteFsClusterClient.class, "Node failed with exception", e));
		};
	}

	// shortcut for creating single Exception from list of possibly failed tries
	private static <T, U> Promise<T> ofFailure(String message, List<Try<U>> failed) {
		RemoteFsException exception = new RemoteFsException(RemoteFsClusterClient.class, message);
		failed.stream()
				.map(Try::getExceptionOrNull)
				.filter(Objects::nonNull)
				.forEach(exception::addSuppressed);
		return Promise.ofException(exception);
	}

	@Override
	public Promise<ChannelConsumer<ByteBuf>> upload(String filename, long offset) {
		checkNotNull(filename, "fileName");

		List<Object> selected = serverSelector.selectFrom(filename, aliveClients.keySet(), replicationCount);

		checkState(!selected.isEmpty(), "Selected no servers to upload file " + filename);
		checkState(aliveClients.keySet().containsAll(selected), "Selected an id that is not one of client ids");

		class ConsumerWithId {
			final Object id;
			final ChannelConsumer<ByteBuf> consumer;

			ConsumerWithId(Object id, ChannelConsumer<ByteBuf> consumer) {
				this.id = id;
				this.consumer = consumer;
			}
		}

		return Promises.toList(selected.stream()
				.map(id -> aliveClients.get(id)
						.upload(filename, offset)
						.thenComposeEx(wrapDeath(id))
						.thenApply(consumer -> new ConsumerWithId(id,
								consumer.withAcknowledgement(ack ->
										ack.whenException(e -> markIfDead(id, e)))))
						.toTry()))
				.thenCompose(tries -> {
					List<ConsumerWithId> successes = tries.stream()
							.filter(Try::isSuccess)
							.map(Try::getOrNull)
							.collect(toList());

					if (successes.isEmpty()) {
						return ofFailure("Couldn't connect to any partition to upload file " + filename, tries);
					}

					ChannelSplitter<ByteBuf> splitter = ChannelSplitter.<ByteBuf>create().lenient();

					MaterializedPromise<List<Try<Void>>> uploadResults = Promises.collect(toList(), successes.stream()
							.map(s -> getAcknowledgement(cb ->
									splitter.addOutput()
											.set(s.consumer.withAcknowledgement(cb)))
									.toTry()))
							.materialize();

					if (logger.isTraceEnabled()) {
						logger.trace("uploading file {} to {}, {}", filename, successes.stream().map(s -> s.id.toString()).collect(joining(", ", "[", "]")), this);
					}

					ChannelConsumer<ByteBuf> consumer = splitter.getInput().getConsumer();

					// check number of uploads only here, so even if there were less connections
					// than replicationCount, they will still upload
					return Promise.of(consumer.withAcknowledgement(ack -> ack
							.thenCompose($ -> uploadResults)
							.thenCompose(ackTries -> {
								long successCount = ackTries.stream().filter(Try::isSuccess).count();
								// check number of uploads only here, so even if there were less connections
								// than replicationCount, they will still upload
								if (ackTries.size() < replicationCount) {
									return ofFailure("Didn't connect to enough partitions uploading " +
											filename + ", only " + successCount + " finished uploads", ackTries);
								}
								if (successCount < replicationCount) {
									return ofFailure("Couldn't finish uploadind file " +
											filename + ", only " + successCount + " acknowlegdes received", ackTries);
								}
								return Promise.complete();
							})
							.whenComplete(uploadFinishPromise.recordStats())));
				})
				.whenComplete(uploadStartPromise.recordStats());
	}

	@Override
	public Promise<ChannelSupplier<ByteBuf>> download(String filename, long offset, long length) {
		checkNotNull(filename, "fileName");

		class PartitionIdWithFileSize {
			final Object partitionId;
			final long fileSize;

			PartitionIdWithFileSize(Object partitionId, long fileSize) {
				this.partitionId = partitionId;
				this.fileSize = fileSize;
			}
		}

		if (deadClients.size() >= replicationCount) {
			return ofFailure("There are more dead partitions than replication count(" +
					deadClients.size() + " dead, replication count is " + replicationCount + "), aborting", emptyList());
		}

		return Promises.toList(
				aliveClients.entrySet().stream()
						.map(e -> {
							Object partitionId = e.getKey();
							return e.getValue().list(filename) //   ↓ use null's as file non-existense indicators
									.thenApply(res -> res.isEmpty() ? null : new PartitionIdWithFileSize(partitionId, res.get(0).getSize()))
									.thenComposeEx(wrapDeath(partitionId))
									.toTry();
						}))
				.thenCompose(tries -> {
					List<PartitionIdWithFileSize> successes = tries.stream() // filter succesfull connections
							.filter(Try::isSuccess)
							.map(Try::getOrNull)
							.collect(toList());

					// recheck if our download request marked any partitions as dead
					if (deadClients.size() >= replicationCount) {
						return ofFailure("There are more dead partitions than replication count(" +
								deadClients.size() + " dead, replication count is " + replicationCount + "), aborting", tries);
					}

					List<Object> found = successes.stream() // ↙ filter partitions where file was found
							.filter(Objects::nonNull) //   ↓ and also sort them by file size
							.sorted(Comparator.<PartitionIdWithFileSize>comparingLong(piwfs -> piwfs.fileSize).reversed())
							.map(piwfs -> piwfs.partitionId)
							.collect(toList());

					if (found.isEmpty()) {
						return ofFailure("File not found: " + filename, tries);
					}

					return Promises.firstSuccessful(found.stream() // try to download successively
							.map(partitionId -> {
								FsClient client = aliveClients.get(partitionId);
								if (client == null) { // marked as dead already by somebody
									return Promise.ofException(new RemoteFsException(RemoteFsClusterClient.class, "Client " + partitionId + " is not alive"));
								}
								logger.trace("downloading file {} from {}", filename, partitionId);
								return client.download(filename, offset, length)
										.whenException(e -> logger.warn("Failed to connect to server with key " + partitionId + " to download file " + filename, e))
										.thenComposeEx(wrapDeath(partitionId))
										.thenApply(supplier -> supplier
												.withEndOfStream(eos -> eos
														.whenException(e -> markIfDead(partitionId, e))
														.whenComplete(downloadFinishPromise.recordStats())));
							}));
				})
				.whenComplete(downloadStartPromise.recordStats());
	}

	private Promise<Set<String>> bulkMethod(Map<String, String> mapping, BiFunction<FsClient, Map<String, String>, Promise<Set<String>>> fn, String fnName) {
		return Promises.toList(aliveClients.entrySet().stream()
				.map(e -> fn.apply(e.getValue(), mapping)
						.thenComposeEx(wrapDeath(e.getKey()))
						.toTry()))
				.thenCompose(tries -> {
					if (tries.stream().noneMatch(Try::isSuccess)) {
						return ofFailure("Couldn't " + fnName + " on any partition", tries);
					}

					return Promise.of(tries.stream()
							.filter(Try::isSuccess) // extract successes
							.map(Try::getOrNull)
							.flatMap(Set::stream)  // collapse list of sets into one stream
							.collect(groupingBy(Function.identity(), Collectors.counting())) // map strings to their frequencies
							.entrySet()
							.stream()
							.filter(e -> e.getValue() >= replicationCount) // and filter these which were moved at least replicationCount times
							.map(Map.Entry::getKey)
							.collect(toSet()));
				});
	}

	@Override
	public Promise<Set<String>> move(Map<String, String> changes) {
		checkNotNull(changes, "changes");

		return bulkMethod(changes, FsClient::move, "move")
				.whenComplete(movePromise.recordStats());
	}

	@Override
	public Promise<Set<String>> copy(Map<String, String> changes) {
		checkNotNull(changes, "changes");

		return bulkMethod(changes, FsClient::copy, "copy")
				.whenComplete(movePromise.recordStats());
	}

	@Override
	public Promise<Void> delete(String glob) {
		checkNotNull(glob, "glob");

		return Promises.toList(aliveClients.entrySet().stream()
				.map(e -> e.getValue().delete(glob)
						.thenComposeEx(wrapDeath(e.getKey()))
						.toTry()))
				.thenCompose(tries -> {
					if (tries.stream().anyMatch(Try::isSuccess)) { // connected at least to somebody
						return Promise.complete();
					}
					return ofFailure("Couldn't delete on any partition", tries);
				})
				.whenComplete(deletePromise.recordStats());
	}

	@Override
	public Promise<List<FileMetadata>> list(String glob) {
		checkNotNull(glob, "glob");

		if (deadClients.size() >= replicationCount) {
			return ofFailure("There are more dead partitions than replication count(" +
					deadClients.size() + " dead, replication count is " + replicationCount + "), aborting", emptyList());
		}

		// this all is the same as delete, but with list of lists of results, flattened and unified
		return Promises.toList(aliveClients.entrySet().stream()
				.map(e -> e.getValue().list(glob)
						.thenComposeEx(wrapDeath(e.getKey()))
						.toTry()))
				.<List<FileMetadata>>thenCompose(tries -> {
					// recheck if our list request marked any partitions as dead
					if (deadClients.size() >= replicationCount) {
						return ofFailure("There are more dead partitions than replication count(" +
								deadClients.size() + " dead, replication count is " + replicationCount + "), aborting", tries);
					}
					return Promise.of(new ArrayList<>(tries.stream()
							.filter(Try::isSuccess)
							.map(Try::getOrNull)
							.flatMap(List::stream)
							.collect(toSet())));
				})
				.whenComplete(listPromise.recordStats());
	}

	@Override
	public Promise<Void> ping() {
		return checkAllPartitions();
	}

	@Override
	public Promise<Void> start() {
		return Promise.complete();
	}

	@Override
	public Promise<Void> stop() {
		return Promise.complete();
	}

	@Override
	public String toString() {
		return "RemoteFsClusterClient{clients=" + clients + ", dead=" + deadClients.keySet() + '}';
	}

	// region JMX
	@JmxAttribute
	public int getReplicationCount() {
		return replicationCount;
	}

	@JmxAttribute
	public void setReplicationCount(int replicationCount) {
		withReplicationCount(replicationCount);
	}

	@JmxAttribute
	public int getAlivePartitionCount() {
		return aliveClients.size();
	}

	@JmxAttribute
	public int getDeadPartitionCount() {
		return deadClients.size();
	}

	@JmxAttribute
	public String[] getAlivePartitions() {
		return aliveClients.keySet().stream()
				.map(Object::toString)
				.toArray(String[]::new);
	}

	@JmxAttribute
	public String[] getDeadPartitions() {
		return deadClients.keySet().stream()
				.map(Object::toString)
				.toArray(String[]::new);
	}

	@JmxAttribute
	public PromiseStats getConnectPromise() {
		return connectPromise;
	}

	@JmxAttribute
	public PromiseStats getUploadStartPromise() {
		return uploadStartPromise;
	}

	@JmxAttribute
	public PromiseStats getUploadFinishPromise() {
		return uploadFinishPromise;
	}

	@JmxAttribute
	public PromiseStats getDownloadStartPromise() {
		return downloadStartPromise;
	}

	@JmxAttribute
	public PromiseStats getDownloadFinishPromise() {
		return downloadFinishPromise;
	}

	@JmxAttribute
	public PromiseStats getMovePromise() {
		return movePromise;
	}

	@JmxAttribute
	public PromiseStats getCopyPromise() {
		return copyPromise;
	}

	@JmxAttribute
	public PromiseStats getListPromise() {
		return listPromise;
	}

	@JmxAttribute
	public PromiseStats getDeletePromise() {
		return deletePromise;
	}
	// endregion
}
