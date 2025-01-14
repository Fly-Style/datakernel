package io.datakernel.async;

import io.datakernel.test.rules.EventloopRule;
import org.junit.ClassRule;
import org.junit.Test;

import static io.datakernel.async.AsyncSuppliers.coalesce;
import static io.datakernel.async.AsyncSuppliers.reuse;
import static io.datakernel.async.TestUtils.await;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class AsyncSuppliersTest {

	@ClassRule
	public static final EventloopRule eventloopRule = new EventloopRule();

	@Test
	public void testReuse() {
		AsyncSupplier<Void> reuse = reuse(() -> Promise.complete().async());

		Promise<Void> promise1 = reuse.get();
		Promise<Void> promise2 = reuse.get();
		Promise<Void> promise3 = reuse.get();
		Promise<Void> promise4 = reuse.get();

		assertSame(promise1, promise2);
		assertSame(promise2, promise3);
		assertSame(promise3, promise4);
	}

	@Test
	public void subscribeNormalUsage() {
		AsyncSupplier<Void> subscribe = coalesce(() -> Promise.complete().async());

		Promise<Void> promise1 = subscribe.get();

		Promise<Void> promise2 = subscribe.get();
		Promise<Void> promise3 = subscribe.get();
		Promise<Void> promise4 = subscribe.get();

		assertNotSame(promise1, promise2);

		assertSame(promise2, promise3);
		assertSame(promise2, promise4);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void subscribeIfGetAfterFirstPromise() {
		AsyncSupplier<Void> subscribe = coalesce(() -> Promise.complete().async());

		Promise<Void>[] nextPromise = new Promise[1];
		Promise<Void> promise1 = subscribe.get()
				.whenComplete(($, e) -> nextPromise[0] = subscribe.get());

		Promise<Void> promise2 = subscribe.get();
		Promise<Void> promise3 = subscribe.get();
		Promise<Void> promise4 = subscribe.get();

		await(promise1);

		assertNotSame(promise1, promise2);

		assertSame(promise2, promise3);
		assertSame(promise2, promise4);

		// subscribed to secondly returned promise
		assertNotSame(nextPromise[0], promise1);
		assertSame(nextPromise[0], promise2);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void subscribeIfGetAfterFirstPromiseRecursive() {
		AsyncSupplier<Void> subscribe = coalesce(() -> Promise.complete().async());

		Promise<Void>[] nextPromise = new Promise[3];
		Promise<Void> promise1 = subscribe.get()
				.whenComplete(($1, e1) -> nextPromise[0] = subscribe.get()
						.whenComplete(($2, e2) -> nextPromise[1] = subscribe.get()
								.whenComplete(($3, e3) -> nextPromise[2] = subscribe.get())));

		Promise<Void> promise2 = subscribe.get();
		Promise<Void> promise3 = subscribe.get();
		Promise<Void> promise4 = subscribe.get();

		await(promise1);

		assertNotSame(promise1, promise2);

		assertSame(promise2, promise3);
		assertSame(promise2, promise4);

		// first recursion subscribed to secondly returned promise
		assertNotSame(nextPromise[0], promise1);
		assertSame(nextPromise[0], promise2);

		// next recursions subscribed to newly created promises
		assertNotSame(nextPromise[0], nextPromise[1]);
		assertNotSame(nextPromise[1], nextPromise[2]);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void subscribeIfGetAfterLaterPromises() {
		AsyncSupplier<Void> subscribe = coalesce(() -> Promise.complete().async());

		Promise<Void>[] nextPromise = new Promise[1];
		Promise<Void> promise1 = subscribe.get();

		Promise<Void> promise2 = subscribe.get();
		Promise<Void> promise3 = subscribe.get()
				.whenComplete(($, e) -> nextPromise[0] = subscribe.get());
		Promise<Void> promise4 = subscribe.get();

		await(promise1);

		assertNotSame(promise1, promise2);

		assertSame(promise2, promise3);
		assertSame(promise2, promise4);

		// subscribed to new promise
		assertNotSame(nextPromise[0], promise1);
		assertNotSame(nextPromise[0], promise2);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void subscribeIfGetAfterLaterPromisesRecursive() {
		AsyncSupplier<Void> subscribe = coalesce(() -> Promise.complete().async());

		Promise<Void>[] nextPromise = new Promise[3];
		Promise<Void> promise1 = subscribe.get();

		Promise<Void> promise2 = subscribe.get();
		Promise<Void> promise3 = subscribe.get()
				.whenComplete(($1, e1) -> nextPromise[0] = subscribe.get()
						.whenComplete(($2, e2) -> nextPromise[1] = subscribe.get()
								.whenComplete(($3, e3) -> nextPromise[2] = subscribe.get())));
		Promise<Void> promise4 = subscribe.get();

		await(promise1);

		assertNotSame(promise1, promise2);

		assertSame(promise2, promise3);
		assertSame(promise2, promise4);

		// first recursion subscribed to new promise
		assertNotSame(nextPromise[0], promise1);
		assertNotSame(nextPromise[0], promise2);

		// next recursions subscribed to newly created promises
		assertNotSame(nextPromise[0], nextPromise[1]);
		assertNotSame(nextPromise[1], nextPromise[2]);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void subscribeMultipleRecursions() {
		AsyncSupplier<Void> subscribe = coalesce(() -> Promise.complete().async());

		Promise<Void>[] nextPromise1 = new Promise[3];
		Promise<Void>[] nextPromise2 = new Promise[3];
		Promise<Void> promise1 = subscribe.get();

		Promise<Void> promise2 = subscribe.get();
		Promise<Void> promise3 = subscribe.get()
				.whenComplete(($1, e1) -> nextPromise1[0] = subscribe.get()
						.whenComplete(($2, e2) -> nextPromise1[1] = subscribe.get()
								.whenComplete(($3, e3) -> nextPromise1[2] = subscribe.get())));
		Promise<Void> promise4 = subscribe.get()
				.whenComplete(($1, e1) -> nextPromise2[0] = subscribe.get()
						.whenComplete(($2, e2) -> nextPromise2[1] = subscribe.get()
								.whenComplete(($3, e3) -> nextPromise2[2] = subscribe.get())));

		await(promise1);

		assertNotSame(promise1, promise2);

		assertSame(promise2, promise3);
		assertSame(promise2, promise4);

		// first recursions subscribed to new promise and are the same
		assertNotSame(nextPromise1[0], promise1);
		assertNotSame(nextPromise2[0], promise1);
		assertNotSame(nextPromise1[0], promise2);
		assertNotSame(nextPromise2[0], promise2);
		assertSame(nextPromise1[0], nextPromise2[0]);

		// next recursions subscribed to newly created promises and are the same (between each other)
		assertNotSame(nextPromise1[0], nextPromise1[1]);
		assertNotSame(nextPromise1[1], nextPromise1[2]);

		assertNotSame(nextPromise2[0], nextPromise2[1]);
		assertNotSame(nextPromise2[1], nextPromise2[2]);

		assertSame(nextPromise1[1], nextPromise1[1]);
		assertSame(nextPromise1[2], nextPromise1[2]);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void subscribeIfNotAsync() {
		AsyncSupplier<Void> subscribe = coalesce(Promise::complete);

		Promise<Void>[] nextPromise = new Promise[1];
		Promise<Void> promise1 = subscribe.get();
		Promise<Void> promise2 = subscribe.get()
				.whenComplete(($, e) -> nextPromise[0] = subscribe.get());
		Promise<Void> promise3 = subscribe.get();

		await(promise1);

		assertNotSame(promise1, promise2);
		assertNotSame(promise1, promise3);
		assertNotSame(promise1, nextPromise[0]);
		assertNotSame(promise2, promise3);
		assertNotSame(promise2, nextPromise[0]);
		assertNotSame(promise3, nextPromise[0]);
	}
}
