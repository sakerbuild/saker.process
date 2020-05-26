package saker.process.platform.win32;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import saker.build.file.path.SakerPath;
import saker.process.platform.NativeProcess;
import saker.process.platform.NativeProcessIOConsumer;

public class Win32NativeProcess extends NativeProcess {
	private static final int DEFAULT_IO_PROCESSING_DIRECT_BUFFER_SIZE = 1024 * 8;

	private static final AtomicIntegerFieldUpdater<Win32NativeProcess> AIFU_processingFlag = AtomicIntegerFieldUpdater
			.newUpdater(Win32NativeProcess.class, "processingFlag");
	private volatile int processingFlag;

	private static final AtomicIntegerFieldUpdater<Win32NativeProcess> AIFU_closed = AtomicIntegerFieldUpdater
			.newUpdater(Win32NativeProcess.class, "closed");
	private volatile int closed;

	private static final AtomicIntegerFieldUpdater<Win32NativeProcess> AIFU_useCounter = AtomicIntegerFieldUpdater
			.newUpdater(Win32NativeProcess.class, "useCounter");
	private volatile int useCounter = 1;

	private static final AtomicLongFieldUpdater<Win32NativeProcess> ALFU_nativePtr = AtomicLongFieldUpdater
			.newUpdater(Win32NativeProcess.class, "nativePtr");
	private volatile long nativePtr;

	private boolean processstdout;
	private boolean processstderr;

	/*default*/ Win32NativeProcess(long nativePtr, boolean processstdout, boolean processstderr) {
		this.nativePtr = nativePtr;
		this.processstdout = processstdout;
		this.processstderr = processstderr;
	}

	private boolean tryUse() {
		while (true) {
			int c = this.useCounter;
			if (c <= 0) {
				return false;
			}
			if (AIFU_useCounter.compareAndSet(this, c, c + 1)) {
				return true;
			}
		}
	}

	private void use() throws IllegalStateException {
		AIFU_useCounter.updateAndGet(this, c -> {
			if (c <= 0) {
				throw new IllegalStateException("Closed.");
			}
			return c + 1;
		});
	}

	private void release() {
		int c = AIFU_useCounter.decrementAndGet(this);
		if (c == 0) {
			closeNativeCore();
		}
	}

	@Override
	@SuppressWarnings("try")
	public void processIO() throws IOException, InterruptedIOException, IllegalStateException {
		use();
		try {
			long nativeptr = nativePtr;
			if (nativeptr == 0) {
				throw new IllegalStateException("closed.");
			}

			if (!AIFU_processingFlag.compareAndSet(this, 0, 1)) {
				throw new IllegalStateException("Multiple concurrent processIO calls.");
			}
			try {
				ByteBuffer errbuffer;
				if (processstderr) {
					errbuffer = ByteBuffer.allocateDirect(DEFAULT_IO_PROCESSING_DIRECT_BUFFER_SIZE)
							.order(ByteOrder.nativeOrder());
				} else {
					errbuffer = null;
				}
				ByteBuffer stdbuffer;
				if (processstdout) {
					stdbuffer = ByteBuffer.allocateDirect(DEFAULT_IO_PROCESSING_DIRECT_BUFFER_SIZE)
							.order(ByteOrder.nativeOrder());
				} else {
					stdbuffer = null;
				}

				try (InterrupterSelector interruptor = InterrupterSelector.create()) {
					if (Thread.currentThread().isInterrupted()) {
						throw new InterruptedIOException("Process IO processing interrupted.");
					}
					try {
						native_processIO(nativeptr, stdbuffer, errbuffer, interruptor.getInterruptEventPtr());
					} catch (InterruptedException e) {
						//reinterrupt the thread, as we don't directly throw the interrupted exception
						Thread.currentThread().interrupt();
						throw new InterruptedIOException(e.getMessage());
					}
				}
			} finally {
				AIFU_processingFlag.compareAndSet(this, 1, 0);
			}
		} finally {
			release();
		}
	}

	@Override
	public Integer exitValue() throws IllegalThreadStateException, IOException {
		use();
		try {
			long nativeptr = nativePtr;
			if (nativeptr == 0) {
				throw new IllegalStateException("closed.");
			}
			return native_getExitCode(nativeptr);
		} finally {
			release();
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
		if (!AIFU_closed.compareAndSet(this, 0, 1)) {
			//already closed
			return;
		}
		release();
	}

	private void closeNativeCore() {
		long ptr = ALFU_nativePtr.getAndSet(this, 0);
		if (ptr == 0) {
			return;
		}
		native_close(ptr);
	}

	@SuppressWarnings("try")
	private Integer waitForNativeImpl(long millis) throws IOException, InterruptedException {
		use();
		try {
			long nativeptr = nativePtr;
			if (nativeptr == 0) {
				throw new IllegalStateException("closed.");
			}
			try (InterrupterSelector interruptor = InterrupterSelector.create()) {
				if (Thread.interrupted()) {
					throw new InterruptedException("Process waiting interrupted.");
				}
				return native_waitFor(nativeptr, millis, interruptor.getInterruptEventPtr());
			}
		} finally {
			release();
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
			int flags, String pipeid, String envstr, NativeProcessIOConsumer standardOutputConsumer,
			NativeProcessIOConsumer standardErrorConsumer, String stdoutfilepath, String stderrfilepath,
			String stdinfilepath) throws IOException;

	/*default*/ static native long native_createInterruptEvent();

	private static native Integer native_waitFor(long nativeptr, long timeoutmillis, long interrupteventptr)
			throws InterruptedException, IOException;

	private static native void native_processIO(long nativeptr, ByteBuffer stdoutbytedirectbuffer,
			ByteBuffer errbytedirectbuffer, long interrupteventptr) throws IOException, InterruptedException;

	private static native Integer native_getExitCode(long nativeptr) throws IOException, IllegalThreadStateException;

	private static native void native_close(long nativeptr);

	/*default*/ static native void native_closeInterruptEvent(long interrupteventptr);

	private static native void native_interrupt(long interrupteventptr);

	//based on https://github.com/NWilson/javaInterruptHook
	private static class InterrupterSelector extends AbstractSelector {
		private long interruptEventPtr;

		private InterrupterSelector() {
			super(null);
			interruptEventPtr = native_createInterruptEvent();
			if (interruptEventPtr == 0) {
				throw new RuntimeException("Failed to create native event.");
			}
		}

		public static InterrupterSelector create() {
			InterrupterSelector result = new InterrupterSelector();
			try {
				result.begin();
			} catch (Throwable e) {
				try {
					result.close();
				} catch (Throwable e2) {
					e.addSuppressed(e2);
					throw e;
				}
				throw e;
			}
			return result;
		}

		@Override
		protected synchronized void implCloseSelector() throws IOException {
			long ptr = interruptEventPtr;
			interruptEventPtr = 0;
			try {
				end();
			} finally {
				native_closeInterruptEvent(ptr);
			}
		}

		public long getInterruptEventPtr() {
			return interruptEventPtr;
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
		public synchronized Selector wakeup() {
			long ptr = interruptEventPtr;
			if (ptr == 0) {
				return this;
			}
			native_interrupt(ptr);
			return this;
		}

	}
}
