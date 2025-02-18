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

package io.datakernel.util;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public final class JmxUtils {

	private JmxUtils() {
	}

	public static Map<String, Map<String, String>> createDescriptionMap(String name, @Nullable String description) {
		if (description != null) {
			return Collections.singletonMap(name, Collections.singletonMap(name, description));
		} else {
			return Collections.singletonMap(name, Collections.emptyMap());
		}
	}

}
