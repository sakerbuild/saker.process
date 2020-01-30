package testing.saker.process;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.SakerTest;
import testing.saker.process.OutputFileTaskTest.SimpleFileWritingMain;
import testing.saker.process.util.ProcessTestUtils;

@SakerTest
public class LocalOutputFileTaskTest extends SakerProcessTestCase {

	private Path outputDir;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.outdir", outputDir.toString());
		return result;
	}

	@Override
	protected void runProcessTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(SimpleFileWritingMain.class));

		outputDir = getBuildDirectory().resolve("outdir");
		Path outpath = outputDir.resolve("output.txt");
		LocalFileProvider fp = LocalFileProvider.getInstance();
		fp.createDirectories(outputDir);

		//clear the build directory for a clean test state
		fp.clearDirectoryRecursively(outputDir);

		runScriptTask("build");
		assertEquals(fp.getAllBytes(outpath).toString(), "content");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		fp.delete(outpath);
		runScriptTask("build");
		assertEquals(fp.getAllBytes(outpath).toString(), "content");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		fp.writeToFile(new UnsyncByteArrayInputStream("mod".getBytes()), outpath);
		runScriptTask("build");
		assertEquals(fp.getAllBytes(outpath).toString(), "content");
	}

}
