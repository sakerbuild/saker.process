package saker.process.api.args;

public interface ProcessResultHandler {
	public void handleProcessResult(ProcessResultContext context) throws Exception;
}
