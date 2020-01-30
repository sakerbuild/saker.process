/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.process.platform;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Native;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import saker.build.file.path.SakerPath;

public abstract class NativeProcess implements Closeable {
	private static final PlatformProcessFactory PLATFORM_PROCESS_FACTORY;
	public static final boolean LOADED;
	static {
		boolean loaded;
		PlatformProcessFactory procfactory;
		try {
			System.loadLibrary("nativeprocess");
			procfactory = (PlatformProcessFactory) NativeProcess.class.getClassLoader()
					.loadClass(native_getNativePlatformProcessFactoryImplementationClassName()).getConstructor()
					.newInstance();
			loaded = true;
		} catch (LinkageError | Exception e) {
			//can happen if the classloader was reloaded, but still not garbage collected
			//    or if any other implementation error occurs
			//don't print stacktrace, as we don't need it, but print an error nonetheless
			System.err.println("Failed to load native process library of saker.process-platform: " + e);
			loaded = false;
			procfactory = null;
		}
		LOADED = loaded;
		PLATFORM_PROCESS_FACTORY = procfactory;
	}

	@Native
	public static final int FLAG_MERGE_STDERR = 1 << 0;
	@Native
	public static final int FLAG_NULL_STDOUT = 1 << 1;
	@Native
	public static final int FLAG_NULL_STDERR = 1 << 2;

	public static NativeProcess startNativeProcess(SakerPath exe, String[] commands, SakerPath workingdirectory,
			int flags, Map<String, String> environment) throws IOException, IllegalArgumentException {
		if (!LOADED) {
			throw new IOException("Failed to load native platform process builder.");
		}
		if (((flags & (FLAG_MERGE_STDERR | FLAG_NULL_STDOUT)) == (FLAG_MERGE_STDERR | FLAG_NULL_STDOUT))) {
			throw new IllegalArgumentException("Cannot use FLAG_MERGE_STDERR and FLAG_NULL_STDOUT together.");
		}
		return PLATFORM_PROCESS_FACTORY.startProcess(exe, commands, workingdirectory, flags, environment);
	}

	public abstract void processIO(NativeProcessIOConsumer stdoutprocessor, NativeProcessIOConsumer stderrprocessor)
			throws IOException, InterruptedIOException, IllegalArgumentException;

	public abstract Integer exitValue() throws IllegalThreadStateException, IOException;

	public abstract int waitFor() throws InterruptedException, IOException;

	public abstract Integer waitFor(long timeout, TimeUnit unit) throws InterruptedException, IOException;

	private static native String native_getNativePlatformProcessFactoryImplementationClassName();
}
