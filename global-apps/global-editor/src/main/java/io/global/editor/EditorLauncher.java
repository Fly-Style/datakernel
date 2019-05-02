package io.global.editor;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.datakernel.config.Config;
import io.datakernel.config.ConfigModule;
import io.datakernel.http.AsyncHttpServer;
import io.datakernel.launcher.Launcher;
import io.datakernel.service.ServiceGraphModule;
import io.global.LocalNodeCommonModule;
import io.global.editor.document.DocumentMultiOperation;
import io.global.launchers.GlobalNodesModule;
import io.global.ot.SharedRepoModule;
import io.global.ot.friendlist.ContactsModule;
import io.global.ot.service.UserContainerModule;
import io.global.ot.shared.IndexRepoModule;
import io.global.ot.stub.CommitStorageStub;
import io.global.pm.MapMessageStorage;
import io.global.pm.api.MessageStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.google.inject.util.Modules.override;
import static io.datakernel.config.Config.ofProperties;
import static java.lang.Boolean.parseBoolean;
import static java.util.Arrays.asList;

public final class EditorLauncher extends Launcher {
	private static final String EAGER_SINGLETONS_MODE = "eagerSingletonsMode";
	private static final String PROPERTIES_FILE = "editor.properties";
	private static final String DEFAULT_LISTEN_ADDRESSES = "*:8080";
	private static final String DEFAULT_SERVER_ID = "Global Editor";
	private static final String DOCUMENT_REPO_PREFIX = "editor/document";
	private static final String EDITOR_INDEX_REPO = "editor/index";

	@Inject
	@Named("Editor")
	AsyncHttpServer server;

	@Override
	public Collection<com.google.inject.Module> getModules() {
		return asList(
				ServiceGraphModule.defaultInstance(),
				ConfigModule.create(() ->
						Config.create()
								.with("http.listenAddresses", DEFAULT_LISTEN_ADDRESSES)
								.with("node.serverId", DEFAULT_SERVER_ID)
								.override(Config.ofProperties(PROPERTIES_FILE, true))
								.override(ofProperties(System.getProperties()).getChild("config")))
						.printEffectiveConfig(),
				new EditorModule(),
				new ContactsModule(),
				new IndexRepoModule(EDITOR_INDEX_REPO),
				new SharedRepoModule<DocumentMultiOperation>(DOCUMENT_REPO_PREFIX) {},
				new UserContainerModule<DocumentMultiOperation>(EDITOR_INDEX_REPO, DOCUMENT_REPO_PREFIX) {},
				// override for debug purposes
				override(new GlobalNodesModule())
						.with(new LocalNodeCommonModule(DEFAULT_SERVER_ID))
		);
	}

	@Override
	public void run() throws Exception {
		awaitShutdown();
	}

	public static void main(String[] args) throws Exception {
		new EditorLauncher().launch(parseBoolean(System.getProperty(EAGER_SINGLETONS_MODE)), args);
	}
}
