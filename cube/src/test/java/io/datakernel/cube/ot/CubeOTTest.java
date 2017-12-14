package io.datakernel.cube.ot;

import io.datakernel.aggregation.AggregationChunk;
import io.datakernel.aggregation.PrimaryKey;
import io.datakernel.aggregation.ot.AggregationDiff;
import io.datakernel.logfs.LogFile;
import io.datakernel.logfs.LogPosition;
import io.datakernel.logfs.ot.LogDiff;
import io.datakernel.logfs.ot.LogDiff.LogPositionDiff;
import io.datakernel.logfs.ot.LogOT;
import io.datakernel.ot.OTSystem;
import io.datakernel.ot.TransformResult;
import io.datakernel.ot.TransformResult.ConflictResolution;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.datakernel.aggregation.PrimaryKey.ofArray;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class CubeOTTest {
	private OTSystem<CubeDiff> cubeSystem;
	private OTSystem<LogDiff<CubeDiff>> logSystem;

	@Before
	public void before() {
		cubeSystem = CubeOT.createCubeOT();
		logSystem = LogOT.createLogOT(cubeSystem);
	}

	private static LogPositionDiff positionDiff(LogFile logFile, long start, long end) {
		return new LogPositionDiff(LogPosition.create(logFile, start), LogPosition.create(logFile, end));
	}

	private static CubeDiff cubeDiff(String key, AggregationChunk... added) {
		return cubeDiff(key, asList(added), Collections.emptyList());
	}

	private static CubeDiff cubeDiff(String key, List<AggregationChunk> added, List<AggregationChunk> removed) {
		return CubeDiff.of(singletonMap(key, AggregationDiff.of(added.stream().collect(Collectors.toSet()),
				removed.stream().collect(Collectors.toSet()))));
	}

	private static AggregationChunk chunk(long chunkId, List<String> fields, PrimaryKey minKey, PrimaryKey maxKey, int count) {
		return AggregationChunk.create(chunkId, fields, minKey, maxKey, count);
	}

	private static List<AggregationChunk> addedChunks(Collection<CubeDiff> cubeDiffs) {
		return cubeDiffs.stream()
				.flatMap(cubeDiff -> cubeDiff.keySet().stream().map(cubeDiff::get))
				.map(AggregationDiff::getAddedChunks)
				.flatMap(Collection::stream)
				.collect(toList());
	}

	@Test
	public void test() {
		final LogFile logFile = new LogFile("file", 1);
		final List<String> fields = asList("field1", "field2");
		final LogDiff<CubeDiff> changesLeft = LogDiff.of(
				singletonMap("clicks", positionDiff(logFile, 0, 10)),
				cubeDiff("key", chunk(1, fields, ofArray("str", 10), ofArray("str", 20), 15)));

		final LogDiff<CubeDiff> changesRight = LogDiff.of(
				singletonMap("clicks", positionDiff(logFile, 0, 20)),
				cubeDiff("key", chunk(1, fields, ofArray("str", 10), ofArray("str", 25), 30)));
		final TransformResult<LogDiff<CubeDiff>> transform = logSystem.transform(changesLeft, changesRight);

		assertTrue(transform.hasConflict());
		assertEquals(ConflictResolution.RIGHT, transform.resolution);
		assertThat(transform.right, IsEmptyCollection.empty());

		final LogDiff<CubeDiff> result = LogDiff.of(
				singletonMap("clicks", positionDiff(logFile, 10, 20)),
				cubeDiff("key", addedChunks(changesRight.diffs), addedChunks(changesLeft.diffs)));

		assertEquals(1, transform.left.size());
		assertEquals(result, transform.left.get(0));
	}

}