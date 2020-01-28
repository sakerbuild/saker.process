package testing.saker.process;

import java.io.IOException;
import java.io.PrintStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import saker.build.thirdparty.org.objectweb.asm.ClassWriter;
import saker.build.thirdparty.org.objectweb.asm.MethodVisitor;
import saker.build.thirdparty.org.objectweb.asm.Opcodes;
import saker.build.thirdparty.org.objectweb.asm.Type;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;

@SakerTest
public class InputFileTaskTest extends RepositoryLoadingVariablesMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(stdout);

		files.putFile(PATH_WORKING_DIRECTORY.resolve("echo.jar"), genEchoJarBytes("first"));
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]first"));

		stdout.reset();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(PATH_WORKING_DIRECTORY.resolve("echo.jar"), genEchoJarBytes("second"));
		stdout.reset();
		runScriptTask("build");
		assertEquals(listOf(stdout.toString().split("\r\n|\r|\n")), listOf("[proc.run]second"));
	}

	public static byte[] genEchoClassBytes(String contents) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "EchoClass", null, Type.getInternalName(Object.class), null);

		MethodVisitor mw = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V",
				null, null);
		mw.visitCode();
		mw.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out",
				Type.getDescriptor(PrintStream.class));
		mw.visitLdcInsn(contents);
		mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println",
				"(Ljava/lang/String;)V", false);
		mw.visitInsn(Opcodes.RETURN);
		mw.visitMaxs(0, 0);
		mw.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}

	public static byte[] genEchoJarBytes(String contents) throws IOException {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		try (JarOutputStream jaros = new JarOutputStream(baos)) {
			ZipEntry entry = new ZipEntry("EchoClass.class");
			jaros.putNextEntry(entry);
			jaros.write(genEchoClassBytes(contents));
			jaros.closeEntry();
		}
		return baos.toByteArray();
	}

}
