package saker.process.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.process.impl.JavaSakerProcessBuilder;
import saker.process.impl.NativeSakerProcessBuilder;
import saker.process.platform.NativeProcess;

public interface SakerProcessBuilder {
	public SakerProcessBuilder setCommand(List<String> command) throws NullPointerException;

	public Map<String, String> getEnvironment();

	public SakerProcessBuilder setWorkingDirectory(SakerPath directorypath) throws InvalidPathFormatException;

	public SakerProcessBuilder setStandardOutputConsumer(ProcessIOConsumer consumer);

	public SakerProcessBuilder setStandardErrorConsumer(ProcessIOConsumer consumer);

	//doc: clears the error consumer
	public SakerProcessBuilder setStandardErrorMerge(boolean mergestderr);

	public SakerProcessBuilder setStandardInputFile(SakerPath file) throws NullPointerException;

	public SakerProcessBuilder setStandardInputPipe(boolean pipe);

	public SakerProcess start() throws IllegalStateException, IOException;

	public static SakerProcessBuilder create() {
		if (NativeProcess.LOADED) {
			return new NativeSakerProcessBuilder();
		}
		return new JavaSakerProcessBuilder();
	}

	public static SakerProcessBuilder createJREBuilder() {
		return new JavaSakerProcessBuilder();
	}
}
