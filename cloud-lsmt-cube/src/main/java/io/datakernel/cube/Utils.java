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

package io.datakernel.cube;

import io.datakernel.async.Promise;
import io.datakernel.codegen.ClassBuilder;
import io.datakernel.codegen.DefiningClassLoader;
import io.datakernel.codegen.Expression;
import io.datakernel.codegen.ExpressionSequence;
import io.datakernel.cube.attributes.AttributeResolver;
import io.datakernel.cube.attributes.AttributeResolver.AttributesFunction;
import io.datakernel.cube.attributes.AttributeResolver.KeyFunction;
import io.datakernel.cube.ot.CubeDiff;

import java.util.*;

import static io.datakernel.codegen.Expressions.*;
import static java.util.stream.Collectors.toSet;

public final class Utils {
	private Utils() {
	}

	public static <R> Class<R> createResultClass(Collection<String> attributes, Collection<String> measures,
			Cube cube, DefiningClassLoader classLoader) {
		ClassBuilder<R> builder = ClassBuilder.create(classLoader, Object.class);
		for (String attribute : attributes) {
			builder.withField(attribute.replace('.', '$'), cube.getAttributeInternalType(attribute));
		}
		for (String measure : measures) {
			builder.withField(measure, cube.getMeasureInternalType(measure));
		}
		return builder.build();
	}

	static boolean startsWith(List<String> list, List<String> prefix) {
		if (prefix.size() >= list.size())
			return false;

		for (int i = 0; i < prefix.size(); ++i) {
			if (!list.get(i).equals(prefix.get(i)))
				return false;
		}

		return true;
	}

	@SuppressWarnings("unchecked")
	public static <R> Promise<Void> resolveAttributes(List<R> results, AttributeResolver attributeResolver,
			List<String> recordDimensions, List<String> recordAttributes,
			Map<String, Object> fullySpecifiedDimensions,
			Class<R> recordClass, DefiningClassLoader classLoader) {
		Object[] fullySpecifiedDimensionsArray = new Object[recordDimensions.size()];
		for (int i = 0; i < recordDimensions.size(); i++) {
			String dimension = recordDimensions.get(i);
			if (fullySpecifiedDimensions.containsKey(dimension)) {
				fullySpecifiedDimensionsArray[i] = fullySpecifiedDimensions.get(dimension);
			}
		}
		KeyFunction keyFunction = ClassBuilder.create(classLoader, KeyFunction.class)
				.withMethod("extractKey", () -> {
					ExpressionSequence extractKey = ExpressionSequence.create();
					Expression key = let(newArray(Object.class, value(recordDimensions.size())));
					for (int i = 0; i < recordDimensions.size(); i++) {
						String dimension = recordDimensions.get(i);
						extractKey.add(setArrayItem(key, value(i),
								fullySpecifiedDimensions.containsKey(dimension) ?
										getArrayItem(value(fullySpecifiedDimensionsArray), value(i)) :
										cast(property(cast(arg(0), recordClass), dimension), Object.class)));
					}
					return extractKey.add(key);
				})
				.buildClassAndCreateNewInstance();

		List<String> resolverAttributes = new ArrayList<>(attributeResolver.getAttributeTypes().keySet());
		AttributesFunction attributesFunction = ClassBuilder.create(classLoader, AttributesFunction.class)
				.withMethod("applyAttributes", () -> {
					ExpressionSequence applyAttributes = ExpressionSequence.create();
					for (String attribute : recordAttributes) {
						String attributeName = attribute.substring(attribute.indexOf('.') + 1);
						int resolverAttributeIndex = resolverAttributes.indexOf(attributeName);
						applyAttributes.add(set(
								property(cast(arg(0), recordClass), attribute.replace('.', '$')),
								getArrayItem(arg(1), value(resolverAttributeIndex))));
					}
					return applyAttributes;
				})
				.buildClassAndCreateNewInstance();

		return attributeResolver.resolveAttributes((List<Object>) results, keyFunction, attributesFunction);
	}

	@SuppressWarnings("unchecked")
	public static <D, C> Set<C> chunksInDiffs(CubeDiffScheme<D> cubeDiffsExtractor,
			List<? extends D> diffs) {
		return diffs.stream()
				.flatMap(cubeDiffsExtractor::unwrapToStream)
				.flatMap(CubeDiff::addedChunks)
				.map(id -> (C) id)
				.collect(toSet());
	}
}
