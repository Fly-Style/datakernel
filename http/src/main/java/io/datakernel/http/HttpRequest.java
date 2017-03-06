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

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.exception.ParseException;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.*;

import static io.datakernel.bytebuf.ByteBufStrings.*;
import static io.datakernel.http.GzipProcessor.toGzip;
import static io.datakernel.http.HttpHeaders.*;
import static io.datakernel.http.HttpMethod.GET;
import static io.datakernel.http.HttpMethod.POST;

/**
 * Represent the HTTP request which {@link AsyncHttpClient} send to {@link AsyncHttpServer}. It must have only one owner in
 * each  part of time. After creating in server {@link HttpResponse} it will be recycled and you can not
 * use it later.
 */
public final class HttpRequest extends HttpMessage {
	private final HttpMethod method;
	private HttpUrl url;
	private InetAddress remoteAddress;

	private Map<String, String> bodyParameters;
	private Map<String, String> pathParameters;

	private boolean gzip = false;

	// region builders
	private HttpRequest(HttpMethod method) {
		this.method = method;
	}

	public static HttpRequest of(HttpMethod method, String url) throws IllegalArgumentException {
		assert method != null;
		HttpRequest request = new HttpRequest(method);
		request.setUrl(url);
		return request;
	}

	static HttpRequest of(HttpMethod method, HttpUrl url) {
		assert method != null;
		HttpRequest request = new HttpRequest(method);
		request.url = url;
		return request;
	}

	public static HttpRequest get(String url) {
		return HttpRequest.of(GET, url);
	}

	public static HttpRequest post(String url) {
		return HttpRequest.of(POST, url);
	}

	// common builder methods
	public HttpRequest withUrl(String url) throws IllegalArgumentException {
		setUrl(url);
		return this;
	}

	public HttpRequest withRemoteAddress(InetAddress inetAddress) {
		setRemoteAddress(inetAddress);
		return this;
	}

	public HttpRequest withHeader(HttpHeader header, ByteBuf value) {
		addHeader(header, value);
		return this;
	}

	public HttpRequest withHeader(HttpHeader header, byte[] value) {
		addHeader(header, value);
		return this;
	}

	public HttpRequest withHeader(HttpHeader header, String value) {
		addHeader(header, value);
		return this;
	}

	public HttpRequest withBody(byte[] array) {
		setBody(array);
		return this;
	}

	public HttpRequest withBody(ByteBuf body) {
		setBody(body);
		return this;
	}

	// specific builder methods
	public HttpRequest withAccept(List<AcceptMediaType> value) {
		setAccept(value);
		return this;
	}

	public HttpRequest withAccept(AcceptMediaType... value) {
		setAccept(value);
		return this;
	}

	public HttpRequest withAcceptCharsets(List<AcceptCharset> values) {
		setAcceptCharsets(values);
		return this;
	}

	public HttpRequest withAcceptCharsets(AcceptCharset... values) {
		setAcceptCharsets(values);
		return this;
	}

	public HttpRequest withCookies(List<HttpCookie> cookies) {
		addCookies(cookies);
		return this;
	}

	public HttpRequest withCookies(HttpCookie... cookie) {
		addCookies(cookie);
		return this;
	}

	public HttpRequest withCookie(HttpCookie cookie) {
		addCookie(cookie);
		return this;
	}

	public HttpRequest withContentType(ContentType contentType) {
		setContentType(contentType);
		return this;
	}

	public HttpRequest withContentType(MediaType mime) {
		setContentType(mime);
		return this;
	}

	public HttpRequest withContentType(MediaType mime, Charset charset) {
		setContentType(mime, charset);
		return this;
	}

	public HttpRequest withContentType(MediaType mime, String charset) {
		return withContentType(mime, Charset.forName(charset));
	}

	public HttpRequest withDate(Date date) {
		setDate(date);
		return this;
	}

	public HttpRequest withIfModifiedSince(Date date) {
		setIfModifiedSince(date);
		return this;
	}

	public HttpRequest withIfUnModifiedSince(Date date) {
		setIfUnModifiedSince(date);
		return this;
	}

	public HttpRequest withGzipCompression() {
		setGzipCompression();
		return this;
	}
	// endregion

