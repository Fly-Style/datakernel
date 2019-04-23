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
package io.datakernel.examples;

// [START EXAMPLE]
import io.datakernel.serializer.annotations.Deserialize;
import io.datakernel.serializer.annotations.Serialize;

public class PutRequest {

	private final String key;
	private final String value;

	public PutRequest(@Deserialize("key") String key, @Deserialize("value") String value) {
		this.key = key;
		this.value = value;
	}

	@Serialize(order = 0)
	public String getKey() {
		return key;
	}

	@Serialize(order = 1)
	public String getValue() {
		return value;
	}
}
// [END EXAMPLE]
