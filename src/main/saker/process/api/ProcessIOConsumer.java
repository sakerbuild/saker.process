package saker.process.api;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface ProcessIOConsumer extends Closeable {
	public void handleOutput(ByteBuffer bytes) throws IOException;

	/**
	 * Releases this IO consumer.
	 * <p>
	 * Closing a process IO consumer will signal that it won't be used anymore. It should release any resources that it
	 * manages.
	 */
	@Override
	public default void close() throws IOException {
	}
}
