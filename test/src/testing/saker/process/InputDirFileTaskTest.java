package testing.saker.process;

import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;

@SakerTest
public class InputDirFileTaskTest extends SakerProcessTestCase {

	@Override
	protected void runProcessTestImpl() throws Throwable {
		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(
				new MultiplexOutputStream(ByteSink.toOutputStream(parameters.getStandardOutput()), stdout));

		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp/EchoClass.class"),
				InputFileTaskTest.genEchoClassBytes("first"));
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]first"));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp/EchoClass.class"),
				InputFileTaskTest.genEchoClassBytes("second"));
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]second"));
	}

}
