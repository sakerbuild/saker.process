package saker.process.api.args;

import java.util.NavigableMap;

import saker.build.task.TaskContext;
import saker.sdk.support.api.SDKReference;

public interface ProcessArgumentContext {
	public TaskContext getTaskContext();
	
	public NavigableMap<String, SDKReference> getSDKs();
}
