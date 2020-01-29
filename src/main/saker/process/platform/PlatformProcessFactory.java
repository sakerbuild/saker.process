package saker.process.platform;

import java.io.IOException;
import java.util.Map;

import saker.build.file.path.SakerPath;

public interface PlatformProcessFactory {
	public NativeProcess startProcess(SakerPath exe, String[] commands, SakerPath workingdirectory, int flags,
			Map<String, String> environment) throws IOException, IllegalArgumentException;
}
