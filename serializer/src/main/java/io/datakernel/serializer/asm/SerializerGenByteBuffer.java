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

package io.datakernel.serializer.asm;

import io.datakernel.codegen.Expression;
import io.datakernel.codegen.ExpressionSequence;
import io.datakernel.codegen.Variable;
import io.datakernel.serializer.CompatibilityLevel;
import io.datakernel.serializer.NullableOptimization;
import io.datakernel.serializer.SerializerBuilder;
import io.datakernel.serializer.SerializerUtils;

import java.nio.ByteBuffer;

import static io.datakernel.codegen.Expressions.*;

public class SerializerGenByteBuffer implements SerializerGen, NullableOptimization {
	private final boolean wrapped;
	private final boolean nullable;

	public SerializerGenByteBuffer() {
		this(false);
	}

	public SerializerGenByteBuffer(boolean wrapped) {
		this.wrapped = wrapped;
		this.nullable = false;
	}

	public SerializerGenByteBuffer(boolean wrapped, boolean nullable) {
		this.wrapped = wrapped;
		this.nullable = nullable;
	}

	@Override
	public void getVersions(VersionsCollector versions) {
	}

	@Override
	public boolean isInline() {
		return true;
	}

	@Override
	public Class<?> getRawType() {
		return ByteBuffer.class;
	}

	@Override
	public void prepareSerializeStaticMethods(int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {

	}

	@Override
	public Expression serialize(Expression byteArray, Variable off, Expression value, int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {
		value = let(cast(value, ByteBuffer.class));
		Expression array = call(value, "array");
		Expression position = call(value, "position");
		Expression remaining = let(call(value, "remaining"));
		Expression writeLength = set(off, callStatic(SerializerUtils.class, "writeVarInt", byteArray, off, (!nullable ? remaining : inc(remaining))));
		ExpressionSequence write = sequence(writeLength, callStatic(SerializerUtils.class, "write", array, position, byteArray, off, remaining));

		if (!nullable) {
			return write;
		} else {
			return choice(isNull(value),
					callStatic(SerializerUtils.class, "writeVarInt", byteArray, off, value(0)),
					write);
		}
	}

	@Override
	public void prepareDeserializeStaticMethods(int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {

	}

	@Override
	public Expression deserialize(Class<?> targetType, int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {
		Expression length = let(call(arg(0), "readVarInt"));

		if (!wrapped) {
			if (!nullable) {
				Expression array = let(newArray(byte[].class, length));
				return sequence(length, call(arg(0), "read", array), callStatic(ByteBuffer.class, "wrap", array));
			} else {
				Expression array = let(newArray(byte[].class, dec(length)));
				return choice(cmpEq(length, value(0)),
						nullRef(ByteBuffer.class),
						sequence(length, call(arg(0), "read", array), callStatic(ByteBuffer.class, "wrap", array)));
			}
		} else {
			Expression inputBuffer = call(arg(0), "array");
			Expression position = let(call(arg(0), "position"));
			Expression setPosition = call(arg(0), "position", add(position, (!nullable ? length : dec(length))));

			if (!nullable) {
				return sequence(length, setPosition, callStatic(ByteBuffer.class, "wrap", inputBuffer, position, length));
			} else {
				return choice(cmpEq(length, value(0)),
						nullRef(ByteBuffer.class),
						sequence(length, setPosition, callStatic(ByteBuffer.class, "wrap", inputBuffer, position, dec(length))));
			}

		}
	}

	@Override
	public SerializerGen setNullable() {
		return new SerializerGenByteBuffer(wrapped, true);
	}
}
