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

package io.datakernel.cube;

import io.datakernel.aggregation.Aggregation;
import io.datakernel.aggregation.LocalFsChunkStorage;
import io.datakernel.aggregation.fieldtype.FieldTypes;
import io.datakernel.codegen.DefiningClassLoader;
import io.datakernel.cube.ot.CubeDiff;
import io.datakernel.cube.ot.CubeDiffJson;
import io.datakernel.cube.ot.CubeOT;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.logfs.LocalFsLogFileSystem;
import io.datakernel.logfs.LogManager;
import io.datakernel.logfs.LogManagerImpl;
import io.datakernel.logfs.ot.*;
import io.datakernel.ot.OTCommit;
import io.datakernel.ot.OTRemoteSql;
import io.datakernel.ot.OTStateManager;
import io.datakernel.ot.OTSystem;
import io.datakernel.serializer.SerializerBuilder;
import io.datakernel.stream.StreamConsumers;
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.StreamProducers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static io.datakernel.aggregation.AggregationPredicates.alwaysTrue;
import static io.datakernel.aggregation.fieldtype.FieldTypes.ofDouble;
import static io.datakernel.aggregation.fieldtype.FieldTypes.ofLong;
import static io.datakernel.aggregation.measure.Measures.sum;
import static io.datakernel.cube.Cube.AggregationConfig.id;
import static io.datakernel.cube.CubeTestUtils.dataSource;
import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.DataStreams.stream;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class CubeIntegrationTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@SuppressWarnings({"ConstantConditions", "unchecked"})
	@Test
	public void test() throws Exception {
		Path aggregationsDir = temporaryFolder.newFolder().toPath();
		Path logsDir = temporaryFolder.newFolder().toPath();

		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());
		ExecutorService executor = Executors.newCachedThreadPool();
		DefiningClassLoader classLoader = DefiningClassLoader.create();

		LocalFsChunkStorage aggregationChunkStorage = LocalFsChunkStorage.create(eventloop, executor, new IdGeneratorStub(), aggregationsDir);
		Cube cube = Cube.create(eventloop, executor, classLoader, aggregationChunkStorage)
				.withDimension("date", FieldTypes.ofLocalDate())
				.withDimension("advertiser", FieldTypes.ofInt())
				.withDimension("campaign", FieldTypes.ofInt())
				.withDimension("banner", FieldTypes.ofInt())
				.withRelation("campaign", "advertiser")
				.withRelation("banner", "campaign")
				.withMeasure("impressions", sum(ofLong()))
				.withMeasure("clicks", sum(ofLong()))
				.withMeasure("conversions", sum(ofLong()))
				.withMeasure("revenue", sum(ofDouble()))
				.withAggregation(id("detailed")
						.withDimensions("date", "advertiser", "campaign", "banner")
						.withMeasures("impressions", "clicks", "conversions", "revenue"))
				.withAggregation(id("date")
						.withDimensions("date")
						.withMeasures("impressions", "clicks", "conversions", "revenue"))
				.withAggregation(id("advertiser")
						.withDimensions("advertiser")
						.withMeasures("impressions", "clicks", "conversions", "revenue"));

		DataSource dataSource = dataSource("test.properties");
		final OTSystem<LogDiff<CubeDiff>> otSystem = LogOT.createLogOT(CubeOT.createCubeOT());
		OTRemoteSql<LogDiff<CubeDiff>> otSourceSql = OTRemoteSql.create(executor, dataSource, otSystem, LogDiffJson.create(CubeDiffJson.create(cube)));
		otSourceSql.truncateTables();
		otSourceSql.createId().thenCompose(integer -> otSourceSql.push(OTCommit.ofRoot(integer)));
		eventloop.run();

		final LogOTState<CubeDiff> cubeDiffLogOTState = new LogOTState<>(cube);
		OTStateManager<Integer, LogDiff<CubeDiff>> logCubeStateManager = new OTStateManager<>(eventloop,
				otSystem,
				otSourceSql,
				Integer::compare,
				cubeDiffLogOTState);

		LogManager<LogItem> logManager = LogManagerImpl.create(eventloop,
				LocalFsLogFileSystem.create(executor, logsDir),
				SerializerBuilder.create(classLoader).build(LogItem.class));

		LogOTProcessor<LogItem, CubeDiff> logOTProcessor = LogOTProcessor.create(eventloop,
				logManager,
				cube.logStreamConsumer(LogItem.class),
				"testlog",
				asList("partitionA"),
				cubeDiffLogOTState);

		// checkout first (root) revision

		CompletableFuture<?> future;

		future = logCubeStateManager.checkout().toCompletableFuture();
		eventloop.run();
		future.get();

		// Save and aggregate logs
		List<LogItem> listOfRandomLogItems = LogItem.getListOfRandomLogItems(100);
		StreamProducer<LogItem> producerOfRandomLogItems = StreamProducers.ofIterator(listOfRandomLogItems.iterator());
		stream(producerOfRandomLogItems, logManager.consumerStream("partitionA"));
		eventloop.run();
		Files.list(logsDir).forEach(System.out::println);

