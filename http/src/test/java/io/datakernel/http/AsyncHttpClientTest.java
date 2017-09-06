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

import io.datakernel.async.IgnoreCompletionCallback;
import io.datakernel.async.ResultCallback;
import io.datakernel.async.ResultCallbackFuture;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.bytebuf.ByteBufStrings;
import io.datakernel.eventloop.AsyncTcpSocket;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.SimpleServer;
import io.datakernel.eventloop.SimpleServer.SocketHandlerProvider;
import io.datakernel.exception.AsyncTimeoutException;
import io.datakernel.exception.ParseException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import static io.datakernel.bytebuf.ByteBufPool.*;
import static io.datakernel.bytebuf.ByteBufStrings.decodeUtf8;
import static io.datakernel.bytebuf.ByteBufStrings.encodeAscii;
import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static org.junit.Assert.assertEquals;

public class AsyncHttpClientTest {
	private static final int PORT = 45788;
	public static final byte[] TIMEOUT_EXCEPTION_BYTES = encodeAscii("ERROR: Must be TimeoutException");

	private static final InetAddress GOOGLE_PUBLIC_DNS = HttpUtils.inetAddress("8.8.8.8");

	@Before
	public void before() {
		ByteBufPool.clear();
		ByteBufPool.setSizes(0, Integer.MAX_VALUE);
	}

	@Test
	public void testAsyncClient() throws Exception {
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		final AsyncHttpServer httpServer = HelloWorldServer.helloWorldServer(eventloop, PORT);
		final AsyncHttpClient httpClient = AsyncHttpClient.create(eventloop);
		final ResultCallbackFuture<String> resultObserver = ResultCallbackFuture.create();

		httpServer.listen();

		httpClient.send(HttpRequest.get("http://127.0.0.1:" + PORT), new ResultCallback<HttpResponse>() {
			@Override
			public void onResult(final HttpResponse result) {
				try {
					resultObserver.setResult(decodeUtf8(result.getBody()));
				} catch (ParseException e) {
					onException(e);
				}
				httpClient.stop(IgnoreCompletionCallback.create());
				httpServer.close(IgnoreCompletionCallback.create());
			}

			@Override
			public void onException(Exception exception) {
				resultObserver.setException(exception);
				httpClient.stop(IgnoreCompletionCallback.create());
				httpServer.close(IgnoreCompletionCallback.create());
			}
		});

		eventloop.run();

		assertEquals(decodeUtf8(HelloWorldServer.HELLO_WORLD), resultObserver.get());

		assertEquals(getPoolItemsString(), ByteBufPool.getCreatedItems(), ByteBufPool.getPoolItems());
	}

	@Test(expected = AsyncTimeoutException.class)
	public void testClientTimeoutConnect() throws Throwable {
		final int TIMEOUT = 1;
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		final AsyncHttpClient httpClient = AsyncHttpClient.create(eventloop).withConnectTimeout(TIMEOUT);
		final ResultCallbackFuture<String> resultObserver = ResultCallbackFuture.create();

		httpClient.send(HttpRequest.get("http://google.com"), new ResultCallback<HttpResponse>() {
			@Override
			public void onResult(HttpResponse result) {
				try {
					resultObserver.setResult(decodeUtf8(result.getBody()));
				} catch (ParseException e) {
					onException(e);
				}
				httpClient.stop(IgnoreCompletionCallback.create());
			}

			@Override
			public void onException(Exception exception) {
				resultObserver.setException(exception);
				httpClient.stop(IgnoreCompletionCallback.create());
			}
		});

		eventloop.run();
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());

