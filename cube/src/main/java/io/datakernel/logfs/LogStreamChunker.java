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

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.stream.*;
import org.joda.time.format.DateTimeFormatter;

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
		chunker.setActualConsumer(switcher, chunker.getCompletion()
				.thenCompose(aVoid -> chunker.currentConsumer.getResult()));
		long timestamp = eventloop.currentTimeMillis();
		String chunkName = datetimeFormat.print(timestamp);
		chunker.startNewChunk(chunkName);
		return chunker;
	}

	@Override
	public void onData(ByteBuf item) {
		long timestamp = eventloop.currentTimeMillis();

		String chunkName = datetimeFormat.print(timestamp);
		if (!chunkName.equals(currentChunkName)) {
			startNewChunk(chunkName);
		}

		dataReceiver.onData(item);
	}

	@Override
	protected StreamDataReceiver<ByteBuf> onProduce(StreamDataReceiver<ByteBuf> dataReceiver) {
		this.dataReceiver = dataReceiver;
		return this;
	}

	private void startNewChunk(String newChunkName) {
		currentChunkName = newChunkName;
		currentConsumer = StreamConsumers.ofStageWithResult(fileSystem
				.makeUniqueLogFile(logPartition, newChunkName)
				.thenCompose(logFile -> fileSystem.write(logPartition, logFile)));
		switcher.switchTo(currentConsumer);
	}

}