	// region setters
	public void setUrl(String url) throws IllegalArgumentException {
		assert !recycled;
		this.url = HttpUrl.of(url);
		if (!this.url.isRelativePath()) {
			setHeader(HttpHeaders.ofString(HttpHeaders.HOST, this.url.getHostAndPort()));
		}
	}

	public void setRemoteAddress(InetAddress inetAddress) {
		assert !recycled;
		this.remoteAddress = inetAddress;
	}

	public void setAccept(List<AcceptMediaType> value) {
		addHeader(ofAcceptContentTypes(HttpHeaders.ACCEPT, value));
	}

	public void setAccept(AcceptMediaType... value) {
		setAccept(Arrays.asList(value));
	}

	public void setAcceptCharsets(List<AcceptCharset> values) {
		addHeader(ofCharsets(HttpHeaders.ACCEPT_CHARSET, values));
	}

	public void setAcceptCharsets(AcceptCharset... values) {
		setAcceptCharsets(Arrays.asList(values));
	}

	public void addCookies(List<HttpCookie> cookies) {
		addHeader(ofCookies(COOKIE, cookies));
	}

	public void addCookies(HttpCookie... cookie) {
		addCookies(Arrays.asList(cookie));
	}

	public void addCookie(HttpCookie cookie) {
		addCookies(Collections.singletonList(cookie));
	}

	public void setContentType(ContentType contentType) {
		setHeader(ofContentType(HttpHeaders.CONTENT_TYPE, contentType));
	}

	public void setContentType(MediaType mime) {
		setContentType(ContentType.of(mime));
	}

	public void setContentType(MediaType mime, Charset charset) {
		setContentType(ContentType.of(mime, charset));
	}

	public void setDate(Date date) {
		setHeader(ofDate(HttpHeaders.DATE, date));
	}

	public void setIfModifiedSince(Date date) {
		setHeader(ofDate(IF_MODIFIED_SINCE, date));
	}

	public void setIfUnModifiedSince(Date date) {
		setHeader(ofDate(IF_UNMODIFIED_SINCE, date));
	}

	public void setGzipCompression() {
		setHeader(HttpHeaders.ofString(CONTENT_ENCODING, "gzip"));
		gzip = true;
	}
	// endregion

	// region getters
	public HttpMethod getMethod() {
		assert !recycled;
		return method;
	}

	public InetAddress getRemoteAddress() {
		assert !recycled;
		return remoteAddress;
	}

	public String getFullUrl() {
		if (!url.isRelativePath()) {
			return url.toString();
		}
		String host = getHost();
		if (host == null) {
			return null;
		}
		return "http://" + host + url.getPathAndQuery();
	}

	public boolean isHttps() {
		return url.isHttps();
	}

	HttpUrl getUrl() {
		assert !recycled;
		return url;
	}

	public String getHostAndPort() {
		return url.getHostAndPort();
	}

	public String getHost() {
		String host = getHeader(HttpHeaders.HOST);
		if ((host == null) || host.isEmpty())
			return null;
		return host;
	}

	public String getPath() {
		assert !recycled;
		return url.getPath();
	}

	public String getPathAndQuery() {
		assert !recycled;
		return url.getPathAndQuery();
	}

	public String getQuery() {
		return url.getQuery();
	}

	public String getFragment() {
		return url.getFragment();
	}

	public String getQueryParameter(String key) {
		assert !recycled;
		return url.getQueryParameter(key);
	}

	public List<String> getQueryParameters(String key) {
		assert !recycled;
		return url.getQueryParameters(key);
	}

	public Iterable<QueryParameter> getQueryParametersIterable() {
		assert !recycled;
		return url.getQueryParametersIterable();
	}

	public Map<String, String> getQueryParameters() {
		assert !recycled;
		return url.getQueryParameters();
	}

	public String getPostParameter(String key) {
		assert !recycled;
		parseBodyParams();
		return bodyParameters.get(key);
	}

	public Map<String, String> getPostParameters() {
		assert !recycled;
		parseBodyParams();
		return bodyParameters;
	}

	public String getPathParameter(String key) {
		assert !recycled;
		return pathParameters == null ? null : pathParameters.get(key);
	}

	public Map<String, String> getPathParameters() {
		assert !recycled;
		return pathParameters == null ? Collections.<String, String>emptyMap() : pathParameters;
	}

