package saker.process.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcessBuilder;

public abstract class SakerProcessBuilderBase implements SakerProcessBuilder {
	protected List<String> command;
	protected SakerPath workingDirectory;

	protected Map<String, String> environment;

	protected ProcessIOConsumer standardErrorConsumer;
	protected ProcessIOConsumer standardOutputConsumer;
	protected boolean mergeStandardError;

	@Override
	public SakerProcessBuilder setCommand(List<String> command) {
		Objects.requireNonNull(command, "command");
		this.command = command;
		return this;
	}

	@Override
	public Map<String, String> getEnvironment() {
		Map<String, String> result = this.environment;
		if (result == null) {
			this.environment = new TreeMap<>(System.getenv());
			result = this.environment;
		}
		return result;
	}

	@Override
	public SakerProcessBuilder setWorkingDirectory(SakerPath directorypath) {
		if (directorypath != null) {
			SakerPathFiles.requireAbsolutePath(directorypath);
			this.workingDirectory = directorypath;
		} else {
			this.workingDirectory = null;
		}
		return this;
	}

	@Override
	public SakerProcessBuilder setStandardOutputConsumer(ProcessIOConsumer consumer) {
		this.standardOutputConsumer = consumer;
		return null;
	}

	@Override
	public SakerProcessBuilder setStandardErrorMerge(boolean mergestderr) {
		this.standardErrorConsumer = null;
		this.mergeStandardError = mergestderr;
		return this;
	}

	@Override
	public SakerProcessBuilder setStandardErrorConsumer(ProcessIOConsumer consumer) {
		this.standardErrorConsumer = consumer;
		this.mergeStandardError = false;
		return null;
	}
}
