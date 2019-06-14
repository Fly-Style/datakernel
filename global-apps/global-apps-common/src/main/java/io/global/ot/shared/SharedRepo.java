package io.global.ot.shared;

import io.global.common.PubKey;

import java.util.Set;

public final class SharedRepo {
	private final String id;
	private final Set<PubKey> participants;

	public SharedRepo(String id, Set<PubKey> participants) {
		this.id = id;
		this.participants = participants;
	}

	public String getId() {
		return id;
	}

	public Set<PubKey> getParticipants() {
		return participants;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		SharedRepo sharedRepo = (SharedRepo) o;

		if (!id.equals(sharedRepo.id)) return false;
		if (!participants.equals(sharedRepo.participants)) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = id.hashCode();
		result = 31 * result + participants.hashCode();
		return result;
	}
}