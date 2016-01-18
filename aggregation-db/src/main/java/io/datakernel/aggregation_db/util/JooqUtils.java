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

package io.datakernel.aggregation_db.util;

import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class JooqUtils extends DSL {
	public static <T> Field<T> values(Field<T> field) {
		return function("VALUES", nullSafeDataType(field), DSL.nullSafe(field));
	}

	public static Map<Field<?>, Field<?>> onDuplicateKeyUpdateValues(Collection<Field<?>> fields) {
		Map<Field<?>, Field<?>> result = new HashMap<>();
		for (Field<?> field : fields) {
			result.put(field, values(field));
		}
		return result;
	}
}