		try {
			System.err.println("Result: " + resultObserver.get());
		} catch (ExecutionException e) {
			throw e.getCause();
		}
	}

	@Test(expected = ParseException.class)
	public void testBigHttpMessage() throws Throwable {
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		final AsyncHttpServer httpServer = HelloWorldServer.helloWorldServer(eventloop, PORT);
		final AsyncHttpClient httpClient = AsyncHttpClient.create(eventloop)
				.withMaxHttpMessageSize(12);
		final ResultCallbackFuture<String> resultObserver = ResultCallbackFuture.create();

		httpServer.listen();

		httpClient.send(HttpRequest.get("http://127.0.0.1:" + PORT), new ResultCallback<HttpResponse>() {
			@Override
			public void onResult(HttpResponse result) {
				try {
					resultObserver.setResult(decodeUtf8(result.getBody()));
				} catch (ParseException e) {
					onException(e);
				}
				httpClient.stop(IgnoreCompletionCallback.create());
				httpServer.close(IgnoreCompletionCallback.create());
			}

			@Override
			public void onException(Exception exception) {
				resultObserver.setException(exception);
				httpClient.stop(IgnoreCompletionCallback.create());
				httpServer.close(IgnoreCompletionCallback.create());
			}
		});

		eventloop.run();
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());

		try {
			System.err.println("Result: " + resultObserver.get());
		} catch (ExecutionException e) {
			throw e.getCause();
		}
	}

	@Test(expected = ParseException.class)
	public void testEmptyLineResponse() throws Throwable {
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		final SocketHandlerProvider socketHandlerProvider = new SocketHandlerProvider() {
			@Override
			public AsyncTcpSocket.EventHandler createSocketHandler(final AsyncTcpSocket asyncTcpSocket) {
				return new AsyncTcpSocket.EventHandler() {
					@Override
					public void onRegistered() {
						asyncTcpSocket.read();
					}

					@Override
					public void onRead(ByteBuf buf) {
						buf.recycle();
						asyncTcpSocket.write(ByteBufStrings.wrapAscii("\r\n"));
					}

					@Override
					public void onReadEndOfStream() {
						// empty
					}

					@Override
					public void onWrite() {
						asyncTcpSocket.close();
					}

					@Override
					public void onClosedWithError(Exception e) {
						// empty
					}
				};
			}
		};

		final SimpleServer server = SimpleServer.create(eventloop, socketHandlerProvider).withListenAddress(new InetSocketAddress("localhost", PORT));
		final AsyncHttpClient httpClient = AsyncHttpClient.create(eventloop);
		final ResultCallbackFuture<String> resultObserver = ResultCallbackFuture.create();

		server.listen();

		httpClient.send(HttpRequest.get("http://127.0.0.1:" + PORT), new ResultCallback<HttpResponse>() {
			@Override
			public void onResult(HttpResponse result) {
				try {
					resultObserver.setResult(decodeUtf8(result.getBody()));
				} catch (ParseException e) {
					onException(e);
				}
				httpClient.stop(IgnoreCompletionCallback.create());
			}

			@Override
			public void onException(Exception e) {
				resultObserver.setException(e);
				httpClient.stop(IgnoreCompletionCallback.create());
				server.close(IgnoreCompletionCallback.create());
			}
		});

		eventloop.run();
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());

		try {
			System.err.println("Result: " + resultObserver.get());
		} catch (ExecutionException e) {
			throw e.getCause();
		}
	}

	@Ignore
	@Test(expected = AsyncTimeoutException.class)
	public void testRecyclesBufsIfFailedToSend() throws Throwable {
		final int TIMEOUT = 1;
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError());

		final AsyncHttpClient httpClient = AsyncHttpClient.create(eventloop).withConnectTimeout(TIMEOUT);
		final ResultCallbackFuture<String> resultObserver = ResultCallbackFuture.create();

		HttpRequest request = HttpRequest.get("http://google.com");
		request.addHeader(HttpHeaders.COOKIE, ByteBufStrings.wrapAscii("prov=478d2a8b-1d34-4040-9877-893f4204afa1;" +
				" __qca=P0-372125722-1460720847866; " +
				"_ym_uid=1462979057991354365; " +
				"cc=0424c5415e9b42aeb86f333471619b41; " +
				"_ga=GA1.2.1152383523.1471249212"));

		httpClient.send(request, new ResultCallback<HttpResponse>() {
			@Override
			public void onResult(HttpResponse result) {
				try {
					resultObserver.setResult(decodeUtf8(result.getBody()));
				} catch (ParseException e) {
					onException(e);
				}
				httpClient.stop(IgnoreCompletionCallback.create());
			}

			@Override
			public void onException(Exception exception) {
				resultObserver.setException(exception);
				httpClient.stop(IgnoreCompletionCallback.create());
			}
		});

		eventloop.run();
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());

		try {
			System.err.println("Result: " + resultObserver.get());
		} catch (ExecutionException e) {
			throw e.getCause();
		}
	}
}
