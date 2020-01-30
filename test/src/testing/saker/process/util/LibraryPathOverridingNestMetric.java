package testing.saker.process.util;

import java.nio.file.Path;

import testing.saker.nest.NestMetric;

public class LibraryPathOverridingNestMetric implements NestMetric {
	private Path basePath;

	public LibraryPathOverridingNestMetric(Path basePath) {
		this.basePath = basePath;
	}

	@Override
	public Path overrideNativeLibraryPath(ClassLoader cl, Path libpath) {
		return basePath.resolve(libpath.getFileName());
	}
}
