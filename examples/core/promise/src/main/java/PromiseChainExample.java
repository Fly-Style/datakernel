import io.datakernel.async.Promise;
import io.datakernel.async.Promises;
import io.datakernel.eventloop.Eventloop;

//[START EXAMPLE]
public class PromiseChainExample {
	private static final Eventloop eventloop = Eventloop.create().withCurrentThread();
	public static void main(String[] args) {
		//[START REGION_1]
		doSomeProcess()
				.whenResult(result -> System.out.println(String.format("Result of some process is '%s'", result)))
				.whenException(e -> System.out.println(String.format("Exception after some process is '%s'",e.getMessage())))
				.map(String::toLowerCase)
				.mapEx((result, e) -> e == null ? String.format("The mapped result is '%s'", result) : e.getMessage())
				.whenResult(System.out::println);
		//[END REGION_1]
		Promise.complete()
				.then($ -> loadData())
				.whenResult(result -> System.out.println(String.format("Loaded data is '%s'", result)));
		eventloop.run();
	}

	private static Promise<String> loadData() {
		return Promise.of("Hello World");
	}

	public static Promise<String> doSomeProcess() {
		return Promises.delay(1000, Math.random() > 0.5 ?
				Promise.of("Hello World") :
				Promise.ofException(new RuntimeException("Something went wrong")));
	}
}
//[END EXAMPLE]