//		AsynchronousFileChannel channel = AsynchronousFileChannel.open(Files.list(logsDir).findFirst().get(),
//				EnumSet.of(StandardOpenOption.WRITE), executor);
//		channel.truncate(13);
//		channel.write(ByteBuffer.wrap(new byte[]{123}), 0).get();
//		channel.close();

		future = logOTProcessor.processLog()
				.thenCompose(logDiff -> aggregationChunkStorage
						.finish(logDiff.diffs().flatMap(CubeDiff::addedChunks).collect(toSet()))
						.thenApply($ -> logDiff))
				.thenAccept(logCubeStateManager::add)
				.thenApply(aVoid -> logCubeStateManager)
				.thenCompose(OTStateManager::commitAndPush).toCompletableFuture();
		eventloop.run();
		future.get();

		future = logOTProcessor.processLog()
				.thenCompose(logDiff -> aggregationChunkStorage
						.finish(logDiff.diffs().flatMap(CubeDiff::addedChunks).collect(toSet()))
						.thenApply($ -> logDiff))
				.thenAccept(logCubeStateManager::add)
				.thenApply(aVoid -> logCubeStateManager)
				.thenCompose(OTStateManager::commitAndPush).toCompletableFuture();
		eventloop.run();
		future.get();

		List<LogItem> listOfRandomLogItems2 = LogItem.getListOfRandomLogItems(300);
		producerOfRandomLogItems = StreamProducers.ofIterator(listOfRandomLogItems2.iterator());
		stream(producerOfRandomLogItems, logManager.consumerStream("partitionA"));
		eventloop.run();
		Files.list(logsDir).forEach(System.out::println);

		future = logOTProcessor.processLog()
				.thenCompose(logDiff -> aggregationChunkStorage
						.finish(logDiff.diffs().flatMap(CubeDiff::addedChunks).collect(toSet()))
						.thenApply($ -> logDiff))
				.thenAccept(logCubeStateManager::add)
				.thenApply(aVoid -> logCubeStateManager)
				.thenCompose(OTStateManager::commitAndPush).toCompletableFuture();
		eventloop.run();
		future.get();

		List<LogItem> listOfRandomLogItems3 = LogItem.getListOfRandomLogItems(50);
		producerOfRandomLogItems = StreamProducers.ofIterator(listOfRandomLogItems3.iterator());
		stream(producerOfRandomLogItems, logManager.consumerStream("partitionA"));
		eventloop.run();
		Files.list(logsDir).forEach(System.out::println);

		future = logOTProcessor.processLog()
				.thenCompose(logDiff -> aggregationChunkStorage
						.finish(logDiff.diffs().flatMap(CubeDiff::addedChunks).collect(toSet()))
						.thenApply($ -> logDiff))
				.thenAccept(logCubeStateManager::add)
				.thenApply(aVoid -> logCubeStateManager)
				.thenCompose(OTStateManager::commitAndPush).toCompletableFuture();
		eventloop.run();
		future.get();

		future = aggregationChunkStorage.backup("backup1", cube.getAllChunks()).toCompletableFuture();
		eventloop.run();
		future.get();

		Future<List<LogItem>> futureResult = StreamConsumers.toList(
				cube.queryRawStream(asList("date"), asList("clicks"), alwaysTrue(),
						LogItem.class, DefiningClassLoader.create(classLoader))).toCompletableFuture();
		eventloop.run();

		// Aggregate manually
		Map<Integer, Long> map = new HashMap<>();
		aggregateToMap(map, listOfRandomLogItems);
		aggregateToMap(map, listOfRandomLogItems2);
		aggregateToMap(map, listOfRandomLogItems3);

		// Check query results
		assertEquals(map, futureResult.get().stream().collect(toMap(r -> r.date, r -> r.clicks)));

		// checkout revision 3 and consolidate it:
		future = logCubeStateManager.checkout(3).toCompletableFuture();
		eventloop.run();
		future.get();

		CompletableFuture<CubeDiff> future1 = cube.consolidate(Aggregation::consolidateHotSegment).toCompletableFuture();
		eventloop.run();
		CubeDiff consolidatingCubeDiff = future1.get();
		assertEquals(false, consolidatingCubeDiff.isEmpty());

		logCubeStateManager.add(LogDiff.forCurrentPosition(consolidatingCubeDiff));
		future = logCubeStateManager.commitAndPush().toCompletableFuture();
		eventloop.run();
		future.get();

		future = aggregationChunkStorage.finish(consolidatingCubeDiff.addedChunks().collect(toSet())).toCompletableFuture();
		eventloop.run();
		future.get();

		// merge heads: revision 4, and revision 5 (which is a consolidation of 3)

		future = logCubeStateManager.mergeHeadsAndPush().toCompletableFuture();
		eventloop.run();
		future.get();

		// make a checkpoint and checkout it

		future = logCubeStateManager.checkout(6).toCompletableFuture();
		eventloop.run();
		future.get();

		future = aggregationChunkStorage.cleanup(cube.getAllChunks()).toCompletableFuture();
		eventloop.run();
		future.get();

		// Query
		futureResult = StreamConsumers.toList(
				cube.queryRawStream(asList("date"), asList("clicks"), alwaysTrue(),
						LogItem.class, DefiningClassLoader.create(classLoader))).toCompletableFuture();
		eventloop.run();

		assertEquals(map, futureResult.get().stream().collect(toMap(r -> r.date, r -> r.clicks)));

		// Check files in aggregations directory
		Set<String> actualChunkFileNames = new TreeSet<>();
		for (File file : aggregationsDir.toFile().listFiles()) {
			actualChunkFileNames.add(file.getName());
		}
		assertEquals(concat(Stream.of("backups"), Stream.of(7, 8, 9, 10, 11, 12).map(n -> n + ".log")).collect(toSet()),
				actualChunkFileNames);
	}

	private void aggregateToMap(Map<Integer, Long> map, List<LogItem> logItems) {
		for (LogItem logItem : logItems) {
			int date = logItem.date;
			long clicks = logItem.clicks;
			if (map.get(date) == null) {
				map.put(date, clicks);
			} else {
				Long clicksForDate = map.get(date);
				map.put(date, clicksForDate + clicks);
			}
		}
	}
}
