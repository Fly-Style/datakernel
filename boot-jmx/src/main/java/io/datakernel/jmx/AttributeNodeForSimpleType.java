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

package io.datakernel.jmx;

import org.jetbrains.annotations.Nullable;

import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.datakernel.util.Preconditions.checkArgument;
import static io.datakernel.util.Preconditions.checkNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;

@SuppressWarnings("rawtypes")
final class AttributeNodeForSimpleType extends AttributeNodeForLeafAbstract {
	private final Method setter;
	private final Class<?> type;
	private final JmxReducer reducer;

	public AttributeNodeForSimpleType(String name, @Nullable String description, boolean visible,
			ValueFetcher fetcher, @Nullable Method setter,
			Class<?> attributeType, JmxReducer reducer) {
		super(name, description, fetcher, visible);
		this.setter = setter;
		this.type = attributeType;
		this.reducer = checkNotNull(reducer);
	}

	@Override
	public Map<String, OpenType<?>> getOpenTypes() {
		return singletonMap(name, simpleTypeOf(type));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object aggregateAttribute(String attrName, List<?> sources) {
		List<Object> values = new ArrayList<>(sources.size());
		for (Object notNullSource : sources) {
			Object currentValue = fetcher.fetchFrom(notNullSource);
			values.add(currentValue);
		}

		return reducer.reduce(values);
	}

	@Override
	public List<JmxRefreshable> getAllRefreshables(Object source) {
		return emptyList();
	}

	@Override
	public boolean isSettable(String attrName) {
		checkArgument(attrName.equals(name), "Attribute names do not match");
		return setter != null;
	}

	@Override
	public void setAttribute(String attrName, Object value, List<?> targets) throws SetterException {
		checkArgument(attrName.equals(name), "Attribute names do not match");
		checkNotNull(targets);
		List<?> notNullTargets = targets.stream().filter(Objects::nonNull).collect(Collectors.toList());
		if (notNullTargets.size() == 0) {
			return;
		}

		for (Object target : notNullTargets) {
			try {
				setter.invoke(target, value);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new SetterException(e);
			}
		}
	}

	private static SimpleType<?> simpleTypeOf(Class<?> clazz) throws IllegalArgumentException {
		if (clazz == boolean.class || clazz == Boolean.class) {
			return SimpleType.BOOLEAN;
		} else if (clazz == byte.class || clazz == Byte.class) {
			return SimpleType.BYTE;
		} else if (clazz == short.class || clazz == Short.class) {
			return SimpleType.SHORT;
		} else if (clazz == char.class || clazz == Character.class) {
			return SimpleType.CHARACTER;
		} else if (clazz == int.class || clazz == Integer.class) {
			return SimpleType.INTEGER;
		} else if (clazz == long.class || clazz == Long.class) {
			return SimpleType.LONG;
		} else if (clazz == float.class || clazz == Float.class) {
			return SimpleType.FLOAT;
		} else if (clazz == double.class || clazz == Double.class) {
			return SimpleType.DOUBLE;
		} else if (clazz == String.class) {
			return SimpleType.STRING;
		} else {
			throw new IllegalArgumentException("There is no SimpleType for " + clazz.getName());
		}
	}
}
