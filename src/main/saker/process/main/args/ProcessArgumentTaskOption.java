package saker.process.main.args;

import saker.process.api.args.ProcessInvocationArgument;
import saker.sdk.support.api.SDKPathReference;
import saker.sdk.support.api.SDKPropertyReference;
import saker.std.api.file.location.FileLocation;

public abstract class ProcessArgumentTaskOption {

	public abstract void accept(Visitor visitor);

	public static ProcessArgumentTaskOption valueOf(String arg) {
		return new StringProcessArgumentTaskOption(arg);
	}

	public static ProcessArgumentTaskOption valueOf(ProcessInvocationArgument arg) {
		return new InvocationProcessArgumentTaskOption(arg);
	}

	public static ProcessArgumentTaskOption valueOf(FileLocation arg) {
		return new InputFileProcessArgumentTaskOption(arg);
	}

	public static ProcessArgumentTaskOption valueOf(SDKPathReference arg) {
		return new SDKPathProcessArgumentTaskOption(arg);
	}

	public static ProcessArgumentTaskOption valueOf(SDKPropertyReference arg) {
		return new SDKPropertyProcessArgumentTaskOption(arg);
	}

	public interface Visitor {
		public default void visit(StringProcessArgumentTaskOption arg) {
			throw new UnsupportedOperationException("Unsupported process argument kind: " + arg);
		}

		public default void visit(InvocationProcessArgumentTaskOption arg) {
			throw new UnsupportedOperationException("Unsupported process argument kind: " + arg);
		}

		public default void visit(InputFileProcessArgumentTaskOption arg) {
			throw new UnsupportedOperationException("Unsupported process argument kind: " + arg);
		}

		public default void visit(SDKPathProcessArgumentTaskOption arg) {
			throw new UnsupportedOperationException("Unsupported process argument kind: " + arg);
		}

		public default void visit(SDKPropertyProcessArgumentTaskOption arg) {
			throw new UnsupportedOperationException("Unsupported process argument kind: " + arg);
		}
	}
}
