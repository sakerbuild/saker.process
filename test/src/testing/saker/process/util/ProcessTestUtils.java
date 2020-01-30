package testing.saker.process.util;

import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;

public class ProcessTestUtils {
	private static final FileTime FILETIME_ZERO = FileTime.fromMillis(0);

	private ProcessTestUtils() {
		throw new UnsupportedOperationException();
	}

	public static ByteArrayRegion createJarWithMainAndClassFileBytes(Class<?> mainclass, Class<?>... classes)
			throws IOException {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainclass.getName());
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		try (JarOutputStream jaros = new JarOutputStream(baos, manifest)) {
			addJarClasses(jaros, mainclass);
			addJarClasses(jaros, classes);
		}
		return baos.toByteArrayRegion();
	}

	public static ByteArrayRegion createJarWithClassFileBytes(Class<?>... classes) throws IOException {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		try (JarOutputStream jaros = new JarOutputStream(baos)) {
			addJarClasses(jaros, classes);
		}
		return baos.toByteArrayRegion();
	}

	private static void addJarClasses(JarOutputStream jaros, Class<?>... classes) throws IOException {
		for (Class<?> c : classes) {
			addJarClassBytes(jaros, c);
		}
	}

	private static void addJarClassBytes(JarOutputStream jaros, Class<?> c) throws IOException {
		ZipEntry entry = new ZipEntry(c.getName().replace('.', '/') + ".class");
		entry.setLastAccessTime(FILETIME_ZERO);
		entry.setCreationTime(FILETIME_ZERO);
		entry.setLastModifiedTime(FILETIME_ZERO);

		jaros.putNextEntry(entry);
		ReflectUtils.getClassBytesUsingClassLoader(c).writeTo(jaros);
		jaros.closeEntry();
	}
}
