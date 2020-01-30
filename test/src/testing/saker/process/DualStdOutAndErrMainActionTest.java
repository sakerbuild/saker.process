package testing.saker.process;

import java.nio.file.Files;
import java.nio.file.Path;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.process.util.MainActionTestCase;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class DualStdOutAndErrMainActionTest extends MainActionTestCase {

	public static class DualMain {
		public static void main(String[] args) {
			System.out.print("print-stdout");
			System.err.print("print-stderr");
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path jarpath = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/")).resolve("cp.jar");
		Files.createDirectories(jarpath.getParent());

		Files.write(jarpath, ProcessTestUtils.createJarWithMainAndClassFileBytes(DualMain.class).copyOptionally());
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.DualStdOutAndErrMain",
				"-Unest.params.bundles=" + parameterBundlesString, jarpath.toString());
	}
}
