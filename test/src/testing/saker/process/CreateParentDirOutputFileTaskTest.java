package testing.saker.process;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import testing.saker.SakerTest;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;
import testing.saker.process.OutputFileTaskTest.SimpleFileWritingMain;

@SakerTest
public class CreateParentDirOutputFileTaskTest extends RepositoryLoadingVariablesMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(SimpleFileWritingMain.class));

		//clear the build directory for a clean test state
		LocalFileProvider.getInstance().clearDirectoryRecursively(getBuildDirectory());

		SakerPath outpath = PATH_BUILD_DIRECTORY.resolve("std.file.place/outdir/output.txt");

		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.delete(outpath);
		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(outpath, "mod");
		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");
	}
}
