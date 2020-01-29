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
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;

@SakerTest
public class LocalInputDirFileTaskTest extends NestRepositoryCachingEnvironmentTestCase {

	private Path localDir;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.localdir", localDir.toString());
		return result;
	}

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(
				new MultiplexOutputStream(ByteSink.toOutputStream(parameters.getStandardOutput()), stdout));

		localDir = getBuildDirectory().resolve("cp");
		LocalFileProvider fp = LocalFileProvider.getInstance();
		fp.createDirectories(localDir);
		fp.clearDirectoryRecursively(localDir);

		fp.writeToFile(new UnsyncByteArrayInputStream(InputFileTaskTest.genEchoClassBytes("first")),
				localDir.resolve("EchoClass.class"));
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]first"));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		fp.writeToFile(new UnsyncByteArrayInputStream(InputFileTaskTest.genEchoClassBytes("second")),
				localDir.resolve("EchoClass.class"));
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]second"));
	}

}
