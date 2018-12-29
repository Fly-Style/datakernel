package io.datakernel.csp.process;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufQueue;
import io.datakernel.bytebuf.ByteBufStrings;
import io.datakernel.csp.ChannelConsumer;
import io.datakernel.csp.ChannelSupplier;
import io.datakernel.stream.processor.DatakernelRunner;
import net.jpountz.lz4.LZ4Factory;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.datakernel.async.TestUtils.await;
import static io.datakernel.async.TestUtils.awaitException;
import static io.datakernel.csp.binary.BinaryChannelSupplier.UNEXPECTED_DATA_EXCEPTION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertSame;

@RunWith(DatakernelRunner.class)
public class ChannelLZ4DecompressorTest {

	@Test
	public void testTruncatedData() {
		ChannelLZ4Compressor compressor = ChannelLZ4Compressor.create(LZ4Factory.fastestInstance().fastCompressor());
		ChannelLZ4Decompressor decompressor = ChannelLZ4Decompressor.create();
		ByteBufQueue queue = new ByteBufQueue();

		await(ChannelSupplier.of(ByteBufStrings.wrapAscii("TestData")).transformWith(compressor)
				.streamTo(ChannelConsumer.ofConsumer(queue::add)));

		// add trailing 0 - bytes
		queue.add(ByteBuf.wrapForReading(new byte[10]));

		Throwable e = awaitException(ChannelSupplier.of(queue.takeRemaining())
				.transformWith(decompressor)
				.streamTo(ChannelConsumer.ofConsumer(data -> System.out.println(data.asString(UTF_8)))));

		assertSame(UNEXPECTED_DATA_EXCEPTION, e);
	}
}
