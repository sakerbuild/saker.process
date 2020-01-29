package testing.saker.process.cluster;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;
import testing.saker.process.ProcessTestUtils;
import testing.saker.process.cluster.SimpleClusterProcessTaskTest.WorkingDirWritingMain;

@SakerTest
public class LocalFileNoClusterTaskTest extends NestRepositoryCachingEnvironmentTestCase {
	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setClusterNames(ImmutableUtils.makeImmutableNavigableSet(new String[] { "cluster1", "cluster2" }))
				.build();
	}

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.localinpath", getWorkingDirectory().toString());
		return result;
	}

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(WorkingDirWritingMain.class));

		//the task cannot be run, as the local file argument prevents it from running on a cluster
		//however, the SDK requires cluster1, therefore there's no suitable environment to run.
		//XXX reify exception class
		assertException(Exception.class, () -> runScriptTask("build"));
	}

}
