package testing.saker.process;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;
import testing.saker.process.OutputFileTaskTest.SimpleFileWritingMain;

@SakerTest
public class OutputFileBindingTaskTest extends RepositoryLoadingVariablesMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(
				new MultiplexOutputStream(ByteSink.toOutputStream(parameters.getStandardOutput()), stdout));

		files.putFile(PATH_WORKING_DIRECTORY.resolve("outcp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(SimpleFileWritingMain.class));

		//clear the build directory for a clean test state
		LocalFileProvider.getInstance().clearDirectoryRecursively(getBuildDirectory());

		SakerPath outpath = PATH_BUILD_DIRECTORY.resolve("std.file.place/outdir/output.txt");
		SakerPath copypath = PATH_BUILD_DIRECTORY.resolve("std.file.place/copydir/copy.txt");

		stdout.reset();
		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");
		assertEquals(files.getAllBytes(copypath).toString(), "content");
		assertEquals(listOf(stdout.toString().split("\r\n|\n|\r")),
				listOf("[proc.run]" + SimpleFileWritingMain.RUN_OUTPUT_STRING));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
		assertEquals(files.getAllBytes(outpath).toString(), "content");
		assertEquals(files.getAllBytes(copypath).toString(), "content");

		stdout.reset();
		files.delete(copypath);
		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");
		assertEquals(files.getAllBytes(copypath).toString(), "content");
		assertEquals(stdout.toString(), "");
		
		stdout.reset();
		files.putFile(outpath, "mod");
		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");
		assertEquals(files.getAllBytes(copypath).toString(), "content");
		assertEquals(listOf(stdout.toString().split("\r\n|\n|\r")),
				listOf("[proc.run]" + SimpleFileWritingMain.RUN_OUTPUT_STRING));
	}

}
