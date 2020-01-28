package testing.saker.process;

import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;

@SakerTest
public class JoinArgumentTaskTest extends RepositoryLoadingVariablesMetricEnvironmentTestCase {

	public static class EchoMain {
		public static void main(String[] args) {
			System.out.println(args[0]);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(EchoMain.class));
		
		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(stdout);
		
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]abc"));
		
		stdout.reset();
		runScriptTask("delimited");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]a;b;c"));
	}

}
