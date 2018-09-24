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

import io.datakernel.async.Stage;
import io.datakernel.stream.StreamConsumer;
import io.datakernel.stream.StreamSupplierWithResult;

/**
 * Manages persistence of logs.
 */
public interface LogManager<T> {
	/**
	 * Creates a {@code StreamConsumer} that persists streamed log items to log.
	 *
	 * @param logPartition log partition name
	 * @return StreamConsumer, which will write records, streamed from wired supplier.
	 */
	Stage<StreamConsumer<T>> consumer(String logPartition);

	default StreamConsumer<T> consumerStream(String logPartition) {
		return StreamConsumer.ofStage(consumer(logPartition));
	}

	/**
	 * Creates a {@code StreamSupplier} that streams items, contained in a given partition and file, starting at the specified position.
	 *
	 * @param logPartition  name of log partition
	 * @param startLogFile  log file
	 * @param startPosition position
	 * @return StreamSupplier, which will stream read items to its wired consumer.
	 */
	Stage<StreamSupplierWithResult<T, LogPosition>> supplier(String logPartition,
			LogFile startLogFile, long startPosition,
			LogFile endLogFile);

	default StreamSupplierWithResult<T, LogPosition> supplierStream(String logPartition,
			LogFile startLogFile, long startPosition,
			LogFile endLogFile) {
		return StreamSupplierWithResult.ofStage(
				supplier(logPartition, startLogFile, startPosition, endLogFile));
	}

}
