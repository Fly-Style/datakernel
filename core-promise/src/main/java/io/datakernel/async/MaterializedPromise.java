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

import io.datakernel.functional.Try;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A parent interface for materialized promises: {@link SettablePromise},
 * {@link CompletePromise}, {@link CompleteExceptionallyPromise}.
 * You can {@code getResult} of materialized {@code Promise}s.
 *
 * @param <T> result type
 */
public interface MaterializedPromise<T> extends Promise<T> {
	@Contract(pure = true)
	T getResult();

	@Contract(pure = true)
	@NotNull
	Throwable getException();

	@Contract(pure = true)
	@NotNull
	default Try<T> getTry() {
		return isResult() ? Try.of(getResult()) : Try.ofException(getException());
	}

	@Contract(pure = true, value = "-> this")
	@NotNull
	@Override
	default MaterializedPromise<T> materialize() {
		return this;
	}
}
