package io.datakernel.ot;

import ch.qos.logback.classic.Level;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.datakernel.async.Stages;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.FatalErrorHandlers;
import io.datakernel.ot.utils.*;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static io.datakernel.ot.OTCommit.ofCommit;
import static io.datakernel.ot.OTCommit.ofRoot;
import static io.datakernel.ot.utils.GraphBuilder.edge;
import static io.datakernel.ot.utils.Utils.add;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class OTRemoteSqlTest {

	private Eventloop eventloop;
	private OTRemoteSql<TestOp> otRemote;
	private OTSystem<TestOp> otSystem;
	private Comparator<Integer> keyComparator;

	static {
		ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		rootLogger.setLevel(Level.toLevel("TRACE"));
	}

	@Before
	public void before() throws IOException, SQLException {
		eventloop = Eventloop.create().withFatalErrorHandler(FatalErrorHandlers.rethrowOnAnyError());
		otSystem = Utils.createTestOp();
		otRemote = OTRemoteSql.create(Executors.newFixedThreadPool(4), dataSource("test.properties"), otSystem, Utils.OP_ADAPTER);
		keyComparator = Integer::compareTo;

		otRemote.truncateTables();
	}

	static HikariDataSource dataSource(String databasePropertiesPath) throws IOException {
		Properties properties = new Properties();
		properties.load(new InputStreamReader(new BufferedInputStream(new FileInputStream(new File(databasePropertiesPath))), StandardCharsets.UTF_8));
		return new HikariDataSource(new HikariConfig(properties));
	}

	static <T> Set<T> set(T... values) {
		return Arrays.stream(values).collect(Collectors.toSet());
	}

	private static int apply(List<TestOp> testOps) {
		TestOpState testOpState = new TestOpState();
		testOps.forEach(testOpState::apply);
		return testOpState.getValue();
	}

	@Test
	public void testJson() throws IOException {
		{
			TestAdd testAdd = new TestAdd(1);
			String json = Utils.OP_ADAPTER.toJson(testAdd);
			TestAdd testAdd2 = ((TestAdd) Utils.OP_ADAPTER.fromJson(json));
			assertEquals(testAdd.getDelta(), testAdd2.getDelta());
		}

		{
			TestSet testSet = new TestSet(0, 4);
			String json = Utils.OP_ADAPTER.toJson(testSet);
			TestSet testSet2 = ((TestSet) Utils.OP_ADAPTER.fromJson(json));
			assertEquals(testSet.getPrev(), testSet2.getPrev());
			assertEquals(testSet.getNext(), testSet2.getNext());
		}
	}

	@Test
	public void testRootHeads() throws ExecutionException, InterruptedException {
		CompletableFuture<Set<Integer>> headsFuture = otRemote.createId()
				.thenCompose(id -> otRemote.push(singletonList(ofRoot(id))))
				.thenCompose($ -> otRemote.getHeads())
				.toCompletableFuture();
		eventloop.run();

		Set<Integer> heads = headsFuture.get();
		assertEquals(1, heads.size());
		assertEquals(1, heads.iterator().next().intValue());
	}

	@Test
	public void testReplaceHead() throws ExecutionException, InterruptedException {
		CompletableFuture<Set<Integer>> headsFuture = otRemote.createId()
				.thenCompose(rootId -> otRemote.push(singletonList(ofRoot(rootId)))
						.thenCompose($ -> otRemote.createId())
						.thenCompose(id -> otRemote.push(singletonList(ofCommit(id, rootId, singletonList(new TestSet(0, 5)))))))
				.thenCompose($ -> otRemote.getHeads())
				.toCompletableFuture();
		eventloop.run();

		Set<Integer> heads = headsFuture.get();
		assertEquals(1, heads.size());
		assertEquals(2, heads.iterator().next().intValue());
	}

	@Test
	public void testReplaceHeadsOnMerge() throws ExecutionException, InterruptedException {
		/*
		        / firstId  \
		       /            \
		rootId -- secondId   -- mergeId(HEAD)
		       \            /
		        \ thirdId  /

		 */

		GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);

		CompletableFuture<Map<Integer, Integer>> graphFuture = graphBuilder.buildGraph(asList(
				edge(1, 2, add(1)),
				edge(1, 3, add(1)),
				edge(1, 4, add(1))))
				.toCompletableFuture();

		eventloop.run();
		graphFuture.get();

		CompletableFuture<Set<Integer>> headFuture = otRemote.getHeads().toCompletableFuture();
		eventloop.run();
		Set<Integer> heads = headFuture.get();
		assertEquals(3, heads.size());
		assertEquals(set(2, 3, 4), heads);

		CompletableFuture<Integer> mergeFuture = OTUtils.mergeHeadsAndPush(otSystem, otRemote, keyComparator).toCompletableFuture();

		eventloop.run();
		Integer mergeId = mergeFuture.get();

		CompletableFuture<Set<Integer>> headAfterMergeFuture = otRemote.getHeads().toCompletableFuture();
		eventloop.run();
		Set<Integer> headsAfterMerge = headAfterMergeFuture.get();
		assertEquals(1, headsAfterMerge.size());
		assertEquals(mergeId, headsAfterMerge.iterator().next());
	}

