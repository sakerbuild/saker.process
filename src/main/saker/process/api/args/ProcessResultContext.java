package saker.process.api.args;

import saker.build.task.TaskContext;

public interface ProcessResultContext {
	public TaskContext getTaskContext();

	public int getExitCode();
}
