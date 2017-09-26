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

package io.datakernel.uikernel;

import com.google.gson.Gson;
import io.datakernel.async.Stages;
import io.datakernel.bytebuf.ByteBufStrings;
import io.datakernel.exception.ParseException;
import io.datakernel.http.*;

import java.nio.charset.Charset;
import java.util.List;

import static io.datakernel.http.HttpMethod.*;
import static io.datakernel.uikernel.Utils.deserializeUpdateRequest;
import static io.datakernel.uikernel.Utils.fromJson;

/**
 * Rest API for UiKernel Tables
 */
public class UiKernelServlets {
	private static final ContentType JSON_UTF8 = ContentType.of(MediaTypes.JSON, Charset.forName("UTF-8"));

	private static final String ID_PARAMETER_NAME = "id";

	public static <K, R extends AbstractRecord<K>> MiddlewareServlet apiServlet(GridModel<K, R> model, Gson gson) {
		return MiddlewareServlet.create()
				.with(POST, "/", create(model, gson))
				.with(GET, "/", read(model, gson))
				.with(PUT, "/", update(model, gson))
				.with(DELETE, "/:" + ID_PARAMETER_NAME, delete(model, gson))
				.with(GET, "/:" + ID_PARAMETER_NAME, get(model, gson));
	}

	public static <K, R extends AbstractRecord<K>> AsyncServlet read(final GridModel<K, R> model, final Gson gson) {
		return request -> {
			try {

				ReadSettings<K> settings = ReadSettings.from(gson, request);
				return model.read(settings).thenApply(response ->
						createResponse(response.toJson(gson, model.getRecordType(), model.getIdType())));

			} catch (ParseException e) {
				return Stages.ofException((Throwable) e);

			}
		};
	}

	public static <K, R extends AbstractRecord<K>> AsyncServlet get(final GridModel<K, R> model, final Gson gson) {
		return request -> {
			try {

				ReadSettings<K> settings = ReadSettings.from(gson, request);
				K id = fromJson(gson, request.getPathParameter(ID_PARAMETER_NAME), model.getIdType());
				return model.read(id, settings).thenApply(obj ->
						createResponse(gson.toJson(obj, model.getRecordType())));

			} catch (ParseException e) {
				return Stages.ofException((Throwable) e);

			}
		};
	}

	public static <K, R extends AbstractRecord<K>> AsyncServlet create(final GridModel<K, R> model, final Gson gson) {
		return request -> {
			try {
				String json = ByteBufStrings.decodeUtf8(request.getBody());
				R obj = fromJson(gson, json, model.getRecordType());
				return model.create(obj).thenApply(response ->
						createResponse(response.toJson(gson, model.getIdType())));
			} catch (ParseException e) {
				return Stages.ofException((Throwable) e);
			}
		};
	}

	public static <K, R extends AbstractRecord<K>> AsyncServlet update(final GridModel<K, R> model, final Gson gson) {
		return request -> {
			try {
				String json = ByteBufStrings.decodeUtf8(request.getBody());
				List<R> list = deserializeUpdateRequest(gson, json, model.getRecordType(), model.getIdType());
				return model.update(list).thenApply(result ->
						createResponse(result.toJson(gson, model.getRecordType(), model.getIdType())));
			} catch (ParseException e) {
				return Stages.ofException((Throwable) e);
			}
		};
	}

	public static <K, R extends AbstractRecord<K>> AsyncServlet delete(final GridModel<K, R> model, final Gson gson) {
		return request -> {
			try {
				K id = fromJson(gson, request.getPathParameter("id"), model.getIdType());
				return model.delete(id).thenApply(response -> {
					HttpResponse res = HttpResponse.ok200();
					if (response.hasErrors()) {
						String json = gson.toJson(response.getErrors());
						res.setContentType(JSON_UTF8);
						res.setBody(ByteBufStrings.wrapUtf8(json));
					}
					return res;
				});
			} catch (ParseException e) {
				return Stages.ofException((Throwable) e);
			}
		};
	}

	private static HttpResponse createResponse(String body) {
		return HttpResponse.ok200()
				.withContentType(JSON_UTF8)
				.withBody(ByteBufStrings.wrapUtf8(body));
	}
}
