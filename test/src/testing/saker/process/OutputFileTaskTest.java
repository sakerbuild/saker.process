package testing.saker.process;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;

@SakerTest
public class OutputFileTaskTest extends RepositoryLoadingVariablesMetricEnvironmentTestCase {
	public static class SimpleFileWritingMain {
		public static final String RUN_OUTPUT_STRING = "SimpleFileWritingMain.main()";

		public static void main(String[] args) throws Exception {
			Path outpath = Paths.get(args[0]);
			Files.createDirectories(outpath.getParent());
			Files.write(outpath, "content".getBytes());
			
			System.out.println(RUN_OUTPUT_STRING);
		}
	}

	public static class MakeDirTaskFactory implements TaskFactory<SakerPath>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public MakeDirTaskFactory() {
		}

		@Override
		public Task<? extends SakerPath> createTask(ExecutionContext arg0) {
			return new ParameterizableTask<SakerPath>() {
				@SakerInput(value = { "" }, required = true)
				public SakerPath path;

				@Override
				public SakerPath run(TaskContext taskcontext) throws Exception {
					taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(path);
					return path;
				}

			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected CollectingTestMetric createMetricImpl() {
		CollectingTestMetric result = super.createMetricImpl();
		TreeMap<TaskName, TaskFactory<?>> injected = ObjectUtils.cloneTreeMap(result.getInjectedTaskFactories());
		injected.put(TaskName.valueOf("test.mkdir"), new MakeDirTaskFactory());
		result.setInjectedTaskFactories(injected);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("cp.jar"),
				ProcessTestUtils.createJarWithMainAndClassFileBytes(SimpleFileWritingMain.class));

		//clear the build directory for a clean test state
		LocalFileProvider.getInstance().clearDirectoryRecursively(getBuildDirectory());

		SakerPath outpath = PATH_BUILD_DIRECTORY.resolve("std.file.place/outdir/output.txt");

		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.delete(outpath);
		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(outpath, "mod");
		runScriptTask("build");
		assertEquals(files.getAllBytes(outpath).toString(), "content");
	}

}
