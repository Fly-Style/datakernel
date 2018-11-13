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

package io.global.fs.api;

import io.datakernel.exception.ParseException;
import io.global.common.CryptoUtils;
import io.global.common.PubKey;
import io.global.common.SignedData;
import org.spongycastle.crypto.digests.SHA256Digest;

import java.util.Arrays;

public final class GlobalFsCheckpoint {
	private final String filename;
	private final long position;
	private final SHA256Digest digest;

	private GlobalFsCheckpoint(String filename, long position, SHA256Digest digest) {
		this.filename = filename;
		this.position = position;
		this.digest = digest;
	}

	public static GlobalFsCheckpoint parse(String filename, long position, byte[] digestState) throws ParseException {
		return new GlobalFsCheckpoint(filename, position, CryptoUtils.ofSha256PackedState(digestState, position));
	}

	public static GlobalFsCheckpoint of(String filename, long position, SHA256Digest digest) {
		return new GlobalFsCheckpoint(filename, position, digest);
	}

	public String getFilename() {
		return filename;
	}

	public long getPosition() {
		return position;
	}

	public SHA256Digest getDigest() {
		return digest;
	}

	public byte[] getDigestState() {
		return CryptoUtils.toSha256PackedState(digest);
	}

	public enum CheckpointVerificationResult {
		SIGNATURE_FAIL("can't verify checkpoint signature"),
		FILENAME_FAIL("wrong checkpoint filename"),
		POSITION_FAIL("wrong checkpoint position"),
		CHECKSUM_FAIL("wrong checksum"),
		SUCCESS("");

		public final String message;

		CheckpointVerificationResult(String message) {
			this.message = message;
		}
	}

	public static CheckpointVerificationResult verify(SignedData<GlobalFsCheckpoint> signedCheckpoint, PubKey pubKey, String filename, long position, SHA256Digest digest) {
		if (!signedCheckpoint.verify(pubKey)) {
			return CheckpointVerificationResult.SIGNATURE_FAIL;
		}
		GlobalFsCheckpoint checkpoint = signedCheckpoint.getValue();
		if (!filename.equals(checkpoint.filename)) {
			return CheckpointVerificationResult.FILENAME_FAIL;
		}
		if (checkpoint.position != position) {
			return CheckpointVerificationResult.POSITION_FAIL;
		}
		if (!CryptoUtils.areEqual(checkpoint.digest, digest)) {
			return CheckpointVerificationResult.CHECKSUM_FAIL;
		}
		return CheckpointVerificationResult.SUCCESS;
	}

	public static int[] getExtremes(long[] positions, long offset, long length) {
		assert positions.length >= 1 && positions[0] == 0 : Arrays.toString(positions);
		assert offset >= 0 && length >= -1;

		long endOffset = length == -1 ? positions[positions.length - 1] : offset + length;

		int start = positions.length - 1;
		int finish = 0;

		boolean foundStart = false;
		boolean foundFinish = false;

		for (int i = 0, j = positions.length - 1; i < positions.length - 1; i++, j--) {
			if (!foundStart && positions[i + 1] > offset) {
				start = i;
				foundStart = true;
				if (foundFinish) {
					break;
				}
			}
			if (!foundFinish && positions[j - 1] < endOffset) {
				finish = j;
				foundFinish = true;
				if (foundStart) {
					break;
				}
			}
		}
		return new int[]{start, finish};
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GlobalFsCheckpoint that = (GlobalFsCheckpoint) o;
		if (position != that.position) return false;
		if (!filename.equals(that.filename)) return false;
		return digest.equals(that.digest);
	}

	@Override
	public int hashCode() {
		int result = filename.hashCode();
		result = 31 * result + (int) (position ^ (position >>> 32));
		result = 31 * result + digest.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "GlobalFsCheckpoint{filename='" + filename + '\'' + ", position=" + position + ", digest=@" + Integer.toHexString(Arrays.hashCode(digest.getEncodedState())) + '}';
	}
}
