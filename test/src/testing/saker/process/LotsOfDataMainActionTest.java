package testing.saker.process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.process.util.MainActionTestCase;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class LotsOfDataMainActionTest extends MainActionTestCase {
	public static class ContentGeneratorMain {
		public static void main(String[] args) throws IOException {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			try (PrintStream ps = new PrintStream(os)) {
				Random r = new Random(123456);
				for (int i = 0; i < 100000; i++) {
					ps.println(r.nextLong());
				}
			}
			System.out.write(os.toByteArray());
			System.err.write(os.toByteArray());
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path jarpath = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/")).resolve("cp.jar");
		Files.createDirectories(jarpath.getParent());

		Files.write(jarpath,
				ProcessTestUtils.createJarWithMainAndClassFileBytes(ContentGeneratorMain.class).copyOptionally());
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.LotsOfDataMain", "-Unest.params.bundles=" + parameterBundlesString,
				jarpath.toString());
	}
}
