/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
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

package io.datakernel.cube.http;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.datakernel.cube.QueryResult;
import io.datakernel.cube.Record;
import io.datakernel.cube.RecordScheme;
import io.datakernel.cube.ReportType;
import io.datakernel.json.GsonAdapters.TypeAdapterMapping;
import io.datakernel.util.SimpleType;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.datakernel.json.GsonAdapters.STRING_JSON;
import static io.datakernel.json.GsonAdapters.ofList;
import static io.datakernel.util.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

final class QueryResultGsonAdapter extends TypeAdapter<QueryResult> {
	private static final String MEASURES_FIELD = "measures";
	private static final String ATTRIBUTES_FIELD = "attributes";
	private static final String FILTER_ATTRIBUTES_FIELD = "filterAttributes";
	private static final String RECORDS_FIELD = "records";
	private static final String TOTALS_FIELD = "totals";
	private static final String COUNT_FIELD = "count";
	private static final String SORTED_BY_FIELD = "sortedBy";
	private static final String METADATA_FIELD = "metadata";

	private final Map<String, TypeAdapter<?>> attributeAdapters;
	private final Map<String, TypeAdapter<?>> measureAdapters;

	private final Map<String, Class<?>> attributeTypes;
	private final Map<String, Class<?>> measureTypes;

	private final TypeAdapter<List<String>> stringListAdapter = ofList(STRING_JSON);

	public QueryResultGsonAdapter(Map<String, TypeAdapter<?>> attributeAdapters, Map<String, TypeAdapter<?>> measureAdapters, Map<String, Class<?>> attributeTypes, Map<String, Class<?>> measureTypes) {
		this.attributeAdapters = attributeAdapters;
		this.measureAdapters = measureAdapters;
		this.attributeTypes = attributeTypes;
		this.measureTypes = measureTypes;
	}

	public static QueryResultGsonAdapter create(TypeAdapterMapping mapping, Map<String, Type> attributeTypes, Map<String, Type> measureTypes) {
		Map<String, TypeAdapter<?>> attributeAdapters = new LinkedHashMap<>();
		Map<String, TypeAdapter<?>> measureAdapters = new LinkedHashMap<>();
		Map<String, Class<?>> attributeRawTypes = new LinkedHashMap<>();
		Map<String, Class<?>> measureRawTypes = new LinkedHashMap<>();
		for(String attribute : attributeTypes.keySet()) {
			SimpleType token = SimpleType.of(attributeTypes.get(attribute));
			attributeAdapters.put(attribute, mapping.getAdapter(token.getType()).nullSafe());
			attributeRawTypes.put(attribute, token.getRawType());
		}
		for(String measure : measureTypes.keySet()) {
			SimpleType token = SimpleType.of(measureTypes.get(measure));
			measureAdapters.put(measure, mapping.getAdapter(token.getType()));
			measureRawTypes.put(measure, token.getRawType());
		}
		return new QueryResultGsonAdapter(attributeAdapters, measureAdapters, attributeRawTypes, measureRawTypes);
	}

	@Override
	public QueryResult read(JsonReader reader) throws JsonParseException, IOException {
		reader.beginObject();

		checkArgument(METADATA_FIELD.equals(reader.nextName()));
		reader.beginObject();

		checkArgument(ATTRIBUTES_FIELD.equals(reader.nextName()));
		List<String> attributes = stringListAdapter.read(reader);

		checkArgument(MEASURES_FIELD.equals(reader.nextName()));
		List<String> measures = stringListAdapter.read(reader);

		reader.endObject();
		ReportType reportType = ReportType.METADATA;

		List<String> sortedBy = emptyList();
		if (reader.hasNext() && SORTED_BY_FIELD.equals(reader.nextName())) {
			sortedBy = stringListAdapter.read(reader);
		}

		RecordScheme recordScheme = recordScheme(attributes, measures);

		List<Record> records = emptyList();
		int count = 0;
		Map<String, Object> filterAttributes = emptyMap();
		if (reader.hasNext() && RECORDS_FIELD.equals(reader.nextName())) {
			records = readRecords(reader, recordScheme);

			checkArgument(COUNT_FIELD.equals(reader.nextName()));
			count = reader.nextInt();

			checkArgument(FILTER_ATTRIBUTES_FIELD.equals(reader.nextName()));
			filterAttributes = readFilterAttributes(reader);

			reportType = ReportType.DATA;
		}

		Record totals = Record.create(recordScheme);
		if (reader.hasNext() && TOTALS_FIELD.equals(reader.nextName())) {
			totals = readTotals(reader, recordScheme);
			reportType = ReportType.DATA_WITH_TOTALS;
		}

		reader.endObject();

		return QueryResult.create(recordScheme, records, totals, count,
				attributes, measures, sortedBy, filterAttributes, reportType);
	}

	private List<Record> readRecords(JsonReader reader, RecordScheme recordScheme) throws JsonParseException, IOException {
		List<Record> records = new ArrayList<>();

		TypeAdapter[] fieldTypeAdapters = getTypeAdapters(recordScheme);

		reader.beginArray();
		while (reader.hasNext()) {
			reader.beginArray();
			Record record = Record.create(recordScheme);
			for (int i = 0; i < fieldTypeAdapters.length; i++) {
				Object fieldValue = fieldTypeAdapters[i].read(reader);
				record.put(i, fieldValue);
			}
			records.add(record);
			reader.endArray();
		}
		reader.endArray();

		return records;
	}

