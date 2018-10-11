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

package io.datakernel.async;

import io.datakernel.exception.StacklessException;

/**
 * This interface describes methods that are used to handle exceptional behaviour or to handle closing.
 * <p>
 * After {@link #close()}, {@link #close(Throwable)} or {@link #cancel()} is called, the following things
 * should be done:
 * <ul>
 * <li>Resources held by an object should be freed</li>
 * <li>All pending asynchronous operations should be completed exceptionally</li>
 * </ul>
 * All operations of this interface are idempotent.
 */
public interface Cancellable {
	StacklessException CANCEL_EXCEPTION = new StacklessException(Cancellable.class, "Cancelled");
	StacklessException CLOSE_EXCEPTION = new StacklessException(Cancellable.class, "Closed");

	/**
	 * This method should be called to close some process exceptionally in case of some exception is thrown while
	 * executing the given process.
	 *
	 * @param e exception that is used to close process with
	 */
	void close(Throwable e);

	/**
	 * This method should be called in case user wants to cancel some process
	 */
	default void cancel() {
		close(CANCEL_EXCEPTION);
	}

	/**
	 * This method should be called after process has finished its job
	 */
	default void close() {
		close(CLOSE_EXCEPTION);
	}
}
