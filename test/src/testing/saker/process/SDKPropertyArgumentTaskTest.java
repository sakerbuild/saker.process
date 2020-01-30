package testing.saker.process;

import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class SDKPropertyArgumentTaskTest extends SakerProcessTestCase {
	public static class TestMain {
		public static void main(String[] args) throws Exception {
			System.out.println(args[0]);
		}
	}

	@Override
	protected void runProcessTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(TestMain.class));
		
		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(
				new MultiplexOutputStream(ByteSink.toOutputStream(parameters.getStandardOutput()), stdout));

		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]val"));
		
		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
