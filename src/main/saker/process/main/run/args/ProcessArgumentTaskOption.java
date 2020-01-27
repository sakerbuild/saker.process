package saker.process.main.run.args;

import saker.sdk.support.api.SDKPathReference;
import saker.sdk.support.api.SDKPropertyReference;
import saker.std.api.file.location.FileLocation;

public abstract class ProcessArgumentTaskOption {

	public abstract void accept(Visitor visitor);

	public static ProcessArgumentTaskOption valueOf(String arg) {
		return new StringProcessArgumentTaskOption(arg);
	}

	//TODO support these kinds of inputs

//	public static ProcessArgumentTaskOption valueOf(FileLocation arg) {
//
//	}
//
//	public static ProcessArgumentTaskOption valueOf(SDKPathReference arg) {
//
//	}
//
//	public static ProcessArgumentTaskOption valueOf(SDKPropertyReference arg) {
//
//	}

	public interface Visitor {
		public default void visit(StringProcessArgumentTaskOption arg) {
			throw new UnsupportedOperationException("Unsupported process argument kind: " + arg);
		}
	}
}