//	@Test
//	public void testMergeSnapshotOnMergeNodes() throws ExecutionException, InterruptedException {
//		final GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);
//		final CompletableFuture<Void> graphFuture = graphBuilder.buildGraph(asList(
//				edge(1, 2, add(1)),
//				edge(1, 3, add(1)),
//				edge(1, 4, add(1)),
//				edge(1, 5, add(1)),
//				edge(2, 6, add(1)),
//				edge(3, 6, add(1)),
//				edge(4, 7, add(1)),
//				edge(5, 7, add(1)),
//				edge(6, 8, add(1)),
//				edge(7, 9, add(1))))
//				.toCompletableFuture();
//
//		eventloop.run();
//		graphFuture.get();
//
//		final CompletableFuture<Integer> beforeVirtualЩFutureNode4 = OTAlgorithms
//				.loadAllChanges(otRemote, keyComparator, otSystem, 6)
//				.thenApply(OTRemoteSqlTest::apply)
//				.toCompletableFuture();
//
//		final CompletableFuture<Integer> beforeVirtualFutureNode5 = OTAlgorithms
//				.loadAllChanges(otRemote, keyComparator, otSystem, 8)
//				.thenApply(OTRemoteSqlTest::apply)
//				.toCompletableFuture();
//
//		eventloop.run();
//
//		final Integer node4Before = beforeVirtualFutureNode4.get();
//		final Integer node5Before = beforeVirtualFutureNode5.get();
//		assertEquals(2, node4Before.intValue());
//		assertEquals(3, node5Before.intValue());
//
//		final CompletableFuture<?> virtualFuture = OTAlgorithms
//				.saveCheckpoint(otRemote, keyComparator, otSystem, newHashSet(6, 7))
//				.toCompletableFuture();
//		eventloop.run();
//		virtualFuture.get();
//
//		final CompletableFuture<Integer> afterVirtualFutureNode4 = OTAlgorithms
//				.loadAllChanges(otRemote, keyComparator, otSystem, 6)
//				.thenApply(OTRemoteSqlTest::apply)
//				.toCompletableFuture();
//
//		final CompletableFuture<Integer> afterVirtualFutureNode5 = OTAlgorithms
//				.loadAllChanges(otRemote, keyComparator, otSystem, 8)
//				.thenApply(OTRemoteSqlTest::apply)
//				.toCompletableFuture();
//
//		eventloop.run();
//		final Integer node4After = afterVirtualFutureNode4.get();
//		final Integer node5After = afterVirtualFutureNode5.get();
//
//		assertEquals(node5Before, node5After);
//		assertEquals(node4Before, node4After);
//
//		OTAlgorithms.mergeHeadsAndPush(otSystem, otRemote, Integer::compareTo);
//		// logs
//		eventloop.run();
//	}

	@Test
	public void testForkMerge() throws ExecutionException, InterruptedException {
		GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);
		CompletableFuture<Map<Integer, Integer>> graphFuture = graphBuilder.buildGraph(asList(
				edge(1, 2, add(1)),
				edge(2, 3, add(1)),
				edge(3, 4, add(1)),
				edge(4, 5, add(1)),
				edge(4, 6, add(1)),
				edge(5, 7, add(1)),
				edge(6, 8, add(1))))
				.toCompletableFuture();

		eventloop.run();
		graphFuture.get();

		CompletableFuture<Integer> merge = OTUtils
				.mergeHeadsAndPush(otSystem, otRemote, Integer::compareTo)
				.toCompletableFuture();

		eventloop.run();
		merge.get();

