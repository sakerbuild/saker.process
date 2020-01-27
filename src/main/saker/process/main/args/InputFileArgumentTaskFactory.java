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

public class InputFileArgumentTaskFactory extends FrontendTaskFactory<ProcessInvocationArgument> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "proc.arg.in.file";

	@Override
	public ParameterizableTask<? extends ProcessInvocationArgument> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<ProcessInvocationArgument>() {

			@SakerInput(value = { "", "File", "Path" }, required = true)
			public FileLocationTaskOption fileOption;

			@Override
			public ProcessInvocationArgument run(TaskContext taskcontext) throws Exception {
				if (fileOption == null) {
					taskcontext.abortExecution(new IllegalArgumentException("No file specified."));
					return null;
				}
				FileLocationTaskOption infile = fileOption.clone();
				FileLocation[] resultlocation = { null };
				infile.accept(new FileLocationTaskOption.Visitor() {
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
				return ProcessInvocationArgument.createInputFile(resultlocation[0]);
			}
		};
	}

}
