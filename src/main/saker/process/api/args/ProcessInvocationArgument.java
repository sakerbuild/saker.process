package saker.process.api.args;

import java.util.List;

import saker.process.impl.args.StringProcessInvocationArgument;

public interface ProcessInvocationArgument {
	public List<String> getArguments(ProcessArgumentContext argcontext);

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	public static ProcessInvocationArgument createSimpleString(String arg) throws NullPointerException {
		return new StringProcessInvocationArgument(arg);
	}
}
