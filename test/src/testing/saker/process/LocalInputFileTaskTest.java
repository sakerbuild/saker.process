package testing.saker.process;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;

@SakerTest
public class LocalInputFileTaskTest extends SakerProcessTestCase {

	private Path jarPath;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.cppath", jarPath.toString());
		return result;
	}

	@Override
	protected void runProcessTestImpl() throws Throwable {
		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(
				new MultiplexOutputStream(ByteSink.toOutputStream(parameters.getStandardOutput()), stdout));

		jarPath = getBuildDirectory().resolve("echo.jar");
		LocalFileProvider.getInstance().createDirectories(jarPath.getParent());

		LocalFileProvider.getInstance()
				.writeToFile(new UnsyncByteArrayInputStream(InputFileTaskTest.genEchoJarBytes("first")), jarPath);

		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]first"));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		LocalFileProvider.getInstance()
		.writeToFile(new UnsyncByteArrayInputStream(InputFileTaskTest.genEchoJarBytes("second")), jarPath);
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]second"));
	}

}