	public List<AcceptMediaType> getAccept() {
		assert !recycled;
		List<AcceptMediaType> list = new ArrayList<>();
		List<Value> headers = getHeaderValues(ACCEPT);
		for (Value header : headers) {
			ValueOfBytes value = (ValueOfBytes) header;
			try {
				AcceptMediaType.parse(value.array, value.offset, value.size, list);
			} catch (ParseException e) {
				return Collections.emptyList();
			}
		}
		return list;
	}

	public List<AcceptCharset> getAcceptCharsets() {
		assert !recycled;
		List<AcceptCharset> charsets = new ArrayList<>();
		List<Value> headers = getHeaderValues(ACCEPT_CHARSET);
		for (Value header : headers) {
			ValueOfBytes value = (ValueOfBytes) header;
			try {
				AcceptCharset.parse(value.array, value.offset, value.size, charsets);
			} catch (ParseException e) {
				return Collections.emptyList();
			}
		}
		return charsets;
	}

	public Date getIfModifiedSince() {
		assert !recycled;
		ValueOfBytes header = (ValueOfBytes) getHeaderValue(IF_MODIFIED_SINCE);
		if (header != null) {
			try {
				return new Date(HttpDate.parse(header.array, header.offset));
			} catch (ParseException e) {
				return null;
			}
		}
		return null;
	}

	public Date getIfUnModifiedSince() {
		assert !recycled;
		ValueOfBytes header = (ValueOfBytes) getHeaderValue(IF_UNMODIFIED_SINCE);
		if (header != null)
			try {
				return new Date(HttpDate.parse(header.array, header.offset));
			} catch (ParseException e) {
				return null;
			}
		return null;
	}

	@Override
	public List<HttpCookie> getCookies() {
		assert !recycled;
		List<HttpCookie> cookie = new ArrayList<>();
		List<Value> headers = getHeaderValues(COOKIE);
		for (Value header : headers) {
			ValueOfBytes value = (ValueOfBytes) header;
			try {
				HttpCookie.parseSimple(value.array, value.offset, value.offset + value.size, cookie);
			} catch (ParseException e) {
				return new ArrayList<>();
			}
		}
		return cookie;
	}
	// endregion

	// region internal
	private void parseBodyParams() {
		if (bodyParameters != null)
			return;
		if (method == POST
				&& getContentType() != null
				&& getContentType().getMediaType() == MediaTypes.X_WWW_FORM_URLENCODED
				&& body.readPosition() != body.writePosition()) {
			bodyParameters = HttpUtils.parseQueryParameters(decodeAscii(getBody()));
		} else {
			bodyParameters = Collections.emptyMap();
		}
	}

	int getPos() {
		return url.pos;
	}

	void setPos(int pos) {
		url.pos = (short) pos;
	}

	String getRelativePath() {
		assert !recycled;
		return url.getRelativePath();
	}

	String pollUrlPart() {
		assert !recycled;
		return url.pollUrlPart();
	}

	void removePathParameter(String key) {
		pathParameters.remove(key);
	}

	void putPathParameter(String key, String value) {
		if (pathParameters == null) {
			pathParameters = new HashMap<>();
		}
		pathParameters.put(key, value);
	}

	private final static int LONGEST_HTTP_METHOD_SIZE = 12;
	private static final byte[] HTTP_1_1 = encodeAscii(" HTTP/1.1");
	private static final int HTTP_1_1_SIZE = HTTP_1_1.length;

	@Override
	public ByteBuf toByteBuf() {
		assert !recycled;
		if (body != null || method != GET) {
			if (gzip) {
				body = toGzip(body);
			}
			setHeader(HttpHeaders.ofDecimal(HttpHeaders.CONTENT_LENGTH, body == null ? 0 : body.readRemaining()));
		}
		int estimatedSize = estimateSize(LONGEST_HTTP_METHOD_SIZE
				+ 1 // SPACE
				+ url.getPathAndQueryLength())
				+ HTTP_1_1_SIZE;
		ByteBuf buf = ByteBufPool.allocate(estimatedSize);

		method.write(buf);
		buf.put(SP);
		url.writePathAndQuery(buf);
		buf.put(HTTP_1_1);

		writeHeaders(buf);

		writeBody(buf);

		return buf;
	}

	@Override
	public String toString() {
		return getFullUrl();
	}
	// endregion
}
