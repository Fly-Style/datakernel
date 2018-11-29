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

package io.global.fs.launchers;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.datakernel.async.EventloopTaskScheduler;
import io.datakernel.config.Config;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.http.AsyncHttpClient;
import io.datakernel.remotefs.FsClient;
import io.global.common.RawServerId;
import io.global.common.api.DiscoveryService;
import io.global.common.api.NodeFactory;
import io.global.common.discovery.HttpDiscoveryService;
import io.global.fs.api.GlobalFsNode;
import io.global.fs.http.HttpGlobalFsNode;
import io.global.fs.local.LocalGlobalFsNode;

import java.net.InetSocketAddress;
import java.util.HashSet;

import static io.datakernel.config.ConfigConverters.*;
import static io.global.fs.launchers.GlobalFsConfigConverters.ofPubKey;
import static io.global.fs.local.LocalGlobalFsNode.DEFAULT_LATENCY_MARGIN;

public class HttpGlobalFsNodeModule extends AbstractModule {

	private HttpGlobalFsNodeModule() {
	}

	public static HttpGlobalFsNodeModule create() {
		return new HttpGlobalFsNodeModule();
	}

	@Override
	protected void configure() {
		bind(GlobalFsNode.class).to(LocalGlobalFsNode.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	DiscoveryService provide(Config config, AsyncHttpClient client) {
		return HttpDiscoveryService.create(config.get(ofInetSocketAddress(), "globalfs.discoveryService"), client);
	}

	@Provides
	@Singleton
	NodeFactory<GlobalFsNode> provide(AsyncHttpClient httpClient) {
		return serverId -> {
			int port = Integer.parseInt(serverId.getServerIdString().split(":")[1]);
			return new HttpGlobalFsNode(httpClient, new InetSocketAddress(port));
		};
	}

	@Provides
	@Singleton
	LocalGlobalFsNode provide(Config config, DiscoveryService discoveryService, NodeFactory<GlobalFsNode> nodeFactory, FsClient storage) {
		RawServerId id = new RawServerId(config.get(ofString(), "globalfs.http.listenAddresses"));
		return LocalGlobalFsNode.create(id, discoveryService, nodeFactory, storage)
				.withManagedPubKeys(new HashSet<>(config.get(ofList(ofPubKey()), "globalfs.managedPubKeys")))
				.withDownloadCaching(config.get(ofBoolean(), "globalfs.caching.download", true))
				.withUploadCaching(config.get(ofBoolean(), "globalfs.caching.upload", false))
				.withLatencyMargin(config.get(ofDuration(), "globalfs.fetching.latencyMargin", DEFAULT_LATENCY_MARGIN));
	}

	@Provides
	@Singleton
	EventloopTaskScheduler provide(Config config, Eventloop eventloop, LocalGlobalFsNode node) {
		return EventloopTaskScheduler.create(eventloop, node::fetch)
				.initialize(Initializers.ofEventloopTaskScheduler(config.getChild("globalfs.fetching")));
	}
}
