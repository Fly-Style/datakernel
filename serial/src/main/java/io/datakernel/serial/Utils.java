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

package io.datakernel.serial;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.exception.ParseException;

class Utils {
	private Utils() {}

	static ByteBufsParser<ByteBuf> parseUntilTerminatorByte(byte terminator, int maxSize) {
		return bufs -> {
			for (int i = 0; i < Math.min(bufs.remainingBytes(), maxSize); i++) {
				if (bufs.peekByte(i) == terminator) {
					ByteBuf buf = bufs.takeExactSize(i);
					bufs.skip(1);
					return buf;
				}
			}
			if (bufs.remainingBytes() >= maxSize) throw new ParseException(ByteBufsParser.class);
			return null;
		};
	}

}
