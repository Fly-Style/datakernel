import io.datakernel.async.Promise;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.config.Config;
import io.datakernel.config.ConfigModule;
import io.datakernel.di.annotation.Inject;
import io.datakernel.di.annotation.Provides;
import io.datakernel.di.core.Key;
import io.datakernel.di.module.Module;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.launcher.Launcher;
import io.datakernel.launcher.OnStart;
import io.datakernel.memcache.client.MemcacheClient;
import io.datakernel.memcache.client.MemcacheClientModule;
import io.datakernel.service.ServiceGraphModule;

import java.util.concurrent.CompletionStage;

import static io.datakernel.di.module.Modules.combine;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author is Alex Syrotenko (@pantokrator)
 * Created on 06.08.19.
 */
public class MemcacheLikeClient extends Launcher {
	@Provides
	Eventloop eventloop() {
		return Eventloop.create();
	}

	@Provides
	Config config() {
		return Config.create()
				.with("protocol.compression", "false")
				.with("server.listenAddresses", "localhost:8080")
				.with("client.addresses", "localhost:8080")
				.overrideWith(Config.ofProperties(System.getProperties()));
	}

	@Inject
	MemcacheClient client;

	@Inject
	Eventloop eventloop;

	@Override
	protected Module getModule() {
		return combine(ServiceGraphModule.create(),
				ConfigModule.create()
						.printEffectiveConfig()
						.rebindImport(new Key<CompletionStage<Void>>() {}, new Key<CompletionStage<Void>>(OnStart.class) {}),
				new MemcacheClientModule());
	}

	@Override
	protected void run() {
		byte[][] bytes = new byte[15][1];
		for (int i = 0; i < 15; ++i) {
			final int idx = i;
			bytes[idx] = new byte[] { (byte) idx };
			byte[] message = "Strong and smart people always be successful".getBytes();
			Promise<Void> put = client.put(bytes[idx], ByteBuf.wrapForReading(message), 50);
			put.whenComplete((res, e) ->  {
				if (e != null) {
					e.printStackTrace();
				}
				System.out.println("Request sent");
				Promise<ByteBuf> byteBufPromise = client.get(bytes[idx], 20);
				byteBufPromise.whenResult(resp -> System.out.println("Got back from [" + idx + "] : " + resp.getString(UTF_8)));
			} );
		}

		eventloop.run();
	}

	public static void main(String[] args) throws Exception {
		Launcher client = new MemcacheLikeClient();
		client.launch(args);
	}
}