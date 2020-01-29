package saker.process.api;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public interface SakerProcess extends Closeable {
	public Integer exitValue() throws IllegalStateException, IOException;

	public int waitFor() throws IllegalStateException, InterruptedException, IOException;

	public boolean waitFor(long timeout, TimeUnit unit) throws IllegalStateException, InterruptedException, IOException;

	public void processIO(ProcessIOConsumer stdouthandler, ProcessIOConsumer stderrhandler)
			throws IllegalStateException, IOException;

	/**
	 * Releases the native resources for this process.
	 * <p>
	 * <b>Note</b>: closing doesn't destroy the created process. It only releases the native resources that are used to
	 * deal with the started process. If you need to destroy the process, you shoul do that before closing this
	 * instance.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@Override
	public void close() throws IOException;
}
