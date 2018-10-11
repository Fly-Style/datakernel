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

package io.datakernel.serializer.asm;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("PointlessArithmeticExpression")
public final class SerializerGenList extends AbstractSerializerGenCollection {

	// region creators
	public SerializerGenList(SerializerGen valueSerializer, boolean nullable) {
		super(valueSerializer, List.class, ArrayList.class, Object.class, nullable);
	}

	public SerializerGenList(SerializerGen valueSerializer) {
		this(valueSerializer, false);
	}
	// endregion

	@Override
	public SerializerGen asNullable() {
		return new SerializerGenList(valueSerializer, true);
	}
}
