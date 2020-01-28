package testing.saker.process.cluster;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;
import testing.saker.process.ProcessTestUtils;

@SakerTest
public class SimpleClusterProcessTaskTest extends RepositoryLoadingVariablesMetricEnvironmentTestCase {
	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setClusterNames(ImmutableUtils.makeImmutableNavigableSet(new String[] { "cluster1", "cluster2" }))
				.build();
	}

	public static class WorkingDirWritingMain {
		public static void main(String[] args) {
			System.out.println(Paths.get("").toAbsolutePath());
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(WorkingDirWritingMain.class));

		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(
				new MultiplexOutputStream(ByteSink.toOutputStream(parameters.getStandardOutput()), stdout));

		Path c1mirrordir = getClusterMirrorDirectory("cluster1");
		Path c2mirrordir = getClusterMirrorDirectory("cluster2");

		stdout.reset();
		runScriptTask("build");
		assertTrue(stdout.toString().contains(c1mirrordir.toString()));
		assertFalse(stdout.toString().contains(c2mirrordir.toString()));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
