package testing.saker.process;

import java.nio.file.Files;
import java.nio.file.Path;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.process.DualStdOutAndErrMainActionTest.DualMain;
import testing.saker.process.util.MainActionTestCase;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class RedirectFileMergeErrorMainActionTest extends MainActionTestCase {
	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path outdir = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/"));
		Path jarpath = outdir.resolve("cp.jar");
		Path outputpath = outdir.resolve("output.txt");

		Files.createDirectories(outdir);
		Files.deleteIfExists(outputpath);

		Files.write(jarpath, ProcessTestUtils.createJarWithMainAndClassFileBytes(DualMain.class).copyOptionally());
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.RedirectFileMergeErrorMain", "-Unest.params.bundles=" + parameterBundlesString,
				jarpath.toString(), outputpath.toString());

		assertEquals(Files.readAllBytes(outputpath), "print-stdoutprint-stderr".getBytes());
	}
}
