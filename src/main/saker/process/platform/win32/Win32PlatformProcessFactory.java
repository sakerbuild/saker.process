package saker.process.platform.win32;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.process.platform.NativeProcess;
import saker.process.platform.PlatformProcessFactory;

public class Win32PlatformProcessFactory implements PlatformProcessFactory {
	@Override
	public NativeProcess startProcess(SakerPath exe, String[] commands, SakerPath workingdirectory, int flags,
			Map<String, String> environment) throws IOException, IllegalArgumentException {
		if (exe != null) {
			SakerPathFiles.requireAbsolutePath(exe);
		}
		if (workingdirectory != null) {
			SakerPathFiles.requireAbsolutePath(workingdirectory);
		}
		if (((flags & NativeProcess.FLAG_MERGE_STDERR) == NativeProcess.FLAG_MERGE_STDERR)) {
		} else {
			throw new UnsupportedOperationException("Non-merged std err is not yet supported.");
		}
		long event = Win32NativeProcess.native_createInterruptEvent();
		if (event == 0) {
			throw new IOException("Failed to create process interruption event.");
		}
		long nativeproc;
		try {
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
					nativeworkdirstr, flags, UUID.randomUUID().toString(), event, envstr);
		} catch (Throwable e) {
			try {
				Win32NativeProcess.native_closeInterruptEvent(event);
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
		return new Win32NativeProcess(nativeproc, event, flags);
	}
}
