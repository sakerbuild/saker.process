package saker.process.platform;

import java.io.IOException;
import java.nio.ByteBuffer;

import saker.build.file.path.SakerPath;

public interface NativeProcessIOConsumer {
	public void handleOutput(ByteBuffer bytes) throws IOException;

	public static NativeProcessIOConsumer redirectFile(SakerPath path, NativeProcessIOConsumer consumer) {
		return new RedirectFileNativeProcessIOConsumer(consumer, path);
	}
}
