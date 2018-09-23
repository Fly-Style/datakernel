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

package io.datakernel.http;

import io.datakernel.async.Stage;
import io.datakernel.async.Stages;
import io.datakernel.bytebuf.ByteBufStrings;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.jmx.EventStats;
import io.datakernel.stream.processor.ByteBufRule;
import org.junit.Rule;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.datakernel.bytebuf.ByteBufStrings.encodeAscii;
import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.http.HttpHeaders.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;

public class AbstractHttpConnectionTest {
	private static final int PORT = 5050;
	private static final String url = "http://127.0.0.1:" + PORT;

	private Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
	private AsyncHttpClient client = AsyncHttpClient.create(eventloop);

	@Rule
	public ByteBufRule byteBufRule = new ByteBufRule();

	@Test
	public void testMultiLineHeader() throws Exception {
		AsyncHttpServer server = AsyncHttpServer.create(eventloop,
				request -> Stage.of(
						HttpResponse.ok200()
								.withHeader(DATE, "Mon, 27 Jul 2009 12:28:53 GMT")
								.withHeader(CONTENT_TYPE, "text/\n          html")
								.withBody(ByteBufStrings.wrapAscii("  <html>\n<body>\n<h1>Hello, World!</h1>\n</body>\n</html>"))))
				.withListenAddress(new InetSocketAddress("localhost", PORT));
		server.listen();

		Map<String, String> data = new HashMap<>();
		CompletableFuture<Void> future = client.request(HttpRequest.get(url))
				.whenResult(result -> {
					data.put("body", result.getBody().asString(UTF_8));
					data.put("header", result.getHeader(CONTENT_TYPE));
				})
				.thenCompose($ -> stopClientAndServer(client, server))
				.toCompletableFuture();

		eventloop.run();
		future.get();

		assertEquals("text/           html", data.get("header"));
		assertEquals("  <html>\n<body>\n<h1>Hello, World!</h1>\n</body>\n</html>", data.get("body"));
	}

	@Test
	public void testGzipCompression() throws Exception {
		AsyncHttpServer server = AsyncHttpServer.create(eventloop,
				request -> Stage.of(
						HttpResponse.ok200()
								.withBodyGzipCompression()
								.withBody(encodeAscii("Test message"))))
				.withListenAddress(new InetSocketAddress("localhost", PORT));
		server.listen();

		HttpRequest request = HttpRequest.get(url).withHeader(ACCEPT_ENCODING, "gzip");
		CompletableFuture<String> future = client.request(request)
				.thenApply(response -> {
					assertNotNull(response.getHeaderValue(CONTENT_ENCODING));
					return response.getBody().asString(UTF_8);
				})
				.thenRunEx(() -> stopClientAndServer(client, server))
				.toCompletableFuture();

		eventloop.run();
		assertEquals("Test message", future.get());
	}

	@Test
	public void testClientWithMaxKeepAliveRequests() throws Exception {
		client.withMaxKeepAliveRequests(3);

		AsyncServlet servlet = request -> Stage.of(HttpResponse.ok200());
		AsyncHttpServer server = AsyncHttpServer.create(eventloop, servlet)
				.withListenAddress(new InetSocketAddress("localhost", PORT));
		server.listen();

		EventStats clientConnectionCount = client.getStats().getConnected();

		client.request(HttpRequest.get(url))
				.thenCompose(response -> {
					clientConnectionCount.refresh(System.currentTimeMillis());
					assertEquals("keep-alive", response.getHeaderValue(CONNECTION).toString());
					assertEquals(1, clientConnectionCount.getTotalCount());
					return client.request(HttpRequest.get(url));
				})
				.thenCompose(response -> {
					clientConnectionCount.refresh(System.currentTimeMillis());
					assertEquals("keep-alive", response.getHeaderValue(CONNECTION).toString());
					assertEquals(1, clientConnectionCount.getTotalCount());
					return client.request(HttpRequest.get(url));
				})
				.thenCompose(response -> {
					clientConnectionCount.refresh(System.currentTimeMillis());
					assertEquals("close", response.getHeaderValue(CONNECTION).toString());
					assertEquals(1, client.getStats().getConnected().getTotalCount());
					return client.request(HttpRequest.get(url));
				})
				.thenCompose(response -> {
					clientConnectionCount.refresh(System.currentTimeMillis());
					assertEquals("keep-alive", response.getHeaderValue(CONNECTION).toString());
					assertEquals(2, clientConnectionCount.getTotalCount());
					return stopClientAndServer(client, server);
				})
				.toCompletableFuture();

		eventloop.run();
	}

	@Test
	public void testServerWithMaxKeepAliveRequests() throws Exception {
		AsyncHttpServer server = AsyncHttpServer.create(eventloop,
				request -> Stage.of(
						HttpResponse.ok200()))
				.withListenAddress(new InetSocketAddress("localhost", PORT))
				.withMaxKeepAliveRequests(3);
		server.listen();

		EventStats clientConnectionCount = server.getAccepts();
		assert clientConnectionCount != null;

		client.request(HttpRequest.get(url))
				.thenCompose(response -> {
					clientConnectionCount.refresh(System.currentTimeMillis());
					assertEquals("keep-alive", response.getHeaderValue(CONNECTION).toString());
					assertEquals(1, clientConnectionCount.getTotalCount());
					return client.request(HttpRequest.get(url));
				})
				.thenCompose(response2 -> {
					clientConnectionCount.refresh(System.currentTimeMillis());
					assertEquals("keep-alive", response2.getHeaderValue(CONNECTION).toString());
					assertEquals(1, clientConnectionCount.getTotalCount());
					return client.request(HttpRequest.get(url));
				})
				.thenCompose(response3 -> {
					clientConnectionCount.refresh(System.currentTimeMillis());
					assertEquals("close", response3.getHeaderValue(CONNECTION).toString());
					assertEquals(1, clientConnectionCount.getTotalCount());
					return client.request(HttpRequest.get(url));
				})
				.thenCompose(response4 -> {
					clientConnectionCount.refresh(System.currentTimeMillis());
					assertEquals("keep-alive", response4.getHeaderValue(CONNECTION).toString());
					assertEquals(2, clientConnectionCount.getTotalCount());
					return stopClientAndServer(client, server);
				})
				.toCompletableFuture();

		eventloop.run();
	}

	private Stage<Void> stopClientAndServer(AsyncHttpClient client, AsyncHttpServer server) {
		return Stages.all(client.stop(), server.close());
	}
}
