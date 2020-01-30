package testing.saker.process;

import java.nio.file.Files;
import java.nio.file.Path;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.process.util.MainActionTestCase;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class DelayInterruptedMainActionTest extends MainActionTestCase {

	public static class LongWritingMain {
		public static void main(String[] args) throws InterruptedException {
			for (int i = 0; i < 30; i++) {
				System.out.println(i);
				System.err.println(i);
				Thread.sleep(100);
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path jarpath = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/")).resolve("cp.jar");
		Files.createDirectories(jarpath.getParent());

		Files.write(jarpath,
				ProcessTestUtils.createJarWithMainAndClassFileBytes(LongWritingMain.class).copyOptionally());

		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.DelayInterruptedMain",
				"-Unest.params.bundles=" + parameterBundlesString, jarpath.toString());
	}
}
