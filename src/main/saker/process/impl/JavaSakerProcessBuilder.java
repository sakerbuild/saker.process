package saker.process.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.thread.ExceptionThread;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class JavaSakerProcessBuilder implements SakerProcessBuilder {
	private ProcessBuilder pb = new ProcessBuilder();
	private ProcessIOConsumer standardErrorConsumer;
	private ProcessIOConsumer standardOutputConsumer;

	@Override
	public SakerProcessBuilder setCommand(List<String> command) {
		pb.command(command);
		return this;
	}

	@Override
	public Map<String, String> getEnvironment() {
		return pb.environment();
	}

	@Override
	public SakerProcessBuilder setWorkingDirectory(SakerPath directorypath) {
		pb.directory(LocalFileProvider.toRealPath(directorypath).toFile());
		return this;
	}

	@Override
	public SakerProcessBuilder setStandardOutputConsumer(ProcessIOConsumer consumer) {
		this.standardOutputConsumer = consumer;
		return null;
	}

	@Override
	public SakerProcessBuilder setStandardErrorMerge(boolean mergestderr) {
		this.standardErrorConsumer = null;
		pb.redirectErrorStream(mergestderr);
		return this;
	}

	@Override
	public SakerProcessBuilder setStandardErrorConsumer(ProcessIOConsumer consumer) {
		this.standardErrorConsumer = consumer;
		pb.redirectErrorStream(false);
		return null;
	}

	@Override
	public SakerProcess start() throws IOException {
		Process proc = pb.start();
		return new JavaSakerProcess(proc, standardOutputConsumer, standardErrorConsumer);
	}

	private static final class JavaSakerProcess implements SakerProcess {
		private final Process proc;
		private final ProcessIOConsumer standardOutputConsumer;
		private final ProcessIOConsumer standardErrorConsumer;

		private JavaSakerProcess(Process proc, ProcessIOConsumer standardOutputConsumer,
				ProcessIOConsumer standardErrorConsumer) {
			this.proc = proc;
			this.standardOutputConsumer = standardOutputConsumer;
			this.standardErrorConsumer = standardErrorConsumer;
		}

		@Override
		public Integer exitValue() throws IllegalStateException, IOException {
			try {
				return proc.exitValue();
			} catch (IllegalThreadStateException e) {
				return null;
			}
		}

		@Override
		public int waitFor() throws IllegalStateException, InterruptedException, IOException {
			return proc.waitFor();
		}

		@Override
		public boolean waitFor(long timeout, TimeUnit unit)
				throws IllegalStateException, InterruptedException, IOException {
			return proc.waitFor(timeout, unit);
		}

		@Override
		public void processIO() throws IllegalStateException, IOException {
			//synchronize, only a single client can process the IO at once
			synchronized (this) {
				InputStream stderrstream = proc.getErrorStream();
				InputStream stdoutstream = proc.getInputStream();
				if (stderrstream == null) {
					//the standard error is redirected
					copyStreamToConsumers(stdoutstream, standardOutputConsumer);
					return;
				}

				//both streams are present
				//even if the std err handler is null, we need to consume the stream otherwise the child process can halt
				ExceptionThread errconsumer = new ExceptionThread((ThrowingRunnable) () -> {
					copyStreamToConsumers(stderrstream, standardErrorConsumer);
				}, "Proc-stderr-consumer");
				errconsumer.setDaemon(true);
				errconsumer.start();
				Throwable exc = null;
				try {
					copyStreamToConsumers(stdoutstream, standardOutputConsumer);
				} catch (Exception e) {
					exc = IOUtils.addExc(exc, e);
				}
				try {
					exc = errconsumer.joinTakeException();
				} catch (InterruptedException e) {
					exc = IOUtils.addExc(exc, e);
				}
				if (exc != null) {
					throw new IOException("Process I/O handling failed.", exc);
				}
			}
		}

		@Override
		public void close() throws IOException {
		}

		private static final byte[] THROWAWAY_CONSUME_BUFFER = new byte[1024 * 8];

		private static void copyStreamToConsumers(InputStream procin, ProcessIOConsumer consumer) throws IOException {
			if (consumer == null) {
				StreamUtils.consumeStream(procin, THROWAWAY_CONSUME_BUFFER);
				return;
			}
			byte[] buffer = new byte[1024 * 8];
			for (int read; (read = procin.read(buffer)) > 0;) {
				ByteBuffer bbuf = ByteBuffer.wrap(buffer, 0, read);
				consumer.handleOutput(bbuf);
			}
		}
	}
}
