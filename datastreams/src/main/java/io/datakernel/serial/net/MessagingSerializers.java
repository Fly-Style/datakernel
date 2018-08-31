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

package io.datakernel.serial.net;

import com.google.gson.TypeAdapter;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.bytebuf.ByteBufStrings;
import io.datakernel.exception.ParseException;
import io.datakernel.util.MemSize;

import java.io.IOException;

import static io.datakernel.bytebuf.ByteBufStrings.putUtf8;
import static io.datakernel.util.gson.GsonAdapters.fromJson;
import static io.datakernel.util.gson.GsonAdapters.toJson;

@SuppressWarnings("ThrowableInstanceNeverThrown, WeakerAccess")
public final class MessagingSerializers {
	public static final ParseException DESERIALIZE_ERR = new ParseException("Can't deserialize message");

	private MessagingSerializers() {
	}

	public static <IO> MessagingSerializer<IO, IO> ofJson(TypeAdapter<IO> io) {
		return ofJson(io, io);
	}

	public static <I, O> MessagingSerializer<I, O> ofJson(TypeAdapter<I> in, TypeAdapter<O> out) {
		return new MessagingSerializer<I, O>() {
			@Override
			public I tryDeserialize(ByteBuf buf) throws ParseException {
				for (int len = 0; len < buf.readRemaining(); len++) {
					if (buf.peek(len) == '\0') {
						try {
							I item = fromJson(in, ByteBufStrings.decodeUtf8(buf.array(), buf.readPosition(), len));
							buf.moveReadPosition(len + 1); // skipping msg + delimiter
							return item;
						} catch (IOException e) {
							throw DESERIALIZE_ERR;
						}
					}
				}
				return null;
			}

			@Override
			public ByteBuf serialize(O item) {
				ByteBufPoolAppendable appendable = new ByteBufPoolAppendable();
				try {
					toJson(out, item, appendable);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				appendable.append("\0");
				return appendable.get();
			}
		};
	}

	static class ByteBufPoolAppendable implements Appendable {
		static final MemSize INITIAL_BUF_SIZE = MemSize.kilobytes(2);
		ByteBuf container;

		ByteBufPoolAppendable() {
			this(INITIAL_BUF_SIZE);
		}

		ByteBufPoolAppendable(MemSize size) {
			this.container = ByteBufPool.allocate(size);
		}

		ByteBufPoolAppendable(int size) {
			this.container = ByteBufPool.allocate(size);
		}

		@Override
		public Appendable append(CharSequence csq) {
			container = ByteBufPool.ensureWriteRemaining(container, csq.length() * 3);
			for (int i = 0; i < csq.length(); i++) {
				putUtf8(container, csq.charAt(i));
			}
			return this;
		}

		@Override
		public Appendable append(CharSequence csq, int start, int end) {
			return append(csq.subSequence(start, end));
		}

		@Override
		public Appendable append(char c) {
			container = ByteBufPool.ensureWriteRemaining(container, 3);
			putUtf8(container, c);
			return this;
		}

		public ByteBuf get() {
			return container;
		}
	}
}