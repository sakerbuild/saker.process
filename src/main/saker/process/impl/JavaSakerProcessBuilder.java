package saker.process.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.thread.ExceptionThread;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcess;

public class JavaSakerProcessBuilder extends SakerProcessBuilderBase {
	//as in ProcessBulilder.Redirect.NULL_FILE from the JDK source code (9+)
	private static final File NULL_FILE = new File(
			(StringUtils.startsWithIgnoreCase(System.getProperty("os.name"), "windows") ? "NUL" : "/dev/null"));

	@Override
	public SakerProcess start() throws IOException {
		List<String> cmd = this.command;
		boolean mergestderr = this.mergeStandardError;
		SakerPath workingdir = this.workingDirectory;
		Map<String, String> env = this.environment;
		ProcessIOConsumer stdoutconsumer = standardOutputConsumer;
		ProcessIOConsumer stderrconsumer = standardErrorConsumer;
		if (cmd == null) {
			throw new IllegalStateException("Process command was not set.");
		}
		String[] cmdarray = cmd.toArray(ObjectUtils.EMPTY_STRING_ARRAY);
		if (cmdarray.length == 0) {
			throw new IllegalStateException("Empty process command specified.");
		}

		ProcessBuilder pb = new ProcessBuilder(cmdarray);
		if (workingdir != null) {
			pb.directory(LocalFileProvider.toRealPath(workingdir).toFile());
		}
		if (env != null) {
			Map<String, String> pbenv = pb.environment();
			pbenv.clear();
			pbenv.putAll(env);
		}
		if (stdoutconsumer == null) {
			//set process builder stdout redirection to NULL
			pb.redirectOutput(NULL_FILE);
		} else if (stdoutconsumer instanceof RedirectFileProcessIOConsumer) {
			pb.redirectOutput(
					LocalFileProvider.toRealPath(((RedirectFileProcessIOConsumer) stdoutconsumer).getPath()).toFile());
			stdoutconsumer = null;
		} else {
			//default to Redirect.PIPE
		}
		if (mergestderr) {
			pb.redirectErrorStream(true);
		} else if (stderrconsumer == null) {
			pb.redirectError(NULL_FILE);
		} else if (stderrconsumer instanceof RedirectFileProcessIOConsumer) {
			pb.redirectError(
					LocalFileProvider.toRealPath(((RedirectFileProcessIOConsumer) stderrconsumer).getPath()).toFile());
			stderrconsumer = null;
		} else {
			//default to Redirect.PIPE
		}

		if (standardInputPipe) {
			//this is the default.
		} else if (standardInputFile != null) {
			pb.redirectInput(LocalFileProvider.toRealPath(standardInputFile).toFile());
		} else {
			//redirect the std in to null, to signal that there's no input coming from there
			pb.redirectInput(NULL_FILE);
		}

		Process proc = pb.start();
		return new JavaSakerProcess(proc, stdoutconsumer, stderrconsumer);
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
					//if the standard output is redirected as well, the copyStreamToConsumers returns immediately
					copyStreamToConsumers(stdoutstream, standardOutputConsumer);
					return;
				}
				if (stdoutstream == null) {
					//the standard output is redirected
					copyStreamToConsumers(stderrstream, standardErrorConsumer);
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
					errconsumer.interrupt();
					exc = IOUtils.addExc(exc, e);
				}
				if (exc != null) {
					throw new IOException("Process I/O handling failed.", exc);
				}
			}
		}

		@Override
		public void close() throws IOException {
			IOUtils.close(standardOutputConsumer, standardErrorConsumer);
		}

		private static final byte[] THROWAWAY_CONSUME_BUFFER = new byte[1024 * 8];

		private static void copyStreamToConsumers(InputStream procin, ProcessIOConsumer consumer) throws IOException {
			if (procin == null) {
				return;
			}
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
