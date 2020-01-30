package testing.saker.process.util;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.environment.RepositoryManager;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

public abstract class MainActionTestCase extends SakerTestCase {
	protected Map<String, String> parameters;
	protected String parameterBundlesString;

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		Path nativelibbasepath = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(getClass().getSimpleName());
		LibraryPathOverridingNestMetric metric = new LibraryPathOverridingNestMetric(nativelibbasepath);
		TestFlag.set(metric);

		this.parameters = parameters;

		parameterBundlesString = createParameterBundlesString(parameters);

		JarFileClassPathLocation repoloc = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(LocalFileProvider.getInstance(),
						NestIntegrationTestUtils.getTestParameterNestRepositoryJar(parameters)));

		ServiceLoaderClassPathServiceEnumerator<SakerRepositoryFactory> serviceloader = new ServiceLoaderClassPathServiceEnumerator<>(
				SakerRepositoryFactory.class);

		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(getStorageDirectory());
				RepositoryManager repomanager = new RepositoryManager(classpathmanager,
						EnvironmentTestCase.getSakerJarPath())) {
			try (SakerRepository repo = repomanager.loadRepository(repoloc, serviceloader)) {
				runTestOnRepo(repo);
			}
		}
		//to prevent gc
		System.out.println(metric);
	}

	private static String createParameterBundlesString(Map<String, String> parameters) {
		String parambundlepaths = parameters.get("RepositoryParameterBundles");
		String[] parambundles = parambundlepaths.split("[;]+");
		Set<String> parambundlepathpaths = new LinkedHashSet<>();
		for (String pb : parambundles) {
			if (ObjectUtils.isNullOrEmpty(pb)) {
				continue;
			}
			parambundlepathpaths.add("//" + pb);
		}
		String parambundlesstr = String.join(";", parambundlepathpaths);
		return parambundlesstr;
	}

	protected Path getStorageDirectory() {
		return EnvironmentTestCase.getStorageDirectoryPath();
	}

	protected abstract void runTestOnRepo(SakerRepository repo) throws Exception;

}