//		assertEquals(searchSurface, rootNodesFuture.get());
	}

	@Test
	public void testFindRootNodes() throws ExecutionException, InterruptedException {
		GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);
		CompletableFuture<Map<Integer, Integer>> graphFuture = graphBuilder.buildGraph(asList(
				edge(1, 2, add(1)),
				edge(1, 3, add(1)),
				edge(2, 4, add(1)),
				edge(3, 4, add(1)),
				edge(2, 5, add(1)),
				edge(3, 5, add(1)),
				edge(4, 6, add(1)),
				edge(5, 7, add(1))))
				.toCompletableFuture();

		eventloop.run();
		graphFuture.get();

		CompletableFuture<Set<Integer>> rootNodes1Future = OTUtils
				.findCommonParents(otRemote, Integer::compareTo, set(6, 7))
				.toCompletableFuture();

		CompletableFuture<Set<Integer>> rootNodes2Future = OTUtils
				.findCommonParents(otRemote, Integer::compareTo, set(6))
				.toCompletableFuture();

		eventloop.run();

		assertEquals(set(2, 3), rootNodes1Future.get());
		assertEquals(set(6), rootNodes2Future.get());
	}

	@Test
	public void testFindRootNodes2() throws ExecutionException, InterruptedException {
		GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);
		CompletableFuture<Map<Integer, Integer>> graphFuture = graphBuilder.buildGraph(asList(
				edge(1, 2, add(1)),
				edge(2, 3, add(1)),
				edge(3, 4, add(1)),
				edge(4, 5, add(1)),
				edge(4, 6, add(1)))
		).toCompletableFuture();

		eventloop.run();
		graphFuture.get();

		CompletableFuture<Set<Integer>> rootNodesFuture = OTUtils
				.findCommonParents(otRemote, Integer::compareTo, set(4, 5, 6))
				.toCompletableFuture();

		eventloop.run();

		assertEquals(set(4), rootNodesFuture.get());
	}

	@Test
	public void testFindParentCandidatesSurface() throws ExecutionException, InterruptedException {
		GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);
		CompletableFuture<Map<Integer, Integer>> graphFuture = graphBuilder.buildGraph(asList(
				edge(1, 2, add(1)),
				edge(1, 3, add(1)),
				edge(2, 4, add(1)),
				edge(3, 4, add(1)),
				edge(2, 5, add(1)),
				edge(3, 5, add(1)),
				edge(4, 6, add(1)),
				edge(5, 7, add(1))))
				.toCompletableFuture();

		eventloop.run();
		graphFuture.get();

		Set<Integer> searchSurface = set(2, 3);
		CompletableFuture<Set<Integer>> rootNodesFuture = OTUtils
				.findSurface(otRemote, Integer::compareTo, set(6, 7), integers -> Stages.of(searchSurface.equals(integers)))
				.toCompletableFuture();

		eventloop.run();

		assertEquals(searchSurface, rootNodesFuture.get());
	}

	@Test
	public void testSingleCacheCheckpointNode() throws ExecutionException, InterruptedException {
		GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);
		CompletableFuture<Map<Integer, Integer>> graphFuture = graphBuilder.buildGraph(asList(
				edge(1, 2, add(1)),
				edge(2, 3, add(1)),
				edge(3, 4, add(1)),
				edge(4, 5, add(1)),
				edge(5, 6, add(1)),
				edge(5, 7, add(1))))
				.toCompletableFuture();

		eventloop.run();
		graphFuture.get();

		CompletableFuture<?> mergeSnapshotFuture = OTUtils
				.loadAllChanges(otRemote, keyComparator, otSystem, 5)
				.thenCompose(ds -> otRemote.saveSnapshot(5, ds))
				.thenCompose($ -> otRemote.cleanup(5))
				.toCompletableFuture();

		eventloop.run();
		mergeSnapshotFuture.get();

		CompletableFuture<Integer> snapshotFuture = OTUtils
				.loadAllChanges(otRemote, keyComparator, otSystem, 7)
				.thenApply(OTRemoteSqlTest::apply)
				.toCompletableFuture();
		eventloop.run();

		assertEquals(5, snapshotFuture.get().intValue());
	}
}