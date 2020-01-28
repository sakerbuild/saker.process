package saker.process.api.run;

import java.util.Map;

public interface RunProcessTaskOutput {
	public int getExitCode();

	public Map<String, ?> getOutput();
}
