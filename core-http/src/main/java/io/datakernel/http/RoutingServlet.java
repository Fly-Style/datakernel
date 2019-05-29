/*
 * Copyright (C) 2015-2019 SoftIndex LLC.
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

import io.datakernel.async.Promise;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

import static io.datakernel.util.Preconditions.checkArgument;
import static io.datakernel.util.Preconditions.checkNotNull;

public final class RoutingServlet implements AsyncServlet {
	private static final String ROOT = "/";
	private static final String STAR = "*";
	private static final String WILDCARD = "/" + STAR;

	private static final BinaryOperator<AsyncServlet> DEFAULT_MERGER = ($, $2) -> {
		throw new IllegalArgumentException("Already mapped");
	};

	protected final Map<@Nullable HttpMethod, AsyncServlet> rootServlets = new HashMap<>();

	protected final Map<String, RoutingServlet> routes = new HashMap<>();
	protected final Map<String, RoutingServlet> parameters = new HashMap<>();

	protected final Map<@Nullable HttpMethod, AsyncServlet> fallbackServlets = new HashMap<>();

	private RoutingServlet() {
	}

	public static RoutingServlet create() {
		return new RoutingServlet();
	}

	public static RoutingServlet wrap(AsyncServlet servlet) {
		RoutingServlet wrapper = new RoutingServlet();
		wrapper.fallbackServlets.put(null, servlet);
		return wrapper;
	}

	public RoutingServlet with(String path, AsyncServlet servlet) {
		return with(null, path, servlet, DEFAULT_MERGER);
	}

	public RoutingServlet with(String path, AsyncServlet servlet, BinaryOperator<AsyncServlet> merger) {
		return with(null, path, servlet, merger);
	}

	@Contract("_, _, _ -> this")
	public RoutingServlet with(@Nullable HttpMethod method, String path, AsyncServlet servlet) {
		return with(method, path, servlet, DEFAULT_MERGER);
	}

	@Contract("_, _, _, _ -> this")
	public RoutingServlet with(@Nullable HttpMethod method, String path, AsyncServlet servlet, BinaryOperator<AsyncServlet> merger) {
		checkNotNull(servlet);
		checkArgument(path.isEmpty() || path.startsWith(ROOT) || path.endsWith(WILDCARD) || !path.contains(STAR), "Invalid path " + path);

		if (path.endsWith(WILDCARD)) {
			makeSubtree(path.substring(0, path.length() - 2)).mapFallback(method, servlet, DEFAULT_MERGER);
		} else {
			makeSubtree(path).map(method, servlet, DEFAULT_MERGER);
		}
		return this;
	}

	public void walk(Walker walker) {
		walk(ROOT, walker);
	}

	private void walk(String prefix, Walker walker) {
		rootServlets.forEach((method, servlet) -> walker.walk(method, prefix, servlet));
		fallbackServlets.forEach((method, servlet) -> walker.walk(method, prefix + STAR, servlet));
		routes.forEach((route, subtree) -> subtree.walk(prefix + route + "/", walker));
		parameters.forEach((route, subtree) -> subtree.walk(prefix + ":" + route + "/", walker));
	}

	@Nullable
	public RoutingServlet getSubtree(String path) {
		return getOrCreateSubtree(path, (servlet, name) ->
				name.startsWith(":") ?
						servlet.parameters.get(name.substring(1)) :
						servlet.routes.get(name));
	}

	@Contract("_ -> new")
	public RoutingServlet merge(RoutingServlet servlet) {
		return merge(ROOT, servlet);
	}

	@Contract("_, _ -> new")
	public RoutingServlet merge(String path, RoutingServlet servlet) {
		return merge(path, servlet, DEFAULT_MERGER);
	}

	@Contract("_, _, _ -> new")
	public RoutingServlet merge(String path, RoutingServlet servlet, BinaryOperator<AsyncServlet> merger) {
		RoutingServlet merged = new RoutingServlet();
		mergeInto(merged, this, merger);
		mergeInto(merged.makeSubtree(path), servlet, merger);
		return merged;
	}

	@NotNull
	@Override
	public Promise<HttpResponse> serve(@NotNull HttpRequest request) {
		Promise<HttpResponse> processed = tryServe(request);
		return processed != null ?
				processed :
				Promise.ofException(HttpException.notFound404());
	}

	private void map(@Nullable HttpMethod method, AsyncServlet servlet, BinaryOperator<AsyncServlet> merger) {
		rootServlets.merge(method, servlet, merger);
	}

	private void mapFallback(@Nullable HttpMethod method, AsyncServlet servlet, BinaryOperator<AsyncServlet> merger) {
		fallbackServlets.merge(method, servlet, merger);
	}

	@Nullable
	private Promise<HttpResponse> tryServe(HttpRequest request) {
		int introPosition = request.getPos();
		String urlPart = request.pollUrlPart();
		HttpMethod method = request.getMethod();

		if (urlPart.isEmpty()) {
			AsyncServlet servlet = rootServlets.getOrDefault(method, rootServlets.get(null));
			if (servlet != null) {
				return servlet.serve(request);
			}
		} else {
			int position = request.getPos();
			RoutingServlet transit = routes.get(urlPart);
			if (transit != null) {
				Promise<HttpResponse> result = transit.tryServe(request);
				if (result != null) {
					return result;
				}
				request.setPos(position);
			}
			for (Entry<String, RoutingServlet> entry : parameters.entrySet()) {
				String key = entry.getKey();
				request.putPathParameter(key, urlPart);
				Promise<HttpResponse> result = entry.getValue().tryServe(request);
				if (result != null) {
					return result;
				}
				request.removePathParameter(key);
				request.setPos(position);
			}
		}

		AsyncServlet servlet = fallbackServlets.getOrDefault(method, fallbackServlets.get(null));
		if (servlet != null) {
			request.setPos(introPosition);
			return servlet.serve(request);
		}
		return null;
	}

	private RoutingServlet makeSubtree(String path) {
		return getOrCreateSubtree(path, (servlet, name) ->
				name.startsWith(":") ?
						servlet.parameters.computeIfAbsent(name.substring(1), $ -> new RoutingServlet()) :
						servlet.routes.computeIfAbsent(name, $ -> new RoutingServlet()));
	}

	private RoutingServlet getOrCreateSubtree(String path, BiFunction<RoutingServlet, String, @Nullable RoutingServlet> childGetter) {
		if (path.isEmpty() || path.equals(ROOT)) {
			return this;
		}
		RoutingServlet sub = this;
		int slash = path.indexOf('/', 1);
		String remainingPath = path;
		while (true) {
			String urlPart = remainingPath.substring(1, slash == -1 ? remainingPath.length() : slash);

			if (urlPart.isEmpty()) {
				return sub;
			}
			sub = childGetter.apply(sub, urlPart);

			if (slash == -1 || sub == null) {
				return sub;
			}
			remainingPath = remainingPath.substring(slash);
			slash = remainingPath.indexOf('/', 1);
		}
	}

	public static RoutingServlet merge(RoutingServlet first, RoutingServlet second) {
		return merge(first, second, DEFAULT_MERGER);
	}

	public static RoutingServlet merge(RoutingServlet... servlets) {
		return merge(DEFAULT_MERGER, servlets);
	}

	public static RoutingServlet merge(RoutingServlet first, RoutingServlet second, BinaryOperator<AsyncServlet> merger) {
		RoutingServlet merged = new RoutingServlet();
		mergeInto(merged, first, merger);
		mergeInto(merged, second, merger);
		return merged;
	}

	public static RoutingServlet merge(BinaryOperator<AsyncServlet> merger, RoutingServlet... servlets) {
		RoutingServlet merged = new RoutingServlet();
		for (RoutingServlet servlet : servlets) {
			mergeInto(merged, servlet, merger);
		}
		return merged;
	}

	private static void mergeInto(RoutingServlet into, RoutingServlet from, BinaryOperator<AsyncServlet> merger) {
		from.rootServlets.forEach((method, servlet) -> into.map(method, servlet, merger));
		from.fallbackServlets.forEach((method, servlet) -> into.mapFallback(method, servlet, merger));
		from.routes.forEach((key, value) ->
				into.routes.merge(key, value, (s1, s2) -> {
					mergeInto(s1, s2, merger);
					return s1;
				}));
		from.parameters.forEach((key, value) ->
				into.parameters.merge(key, value, (s1, s2) -> {
					mergeInto(s1, s2, merger);
					return s1;
				}));
	}

	@FunctionalInterface
	public interface Walker {

		void walk(@Nullable HttpMethod method, String path, AsyncServlet servlet);
	}
}