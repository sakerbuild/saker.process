package saker.process.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.platform.NativeProcess;
import saker.process.platform.NativeProcessIOConsumer;

public class NativeSakerProcessBuilder extends SakerProcessBuilderBase {
	@Override
	public SakerProcess start() throws IllegalStateException, IOException {
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

		int flags = 0;
		if (mergestderr) {
			flags |= NativeProcess.FLAG_MERGE_STDERR;
		}
		NativeProcess nativeproc = NativeProcess.startNativeProcess(null, cmdarray, workingdir, flags, env,
				toNativeIOConsumer(stdoutconsumer), toNativeIOConsumer(stderrconsumer));
		return new NativeSakerProcess(nativeproc, stdoutconsumer, stderrconsumer);
	}

	private static NativeProcessIOConsumer toNativeIOConsumer(ProcessIOConsumer consumer) {
		if (consumer == null) {
			return null;
		}
		NativeProcessIOConsumer nativeconsumer = new NativeProcessIOConsumer() {
			@Override
			public void handleOutput(ByteBuffer bytes) throws IOException {
				consumer.handleOutput(bytes);
			}
		};
		if (consumer instanceof RedirectFileProcessIOConsumer) {
			RedirectFileProcessIOConsumer redirectconsumer = (RedirectFileProcessIOConsumer) consumer;
			//close so it won't replace the file in its own closing
			redirectconsumer.setClosed();
			return NativeProcessIOConsumer.redirectFile(redirectconsumer.getPath(), nativeconsumer);
		}
		return nativeconsumer;
	}

	private static final class NativeSakerProcess implements SakerProcess {
		private final NativeProcess proc;
		private final ProcessIOConsumer standardOutputConsumer;
		private final ProcessIOConsumer standardErrorConsumer;

		private NativeSakerProcess(NativeProcess nativeproc, ProcessIOConsumer stdoutconsumer,
				ProcessIOConsumer stderrconsumer) {
			this.proc = nativeproc;
			this.standardOutputConsumer = stdoutconsumer;
			this.standardErrorConsumer = stderrconsumer;
		}

		@Override
		public boolean waitFor(long timeout, TimeUnit unit)
				throws IllegalStateException, InterruptedException, IOException {
			Integer ec = proc.waitFor(timeout, unit);
			return ec != null;
		}

		@Override
		public int waitFor() throws IllegalStateException, InterruptedException, IOException {
			return proc.waitFor();
		}

		@Override
		public void processIO() throws IllegalStateException, IOException {
			proc.processIO();
		}

		@Override
		public Integer exitValue() throws IllegalStateException, IOException {
			return proc.exitValue();
		}

		@Override
		public void close() throws IOException {
			IOUtils.close(standardOutputConsumer, standardErrorConsumer, proc);
		}

	}
}
