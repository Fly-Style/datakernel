package io.datakernel;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.datakernel.async.*;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.FatalErrorHandlers;
import io.datakernel.serializer.BufferSerializer;
import io.datakernel.storage.DataStorageTreeMap;
import io.datakernel.storage.HasSortedStreamProducer;
import io.datakernel.storage.HasSortedStreamProducer.KeyValue;
import io.datakernel.storage.Synchronizer;
import io.datakernel.storage.remote.DataStorageRemoteClient;
import io.datakernel.storage.remote.DataStorageRemoteServer;
import io.datakernel.stream.StreamConsumers;
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.processor.StreamReducers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

import static io.datakernel.FullExample.Key.newKey;
import static java.util.Arrays.asList;

public class FullExample {
	private static final int START_PORT = 12457;
	private static final List<InetSocketAddress> addresses = asList(
			new InetSocketAddress(START_PORT), new InetSocketAddress(START_PORT + 1),
			new InetSocketAddress(START_PORT + 2), new InetSocketAddress(START_PORT + 3),
			new InetSocketAddress(START_PORT + 4), new InetSocketAddress(START_PORT + 5));

	private static final BufferSerializer<KeyValue<Key, Set<String>>> KEY_VALUE_SERIALIZER = new BufferSerializer<KeyValue<Key, Set<String>>>() {
		@Override
		public void serialize(ByteBuf output, KeyValue<Key, Set<String>> item) {
			output.writeInt(item.getKey().key);
			output.writeInt(item.getKey().nodeId);
			output.writeInt(item.getValue().size());
			for (String value : item.getValue()) output.writeJavaUTF8(value);
		}

		@Override
		public KeyValue<Key, Set<String>> deserialize(ByteBuf input) {
			final int key = input.readInt();
			final int nodeId = input.readInt();
			final Set<String> values = new TreeSet<>();
			for (int i = 0, size = input.readInt(); i < size; i++) values.add(input.readJavaUTF8());
			return new KeyValue<>(newKey(key, nodeId), values);
		}
	};

