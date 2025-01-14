package io.global.ot.service;

import io.datakernel.async.Promise;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.EventloopService;
import io.datakernel.ot.*;
import io.global.ot.api.CommitId;
import io.global.ot.api.RepoID;
import io.global.ot.client.MyRepositoryId;
import io.global.ot.client.OTDriver;
import io.global.ot.client.OTRepositoryAdapter;
import io.global.ot.service.messaging.CreateSharedRepo;
import io.global.ot.service.messaging.MessagingService;
import io.global.ot.service.synchronization.SynchronizationService;
import io.global.ot.shared.SharedReposOTState;
import io.global.ot.shared.SharedReposOperation;
import io.global.pm.GlobalPmDriver;
import org.jetbrains.annotations.NotNull;

import static io.global.ot.OTUtils.POLL_RETRY_POLICY;
import static io.global.ot.OTUtils.SHARED_REPO_OPERATION_CODEC;
import static io.global.ot.service.UserContainerHolder.LIST_SYSTEM;
import static java.util.Collections.emptySet;

public final class UserContainer<D> implements EventloopService {
	private final Eventloop eventloop;
	private final MyRepositoryId<D> myRepositoryId;
	private final OTStateManager<CommitId, SharedReposOperation> stateManager;

	private final SynchronizationService<D> synchronizationService;
	private final MessagingService messagingService;

	private UserContainer(Eventloop eventloop, MyRepositoryId<D> myRepositoryId, OTDriver driver, OTSystem<D> otSystem,
			OTStateManager<CommitId, SharedReposOperation> stateManager, GlobalPmDriver<CreateSharedRepo> pmDriver,
			String indexRepoName) {
		this.eventloop = eventloop;
		this.myRepositoryId = myRepositoryId;
		this.stateManager = stateManager;
		this.synchronizationService = SynchronizationService.create(eventloop, driver, this, otSystem);
		this.messagingService = MessagingService.create(eventloop, pmDriver, this, indexRepoName);
	}

	public static <D> UserContainer<D> create(Eventloop eventloop, OTDriver driver, OTSystem<D> otSystem, MyRepositoryId<D> myRepositoryId,
			GlobalPmDriver<CreateSharedRepo> pmDriver, String indexRepoName) {
		OTStateManager<CommitId, SharedReposOperation> stateManager = createStateManager(eventloop, driver, myRepositoryId, indexRepoName);
		return new UserContainer<>(eventloop, myRepositoryId, driver, otSystem, stateManager, pmDriver, indexRepoName);
	}

	@NotNull
	@Override
	public Eventloop getEventloop() {
		return eventloop;
	}

	@NotNull
	@Override
	public Promise<Void> start() {
		return Promise.complete()
				.then($ -> stateManager.start())
				.then($ -> synchronizationService.start())
				.then($ -> messagingService.start());
	}

	@NotNull
	@Override
	public Promise<Void> stop() {
		return Promise.complete()
				.then($ -> stateManager.stop())
				.then($ -> synchronizationService.stop())
				.then($ -> messagingService.stop());
	}

	public MyRepositoryId<D> getMyRepositoryId() {
		return myRepositoryId;
	}

	public SharedReposOTState getResourceListState() {
		return (SharedReposOTState) stateManager.getState();
	}

	public OTStateManager<CommitId, SharedReposOperation> getStateManager() {
		return stateManager;
	}

	public SynchronizationService<D> getSynchronizationService() {
		return synchronizationService;
	}

	public MessagingService getMessagingService() {
		return messagingService;
	}

	private static <D> OTStateManager<CommitId, SharedReposOperation> createStateManager(Eventloop eventloop, OTDriver driver,
			MyRepositoryId<D> myRepositoryId, String resourceListRepoName) {
		RepoID repoID = RepoID.of(myRepositoryId.getPrivKey(), resourceListRepoName);
		MyRepositoryId<SharedReposOperation> listRepositoryId = new MyRepositoryId<>(repoID, myRepositoryId.getPrivKey(), SHARED_REPO_OPERATION_CODEC);
		OTRepositoryAdapter<SharedReposOperation> repository = new OTRepositoryAdapter<>(driver, listRepositoryId, emptySet());
		OTState<SharedReposOperation> state = new SharedReposOTState();
		OTNodeImpl<CommitId, SharedReposOperation, OTCommit<CommitId, SharedReposOperation>> node = OTNodeImpl.create(repository, LIST_SYSTEM);
		return OTStateManager.create(eventloop, LIST_SYSTEM, node, state)
				.withPoll(POLL_RETRY_POLICY);
	}
}
