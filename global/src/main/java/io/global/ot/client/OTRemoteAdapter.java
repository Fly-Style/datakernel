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

package io.global.ot.client;

import io.datakernel.async.Stage;
import io.datakernel.ot.OTCommit;
import io.datakernel.ot.OTRemote;
import io.global.ot.api.CommitId;
import io.global.ot.api.RepositoryName;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.datakernel.util.CollectionUtils.union;
import static java.util.Collections.singleton;

public final class OTRemoteAdapter<D> implements OTRemote<CommitId, D> {
	private final OTDriver driver;
	private final MyRepositoryId<D> myRepositoryId;
	private final Set<RepositoryName> originRepositoryIds;

	public OTRemoteAdapter(OTDriver driver,
			MyRepositoryId<D> myRepositoryId, Set<RepositoryName> originRepositoryIds) {
		this.driver = driver;
		this.myRepositoryId = myRepositoryId;
		this.originRepositoryIds = originRepositoryIds;
	}

	@Override
	public Stage<OTCommit<CommitId, D>> createCommit(Map<CommitId, ? extends List<? extends D>> parentDiffs, long level) {
		return Stage.of(driver.createCommit(myRepositoryId, parentDiffs, level));
	}

	@Override
	public Stage<Void> push(OTCommit<CommitId, D> commit) {
		return driver.push(myRepositoryId, commit);
	}

	@Override
	public Stage<Set<CommitId>> getHeads() {
		return driver.getHeads(union(singleton(myRepositoryId.getRepositoryId()), originRepositoryIds));
	}

	@Override
	public Stage<OTCommit<CommitId, D>> loadCommit(CommitId revisionId) {
		return driver.loadCommit(myRepositoryId, originRepositoryIds, revisionId);
	}

	@Override
	public Stage<Optional<List<D>>> loadSnapshot(CommitId revisionId) {
		return driver.loadSnapshot(myRepositoryId, originRepositoryIds, revisionId);
	}

	@Override
	public Stage<Void> saveSnapshot(CommitId revisionId, List<D> diffs) {
		return driver.saveSnapshot(myRepositoryId, revisionId, diffs);
	}
}