package saker.process.api.args;

import java.util.List;

import saker.process.impl.args.InputFileProcessInvocationArgument;
import saker.process.impl.args.SDKPathProcessInvocationArgument;
import saker.process.impl.args.SDKPropertyProcessInvocationArgument;
import saker.process.impl.args.StringProcessInvocationArgument;
import saker.sdk.support.api.SDKPathReference;
import saker.sdk.support.api.SDKPropertyReference;
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

	public static ProcessInvocationArgument createSDKPath(SDKPathReference arg) throws NullPointerException {
		return new SDKPathProcessInvocationArgument(arg);
	}

	public static ProcessInvocationArgument createSDKProperty(SDKPropertyReference arg) throws NullPointerException {
		return new SDKPropertyProcessInvocationArgument(arg);
	}
}
