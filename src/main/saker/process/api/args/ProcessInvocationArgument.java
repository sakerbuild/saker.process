package saker.process.api.args;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.process.impl.args.InputFileProcessInvocationArgument;
import saker.process.impl.args.JoinProcessInvocationArgument;
import saker.process.impl.args.OutputFileProcessInvocationArgument;
import saker.process.impl.args.ParentDirectoryCreateOutputFileProcessInvocationArgument;
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

	public static ProcessInvocationArgument createString(String arg) throws NullPointerException {
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

	public static ProcessInvocationArgument createOutputFile(FileLocation arg) throws NullPointerException {
		return new OutputFileProcessInvocationArgument(arg);
	}

	public static ProcessInvocationArgument createOutputFileCreateDirectory(FileLocation arg)
			throws NullPointerException {
		return new ParentDirectoryCreateOutputFileProcessInvocationArgument(arg);
	}

	public static ProcessInvocationArgument createJoined(Iterable<? extends ProcessInvocationArgument> args)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(args, "arguments");
		Iterator<? extends ProcessInvocationArgument> it = args.iterator();
		if (!it.hasNext()) {
			throw new IllegalArgumentException("Empty arguments.");
		}
		List<ProcessInvocationArgument> argslist = new ArrayList<>();
		do {
			ProcessInvocationArgument arg = it.next();
			if (arg == null) {
				throw new IllegalArgumentException("Null argument.");
			}
			argslist.add(arg);
		} while (it.hasNext());
		if (argslist.size() == 1) {
			return argslist.get(0);
		}
		return new JoinProcessInvocationArgument(argslist);
	}

	public static ProcessInvocationArgument createJoined(String prefix, String delimiter,
			Iterable<? extends ProcessInvocationArgument> args, String suffix)
			throws NullPointerException, IllegalArgumentException {
		if (ObjectUtils.isNullOrEmpty(prefix) && ObjectUtils.isNullOrEmpty(delimiter)
				&& ObjectUtils.isNullOrEmpty(suffix)) {
			return createJoined(args);
		}
		Objects.requireNonNull(args, "arguments");
		Iterator<? extends ProcessInvocationArgument> it = args.iterator();
		if (!it.hasNext()) {
			throw new IllegalArgumentException("Empty arguments.");
		}
		List<ProcessInvocationArgument> argslist = new ArrayList<>();
		do {
			ProcessInvocationArgument arg = it.next();
			if (arg == null) {
				throw new IllegalArgumentException("Null argument.");
			}
			argslist.add(arg);
		} while (it.hasNext());
		return new JoinProcessInvocationArgument(prefix, delimiter, argslist, suffix);
	}

}
