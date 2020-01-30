package saker.process.api;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import saker.build.file.path.SakerPath;
import saker.process.impl.RedirectFileProcessIOConsumer;

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

	public static ProcessIOConsumer redirectFile(SakerPath localfilepath) throws NullPointerException {
		Objects.requireNonNull(localfilepath, "redirect file path");
		return new RedirectFileProcessIOConsumer(localfilepath);
	}
}
