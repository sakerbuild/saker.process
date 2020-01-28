package saker.process.main.args;

import java.util.List;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.nest.utils.FrontendTaskFactory;
import saker.process.api.args.ProcessInvocationArgument;

public class JoinArgumentTaskFactory extends FrontendTaskFactory<ProcessInvocationArgument> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "proc.arg.join";

	@Override
	public ParameterizableTask<? extends ProcessInvocationArgument> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<ProcessInvocationArgument>() {

			@SakerInput(value = { "", "Arguments" }, required = true)
			public List<ProcessArgumentTaskOption> argumentsOption;

			@SakerInput("Prefix")
			public String prefixOption;
			@SakerInput("Delimiter")
			public String delimiterOption;
			@SakerInput("Suffix")
			public String suffixOption;

			@Override
			public ProcessInvocationArgument run(TaskContext taskcontext) throws Exception {
				if (argumentsOption == null) {
					taskcontext.abortExecution(new IllegalArgumentException("No arguments specified to join."));
					return null;
				}
				ImmutableUtils.makeImmutableList(argumentsOption);
				List<ProcessInvocationArgument> argslist = ProcessArgumentUtils.getArguments(argumentsOption);
				return ProcessInvocationArgument.createJoined(prefixOption, delimiterOption, argslist, suffixOption);
			}
		};
	}

}
