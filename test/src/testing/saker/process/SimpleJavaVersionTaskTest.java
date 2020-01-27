package testing.saker.process;

import java.util.List;

import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;

@SakerTest
public class SimpleJavaVersionTaskTest extends RepositoryLoadingVariablesMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		UnsyncByteArrayOutputStream taskout = new UnsyncByteArrayOutputStream();

		parameters.setStandardOutput(taskout);
		runScriptTask("build");

		ProcessBuilder pb = new ProcessBuilder("java", "-version");
		pb.redirectErrorStream(true);
		Process p = pb.start();
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		StreamUtils.copyStream(p.getInputStream(), baos);
		assertEquals(p.waitFor(), 0);
		List<String> expectedlines = listOf(
				listOf(baos.toString().split("\r\n|\n|\r")).stream().map(l -> "[proc.run]" + l).toArray(String[]::new));
		assertEquals(listOf(taskout.toString().split("\r\n|\n|\r")), expectedlines);
		
		taskout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
		assertEquals(taskout.size(), 0);
	}

}
