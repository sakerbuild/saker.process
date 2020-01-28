package saker.process.main.args;

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.nest.utils.FrontendTaskFactory;
import saker.process.api.args.ProcessInvocationArgument;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.main.file.option.FileLocationTaskOption;

public class OutputFileArgumentTaskFactory extends FrontendTaskFactory<ProcessInvocationArgument> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "proc.arg.out.file";

	@Override
	public ParameterizableTask<? extends ProcessInvocationArgument> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<ProcessInvocationArgument>() {

			@SakerInput(value = { "", "Output" })
			public FileLocationTaskOption outputLocationOption;

			@Override
			public ProcessInvocationArgument run(TaskContext taskcontext) throws Exception {
				if (outputLocationOption == null) {
					taskcontext.abortExecution(new IllegalArgumentException("No Output file specified."));
					return null;
				}
				FileLocation[] resultlocation = { null };
				outputLocationOption.clone().accept(new FileLocationTaskOption.Visitor() {
					@Override
					public void visitRelativePath(SakerPath path) {
						resultlocation[0] = ExecutionFileLocation
								.create(taskcontext.getTaskWorkingDirectoryPath().resolve(path));
					}

					@Override
					public void visitFileLocation(FileLocation location) {
						resultlocation[0] = location;
					}
				});
				return ProcessInvocationArgument.createOutputFile(resultlocation[0]);
			}
		};
	}
}
