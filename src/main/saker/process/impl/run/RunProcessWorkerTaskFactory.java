package saker.process.impl.run;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.build.exception.FileMirroringUnavailableException;
import saker.build.file.SakerDirectory;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.AnyTaskExecutionEnvironmentSelector;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;
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
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		pb.setCommand(args);

		workingDirectory.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				SakerDirectory wdir = taskcontext.getTaskUtilities()
						.resolveDirectoryAtAbsolutePathCreateIfAbsent(loc.getPath());
				if (wdir == null) {
					throw new RuntimeException("Failed to resolve working directory at: " + loc.getPath());
				}
				try {
					pb.setWorkingDirectory(SakerPath.valueOf(taskcontext.mirror(wdir)));
				} catch (FileMirroringUnavailableException | NullPointerException | IOException e) {
					throw new RuntimeException("Failed to mirror working directory: " + loc.getPath(), e);
				}
			}

			@Override
			public void visit(LocalFileLocation loc) {
				pb.setWorkingDirectory(loc.getLocalPath());
			}
		});
		if (!ObjectUtils.isNullOrEmpty(this.environment)) {
			Map<String, String> procenv = pb.getEnvironment();
			for (Entry<String, String> entry : this.environment.entrySet()) {
				if (entry.getValue() == null) {
					procenv.remove(entry.getKey());
				} else {
					procenv.put(entry.getKey(), entry.getValue());
				}
			}
		}

		ByteSink stdoutsink = taskcontext.getStandardOut();
		List<ProcessIOConsumer> stdoutconsumers = argcontext.standardOutputConsumers;
		List<ProcessIOConsumer> stderrconsumers = argcontext.standardErrorConsumers;
		if (stdoutconsumers.isEmpty() && stderrconsumers.isEmpty()) {
			pb.setStandardErrorMerge(true);
			pb.setStandardOutputConsumer(new ProcessIOConsumer() {
				private UnsyncByteArrayOutputStream buf;

				@Override
				public void handleOutput(ByteBuffer bytes) throws IOException {
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
		} else {
			pb.setStandardOutputConsumer(MultiProcessIOConsumer.get(stdoutconsumers));
			pb.setStandardErrorConsumer(MultiProcessIOConsumer.get(stderrconsumers));
		}

		int exitcode;
		try (SakerProcess proc = pb.start()) {
			proc.processIO();

			exitcode = proc.waitFor();
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

	private static class MultiProcessIOConsumer implements ProcessIOConsumer {
		private final Iterable<? extends ProcessIOConsumer> consumers;

		public MultiProcessIOConsumer(Collection<? extends ProcessIOConsumer> consumers) {
			this.consumers = consumers;
		}

		public static ProcessIOConsumer get(Collection<? extends ProcessIOConsumer> consumers) {
			if (consumers.isEmpty()) {
				return null;
			}
			if (consumers.size() == 1) {
				return consumers.iterator().next();
			}
			return new MultiProcessIOConsumer(consumers);
		}

		@Override
		public void handleOutput(ByteBuffer bytes) throws IOException {
			int pos = bytes.position();
			for (Iterator<? extends ProcessIOConsumer> it = consumers.iterator(); it.hasNext();) {
				ProcessIOConsumer c = it.next();
				c.handleOutput(bytes);
				if (it.hasNext()) {
					bytes.position(pos);
				}
			}
		}

		@Override
		public void close() throws IOException {
			IOUtils.close(consumers);
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
