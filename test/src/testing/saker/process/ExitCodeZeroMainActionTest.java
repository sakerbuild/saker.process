package testing.saker.process;

import java.nio.file.Files;
import java.nio.file.Path;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.process.util.MainActionTestCase;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class ExitCodeZeroMainActionTest extends MainActionTestCase {
	public static final String UUID = "6ec7c56a-25d6-484f-b511-794382fea874";

	public static class ExiterMain {
		public static void main(String[] args) {
			System.exit(Integer.parseInt(args[0]));
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path outdir = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/"));
		Path jarpath = outdir.resolve("cp.jar");

		Files.createDirectories(outdir);

		Files.write(jarpath, ProcessTestUtils.createJarWithMainAndClassFileBytes(ExiterMain.class).copyOptionally());
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.ExitCodeTestMain", "-Unest.params.bundles=" + parameterBundlesString,
				jarpath.toString(), "0");
		assertEquals(System.clearProperty(UUID), "0");
	}
}
