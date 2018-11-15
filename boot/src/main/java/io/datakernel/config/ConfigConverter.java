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

package io.datakernel.config;

import io.datakernel.annotation.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

import static io.datakernel.util.Preconditions.checkArgument;

public interface ConfigConverter<T> {
	T get(Config config, @Nullable T defaultValue);

	T get(Config config);

	/**
	 * Applies given converter function to the converted value
	 *
	 * @param to   converter from T to V
	 * @param from converter from V to T
	 * @param <V>  return type
	 * @return converter that knows how to get V value from T value saved in config
	 */
	default <V> ConfigConverter<V> transform(Function<T, V> to, Function<V, T> from) {
		ConfigConverter<T> thisConverter = this;
		return new ConfigConverter<V>() {
			@Override
			@Nullable
			public V get(Config config, @Nullable V defaultValue) {
				T value = thisConverter.get(config, defaultValue == null ? null : from.apply(defaultValue));
				return value != null ? to.apply(value) : null;
			}

			@Override
			@Nullable
			public V get(Config config) {
				return to.apply(thisConverter.get(config));
			}
		};
	}

	default ConfigConverter<T> withConstraint(Predicate<T> predicate) {
		ConfigConverter<T> thisConverter = this;
		return new ConfigConverter<T>() {
			@Override
			public T get(Config config, T defaultValue) {
				T value = thisConverter.get(config, defaultValue);
				checkArgument(predicate.test(value), "Predicate has returned false");
				return value;
			}

			@Override
			public T get(Config config) {
				T t = thisConverter.get(config);
				checkArgument(predicate.test(t), "Predicate has returned false");
				return t;
			}
		};
	}
}
