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

package io.datakernel.serializer;

import io.datakernel.asm.DefiningClassLoader;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SerializeStreamTest {

	@Test
	public void test() throws IOException {
		BufferSerializer<String> bufferSerializer = SerializerBuilder.newDefaultInstance(new DefiningClassLoader())
				.create(String.class);

		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		SerializeOutputStream<String> serializeOutputStream =
				new SerializeOutputStream<>(byteOutputStream, bufferSerializer, 30, 10);

		String[] strings = new String[] {"01234567890123456789", "42", "--1", "007"};
		for (String string : strings) {
			serializeOutputStream.write(string);
		}
		serializeOutputStream.close();

		ByteArrayInputStream byteInputStream = new ByteArrayInputStream(byteOutputStream.toByteArray());
		DeserializeInputStream<String> deserializeInputStream =
				new DeserializeInputStream<>(byteInputStream, bufferSerializer, 30, 10);

		for (String string : strings) {
			assertEquals(string, deserializeInputStream.read());
		}
		assertNull(deserializeInputStream.read());
	}
}
