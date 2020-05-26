package testing.saker.process;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.process.util.MainActionTestCase;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class FileInputMainActionTest extends MainActionTestCase {

	public static class EchoMain {
		public static void main(String[] args) throws Exception {
			byte[] buffer = new byte[8196];
			for (int read; (read = System.in.read(buffer)) > 0;) {
				System.out.write(buffer, 0, read);
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		Path outdir = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getCanonicalName().replace(".", "/"));
		Path jarpath = outdir.resolve("cp.jar");
		Path inputpath = outdir.resolve("input.txt");

		Files.createDirectories(outdir);
		Files.write(inputpath, "the-contents".getBytes(StandardCharsets.UTF_8));

		Files.write(jarpath, ProcessTestUtils.createJarWithMainAndClassFileBytes(EchoMain.class).copyOptionally());
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.FileInputMain", "-Unest.params.bundles=" + parameterBundlesString,
				jarpath.toString(), inputpath.toString());
	}
}
