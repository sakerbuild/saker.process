package testing.saker.process;

import java.util.Map.Entry;

import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;

@SakerTest
public class EnvironmentVariableTest extends NestRepositoryCachingEnvironmentTestCase {
	
	public static class EnvironmentEchoMain {
		public static void main(String[] args) {
			for (Entry<String, String> entry : System.getenv().entrySet()) {
				System.out.println(entry.getKey() + "=" + entry.getValue());
			}
		}
	}

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(EnvironmentEchoMain.class));

		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(
				new MultiplexOutputStream(ByteSink.toOutputStream(parameters.getStandardOutput()), stdout));

		stdout.reset();
		runScriptTask("build");
		assertTrue(listOf(stdout.toString().split("\r\n|\r|\n")).contains("[proc.run]MY_ENVIRONMENT=VALUE"));
	}
}
