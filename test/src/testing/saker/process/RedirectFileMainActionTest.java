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
public class RedirectFileMainActionTest extends MainActionTestCase {
	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path outdir = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/"));
		Path jarpath = outdir.resolve("cp.jar");
		Path outputpath = outdir.resolve("output.txt");
		Path errputpath = outdir.resolve("errput.txt");

		Files.createDirectories(outdir);
		Files.deleteIfExists(outputpath);
		Files.deleteIfExists(errputpath);

		Files.write(jarpath, ProcessTestUtils.createJarWithMainAndClassFileBytes(DualMain.class).copyOptionally());
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.RedirectFileMain", "-Unest.params.bundles=" + parameterBundlesString,
				jarpath.toString(), outputpath.toString(), errputpath.toString());

		assertEquals(Files.readAllBytes(outputpath), "print-stdout".getBytes());
		assertEquals(Files.readAllBytes(errputpath), "print-stderr".getBytes());
	}
}
