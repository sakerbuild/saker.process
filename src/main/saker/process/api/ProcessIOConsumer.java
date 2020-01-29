package saker.process.api;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ProcessIOConsumer {
	public void handleOutput(ByteBuffer bytes) throws IOException;
}
