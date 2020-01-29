package saker.process.impl.args;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.UUID;

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.AnyTaskExecutionEnvironmentSelector;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionUtilities;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.file.FixedDirectoryVisitPredicate;
import saker.process.api.args.ProcessInitializationContext;
import saker.process.api.args.ProcessInvocationArgument;
import saker.process.impl.util.LocalDirectoryContentDescriptorExecutionProperty;
import saker.process.impl.util.LocalFileContentDescriptorExecutionProperty;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;

public class InputFileProcessInvocationArgument implements ProcessInvocationArgument, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation file;

	/**
	 * For {@link Externalizable}.
	 */
	public InputFileProcessInvocationArgument() {
	}

	public InputFileProcessInvocationArgument(FileLocation file) {
		Objects.requireNonNull(file, "file");
		this.file = file;
	}

	@Override
	public List<String> getArguments(ProcessInitializationContext argcontext) throws Exception {
		String[] result = { null };
		file.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				TaskContext taskcontext = argcontext.getTaskContext();
				SakerPath path = loc.getPath();
				NavigableMap<SakerPath, SakerFile> files = taskcontext.getTaskUtilities()
						.collectFilesReportInputFileAndAdditionDependency(null,
								new ProcessInputFileCollectionStrategy(path));
				if (files.isEmpty()) {
					throw ObjectUtils.sneakyThrow(new FileNotFoundException("Input file not found: " + path));
				}
				SakerFile f = files.get(path);
				if (f == null) {
					throw ObjectUtils.sneakyThrow(new FileNotFoundException("Input file not found: " + path));
				}
				try {
					if (f instanceof SakerDirectory) {
						result[0] = taskcontext
								.mirror(f,
										new FixedDirectoryVisitPredicate(
												SakerPathFiles.relativizeSubPath(files.navigableKeySet(), path)))
								.toString();
					} else {
						result[0] = taskcontext.mirror(f).toString();
					}
				} catch (IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

			@Override
			public void visit(LocalFileLocation loc) {
				TaskExecutionUtilities taskutils = argcontext.getTaskContext().getTaskUtilities();
				UUID uuidtag = UUID.randomUUID();
				SakerPath localpath = loc.getLocalPath();
				ContentDescriptor cd = taskutils.getReportExecutionDependency(
						new LocalFileContentDescriptorExecutionProperty(uuidtag, localpath));
				result[0] = localpath.toString();
				if (!DirectoryContentDescriptor.INSTANCE.equals(cd)) {
					//the file is a simple file. the dependency is reported, we can finish here
					return;
				}
				//file is a directory
				taskutils.getReportExecutionDependency(
						new LocalDirectoryContentDescriptorExecutionProperty(uuidtag, localpath));
			}
		});
		return ImmutableUtils.singletonList(result[0]);
	}

	@Override
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		return getTaskExecutionEnvironmentSelectorForFileLocation(this.file);
	}

	public static TaskExecutionEnvironmentSelector getTaskExecutionEnvironmentSelectorForFileLocation(
			FileLocation file) {
		TaskExecutionEnvironmentSelector[] result = { null };
		file.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				result[0] = AnyTaskExecutionEnvironmentSelector.INSTANCE;
			}

			@Override
			public void visit(LocalFileLocation loc) {
				//stay as null, restrict to local build environment
			}
		});
		return result[0];
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(file);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		file = (FileLocation) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((file == null) ? 0 : file.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		InputFileProcessInvocationArgument other = (InputFileProcessInvocationArgument) obj;
		if (file == null) {
			if (other.file != null)
				return false;
		} else if (!file.equals(other.file))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + file + "]";
	}
}
