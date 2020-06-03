package saker.process.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.process.api.ProcessIOConsumer;

/**
 * {@link ProcessIOConsumer} that collects the bytes in an internal buffer.
 * <p>
 * The consumer will write the bytes into an internal byte stream. The bytes can be retrieved using one of the methods
 * in the class.
 * <p>
 * The class is not safe to access from multiple threads.
 * 
 * @since saker.process 0.8.1
 */
public class CollectingProcessIOConsumer implements ProcessIOConsumer {
	private UnsyncByteArrayOutputStream out = new UnsyncByteArrayOutputStream();

	/**
	 * Creates a new instance.
	 */
	public CollectingProcessIOConsumer() {
	}

	@Override
	public void handleOutput(ByteBuffer bytes) throws IOException {
		out.write(bytes);
	}

	/**
	 * Gets the output bytes converted to a {@link String}.
	 * <p>
	 * The bytes are decoded using UTF-8.
	 * 
	 * @return The output string.
	 */
	public String getOutputString() {
		return out.toString();
	}

	/**
	 * Gets the output bytes converted to a {@link String} with a given charset.
	 * 
	 * @param charset
	 *            The charset to use.
	 * @return The output string.
	 * @throws NullPointerException
	 *             If the charset is <code>null</code>.
	 */
	public String getOutputString(Charset charset) throws NullPointerException {
		return out.toString(charset);
	}

	/**
	 * Gets the output bytes.
	 * <p>
	 * The returned array is a copy of the byte contents.
	 * 
	 * @return The output bytes.
	 * @see #getByteArrayRegion()
	 */
	public byte[] getByteArray() {
		return out.toByteArray();
	}

	/**
	 * Gets the output bytes as a {@link ByteArrayRegion}.
	 * <p>
	 * The returned {@link ByteArrayRegion} is backed by the internal buffer. No copying takes place.
	 * 
	 * @return The byte array region.
	 */
	public ByteArrayRegion getByteArrayRegion() {
		return out.toByteArrayRegion();
	}
}
