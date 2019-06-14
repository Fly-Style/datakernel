import io.datakernel.async.AsyncPredicate;
import io.datakernel.async.Promise;
import io.datakernel.async.Promises;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Arrays;

public final class PromisesExample {
	private static int counter;

	static {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	private static void repeat() {
		System.out.println("Repeat until exception:");
		Promises.repeat(() -> {
			System.out.println("This is iteration #" + ++counter);
			if (counter == 5) {
				return Promise.ofException(new Exception("Breaking the loop"));
			}
			return Promise.complete();
		});
		System.out.println();
	}

	private static void loop() {
		System.out.println("Looping with condition:");
		Promises.loop(0, AsyncPredicate.of(i -> i < 5), i -> {
			System.out.println("This is iteration #" + ++i);
			return Promise.of(i);
		});
		System.out.println();
	}

	private static void toList() {
		System.out.println("Collecting group of Promises to list of Promises' results:");
		Promises.toList(Promise.of(1), Promise.of(2), Promise.of(3), Promise.of(4), Promise.of(5), Promise.of(6))
				.whenResult(list -> System.out.println("Size of collected list: " + list.size() + "\nList: " + list));
		System.out.println();
	}

	private static void toArray() {
		System.out.println("Collecting group of Promises to array of Promises' results:");
		Promises.toArray(Integer.class, Promise.of(1), Promise.of(2), Promise.of(3), Promise.of(4), Promise.of(5), Promise.of(6))
				.whenResult(array -> System.out.println("Size of collected array: " + array.length + "\nArray: " + Arrays.toString(array)));
		System.out.println();
	}

	public static void main(String[] args) {
		repeat();
		loop();
		toList();
		toArray();
	}
}
