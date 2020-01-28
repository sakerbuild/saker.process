package saker.process.main.args;

import java.util.ArrayList;
import java.util.List;

import saker.process.api.args.ProcessInvocationArgument;

public class ProcessArgumentUtils {
	private ProcessArgumentUtils() {
		throw new UnsupportedOperationException();
	}

	public static List<ProcessInvocationArgument> getArguments(
			Iterable<? extends ProcessArgumentTaskOption> argoptions) {
		ArrayList<ProcessInvocationArgument> arguments = new ArrayList<>();
		for (ProcessArgumentTaskOption cmdtaskoption : argoptions) {
			if (cmdtaskoption == null) {
				continue;
			}
			cmdtaskoption.accept(new ProcessArgumentTaskOption.Visitor() {
				@Override
				public void visit(StringProcessArgumentTaskOption arg) {
					arguments.add(ProcessInvocationArgument.createString(arg.getArgument()));
				}

				@Override
				public void visit(InvocationProcessArgumentTaskOption arg) {
					arguments.add(arg.getArgument());
				}

				@Override
				public void visit(InputFileProcessArgumentTaskOption arg) {
					arguments.add(ProcessInvocationArgument.createInputFile(arg.getFile()));
				}

				@Override
				public void visit(SDKPathProcessArgumentTaskOption arg) {
					arguments.add(ProcessInvocationArgument.createSDKPath(arg.getPathReference()));
				}

				@Override
				public void visit(SDKPropertyProcessArgumentTaskOption arg) {
					arguments.add(ProcessInvocationArgument.createSDKProperty(arg.getPropertyReference()));
				}
			});
		}
		return arguments;
	}
}
