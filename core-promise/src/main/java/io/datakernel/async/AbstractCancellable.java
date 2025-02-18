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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.datakernel.util.Recyclable.tryRecycle;

public abstract class AbstractCancellable implements Cancellable {
	@Nullable
	private Cancellable cancellable;

	private Throwable exception;

	public Throwable getException() {
		return exception;
	}

	public void setCancellable(@Nullable Cancellable cancellable) {
		this.cancellable = cancellable;
	}

	protected void onClosed(@NotNull Throwable e) {
	}

	@Override
	public final void close(@NotNull Throwable e) {
		if (isClosed()) return;
		exception = e;
		onClosed(e);
		if (cancellable != null) {
			cancellable.close(e);
		}
	}

	public final boolean isClosed() {
		return exception != null;
	}

	@NotNull
	public final <T> Promise<T> sanitize(Promise<T> promise) {
		return promise
				.thenEx(this::sanitize);
	}

	@NotNull
	public final <T> Promise<T> sanitize(T value, @Nullable Throwable e) {
		if (exception != null) {
			tryRecycle(value);
			if (value instanceof Cancellable) {
				((Cancellable) value).close(exception);
			}
			return Promise.ofException(exception);
		}
		if (e == null) {
			return Promise.of(value);
		} else {
			close(e);
			return Promise.ofException(e);
		}
	}

}
