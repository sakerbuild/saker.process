package saker.process.api.args;

import java.util.NavigableMap;

import saker.build.task.TaskContext;
import saker.sdk.support.api.SDKDescription;

public interface ProcessArgumentContext {
	public TaskContext getTaskContext();
	
	public NavigableMap<String, SDKDescription> getSDKs();
}
