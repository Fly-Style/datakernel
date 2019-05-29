package io.datakernel.examples;

import io.datakernel.async.Promise;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.http.AsyncHttpServer;
import io.datakernel.http.HttpResponse;

import java.io.IOException;

import static io.datakernel.bytebuf.ByteBufStrings.wrapUtf8;

public final class HelloWorldExample {
	public static void main(String[] args) throws IOException {
		Eventloop eventloop = Eventloop.create().withCurrentThread();

		AsyncHttpServer server = AsyncHttpServer.create(eventloop, request ->
				Promise.of(HttpResponse.ok200()
						.withBody(wrapUtf8("Hello world!"))))
				.withListenPort(8080);

		server.listen();

		System.out.println("Server is running");
		System.out.println("You can connect from browser by visiting 'http://localhost:8080/'");

		eventloop.run();
	}
}