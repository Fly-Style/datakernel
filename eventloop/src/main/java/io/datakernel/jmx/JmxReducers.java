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

import java.util.List;
import java.util.Objects;

import static io.datakernel.jmx.Utils.filterNulls;

/**
 * Contains implementations of JmxReducers
 */
public final class JmxReducers {
	private JmxReducers() {}

	/**
	 * Reduces a list of values to a single value, if all values are equal.
	 * Returns null if list is empty or contains non-equals values.
	 */
	public static final class JmxReducerDistinct implements JmxReducer<Object> {
		@Override
		public Object reduce(List<?> input) {
			if (input.size() == 0) {
				return null;
			}

			Object firstValue = input.get(0);
			for (int i = 1; i < input.size(); i++) {
				Object currentValue = input.get(i);
				if (!Objects.equals(firstValue, currentValue)) {
					return null;
				}
			}
			return firstValue;
		}
	}

	/**
	 * Reduces list of {@code Number} values, counting sum of elements in list.
	 * Returns null if provided list is empty.
	 */
	public static final class JmxReducerSum implements JmxReducer<Number> {

		@Override
		public Number reduce(List<? extends Number> input) {
			List<? extends Number> inputListWithoutNulls = filterNulls(input);

			if (inputListWithoutNulls.size() == 0) {
				return null;
			}

			Number first = inputListWithoutNulls.get(0);
			Class<?> numberClass = first.getClass();
			if (isFloatingPointNumber(numberClass)) {
				double floatingPointSum = first.doubleValue();
				for (int i = 1; i < inputListWithoutNulls.size(); i++) {
					floatingPointSum += inputListWithoutNulls.get(i).doubleValue();

				}
				return convert(floatingPointSum, numberClass);
			} else if (isIntegerNumber(numberClass)) {
				long integerSum = first.longValue();
				for (int i = 1; i < inputListWithoutNulls.size(); i++) {
					integerSum += inputListWithoutNulls.get(i).longValue();
				}
				return convert(integerSum, numberClass);
			} else {
				throw new IllegalArgumentException(
						"Cannot calculate sum of objects of type: " + first.getClass().getName());
			}
		}


	}

	/**
	 * Reduces provided list, returns min value, contained in list.
	 * Returns null if provided list is empty.
	 */
	public static final class JmxReducerMin implements JmxReducer<Number> {

		@Override
		public Number reduce(List<? extends Number> input) {
			List<? extends Number> inputListWithoutNulls = filterNulls(input);

			if (inputListWithoutNulls.size() == 0) {
				return null;
			}

			Number first = inputListWithoutNulls.get(0);
			Class<?> numberClass = first.getClass();
			if (isFloatingPointNumber(numberClass)) {
				double floatingPointMin = first.doubleValue();
				for (int i = 1; i < inputListWithoutNulls.size(); i++) {
					double currentValue = inputListWithoutNulls.get(i).doubleValue();
					if (currentValue < floatingPointMin) {
						floatingPointMin = currentValue;
					}
				}
				return convert(floatingPointMin, numberClass);
			} else if (isIntegerNumber(numberClass)) {
				long integerMin = first.longValue();
				for (int i = 1; i < inputListWithoutNulls.size(); i++) {
					long currentValue = inputListWithoutNulls.get(i).longValue();
					if (currentValue < integerMin) {
						integerMin = currentValue;
					}
				}
				return convert(integerMin, numberClass);
			} else {
				throw new IllegalArgumentException(
						"Cannot calculate min of objects of type: " + first.getClass().getName());
			}
		}
	}

	/**
	 * Reduces provided list, returns max value, contained in list.
	 * Returns null if provided list is empty.
	 */
	public static final class JmxReducerMax implements JmxReducer<Number> {

		@Override
		public Number reduce(List<? extends Number> input) {
			List<? extends Number> inputListWithoutNulls = filterNulls(input);

			if (inputListWithoutNulls.size() == 0) {
				return null;
			}

			Number first = inputListWithoutNulls.get(0);
			Class<?> numberClass = first.getClass();
			if (isFloatingPointNumber(numberClass)) {
				double floatingPointMax = first.doubleValue();
				for (int i = 1; i < inputListWithoutNulls.size(); i++) {
					double currentValue = inputListWithoutNulls.get(i).doubleValue();
					if (currentValue > floatingPointMax) {
						floatingPointMax = currentValue;
					}
				}
				return convert(floatingPointMax, numberClass);
			} else if (isIntegerNumber(numberClass)) {
				long integerMax = first.longValue();
				for (int i = 1; i < inputListWithoutNulls.size(); i++) {
					long currentValue = inputListWithoutNulls.get(i).longValue();
					if (currentValue > integerMax) {
						integerMax = currentValue;
					}
				}
				return convert(integerMax, numberClass);
			} else {
				throw new IllegalArgumentException(
						"Cannot calculate max of objects of type: " + first.getClass().getName());
			}
		}
	}

	private static boolean isFloatingPointNumber(Class<?> numberClass) {
		return Float.class.isAssignableFrom(numberClass) || Double.class.isAssignableFrom(numberClass);
	}

	private static boolean isIntegerNumber(Class<?> numberClass) {
		return Byte.class.isAssignableFrom(numberClass) || Short.class.isAssignableFrom(numberClass)
				|| Integer.class.isAssignableFrom(numberClass) || Long.class.isAssignableFrom(numberClass);
	}

	private static Number convert(Number number, Class<?> targetClass) {
		if (Byte.class.isAssignableFrom(targetClass)) {
			return number.byteValue();
		} else if (Short.class.isAssignableFrom(targetClass)) {
			return number.shortValue();
		} else if (Integer.class.isAssignableFrom(targetClass)) {
			return number.intValue();
		} else if (Long.class.isAssignableFrom(targetClass)) {
			return number.longValue();
		} else if (Float.class.isAssignableFrom(targetClass)) {
			return number.floatValue();
		} else if (Double.class.isAssignableFrom(targetClass)) {
			return number.doubleValue();
		} else {
			throw new IllegalArgumentException("target class is not a number class");
		}
	}
}
