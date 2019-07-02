package io.datakernel.async;

import io.datakernel.test.rules.EventloopRule;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.datakernel.async.AsyncAwait.async;
import static io.datakernel.async.AsyncAwait.await;
import static io.datakernel.eventloop.Eventloop.getCurrentEventloop;
import static java.util.Arrays.asList;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static junit.framework.TestCase.assertEquals;

public class AsyncAwaitTest {

	@ClassRule
	public static final EventloopRule eventloopRule = new EventloopRule();

	@Test
	public void test1() throws ExecutionException, InterruptedException {
		CompletableFuture<List<String>> future = async(newCachedThreadPool(), this::blockingMethod)
				.toCompletableFuture();

		getCurrentEventloop().run();

		assertEquals(asList("hello", "world"), future.get());
	}

	private List<String> blockingMethod() throws Exception {
		Thread.sleep(1);
		List<String> result = await(this::asyncMethod);
		Thread.sleep(1);
		return result;
	}

	private Promise<List<String>> asyncMethod() {
		return Promises.toList(
				async(newCachedThreadPool(), this::blockingMethod2),
				asyncMethod2()
		);
	}

	private String blockingMethod2() throws Exception {
		Thread.sleep(1);
		return "hello";
	}

	private Promise<String> asyncMethod2() {
		return Promises.delay(1L, async(newCachedThreadPool(), this::blockingMethod3, "world"));
	}

	private String blockingMethod3(String result) throws Exception {
		Thread.sleep(1);
		return result;
	}
}
