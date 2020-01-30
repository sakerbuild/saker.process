package testing.saker.process;

import java.nio.file.Path;

import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.nest.ForwardingNestMetric;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;

public abstract class SakerProcessTestCase extends NestRepositoryCachingEnvironmentTestCase {

	private ForwardingNestMetric nativeLibOverrideMetric;

	@Override
	protected final void runNestTaskTestImpl() throws Throwable {
		Path nativelibbasepath = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(getClass().getSimpleName());
		nativeLibOverrideMetric = new ForwardingNestMetric(TestFlag.metric()) {
			@Override
			public Path overrideNativeLibraryPath(ClassLoader cl, Path libpath) {
				return nativelibbasepath.resolve(libpath.getFileName());
			}
		};
		TestFlag.set(nativeLibOverrideMetric);
		runProcessTestImpl();
	}

	protected abstract void runProcessTestImpl() throws Throwable;

}
