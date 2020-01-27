package saker.process.main.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.nest.utils.FrontendTaskFactory;
import saker.process.api.args.ProcessInvocationArgument;
import saker.process.impl.run.RunProcessWorkerTaskFactory;
import saker.process.main.run.args.ProcessArgumentTaskOption;
import saker.process.main.run.args.StringProcessArgumentTaskOption;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.main.file.option.FileLocationTaskOption;

public class RunProcessTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "proc.run";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Command", "Process" }, required = true)
			public List<ProcessArgumentTaskOption> commandOption;

			@SakerInput(value = "WorkingDirectory")
			public FileLocationTaskOption workingDirectoryOption;

			@SakerInput(value = "Environment")
			public Map<String, String> environmentOption;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (ObjectUtils.isNullOrEmpty(commandOption)) {
					taskcontext.abortExecution(new IllegalArgumentException("No process command specified to run."));
					return null;
				}
				NavigableMap<String, String> env = ImmutableUtils.makeImmutableNavigableMap(environmentOption);
				NavigableMap<String, SDKDescription> sdkdescriptions = new TreeMap<>(
						SDKSupportUtils.getSDKNameComparator());
				if (!ObjectUtils.isNullOrEmpty(sdksOption)) {
					//TODO fill sdks
					throw new UnsupportedOperationException();
				}
				FileLocation workingdir;
				if (workingDirectoryOption == null) {
					workingdir = ExecutionFileLocation.create(taskcontext.getTaskWorkingDirectoryPath());
				} else {
					FileLocation[] wdirres = { null };
					workingDirectoryOption.accept(new FileLocationTaskOption.Visitor() {
						@Override
						public void visitRelativePath(SakerPath path) {
							wdirres[0] = ExecutionFileLocation
									.create(taskcontext.getTaskWorkingDirectoryPath().resolve(path));
						}

						@Override
						public void visitFileLocation(FileLocation location) {
							wdirres[0] = location;
						}
					});
					workingdir = wdirres[0];
				}

				List<ProcessInvocationArgument> arguments = new ArrayList<>();
				for (ProcessArgumentTaskOption cmdtaskoption : commandOption) {
					if (cmdtaskoption == null) {
						continue;
					}
					cmdtaskoption.accept(new ProcessArgumentTaskOption.Visitor() {
						@Override
						public void visit(StringProcessArgumentTaskOption arg) {
							arguments.add(ProcessInvocationArgument.createSimpleString(arg.getArgument()));
						}
					});
				}

				RunProcessWorkerTaskFactory workertask = new RunProcessWorkerTaskFactory(arguments, env, workingdir,
						sdkdescriptions);
				taskcontext.startTask(workertask, workertask, null);
				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertask);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
