package saker.process.api.args;

import java.util.List;

import saker.process.impl.args.InputFileProcessInvocationArgument;
import saker.process.impl.args.StringProcessInvocationArgument;
import saker.std.api.file.location.FileLocation;

//doc: clients may implement
public interface ProcessInvocationArgument {
	public List<String> getArguments(ProcessArgumentContext argcontext) throws Exception;

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	public static ProcessInvocationArgument createSimpleString(String arg) throws NullPointerException {
		return new StringProcessInvocationArgument(arg);
	}

	public static ProcessInvocationArgument createInputFile(FileLocation arg) throws NullPointerException {
		return new InputFileProcessInvocationArgument(arg);
	}
}
