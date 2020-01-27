package saker.process.main.args;

import saker.process.api.args.ProcessInvocationArgument;

public class InvocationProcessArgumentTaskOption extends ProcessArgumentTaskOption {
	private ProcessInvocationArgument argument;

	public InvocationProcessArgumentTaskOption(ProcessInvocationArgument argument) {
		this.argument = argument;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	public ProcessInvocationArgument getArgument() {
		return argument;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + argument + "]";
	}

}
