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

package io.datakernel.stream;

import java.util.concurrent.CompletionStage;

/**
 * It represents object for asynchronous sending streams of data.
 * Implementors of this interface are strongly encouraged to extend one of the abstract classes
 * in this package which implement this interface and make the threading and state management
 * easier.
 *
 * @param <T> type of output data
 */
public interface StreamProducer<T> {
	/**
	 * Changes consumer for this producer, removes itself from previous consumer and removes
	 * previous producer for new consumer. Begins to stream to consumer.
	 *
	 * @param consumer consumer for streaming
	 */
	void setConsumer(StreamConsumer<T> consumer);

	/**
	 * This method is called for restore streaming of this producer
	 */
	void produce(StreamDataReceiver<T> dataReceiver);

	/**
	 * This method is called for stop streaming of this producer
	 */
	void suspend();

	CompletionStage<Void> getEndOfStream();
}