	private static final TypeAdapterFactory TYPE_ADAPTER_FACTORY = new TypeAdapterFactory() {
		@SuppressWarnings("unchecked")
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
			if (Predicate.class.isAssignableFrom(typeToken.getRawType())) {
				return (TypeAdapter<T>) new TypeAdapter<ModPredicate>() {
					@Override
					public void write(JsonWriter jsonWriter, ModPredicate modPredicate) throws IOException {
						jsonWriter.beginArray();
						jsonWriter.value(modPredicate.getValue());
						jsonWriter.value(modPredicate.isInvert());
						jsonWriter.endArray();
					}

					@Override
					public ModPredicate read(JsonReader jsonReader) throws IOException {
						jsonReader.beginArray();
						final int value = jsonReader.nextInt();
						final boolean invert = jsonReader.nextBoolean();
						jsonReader.endArray();
						return new ModPredicate(value, invert);
					}
				};
			}
			return null;
		}
	};

	private static ModPredicate predicate(int mod, boolean invert) {
		return new ModPredicate(mod, invert);
	}

	private static ModPredicate evenPredicate() {
		return predicate(2, false);
	}

	private static ModPredicate oddPredicate() {
		return predicate(2, true);
	}

	private static <K extends Comparable<K>, V> DataStorageTreeMap<K, V> createAndStartNode(
			final Eventloop eventloop, Gson gson, BufferSerializer<KeyValue<K, V>> bufferSerializer,
			int port, List<InetSocketAddress> addresses, Predicate<K> keyFilter,
			TreeMap<K, V> treeMap, StreamReducers.Reducer<K, KeyValue<K, V>, KeyValue<K, V>, Void> reducer) throws IOException {

		final List<DataStorageRemoteClient<K, V>> remoteClients = new ArrayList<>();
		for (InetSocketAddress address : addresses) {
			remoteClients.add(new DataStorageRemoteClient<>(eventloop, address, gson, bufferSerializer));
		}

		final DataStorageTreeMap<K, V> dataStorageTreeMap = new DataStorageTreeMap<>(eventloop, treeMap, remoteClients, reducer, keyFilter);
		final DataStorageRemoteServer<K, V> remoteServer = new DataStorageRemoteServer<>(eventloop, dataStorageTreeMap, gson, bufferSerializer)
				.withListenPort(port);

		remoteServer.listen();
		return dataStorageTreeMap;
	}

	private static KeyValue<Integer, Set<String>> keyValue(int key, String... value) {
		return new KeyValue<Integer, Set<String>>(key, Sets.newTreeSet(asList(value)));
	}

	@SafeVarargs
	private static TreeMap<Key, Set<String>> map(final int nodeId, final KeyValue<Integer, Set<String>>... keyValues) {
		return new TreeMap<Key, Set<String>>() {{
			for (KeyValue<Integer, Set<String>> keyValue : keyValues) {
				put(newKey(keyValue.getKey(), nodeId), keyValue.getValue());
			}
		}};
	}

	public static void main(String[] args) throws IOException {
		System.out.println("START");
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(FatalErrorHandlers.rethrowOnAnyError());
		final Gson gson = new GsonBuilder().registerTypeAdapterFactory(TYPE_ADAPTER_FACTORY).create();

		final StreamReducers.Reducer<Key, KeyValue<Key, Set<String>>, KeyValue<Key, Set<String>>, Void> reducer =
				StreamReducers.mergeSortReducer();

		System.out.println("Start nodes");

		final TreeMap<Key, Set<String>> treeMap0 = map(0, keyValue(1, "1:a"), keyValue(2, "2:a"), keyValue(3, "3:a"));
		final List<InetSocketAddress> addresses0 = asList(addresses.get(4), addresses.get(5));
		final DataStorageTreeMap<Key, Set<String>> node0 = createAndStartNode(eventloop, gson, KEY_VALUE_SERIALIZER,
				START_PORT, addresses0, evenPredicate(), treeMap0, reducer);

		final TreeMap<Key, Set<String>> treeMap1 = map(1, keyValue(4, "4:a"), keyValue(5, "5:a"), keyValue(6, "6:a"));
		final List<InetSocketAddress> addresses1 = asList(addresses.get(0), addresses.get(5));
		final DataStorageTreeMap<Key, Set<String>> node1 = createAndStartNode(eventloop, gson, KEY_VALUE_SERIALIZER,
				START_PORT + 1, addresses1, oddPredicate(), treeMap1, reducer);

		final TreeMap<Key, Set<String>> treeMap2 = map(2, keyValue(7, "7:b"), keyValue(8, "8:a"), keyValue(9, "9:a"));
		final List<InetSocketAddress> addresses2 = asList(addresses.get(0), addresses.get(1));
		final DataStorageTreeMap<Key, Set<String>> node2 = createAndStartNode(eventloop, gson, KEY_VALUE_SERIALIZER,
				START_PORT + 2, addresses2, evenPredicate(), treeMap2, reducer);

		final TreeMap<Key, Set<String>> treeMap3 = map(3, keyValue(10, "10:b"), keyValue(11, "11:a"), keyValue(12, "12:a"));
		final List<InetSocketAddress> addresses3 = asList(addresses.get(1), addresses.get(2));
		final DataStorageTreeMap<Key, Set<String>> node3 = createAndStartNode(eventloop, gson, KEY_VALUE_SERIALIZER,
				START_PORT + 3, addresses3, oddPredicate(), treeMap3, reducer);

		final TreeMap<Key, Set<String>> treeMap4 = map(4, keyValue(13, "13:b"), keyValue(14, "14:a"), keyValue(15, "15:a"));
		final List<InetSocketAddress> addresses4 = asList(addresses.get(2), addresses.get(3));
		final DataStorageTreeMap<Key, Set<String>> node4 = createAndStartNode(eventloop, gson, KEY_VALUE_SERIALIZER,
				START_PORT + 4, addresses4, evenPredicate(), treeMap4, reducer);

		final TreeMap<Key, Set<String>> treeMap5 = map(5, keyValue(16, "13:b"), keyValue(17, "14:a"), keyValue(18, "15:a"));
		final List<InetSocketAddress> addresses5 = asList(addresses.get(3), addresses.get(4));
		final DataStorageTreeMap<Key, Set<String>> node5 = createAndStartNode(eventloop, gson, KEY_VALUE_SERIALIZER,
				START_PORT + 5, addresses5, oddPredicate(), treeMap5, reducer);

		System.out.println("All nodes started");

		schedulePrintAndSync(eventloop, node0, node1, node2, node3, node4, node5);
		scheduleStateCheck(eventloop, node0, node1, node2, node3, node4, node5);

		eventloop.keepAlive(true);
		eventloop.run();

		System.out.println("FINISH");

	}

	@SafeVarargs
	private static void scheduleStateCheck(final Eventloop eventloop, final DataStorageTreeMap<Key, Set<String>>... nodes) {
		eventloop.schedule(eventloop.currentTimeMillis() + 9000, new Runnable() {
			@Override
			public void run() {
				final List<AsyncCallable<List<KeyValue<Key, Set<String>>>>> asyncCallables = new ArrayList<>();
				for (DataStorageTreeMap<Key, Set<String>> node : nodes) {
					asyncCallables.add(getState(eventloop, Predicates.<Key>alwaysTrue(), node));
				}

				System.out.println("Start state check");
				AsyncCallables.callAll(eventloop, asyncCallables).call(new AssertingResultCallback<List<List<KeyValue<Key, Set<String>>>>>() {
					@Override
					protected void onResult(List<List<KeyValue<Key, Set<String>>>> result) {
						final Map<KeyValue<Key, Set<String>>, Integer> map = new HashMap<>();
						for (List<KeyValue<Key, Set<String>>> keyValues : result) {
							for (KeyValue<Key, Set<String>> keyValue : keyValues) {
								final Integer count = map.get(keyValue);
								map.put(keyValue, count == null ? 1 : count + 1);
							}
						}
						for (Map.Entry<KeyValue<Key, Set<String>>, Integer> entry : map.entrySet()) {
							if (entry.getValue() < 2) {
								throw new RuntimeException(entry.toString());
							}
						}
						System.out.println("Finish state check");
					}
				});
			}
		});
	}

	@SafeVarargs
	private static <V> void schedulePrintAndSync(final Eventloop eventloop,
	                                             final DataStorageTreeMap<Key, V>... nodes) {
		eventloop.schedule(eventloop.currentTimeMillis() + 3000, new Runnable() {
			@Override
			public void run() {
				AsyncRunnables.runInSequence(eventloop, printState(eventloop, nodes))
						.run(new AssertingCompletionCallback() {
							@Override
							protected void onComplete() {
								System.out.println("Start node sync");
								AsyncRunnables.runInSequence(eventloop, syncState((Synchronizer[]) nodes))
										.run(new AssertingCompletionCallback() {
											@Override
											protected void onComplete() {
												System.out.println("Finish node sync");
												System.out.println(Strings.repeat("-", 80));
												schedulePrintAndSync(eventloop, nodes);
											}
										});
							}
						});
			}
		});
	}

	private static List<AsyncRunnable> syncState(final Synchronizer... nodes) {
		final List<AsyncRunnable> asyncRunnables = new ArrayList<>();
		for (final Synchronizer node : nodes) {
			asyncRunnables.add(new AsyncRunnable() {
				@Override
				public void run(CompletionCallback callback) {
					node.synchronize(callback);
				}
			});
		}
		return asyncRunnables;
	}

	@SafeVarargs
	private static <V> List<AsyncRunnable> printState(final Eventloop eventloop, DataStorageTreeMap<Key, V>... nodes) {
		final List<AsyncRunnable> asyncRunnables = new ArrayList<>();
		for (int nodeId = 0; nodeId < nodes.length; nodeId++) {
			final DataStorageTreeMap<Key, V> node = nodes[nodeId];
			final int finalNodeId = nodeId;
			asyncRunnables.add(new AsyncRunnable() {
				@Override
				public void run(final CompletionCallback callback) {
					getState(eventloop, Predicates.<Key>alwaysTrue(), node).call(new ForwardingResultCallback<List<KeyValue<Key, V>>>(callback) {
						@Override
						protected void onResult(List<KeyValue<Key, V>> result) {
							prettyPrintState(result, finalNodeId);
							callback.setComplete();
						}
					});
				}
			});
		}
		return asyncRunnables;
	}

	private static <V> void prettyPrintState(List<KeyValue<Key, V>> result, final int finalNodeId) {
		Collections.sort(result, new Comparator<KeyValue<Key, V>>() {
			@Override
			public int compare(KeyValue<Key, V> o1, KeyValue<Key, V> o2) {
				return Integer.compare(o1.getKey().nodeId, o2.getKey().nodeId);
			}
		});
		System.out.printf("storage %d:%n", finalNodeId);
		while (!result.isEmpty()) {
			final KeyValue<Key, V> first = result.get(0);
			final Collection<KeyValue<Key, V>> collection = Collections2.filter(result, new Predicate<KeyValue<Key, V>>() {
				@Override
				public boolean apply(KeyValue<Key, V> input) {
					return input.getKey().nodeId == first.getKey().nodeId;
				}
			});
			System.out.println("\t" + collection);
			result.removeAll(collection);
		}
	}

	private static <K, V> AsyncCallable<List<KeyValue<K, V>>> getState(final Eventloop eventloop,
	                                                                   final Predicate<K> predicate,
	                                                                   final HasSortedStreamProducer<K, V> hasSortedStreamProducer) {
		return new AsyncCallable<List<KeyValue<K, V>>>() {
			@Override
			public void call(final ResultCallback<List<KeyValue<K, V>>> callback) {
				hasSortedStreamProducer.getSortedStreamProducer(predicate, new ForwardingResultCallback<StreamProducer<KeyValue<K, V>>>(callback) {
					@Override
					protected void onResult(StreamProducer<KeyValue<K, V>> producer) {
						final StreamConsumers.ToList<KeyValue<K, V>> consumerToList = StreamConsumers.toList(eventloop);
						producer.streamTo(consumerToList);
						consumerToList.setCompletionCallback(new AssertingCompletionCallback() {
							@Override
							protected void onComplete() {
								callback.setResult(consumerToList.getList());
							}
						});
					}
				});
			}
		};
	}

	public static class ModPredicate implements Predicate<Key> {
		private final int value;
		private final boolean invert;

		public ModPredicate(int value, boolean invert) {
			this.value = value;
			this.invert = invert;
		}

		@Override
		public boolean apply(Key input) {
			return !invert ? input.key % getValue() == 0 : input.key % getValue() != 0;
		}

		public int getValue() {
			return value;
		}

		public boolean isInvert() {
			return invert;
		}

		@Override
		public String toString() {
			return "Predicate(n % " + value + ")";
		}

	}

	public static class Key implements Comparable<Key> {
		public final int key;
		public final int nodeId;

		public Key(int key, int nodeId) {
			this.key = key;
			this.nodeId = nodeId;
		}

		public static Key newKey(int key, int nodeId) {
			return new Key(key, nodeId);
		}

		@Override
		public int compareTo(Key o) {
			return Integer.compare(this.key, o.key);
		}

		@Override
		public String toString() {
			return "{" +
					"key=" + key +
					", nodeId=" + nodeId +
					'}';
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Key key1 = (Key) o;

			if (key != key1.key) return false;
			return nodeId == key1.nodeId;

		}

		@Override
		public int hashCode() {
			int result = key;
			result = 31 * result + nodeId;
			return result;
		}
	}

}
