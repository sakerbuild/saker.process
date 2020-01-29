package saker.process.platform.win32;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Native;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.process.platform.NativeProcess;
import saker.process.platform.NativeProcessIOConsumer;
import saker.process.platform.PlatformProcessFactory;

public class Win32NativeProcess extends NativeProcess {
	private static final int DEFAULT_IO_PROCESSING_DIRECT_BUFFER_SIZE = 1024 * 8;

	@Native
	public static final int FLAG_MERGE_STDERR = 1 << 0;

	protected long nativePtr;
	protected long interruptEventPtr;
	protected final int flags;
	protected final InterrupterSelector interruptor = new InterrupterSelector();

	private final Object interruptSync = new Object();

	/*default*/ Win32NativeProcess(long nativePtr, long interruptEventPtr, int flags) {
		this.nativePtr = nativePtr;
		this.interruptEventPtr = interruptEventPtr;
		this.flags = flags;
	}

	@Override
	public void processIO(NativeProcessIOConsumer stdoutprocessor, NativeProcessIOConsumer stderrprocessor)
			throws IOException, InterruptedIOException {
		ByteBuffer errbuffer;
		if (((flags & FLAG_MERGE_STDERR) == FLAG_MERGE_STDERR)) {
			errbuffer = null;
		} else {
			errbuffer = ByteBuffer.allocateDirect(DEFAULT_IO_PROCESSING_DIRECT_BUFFER_SIZE)
					.order(ByteOrder.nativeOrder());
		}
		ByteBuffer stdbuffer = ByteBuffer.allocateDirect(DEFAULT_IO_PROCESSING_DIRECT_BUFFER_SIZE)
				.order(ByteOrder.nativeOrder());

		try {
			interruptor.start();
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedIOException();
			}
			synchronized (this) {
				if (nativePtr == 0) {
					throw new IllegalStateException("closed.");
				}
				try {
					native_processIO(nativePtr, stdoutprocessor, stdbuffer, stderrprocessor, errbuffer);
				} catch (InterruptedException e) {
					//reinterrupt the thread, as we don't directly throw the interrupted exception
					Thread.currentThread().interrupt();
					throw new InterruptedIOException(e.getMessage());
				}
			}
		} finally {
			interruptor.finish();
		}
	}

	@Override
	public Integer exitValue() throws IllegalThreadStateException, IOException {
		synchronized (this) {
			if (nativePtr == 0) {
				throw new IllegalStateException("closed.");
			}
			return native_getExitCode(nativePtr);
		}
	}

	@Override
	public int waitFor() throws InterruptedException, IOException {
		return waitForNativeImpl(-1);
	}

	@Override
	public Integer waitFor(long timeout, TimeUnit unit) throws InterruptedException, IOException {
		long millis = unit.toMillis(timeout);
		return waitForNativeImpl(millis);
	}

	@Override
	public void close() throws IOException {
		Throwable t = null;
		try {
			try {
				closeNativeCore();
			} catch (Throwable e) {
				t = e;
				throw e;
			}
		} finally {
			try {
				closeNativeEvent();
			} catch (Throwable e) {
				IOUtils.addExc(e, t);
				throw e;
			}
		}
	}

	private void closeNativeEvent() throws IOException {
		synchronized (interruptSync) {
			long ptr = interruptEventPtr;
			if (ptr == 0) {
				return;
			}
			interruptEventPtr = 0;
			native_closeInterruptEvent(ptr);
		}
	}

	private void closeNativeCore() throws IOException {
		synchronized (this) {
			long ptr = nativePtr;
			if (ptr == 0) {
				return;
			}
			nativePtr = 0;
			native_close(ptr);
			//the following doesn't really do anything
			interruptor.close();
		}
	}

	private void interruptNative() {
		synchronized (interruptSync) {
			if (interruptEventPtr == 0) {
				return;
			}
			native_interrupt(interruptEventPtr);
		}
	}

	private Integer waitForNativeImpl(long millis) throws IOException, InterruptedException {
		try {
			interruptor.start();
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			synchronized (this) {
				if (nativePtr == 0) {
					throw new IllegalStateException("closed.");
				}
				return native_waitFor(nativePtr, millis);
			}
		} finally {
			interruptor.finish();
		}
	}

	/*default*/ static String getPathForNative(SakerPath path) {
		if (path == null) {
			return null;
		}
		String pathstr = path.toString();
		//MAX_PATH is 260
		if (pathstr.length() > 230) {
			String slashreplaced = pathstr.replace('/', '\\');
			//UNC is based on WindowsPath.addPrefixIfNeeded implementation
			if (slashreplaced.startsWith("\\\\")) {
				return "\\\\?\\UNC" + slashreplaced.substring(1);
			}
			return "\\\\?\\" + slashreplaced;
		}
		return pathstr;
	}

	private static void rewindNotifyOutput(ByteBuffer buffer, int length, NativeProcessIOConsumer processor)
			throws Exception {
		buffer.rewind();
		buffer.limit(length);
		processor.handleOutput(buffer);
	}

	/*default*/ static native long native_startProcess(String exe, String[] commands, String workingdirectory,
			int flags, String pipeid, long interrupteventptr, String envstr) throws IOException;

	/*default*/ static native long native_createInterruptEvent();

	private static native Integer native_waitFor(long nativeptr, long timeoutmillis)
			throws InterruptedException, IOException;

	private static native void native_processIO(long nativeptr, NativeProcessIOConsumer stdoutprocessor,
			ByteBuffer stdoutbytedirectbuffer, NativeProcessIOConsumer stderrprocessor, ByteBuffer errbytedirectbuffer)
			throws IOException, InterruptedException;

	private static native Integer native_getExitCode(long nativeptr) throws IOException, IllegalThreadStateException;

	private static native void native_close(long nativeptr) throws IOException;

	/*default*/ static native void native_closeInterruptEvent(long interrupteventptr) throws IOException;

	private static native void native_interrupt(long interrupteventptr);

	//based on https://github.com/NWilson/javaInterruptHook
	private class InterrupterSelector extends AbstractSelector {
		protected InterrupterSelector() {
			super(null);
		}

		public void start() {
			begin();
		}

		public void finish() {
			end();
		}

		@Override
		protected void implCloseSelector() throws IOException {
		}

		@Override
		protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<SelectionKey> keys() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<SelectionKey> selectedKeys() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int selectNow() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int select(long timeout) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int select() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Selector wakeup() {
			interruptNative();
			return this;
		}
	}
}
