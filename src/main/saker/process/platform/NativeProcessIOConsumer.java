package saker.process.platform;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface NativeProcessIOConsumer {
	public void handleOutput(ByteBuffer bytes) throws IOException;
}
