package saker.process.impl.run;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.build.exception.FileMirroringUnavailableException;
import saker.build.file.SakerDirectory;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.AnyTaskExecutionEnvironmentSelector;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.ExceptionThread;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.process.api.args.ProcessIOConsumer;
import saker.process.api.args.ProcessInitializationContext;
import saker.process.api.args.ProcessInvocationArgument;
import saker.process.api.args.ProcessResultContext;
import saker.process.api.args.ProcessResultHandler;
import saker.process.api.run.RunProcessTaskOutput;
import saker.process.impl.args.JoinProcessInvocationArgument;
import saker.process.impl.util.MultiTaskExecutionEnvironmentSelector;
import saker.process.main.run.RunProcessTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.SDKSupportUtils;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;

public class RunProcessWorkerTaskFactory
		implements TaskFactory<RunProcessTaskOutput>, Task<RunProcessTaskOutput>, TaskIdentifier, Externalizable {

	private static final long serialVersionUID = 1L;

	private List<? extends ProcessInvocationArgument> arguments;
	private NavigableMap<String, String> environment;
	private FileLocation workingDirectory;
	private NavigableMap<String, SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector clusterExecutionEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public RunProcessWorkerTaskFactory() {
	}

	public RunProcessWorkerTaskFactory(List<? extends ProcessInvocationArgument> arguments,
			NavigableMap<String, String> environment, FileLocation workingDirectory,
			NavigableMap<String, SDKDescription> sdkDescriptions) {
		Objects.requireNonNull(arguments, "arguments");
		if (arguments.isEmpty()) {
			throw new IllegalArgumentException("No process arguments specified.");
		}
		this.arguments = arguments;
		this.environment = environment == null ? Collections.emptyNavigableMap()
				: ImmutableUtils.makeImmutableNavigableMap(environment);
		this.workingDirectory = workingDirectory;
		if (ObjectUtils.isNullOrEmpty(sdkDescriptions)) {
			this.sdkDescriptions = ImmutableUtils.emptyNavigableMap(SDKSupportUtils.getSDKNameComparator());
		} else {
			ObjectUtils.requireComparator(sdkDescriptions, SDKSupportUtils.getSDKNameComparator());
			this.sdkDescriptions = sdkDescriptions;

			//only use clusters is there was at least one SDK defined
			//XXX support using clusters without any SDK, via a flag or something
			this.clusterExecutionEnvironmentSelector = SDKSupportUtils
					.getSDKBasedClusterExecutionEnvironmentSelector(sdkDescriptions.values());
			if (this.clusterExecutionEnvironmentSelector != null) {
				TaskExecutionEnvironmentSelector argsselector = JoinProcessInvocationArgument
						.getArgumentsEnvironmentSelector(arguments);
				if (argsselector == null) {
					this.clusterExecutionEnvironmentSelector = null;
				} else if (argsselector.equals(AnyTaskExecutionEnvironmentSelector.INSTANCE)) {
					//cluster selector stays the same
				} else {
					this.clusterExecutionEnvironmentSelector = MultiTaskExecutionEnvironmentSelector
							.create(ObjectUtils.newHashSet(this.clusterExecutionEnvironmentSelector, argsselector));
				}
			}
		}
	}

	@Override
	public int getRequestedComputationTokenCount() {
		//assume 1 as we're running 1 process
		return 1;
	}

	@Override
	public Set<String> getCapabilities() {
		if (this.clusterExecutionEnvironmentSelector != null) {
			return ImmutableUtils.singletonNavigableSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}
		return TaskFactory.super.getCapabilities();
	}

	@Override
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		if (this.clusterExecutionEnvironmentSelector != null) {
			return this.clusterExecutionEnvironmentSelector;
		}
		return TaskFactory.super.getExecutionEnvironmentSelector();
	}

	@Override
	public RunProcessTaskOutput run(TaskContext taskcontext) throws Exception {
		taskcontext.setStandardOutDisplayIdentifier(RunProcessTaskFactory.TASK_NAME);

		NavigableMap<String, SDKReference> sdkreferences = SDKSupportUtils.resolveSDKReferences(taskcontext,
				sdkDescriptions);
		RunArgumentContextImpl argcontext = new RunArgumentContextImpl(taskcontext,
				ImmutableUtils.makeImmutableNavigableMap(sdkreferences));

		List<String> args = new ArrayList<>(arguments.size());
		for (ProcessInvocationArgument invocationarg : arguments) {
			List<String> invocargs = invocationarg.getArguments(argcontext);
			if (!ObjectUtils.isNullOrEmpty(invocargs)) {
				args.addAll(invocargs);
			}
		}
		ProcessBuilder pb = new ProcessBuilder(args);

		workingDirectory.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				SakerDirectory wdir = taskcontext.getTaskUtilities()
						.resolveDirectoryAtAbsolutePathCreateIfAbsent(loc.getPath());
				if (wdir == null) {
					throw new RuntimeException("Failed to resolve working directory at: " + loc.getPath());
				}
				try {
					pb.directory(taskcontext.mirror(wdir).toFile());
				} catch (FileMirroringUnavailableException | NullPointerException | IOException e) {
					throw new RuntimeException("Failed to mirror working directory: " + loc.getPath(), e);
				}
			}

			@Override
			public void visit(LocalFileLocation loc) {
				pb.directory(LocalFileProvider.toRealPath(loc.getLocalPath()).toFile());
			}
		});
		pb.environment().putAll(environment);

		ByteSink stdoutsink = taskcontext.getStandardOut();
		if (argcontext.standardOutputConsumers.isEmpty() && argcontext.standardErrorConsumers.isEmpty()) {
			pb.redirectErrorStream(true);
			argcontext.standardOutputConsumers.add(new ProcessIOConsumer() {
				private UnsyncByteArrayOutputStream buf;

				@Override
				public void handleOutput(ByteBuffer bytes) throws Exception {
					if (bytes.hasArray()) {
						stdoutsink.write(ByteArrayRegion.wrap(bytes.array(), bytes.arrayOffset(), bytes.limit()));
					} else {
						if (buf == null) {
							buf = new UnsyncByteArrayOutputStream(bytes.limit());
						} else {
							buf.reset();
						}
						buf.write(bytes);
						buf.writeTo(stdoutsink);
					}
				}
			});
		}
		Process proc = pb.start();
		InputStream procin = proc.getInputStream();
		List<ProcessIOConsumer> stdoutconsumers = argcontext.standardOutputConsumers;
		List<ProcessIOConsumer> stderrconsumers = argcontext.standardErrorConsumers;
		ExceptionThread errconsumer = null;
		if (!pb.redirectErrorStream()) {
			if (stderrconsumers.isEmpty()) {
				ThreadUtils.startDaemonThread("Proc-stderr-consumer", () -> {
					try {
						//XXX could use a common throwaway byte buffer
						StreamUtils.consumeStream(proc.getErrorStream());
					} catch (NullPointerException | IOException e) {
						//ignoreable
						taskcontext.getTaskUtilities().reportIgnoredException(e);
					}
				});
			} else {
				errconsumer = new ExceptionThread((ThrowingRunnable) () -> {
					copyStreamToConsumers(proc.getErrorStream(), stderrconsumers);
				}, "Proc-stderr-consumer");
				errconsumer.start();
			}
		}
		copyStreamToConsumers(procin, stdoutconsumers);

		int exitcode = proc.waitFor();
		if (errconsumer != null) {
			Throwable exc = errconsumer.joinTakeException();
			if (exc != null) {
				throw new RuntimeException("Failed to consumer process standard error stream.", exc);
			}
		}
		ProcessResultContextImpl resultcontext = new ProcessResultContextImpl(taskcontext, exitcode);
		for (ProcessResultHandler rh : argcontext.resultHandlers) {
			rh.handleProcessResult(resultcontext);
		}
		if (resultcontext.shouldFail) {
			throw new RuntimeException("Process exited with non-zero exit code: " + exitcode);
		}

		return new RunProcessTaskOutputImpl(exitcode, resultcontext.outputs);
	}

	private static void copyStreamToConsumers(InputStream procin, List<ProcessIOConsumer> stdoutconsumers)
			throws IOException, Exception {
		byte[] buffer = new byte[1024 * 8];
		for (int read; (read = procin.read(buffer)) > 0;) {
			ByteBuffer bbuf = ByteBuffer.wrap(buffer, 0, read);
			for (ProcessIOConsumer consumer : stdoutconsumers) {
				consumer.handleOutput(bbuf);
			}
		}
	}

	@Override
	public Task<? extends RunProcessTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	private static final class ProcessResultContextImpl implements ProcessResultContext {
		private final TaskContext taskContext;
		private final int exitCode;

		private boolean shouldFail;
		protected NavigableMap<String, Object> outputs = new TreeMap<>();

		public ProcessResultContextImpl(TaskContext taskContext, int exitCode) {
			this.taskContext = taskContext;
			this.exitCode = exitCode;
			this.shouldFail = exitCode != 0;
		}

		@Override
		public TaskContext getTaskContext() {
			return taskContext;
		}

		@Override
		public int getExitCode() {
			return exitCode;
		}

		@Override
		public void bindOutput(String name, Object value) {
			//XXX should warn if an output is overwritten
			outputs.put(name, value);
		}
	}

	private static final class RunProcessTaskOutputImpl implements RunProcessTaskOutput, Externalizable {
		private static final long serialVersionUID = 1L;

		private int exitCode;

		private Map<String, ?> output;

		/**
		 * For {@link Externalizable}.
		 */
		public RunProcessTaskOutputImpl() {
		}

		public RunProcessTaskOutputImpl(int exitCode, NavigableMap<String, ?> output) {
			this.exitCode = exitCode;
			this.output = ImmutableUtils.unmodifiableNavigableMap(output);
		}

		@Override
		public int getExitCode() {
			return exitCode;
		}

		@Override
		public Map<String, ?> getOutput() {
			return output;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(exitCode);
			SerialUtils.writeExternalMap(out, output);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			exitCode = in.readInt();
			output = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		}

	}

	private final class RunArgumentContextImpl implements ProcessInitializationContext {
		protected final TaskContext taskcontext;
		protected final NavigableMap<String, SDKReference> sdkReferences;
		protected final List<ProcessResultHandler> resultHandlers = new ArrayList<>();

		protected final List<ProcessIOConsumer> standardOutputConsumers = new ArrayList<>();
		protected final List<ProcessIOConsumer> standardErrorConsumers = new ArrayList<>();

		public RunArgumentContextImpl(TaskContext taskcontext, NavigableMap<String, SDKReference> sdkReferences) {
			this.taskcontext = taskcontext;
			this.sdkReferences = sdkReferences;
		}

		@Override
		public TaskContext getTaskContext() {
			return taskcontext;
		}

		@Override
		public NavigableMap<String, SDKReference> getSDKs() {
			return sdkReferences;
		}

		@Override
		public void addResultHandler(ProcessResultHandler handler) {
			Objects.requireNonNull(handler, "process result handler");
			this.resultHandlers.add(handler);
		}

		@Override
		public void addStandardOutputConsumer(ProcessIOConsumer consumer) throws NullPointerException {
			Objects.requireNonNull(consumer, "consumer");
			this.standardOutputConsumers.add(consumer);
		}

		@Override
		public void addStandardErrorConsumer(ProcessIOConsumer consumer) throws NullPointerException {
			Objects.requireNonNull(consumer, "consumer");
			this.standardErrorConsumers.add(consumer);
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, arguments);
		SerialUtils.writeExternalMap(out, environment);
		out.writeObject(workingDirectory);
		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(clusterExecutionEnvironmentSelector);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		arguments = SerialUtils.readExternalImmutableList(in);
		environment = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		workingDirectory = (FileLocation) in.readObject();
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		clusterExecutionEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((arguments == null) ? 0 : arguments.hashCode());
		result = prime * result + ((environment == null) ? 0 : environment.hashCode());
		result = prime * result + ((sdkDescriptions == null) ? 0 : sdkDescriptions.hashCode());
		result = prime * result + ((workingDirectory == null) ? 0 : workingDirectory.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RunProcessWorkerTaskFactory other = (RunProcessWorkerTaskFactory) obj;
		if (arguments == null) {
			if (other.arguments != null)
				return false;
		} else if (!arguments.equals(other.arguments))
			return false;
		if (environment == null) {
			if (other.environment != null)
				return false;
		} else if (!environment.equals(other.environment))
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		if (workingDirectory == null) {
			if (other.workingDirectory != null)
				return false;
		} else if (!workingDirectory.equals(other.workingDirectory))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + arguments;
	}
}
