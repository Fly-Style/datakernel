package io.global.chat;

import io.datakernel.config.Config;
import io.datakernel.config.ConfigModule;
import io.datakernel.di.annotation.Inject;
import io.datakernel.di.annotation.Named;
import io.datakernel.di.module.Module;
import io.datakernel.di.module.Modules;
import io.datakernel.http.AsyncHttpServer;
import io.datakernel.launcher.Launcher;
import io.datakernel.service.ServiceGraphModule;
import io.global.LocalNodeCommonModule;
import io.global.chat.chatroom.messages.MessageOperation;
import io.global.launchers.GlobalNodesModule;
import io.global.ot.ProfileModule;
import io.global.ot.SharedRepoModule;
import io.global.ot.contactlist.ContactsModule;
import io.global.ot.service.UserContainerModule;
import io.global.ot.shared.IndexRepoModule;

import static io.datakernel.config.Config.ofProperties;
import static io.datakernel.di.module.Modules.override;

public final class ChatLauncher extends Launcher {
	private static final String PROPERTIES_FILE = "chat.properties";
	private static final String DEFAULT_LISTEN_ADDRESSES = "*:8080";
	private static final String DEFAULT_SERVER_ID = "Global Chat";
	private static final String CHAT_REPO_PREFIX = "chat/room";
	private static final String CHAT_INDEX_REPO = "chat/index";

	@Inject
	@Named("Chat")
	AsyncHttpServer server;

	@Override
	public Module getModule() {
		return Modules.combine(
				ServiceGraphModule.defaultInstance(),
				ConfigModule.create(() ->
						Config.create()
								.with("http.listenAddresses", DEFAULT_LISTEN_ADDRESSES)
								.with("node.serverId", DEFAULT_SERVER_ID)
								.override(Config.ofProperties(PROPERTIES_FILE, true))
								.override(ofProperties(System.getProperties()).getChild("config")))
						.printEffectiveConfig(),
				new ChatModule(),
				new ProfileModule(),
				new ContactsModule(),
				new IndexRepoModule(CHAT_INDEX_REPO),
				new UserContainerModule<MessageOperation>(CHAT_INDEX_REPO, CHAT_REPO_PREFIX) {},
				new SharedRepoModule<MessageOperation>(CHAT_REPO_PREFIX) {},
				// override for debug purposes
				override(new GlobalNodesModule(),
						new LocalNodeCommonModule(DEFAULT_SERVER_ID))
		);
	}

	@Override
	public void run() throws Exception {
		awaitShutdown();
	}

	public static void main(String[] args) throws Exception {
		new ChatLauncher().launch(args);
	}
}