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

package io.datakernel.logfs.ot;

import io.datakernel.async.AsyncCallable;
import io.datakernel.async.Stages;
import io.datakernel.async.StagesAccumulator;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.EventloopService;
import io.datakernel.jmx.EventloopJmxMBeanEx;
import io.datakernel.jmx.JmxAttribute;
import io.datakernel.jmx.JmxOperation;
import io.datakernel.jmx.StageStats;
import io.datakernel.logfs.LogFile;
import io.datakernel.logfs.LogManager;
import io.datakernel.logfs.LogPosition;
import io.datakernel.logfs.ot.LogDiff.LogPositionDiff;
import io.datakernel.stream.StreamConsumerWithResult;
import io.datakernel.stream.StreamProducerWithResult;
import io.datakernel.stream.processor.StreamStats;
import io.datakernel.stream.processor.StreamStatsBasic;
import io.datakernel.stream.processor.StreamStatsDetailed;
import io.datakernel.stream.processor.StreamUnion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.datakernel.async.AsyncCallable.sharedCall;
import static io.datakernel.async.Stages.onResult;
import static io.datakernel.jmx.ValueStats.SMOOTHING_WINDOW_5_MINUTES;
import static io.datakernel.stream.DataStreams.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

/**
 * Processes logs. Creates new aggregation chunks and persists them using logic defined in supplied {@code AggregatorSplitter}.
 */
public final class LogOTProcessor<T, D> implements EventloopService, EventloopJmxMBeanEx {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final Eventloop eventloop;
	private final LogManager<T> logManager;
	private final LogDataConsumer<T, D> logStreamConsumer;

	private final String log;
	private final List<String> partitions;

	private final LogOTState<D> state;

	// JMX
	private boolean enabled = true;
	private boolean detailed;
	private final StreamStatsBasic streamStatsBasic = StreamStats.basic();
	private final StreamStatsDetailed streamStatsDetailed = StreamStats.detailed();
	private final StageStats stageProcessLog = StageStats.create(SMOOTHING_WINDOW_5_MINUTES);
	private final StageStats stageProducer = StageStats.create(SMOOTHING_WINDOW_5_MINUTES);
	private final StageStats stageConsumer = StageStats.create(SMOOTHING_WINDOW_5_MINUTES);

	private LogOTProcessor(Eventloop eventloop, LogManager<T> logManager, LogDataConsumer<T, D> logStreamConsumer,
	                       String log, List<String> partitions, LogOTState<D> state) {
		this.eventloop = eventloop;
		this.logManager = logManager;
		this.logStreamConsumer = logStreamConsumer;
		this.log = log;
		this.partitions = partitions;
		this.state = state;
	}

	public static <T, D> LogOTProcessor<T, D> create(Eventloop eventloop, LogManager<T> logManager,
	                                                 LogDataConsumer<T, D> logStreamConsumer,
	                                                 String log, List<String> partitions, LogOTState<D> state) {
		return new LogOTProcessor<>(eventloop, logManager, logStreamConsumer, log, partitions, state);
	}

	@Override
	public Eventloop getEventloop() {
		return eventloop;
	}

	@Override
	public CompletionStage<Void> start() {
		return Stages.of(null);
	}

	@Override
	public CompletionStage<Void> stop() {
		return Stages.of(null);
	}

	private final AsyncCallable<LogDiff<D>> processLog = sharedCall(this::doProcessLog);

	public CompletionStage<LogDiff<D>> processLog() {
		return processLog.call();
	}

	private CompletionStage<LogDiff<D>> doProcessLog() {
		if (!enabled) return Stages.of(LogDiff.of(emptyMap(), emptyList()));
		logger.trace("processLog_gotPositions called. Positions: {}", state.getPositions());

		StreamProducerWithResult<T, Map<String, LogPositionDiff>> producer = getProducer();
		StreamConsumerWithResult<T, List<D>> consumer = logStreamConsumer.consume();
		producer.getResult().whenComplete(stageProducer.recordStats());
		consumer.getResult().whenComplete(stageConsumer.recordStats());
		return stream(producer, consumer)
				.whenComplete(stageProcessLog.recordStats())
				.thenApply(result -> LogDiff.of(result.getProducerResult(), result.getConsumerResult()))
				.whenComplete(onResult(logDiff ->
						logger.info("Log '{}' processing complete. Positions: {}", log, logDiff.positions)));
	}

	private StreamProducerWithResult<T, Map<String, LogPositionDiff>> getProducer() {
		StagesAccumulator<Map<String, LogPositionDiff>> result = StagesAccumulator.create(new HashMap<>());
		StreamUnion<T> streamUnion = StreamUnion.create();
		for (String partition : this.partitions) {
			String logName = logName(partition);
			LogPosition logPosition = state.getPositions().get(logName);
			if (logPosition == null) {
				logPosition = LogPosition.create(new LogFile("", 0), 0L);
			}
			logger.info("Starting reading '{}' from position {}", logName, logPosition);

			LogPosition logPositionFrom = logPosition;
			StreamProducerWithResult<T, LogPosition> producer = logManager.producerStream(partition, logPosition.getLogFile(), logPosition.getPosition(), null);
			stream(producer, streamUnion.newInput());
			result.addStage(producer.getResult(), (accumulator, logPositionTo) -> {
				if (!logPositionTo.equals(logPositionFrom)) {
					accumulator.put(logName, new LogPositionDiff(logPositionFrom, logPositionTo));
				}
			});
		}
		return streamUnion.getOutput()
				.with(detailed ? streamStatsDetailed::wrapper : streamStatsBasic::wrapper)
				.withResult(result.get());
	}

	private String logName(String partition) {
		return log != null && !log.isEmpty() ? log + "." + partition : partition;
	}

	@JmxAttribute
	public boolean isEnabled() {
		return enabled;
	}

	@JmxAttribute
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@JmxAttribute
	public StageStats getStageProcessLog() {
		return stageProcessLog;
	}

	@JmxAttribute
	public StageStats getStageProducer() {
		return stageProducer;
	}

	@JmxAttribute
	public StageStats getStageConsumer() {
		return stageConsumer;
	}

	@JmxAttribute
	public StreamStatsBasic getStreamStatsBasic() {
		return streamStatsBasic;
	}

	@JmxAttribute
	public StreamStatsDetailed getStreamStatsDetailed() {
		return streamStatsDetailed;
	}

	@JmxOperation
	public void startDetailedMonitoring() {
		detailed = true;
	}

	@JmxOperation
	public void stopDetailedMonitoring() {
		detailed = false;
	}
}
