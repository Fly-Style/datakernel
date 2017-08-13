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

package io.datakernel.async;

import java.util.concurrent.*;

public final class CompletionCallbackFuture extends CompletionCallback implements Future<Void> {
	private static final Void NOTHING = null;

	private final CountDownLatch latch = new CountDownLatch(1);
	private Exception exception;

	// region builders
	private CompletionCallbackFuture() {
	}

	public static CompletionCallbackFuture create() {
		return new CompletionCallbackFuture();
	}
	// endregion

	@Override
	protected void onComplete() {
		latch.countDown();
	}

	@Override
	protected void onException(Exception exception) {
		this.exception = exception;
		latch.countDown();
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		// not implemented
		return false;
	}

	@Override
	public boolean isCancelled() {
		// not implemented
		return false;
	}

	@Override
	public boolean isDone() {
		return latch.getCount() == 0;
	}

	public boolean isComplete() {
		return isDone() && exception == null;
	}

	public boolean isException() {
		return isDone() && exception != null;
	}

	@Override
	public Void get() throws InterruptedException, ExecutionException {
		latch.await();
		if (exception != null) {
			throw new ExecutionException(exception);
		}
		return NOTHING;
	}

	@Override
	public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		boolean computationCompleted = latch.await(timeout, unit);
		if (computationCompleted) {
			if (exception != null) {
				throw new ExecutionException(exception);
			} else {
				return NOTHING;
			}
		} else {
			throw new TimeoutException();
		}
	}

	public void await() throws InterruptedException, ExecutionException {
		get();
	}

	public void await(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
		get(timeout, unit);
	}

	@Override
	public String toString() {
		return "{" + (isComplete() ? "complete" : (isException() ? exception : "?"));
	}
}
