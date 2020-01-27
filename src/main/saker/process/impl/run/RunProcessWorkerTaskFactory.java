package saker.process.impl.run;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;

import saker.build.exception.FileMirroringUnavailableException;
import saker.build.file.SakerDirectory;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.process.api.args.ProcessArgumentContext;
import saker.process.api.args.ProcessInvocationArgument;
import saker.process.api.run.RunProcessTaskOutput;
import saker.process.main.run.RunProcessTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;

public class RunProcessWorkerTaskFactory
		implements TaskFactory<RunProcessTaskOutput>, Task<RunProcessTaskOutput>, TaskIdentifier, Externalizable {

	private static final long serialVersionUID = 1L;

	private List<ProcessInvocationArgument> arguments;
	private NavigableMap<String, String> environment;
	private FileLocation workingDirectory;
	private NavigableMap<String, SDKDescription> sdkDescriptions;

	/**
	 * For {@link Externalizable}.
	 */
	public RunProcessWorkerTaskFactory() {
	}

	public RunProcessWorkerTaskFactory(List<ProcessInvocationArgument> arguments,
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
		if (sdkDescriptions == null) {
			this.sdkDescriptions = ImmutableUtils.emptyNavigableMap(SDKSupportUtils.getSDKNameComparator());
		} else {
			ObjectUtils.requireComparator(sdkDescriptions, SDKSupportUtils.getSDKNameComparator());
			this.sdkDescriptions = sdkDescriptions;
		}
	}

	@Override
	public RunProcessTaskOutput run(TaskContext taskcontext) throws Exception {
		taskcontext.setStandardOutDisplayIdentifier(RunProcessTaskFactory.TASK_NAME);

		ProcessArgumentContext argcontext = new RunArgumentContextImpl(taskcontext);

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

		//TODO handle stdin and stdout better
		pb.redirectErrorStream(true);

		Process proc = pb.start();
		StreamUtils.copyStream(proc.getInputStream(), ByteSink.toOutputStream(taskcontext.getStandardOut()));
		int exitcode = proc.waitFor();
		if (exitcode != 0) {
			throw new RuntimeException("Process exited with non-zero exit code: " + exitcode);
		}

		// TODO handle output
		return new RunProcessTaskOutputImpl();
	}

	@Override
	public Task<? extends RunProcessTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	private static final class RunProcessTaskOutputImpl implements RunProcessTaskOutput, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public RunProcessTaskOutputImpl() {
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	private final class RunArgumentContextImpl implements ProcessArgumentContext {
		private final TaskContext taskcontext;

		private RunArgumentContextImpl(TaskContext taskcontext) {
			this.taskcontext = taskcontext;
		}

		@Override
		public TaskContext getTaskContext() {
			return taskcontext;
		}

		@Override
		public NavigableMap<String, SDKDescription> getSDKs() {
			return sdkDescriptions;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, arguments);
		SerialUtils.writeExternalMap(out, environment);
		out.writeObject(workingDirectory);
		SerialUtils.writeExternalMap(out, sdkDescriptions);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		arguments = SerialUtils.readExternalImmutableList(in);
		environment = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		workingDirectory = (FileLocation) in.readObject();
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
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