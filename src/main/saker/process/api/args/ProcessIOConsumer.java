package saker.process.api.args;

import java.nio.ByteBuffer;

public interface ProcessIOConsumer {
	public void handleOutput(ByteBuffer bytes) throws Exception;
}
