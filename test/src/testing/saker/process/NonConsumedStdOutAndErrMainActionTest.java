package testing.saker.process;

import java.nio.file.Files;
import java.nio.file.Path;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.process.util.MainActionTestCase;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class NonConsumedStdOutAndErrMainActionTest extends MainActionTestCase {
	//test that if the output and error is not consumed, the process still finished rather than halt because of unread output
	public static class NonConsumedMain {
		public static void main(String[] args) {
			for (int i = 0; i < 1024 * 64; i++) {
				System.out.print(i);
				System.err.print(i);
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path jarpath = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/")).resolve("cp.jar");
		Files.createDirectories(jarpath.getParent());

		Files.write(jarpath,
				ProcessTestUtils.createJarWithMainAndClassFileBytes(NonConsumedMain.class).copyOptionally());
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.NonConsumedStdOutAndErrMain",
				"-Unest.params.bundles=" + parameterBundlesString, jarpath.toString());
	}
}
