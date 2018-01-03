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

package io.datakernel.logfs;

import io.datakernel.async.Stages;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.stream.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionStage;

public final class LogStreamChunker extends StreamConsumerDecorator<ByteBuf, Void> implements StreamDataReceiver<ByteBuf> {
	private final Eventloop eventloop;
	private final StreamConsumerSwitcher<ByteBuf> switcher;

	private final DateTimeFormatter datetimeFormat;
	private final LogFileSystem fileSystem;
	private final String logPartition;

	private String currentChunkName;
	private StreamDataReceiver<ByteBuf> dataReceiver;
	private StreamConsumerWithResult<ByteBuf, Void> currentConsumer;

	private LogStreamChunker(Eventloop eventloop, StreamConsumerSwitcher<ByteBuf> switcher,
	                         LogFileSystem fileSystem, DateTimeFormatter datetimeFormat,
	                         String logPartition) {
		this.eventloop = eventloop;
		this.switcher = switcher;
		this.datetimeFormat = datetimeFormat;
		this.fileSystem = fileSystem;
		this.logPartition = logPartition;
	}

	public static LogStreamChunker create(Eventloop eventloop,
	                                      LogFileSystem fileSystem, DateTimeFormatter datetimeFormat,
	                                      String logPartition) {
		StreamConsumerSwitcher<ByteBuf> switcher = StreamConsumerSwitcher.create(eventloop);
		LogStreamChunker chunker = new LogStreamChunker(eventloop, switcher, fileSystem, datetimeFormat, logPartition);
		chunker.setActualConsumer(switcher, chunker.getEndOfStream()
				.thenCompose(aVoid -> chunker.currentConsumer.getResult()));
		long timestamp = eventloop.currentTimeMillis();
		String chunkName = datetimeFormat.format(Instant.ofEpochMilli(timestamp));
		chunker.startNewChunk(chunkName, Stages.of(null));
		return chunker;
	}

	@Override
	public void onData(ByteBuf item) {
		final String chunkName = datetimeFormat.format(Instant.ofEpochMilli(eventloop.currentTimeMillis()));
		if (!chunkName.equals(currentChunkName)) {
			startNewChunk(chunkName, currentConsumer.getResult());
		}

		dataReceiver.onData(item);
	}

	@Override
	protected StreamDataReceiver<ByteBuf> onProduce(StreamDataReceiver<ByteBuf> dataReceiver) {
		this.dataReceiver = dataReceiver;
		return this;
	}

	private void startNewChunk(String newChunkName, CompletionStage<Void> previousFile) {
		currentChunkName = newChunkName;
		currentConsumer = StreamConsumers.ofStageWithResult(previousFile
				.thenCompose($ -> fileSystem.makeUniqueLogFile(logPartition, newChunkName))
				.thenCompose(logFile -> fileSystem.write(logPartition, logFile)));

		switcher.switchTo(currentConsumer);
	}

}