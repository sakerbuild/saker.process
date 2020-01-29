package testing.saker.process;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;

@SakerTest
public class LocalInputFileTaskTest extends RepositoryLoadingVariablesMetricEnvironmentTestCase {

	private Path jarPath;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.cppath", jarPath.toString());
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(stdout);

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
