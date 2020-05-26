package saker.process.platform.win32;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.process.platform.NativeProcess;
import saker.process.platform.NativeProcessIOConsumer;
import saker.process.platform.PlatformProcessFactory;
import saker.process.platform.RedirectFileNativeProcessIOConsumer;

public class Win32PlatformProcessFactory implements PlatformProcessFactory {
	@Override
	public NativeProcess startProcess(SakerPath exe, String[] commands, SakerPath workingdirectory, int flags,
			Map<String, String> environment, NativeProcessIOConsumer standardOutputConsumer,
			NativeProcessIOConsumer standardErrorConsumer, SakerPath standardinputfile)
			throws IOException, IllegalArgumentException {
		if (exe != null) {
			SakerPathFiles.requireAbsolutePath(exe);
		}
		if (workingdirectory != null) {
			SakerPathFiles.requireAbsolutePath(workingdirectory);
		}

		String stdinfilepath = null;
		if (standardinputfile != null) {
			SakerPathFiles.requireAbsolutePath(standardinputfile);
			stdinfilepath = Win32NativeProcess.getPathForNative(standardinputfile);
		}

		String stdoutfileredirect = null;
		String stderrfileredirect = null;
		if (standardOutputConsumer instanceof RedirectFileNativeProcessIOConsumer) {
			stdoutfileredirect = Win32NativeProcess
					.getPathForNative(((RedirectFileNativeProcessIOConsumer) standardOutputConsumer).getPath());
			standardOutputConsumer = null;
		}
		if (standardErrorConsumer instanceof RedirectFileNativeProcessIOConsumer) {
			stderrfileredirect = Win32NativeProcess
					.getPathForNative(((RedirectFileNativeProcessIOConsumer) standardErrorConsumer).getPath());
			standardErrorConsumer = null;
		}

		long nativeproc;
		String nativeworkdirstr;
		if (workingdirectory != null) {
			nativeworkdirstr = Win32NativeProcess.getPathForNative(workingdirectory);
		} else if (exe != null) {
			nativeworkdirstr = Win32NativeProcess.getPathForNative(exe.getParent());
		} else {
			nativeworkdirstr = null;
		}
		String envstr;
		if (environment == null) {
			//inherit env
			envstr = null;
		} else {
			StringBuilder sb = new StringBuilder();
			for (Entry<String, String> entry : environment.entrySet()) {
				sb.append(entry.getKey());
				sb.append('=');
				sb.append(entry.getValue());
				sb.append('\0');
			}
			sb.append('\0');
			envstr = sb.toString();
		}
		nativeproc = Win32NativeProcess.native_startProcess(Win32NativeProcess.getPathForNative(exe), commands,
				nativeworkdirstr, flags, UUID.randomUUID().toString(), envstr, standardOutputConsumer,
				standardErrorConsumer, stdoutfileredirect, stderrfileredirect, stdinfilepath);
		return new Win32NativeProcess(nativeproc, standardOutputConsumer != null, standardErrorConsumer != null);
	}
}
