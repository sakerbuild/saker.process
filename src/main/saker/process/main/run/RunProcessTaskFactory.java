package saker.process.main.run;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import saker.process.main.args.ProcessArgumentTaskOption;
import saker.process.main.args.ProcessArgumentUtils;
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
				Map<String, SDKDescriptionTaskOption> sdkoptions = new TreeMap<>(
						SDKSupportUtils.getSDKNameComparator());
				if (!ObjectUtils.isNullOrEmpty(sdksOption)) {
					for (Entry<String, SDKDescriptionTaskOption> entry : this.sdksOption.entrySet()) {
						SDKDescriptionTaskOption sdktaskopt = entry.getValue();
						if (sdktaskopt == null) {
							continue;
						}
						SDKDescriptionTaskOption prev = sdkoptions.putIfAbsent(entry.getKey(), sdktaskopt.clone());
						if (prev != null) {
							taskcontext.abortExecution(new IllegalArgumentException(
									"SDK with name " + entry.getKey() + " defined multiple times."));
							return null;
						}
					}
				}

				NavigableMap<String, SDKDescription> sdkdescriptions = new TreeMap<>(
						SDKSupportUtils.getSDKNameComparator());

				for (Entry<String, SDKDescriptionTaskOption> entry : sdkoptions.entrySet()) {
					SDKDescriptionTaskOption val = entry.getValue();
					SDKDescription[] desc = { null };
					if (val != null) {
						val.accept(new SDKDescriptionTaskOption.Visitor() {
							@Override
							public void visit(SDKDescription description) {
								desc[0] = description;
							}
						});
					}
					sdkdescriptions.putIfAbsent(entry.getKey(), desc[0]);
				}

				FileLocation workingdir;
				if (workingDirectoryOption == null) {
					workingdir = ExecutionFileLocation.create(taskcontext.getTaskWorkingDirectoryPath());
				} else {
					FileLocation[] wdirres = { null };
					workingDirectoryOption.clone().accept(new FileLocationTaskOption.Visitor() {
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

				List<ProcessInvocationArgument> arguments = ProcessArgumentUtils.getArguments(commandOption);

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
