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

package io.datakernel.csp.queue;

import io.datakernel.async.Promise;
import io.datakernel.async.SettablePromise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.datakernel.util.Recyclable.tryRecycle;

/**
 * Represents a buffer of zero capacity and stores only
 * one value. There are {@code take} and {@code put}
 * {@link SettablePromise}s which represent status of
 * corresponding operations. Unless they are {@code null},
 * they are waiting for the operation to be completed.
 *
 * @param <T> type of data passed through the buffer
 */
public final class ChannelZeroBuffer<T> implements ChannelQueue<T> {
	private Exception exception;

	@Nullable
	private T value;

	@Nullable
	private SettablePromise<Void> put;
	@Nullable
	private SettablePromise<T> take;

	/**
	 * Shows if this buffer is waiting for take
	 * or put operation to be completed.
	 *
	 * @return {@code true} if either {@code put}
	 * or {@code take} doesn't equal {@code null}
	 */
	public boolean isWaiting() {
		return take != null || put != null;
	}

	public boolean isWaitingPut() {
		return put != null;
	}

	public boolean isWaitingTake() {
		return take != null;
	}

	/**
	 * Sets the provided {@code value} to current {@code value},
	 * then sets {@code put} as a new {@link SettablePromise}
	 * and returns it.
	 * <p>
	 * If {@code take} isn't {@code null}, the {@code value}
	 * will be set to it.
	 * <p>
	 * Current {@code put} must be {@code null}. If current
	 * {@code exception} is not {@code null}, provided
	 * {@code value} will be recycled and a promise of the
	 * exception will be returned.
	 *
	 * @param value a value passed to the buffer
	 * @return {@code put} if current {@code take} is {@code null},
	 * otherwise returns a successfully completed promise. If
	 * current {@code exception} is not {@code null}, a promise of
	 * the {@code exception} will be returned.
	 */
	@Override
	public Promise<Void> put(@Nullable T value) {
		assert put == null;
		if (exception == null) {
			if (take != null) {
				SettablePromise<T> take = this.take;
				this.take = null;
				take.set(value);
				return Promise.complete();
			}

			this.value = value;
			this.put = new SettablePromise<>();
			return put;
		} else {
			tryRecycle(value);
			return Promise.ofException(exception);
		}
	}

	/**
	 * Returns a promise of current {@code value}, if
	 * the {@code put} is not {@code null}.
	 * <p>
	 * Sets {@code put} and {@code value} as {@code null}
	 * after the operation.
	 * <p>
	 * If the {@code put} is {@code null}, sets {@code take}
	 * as a new {@link SettablePromise} and returns it. If
	 * current {@code exception} is not {@code null}, returns
	 * a promise of the exception and does nothing else.
	 *
	 * @return a promise of the {@code value} or of {@code null}.
	 * If this {@code exception} is not {@code null}, returns a
	 * promise of exception.
	 */
	@Override
	public Promise<T> take() {
		assert take == null;
		if (exception == null) {
			if (put != null) {
				T value = this.value;
				SettablePromise<Void> put = this.put;
				this.value = null;
				this.put = null;
				put.set(null);
				return Promise.of(value);
			}

			this.take = new SettablePromise<>();
			return take;
		} else {
			return Promise.ofException(exception);
		}
	}

	/**
	 * Closes the buffer if this {@code exception} is not
	 * {@code null}. Recycles all elements of the buffer and
	 * sets {@code elements}, {@code put} and {@code take} to
	 * {@code null}.
	 *
	 * @param e exception that is used to close buffer with
	 */
	@Override
	public void close(@NotNull Throwable e) {
		if (exception != null) return;
		exception = e instanceof Exception ? (Exception) e : new RuntimeException(e);
		if (put != null) {
			put.setException(e);
			put = null;
		}
		if (take != null) {
			take.setException(e);
			take = null;
		}
		tryRecycle(value);
		value = null;
	}

	@Nullable
	public Throwable getException() {
		return exception;
	}
}