	private Record readTotals(JsonReader reader, RecordScheme recordScheme) throws JsonParseException, IOException {
		reader.beginArray();
		Record totals = Record.create(recordScheme);
		for (int i = 0; i < recordScheme.getFields().size(); i++) {
			String field = recordScheme.getField(i);
			TypeAdapter<?> fieldTypeAdapter = measureAdapters.get(field);
			if (fieldTypeAdapter == null)
				continue;
			Object fieldValue = fieldTypeAdapter.read(reader);
			totals.put(i, fieldValue);
		}
		reader.endArray();
		return totals;
	}

	private Map<String, Object> readFilterAttributes(JsonReader reader) throws JsonParseException, IOException {
		reader.beginObject();
		Map<String, Object> result = new LinkedHashMap<>();
		while (reader.hasNext()) {
			String attribute = reader.nextName();
			Object value = attributeAdapters.get(attribute).read(reader);
			result.put(attribute, value);
		}
		reader.endObject();
		return result;
	}

	@Override
	public void write(JsonWriter writer, QueryResult result) throws IOException {
		writer.beginObject();

		writer.name(METADATA_FIELD);
		writer.beginObject();

		writer.name(ATTRIBUTES_FIELD);
		stringListAdapter.write(writer, result.getAttributes());

		writer.name(MEASURES_FIELD);
		stringListAdapter.write(writer, result.getMeasures());

		writer.endObject();

		if (result.getReportType() == ReportType.DATA || result.getReportType() == ReportType.DATA_WITH_TOTALS) {
			writer.name(SORTED_BY_FIELD);
			stringListAdapter.write(writer, result.getSortedBy());
		}

		if (result.getReportType() == ReportType.DATA || result.getReportType() == ReportType.DATA_WITH_TOTALS) {
			writer.name(RECORDS_FIELD);
			writeRecords(writer, result.getRecordScheme(), result.getRecords());

			writer.name(COUNT_FIELD);
			writer.value(result.getTotalCount());

			writer.name(FILTER_ATTRIBUTES_FIELD);
			writeFilterAttributes(writer, result.getFilterAttributes());
		}

		if (result.getReportType() == ReportType.DATA_WITH_TOTALS) {
			writer.name(TOTALS_FIELD);
			writeTotals(writer, result.getRecordScheme(), result.getTotals());
		}

		writer.endObject();
	}

	@SuppressWarnings("unchecked")
	private void writeRecords(JsonWriter writer, RecordScheme recordScheme, List<Record> records) throws IOException {
		writer.beginArray();

		TypeAdapter[] fieldTypeAdapters = getTypeAdapters(recordScheme);

		for (Record record : records) {
			writer.beginArray();
			for (int i = 0; i < recordScheme.getFields().size(); i++) {
				fieldTypeAdapters[i].write(writer, record.get(i));
			}
			writer.endArray();
		}
		writer.endArray();
	}

	@SuppressWarnings("unchecked")
	private void writeTotals(JsonWriter writer, RecordScheme recordScheme, Record totals) throws IOException {
		writer.beginArray();
		for (int i = 0; i < recordScheme.getFields().size(); i++) {
			String field = recordScheme.getField(i);
			TypeAdapter fieldTypeAdapter = measureAdapters.get(field);
			if (fieldTypeAdapter == null)
				continue;
			fieldTypeAdapter.write(writer, totals.get(i));
		}
		writer.endArray();
	}

	@SuppressWarnings("unchecked")
	private void writeFilterAttributes(JsonWriter writer, Map<String, Object> filterAttributes) throws IOException {
		writer.beginObject();
		for (String attribute : filterAttributes.keySet()) {
			Object value = filterAttributes.get(attribute);
			writer.name(attribute);
			TypeAdapter typeAdapter = attributeAdapters.get(attribute);
			typeAdapter.write(writer, value);
		}
		writer.endObject();
	}

	public RecordScheme recordScheme(List<String> attributes, List<String> measures) {
		RecordScheme recordScheme = RecordScheme.create();
		for (String attribute : attributes) {
			recordScheme = recordScheme.withField(attribute, attributeTypes.get(attribute));
		}
		for (String measure : measures) {
			recordScheme = recordScheme.withField(measure, measureTypes.get(measure));
		}
		return recordScheme;
	}

	private TypeAdapter[] getTypeAdapters(RecordScheme recordScheme) {
		TypeAdapter[] fieldTypeAdapters = new TypeAdapter[recordScheme.getFields().size()];
		for (int i = 0; i < recordScheme.getFields().size(); i++) {
			String field = recordScheme.getField(i);
			fieldTypeAdapters[i] = firstNonNull(attributeAdapters.get(field), measureAdapters.get(field));
		}
		return fieldTypeAdapters;
	}

	private static <T> T firstNonNull(T a, T b) {
		return a != null ? a : b;
	}
}
