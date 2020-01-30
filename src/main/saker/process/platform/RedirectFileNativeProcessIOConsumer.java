package saker.process.platform;

import java.io.IOException;
import java.nio.ByteBuffer;

import saker.build.file.path.SakerPath;

public final class RedirectFileNativeProcessIOConsumer implements NativeProcessIOConsumer {
	private final NativeProcessIOConsumer consumer;
	private final SakerPath path;

	RedirectFileNativeProcessIOConsumer(NativeProcessIOConsumer consumer, SakerPath path) {
		this.consumer = consumer;
		this.path = path;
	}

	@Override
	public void handleOutput(ByteBuffer bytes) throws IOException {
		consumer.handleOutput(bytes);
	}

	public SakerPath getPath() {
		return path;
	}
}