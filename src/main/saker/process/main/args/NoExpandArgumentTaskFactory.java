package saker.process.main.args;

import java.util.List;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.nest.utils.FrontendTaskFactory;
import saker.process.api.args.ProcessInvocationArgument;

public class NoExpandArgumentTaskFactory extends FrontendTaskFactory<ProcessInvocationArgument> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "proc.arg.noexpand";

	@Override
	public ParameterizableTask<? extends ProcessInvocationArgument> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<ProcessInvocationArgument>() {

			@SakerInput(value = { "", "Argument", "Arguments" }, required = true)
			public List<ProcessArgumentTaskOption> argumentOption;

			@Override
			public ProcessInvocationArgument run(TaskContext arg0) throws Exception {
				return ProcessInvocationArgument.createNoExpand(ProcessArgumentUtils.getArguments(argumentOption));
			}
		};
	}

}
