package testing.saker.process;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.process.OutputFileTaskTest.SimpleFileWritingMain;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class CreateParentDirOutputFileTaskTest extends SakerProcessTestCase {

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		Set<EnvironmentTestCaseConfiguration> result = ObjectUtils.newHashSet(super.getTestConfigurations());
		result.addAll(EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).addClusterName("cluster1")
				.addClusterName("cluster2").build());
		return result;
	}

	@Override
	protected void runProcessTestImpl() throws Throwable {
		runTestForTarget("build");
		if (!testConfiguration.getClusterNames().isEmpty()) {
			runTestForTarget("clusterbuild");
		}
	}

	private void runTestForTarget(String targetname)
			throws IOException, Throwable, AssertionError, DirectoryNotEmptyException {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(SimpleFileWritingMain.class));

		//clear the build directory for a clean test state
		LocalFileProvider.getInstance().clearDirectoryRecursively(getBuildDirectory());

		SakerPath outpath = PATH_BUILD_DIRECTORY.resolve("std.file.place/outdir/output.txt");
		files.delete(outpath);

		runScriptTask(targetname);
		assertEquals(files.getAllBytes(outpath).toString(), "content");

		runScriptTask(targetname);
		assertEmpty(getMetric().getRunTaskIdResults());

		files.delete(outpath);
		runScriptTask(targetname);
		assertEquals(files.getAllBytes(outpath).toString(), "content");

		runScriptTask(targetname);
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(outpath, "mod");
		runScriptTask(targetname);
		assertEquals(files.getAllBytes(outpath).toString(), "content");
	}
}
