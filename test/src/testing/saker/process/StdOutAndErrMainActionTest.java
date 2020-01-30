package testing.saker.process;

import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;

@SakerTest
public class StdOutAndErrMainActionTest extends MainActionTestCase {
	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		repo.executeAction("main", "-bundle", "saker.process.test.bundle-v1", "-class",
				"testing.saker.process.test.bundle.StdOutAndErrMain",
				"-Unest.params.bundles=" + parameterBundlesString);
	}
}
