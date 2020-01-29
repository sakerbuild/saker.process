package saker.process.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;
import saker.process.platform.NativeProcess;
import saker.process.platform.NativeProcessIOConsumer;

public class NativeSakerProcessBuilder implements SakerProcessBuilder {
	private List<String> command;
	private boolean mergeStandardError;
	private SakerPath workingDirectory;

	private Map<String, String> environment;

	@Override
	public SakerProcessBuilder setCommand(List<String> command) {
		Objects.requireNonNull(command, "command");
		this.command = command;
		return this;
	}

	@Override
	public Map<String, String> getEnvironment() {
		Map<String, String> result = this.environment;
		if (result == null) {
			this.environment = new TreeMap<>(System.getenv());
			result = this.environment;
		}
		return result;
	}

	@Override
	public SakerProcessBuilder setWorkingDirectory(SakerPath directorypath) {
		if (directorypath != null) {
			SakerPathFiles.requireAbsolutePath(directorypath);
			this.workingDirectory = directorypath;
		} else {
			this.workingDirectory = null;
		}
		return this;
	}

	@Override
	public SakerProcessBuilder setMergeStandardError(boolean mergestderr) {
		mergeStandardError = mergestderr;
		return this;
	}

	@Override
	public SakerProcess start() throws IllegalStateException, IOException {
		List<String> cmd = this.command;
		if (cmd == null) {
			throw new IllegalStateException("Process command was not set.");
		}
		boolean mergestderr = this.mergeStandardError;
		SakerPath workingdir = this.workingDirectory;
		Map<String, String> env = this.environment;

		int flags = 0;
		if (mergestderr) {
			flags |= NativeProcess.FLAG_MERGE_STDERR;
		}
		String[] cmdarray = cmd.toArray(ObjectUtils.EMPTY_STRING_ARRAY);
		NativeProcess nativeproc = NativeProcess.startNativeProcess(null, cmdarray, workingdir, flags, env);
		return new NativeSakerProcess(nativeproc);
	}

	private static final class NativeSakerProcess implements SakerProcess {
		private final NativeProcess proc;

		private NativeSakerProcess(NativeProcess nativeproc) {
			this.proc = nativeproc;
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
		public void processIO(ProcessIOConsumer stdouthandler, ProcessIOConsumer stderrhandler)
				throws IllegalStateException, IOException {
			proc.processIO(toNativeIOConsumer(stdouthandler), toNativeIOConsumer(stderrhandler));
		}

		@Override
		public Integer exitValue() throws IllegalStateException, IOException {
			return proc.exitValue();
		}

		@Override
		public void close() throws IOException {
			proc.close();
		}

		private static NativeProcessIOConsumer toNativeIOConsumer(ProcessIOConsumer stdouthandler) {
			if (stdouthandler == null) {
				return null;
			}
			return new NativeProcessIOConsumer() {
				@Override
				public void handleOutput(ByteBuffer bytes) throws IOException {
					stdouthandler.handleOutput(bytes);
				}
			};
		}
	}
}
