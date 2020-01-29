package saker.process.main.args;

import java.util.Collection;
import java.util.NavigableSet;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.nest.utils.FrontendTaskFactory;
import saker.process.api.args.ProcessInvocationArgument;
import saker.process.impl.args.OutputFileProcessInvocationArgument;
import saker.process.impl.args.ParentDirectoryCreateOutputFileProcessInvocationArgument;
import saker.process.impl.run.OutputBindingProcessInvocationArgument;
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

			@SakerInput({ "CreateParentDir", "CreateParentDirectory" })
			public boolean createDirectoriesOption = false;

			@SakerInput(value = { "SubFiles" })
			public Collection<WildcardPath> subfilesOption;

			@SakerInput({ "BindOutput" })
			public String bindOutputNameOption;
			@SakerInput({ "BindSubFiles" })
			public String bindSubFilesNameOption;

			@Override
			public ProcessInvocationArgument run(TaskContext taskcontext) throws Exception {
				if (outputLocationOption == null) {
					taskcontext.abortExecution(new IllegalArgumentException("No Output file specified."));
					return null;
				}
				FileLocation[] resultlocation = { null };
				NavigableSet<WildcardPath> subfiles = ImmutableUtils.makeImmutableNavigableSet(subfilesOption);
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
				ProcessInvocationArgument result;
				if (createDirectoriesOption) {
					result = new ParentDirectoryCreateOutputFileProcessInvocationArgument(resultlocation[0], subfiles);
				} else {
					result = new OutputFileProcessInvocationArgument(resultlocation[0], subfiles);
				}
				if (bindOutputNameOption != null) {
					result = new OutputBindingProcessInvocationArgument(result, bindOutputNameOption,
							resultlocation[0]);
				}
				if (bindSubFilesNameOption != null) {
					//TODO handle sub files binding
				}
				return result;
			}
		};
	}
}
