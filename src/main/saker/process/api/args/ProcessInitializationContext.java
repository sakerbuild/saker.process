package saker.process.api.args;

import java.util.NavigableMap;

import saker.build.task.TaskContext;
import saker.sdk.support.api.SDKReference;

public interface ProcessInitializationContext {
	public TaskContext getTaskContext();

	public NavigableMap<String, SDKReference> getSDKs();

	public void addResultHandler(ProcessResultHandler handler) throws NullPointerException;

	public void addStandardOutputConsumer(ProcessIOConsumer consumer) throws NullPointerException;

	public void addStandardErrorConsumer(ProcessIOConsumer consumer) throws NullPointerException;
}
