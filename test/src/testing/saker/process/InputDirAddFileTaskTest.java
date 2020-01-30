package testing.saker.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class InputDirAddFileTaskTest extends SakerProcessTestCase {

	public static class TestMain {
		public static void main(String[] args) throws Exception {
			Path dirpath = Paths.get(args[0]);
			Set<Path> files = Files.list(dirpath).collect(Collectors.toCollection(() -> new TreeSet<>()));
			for (Path p : files) {
				System.out.println(dirpath.relativize(p));
			}
		}
	}

	@Override
	protected void runProcessTestImpl() throws Throwable {
		SakerPath dirpath = PATH_WORKING_DIRECTORY.resolve("dir");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(TestMain.class));
		files.createDirectories(dirpath);

		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(
				new MultiplexOutputStream(ByteSink.toOutputStream(parameters.getStandardOutput()), stdout));

		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf(""));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(dirpath.resolve("file.txt"), "1");
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]file.txt"));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(dirpath.resolve("file2.txt"), "2");
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")),
				listOf("[proc.run]file.txt", "[proc.run]file2.txt"));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.delete(dirpath.resolve("file.txt"));
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]file2.txt"));
	}

}
