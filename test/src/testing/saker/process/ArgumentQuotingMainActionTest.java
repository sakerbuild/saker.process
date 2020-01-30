package testing.saker.process;

import java.nio.file.Files;
import java.nio.file.Path;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.process.util.MainActionTestCase;
import testing.saker.process.util.ProcessTestUtils;

/**
 * The quoting of arguments with the legacy {@link ProcessBuilder} has bugs: <br>
 * https://bugs.openjdk.java.net/browse/JDK-8131908
 * <p>
 * This test checks if our implementation can properly deal with it.
 */
@SakerTest
public class ArgumentQuotingMainActionTest extends MainActionTestCase {
	public static class ArgumentEchoLinesMain {
		public static void main(String[] args) {
			for (String a : args) {
				System.out.println(a);
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path jarpath = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/")).resolve("cp.jar");
		Files.createDirectories(jarpath.getParent());

		Files.write(jarpath,
				ProcessTestUtils.createJarWithMainAndClassFileBytes(ArgumentEchoLinesMain.class).copyOptionally());
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.ArgumentQuotingMain",
				"-Unest.params.bundles=" + parameterBundlesString, jarpath.toString());
	}
}
