package saker.process.impl.args;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.process.api.args.ProcessInitializationContext;
import saker.process.api.args.ProcessInvocationArgument;
import saker.process.api.args.ProcessResultContext;
import saker.process.api.args.ProcessResultHandler;
import saker.process.impl.util.LocalFileContentDescriptorExecutionProperty;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;

public class OutputFileProcessInvocationArgument implements ProcessInvocationArgument, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation file;

	/**
	 * For {@link Externalizable}.
	 */
	public OutputFileProcessInvocationArgument() {
	}

	public OutputFileProcessInvocationArgument(FileLocation file) throws NullPointerException {
		Objects.requireNonNull(file, "file");
		this.file = file;
	}

	@Override
	public List<String> getArguments(ProcessInitializationContext argcontext) throws Exception {
		String[] result = { null };
		file.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				SakerPath path = loc.getPath();
				Path mirrorpath;
				if (shouldCreateParentDirectory()) {
					SakerDirectory dir = argcontext.getTaskContext().getTaskUtilities()
							.resolveDirectoryAtAbsolutePathCreate(loc.getPath().getParent());
					try {
						mirrorpath = argcontext.getTaskContext().mirror(dir, DirectoryVisitPredicate.nothing())
								.resolve(path.getFileName());
					} catch (IOException e) {
						throw ObjectUtils.sneakyThrow(e);
					}
				} else {
					mirrorpath = argcontext.getTaskContext().getExecutionContext().toMirrorPath(path);
				}
				result[0] = mirrorpath.toString();
				argcontext.addResultHandler(new ProcessResultHandler() {
					@Override
					public void handleProcessResult(ProcessResultContext context) throws Exception {
						ProviderHolderPathKey outpathkey = LocalFileProvider.getInstance().getPathKey(mirrorpath);
						SakerDirectory outdir = context.getTaskContext().getTaskUtilities()
								.resolveDirectoryAtPath(path.getParent());
						if (outdir == null) {
							throw new FileNotFoundException(
									"Output directory for output file not found: " + path.getParent());
						}
						//XXX don't retrieve the content descriptor in a second call, but use a variant in utils if added
						context.getTaskContext().getTaskUtilities()
								.addSynchronizeInvalidatedProviderPathFileToDirectory(outdir, outpathkey,
										path.getFileName());
						context.getTaskContext().reportOutputFileDependency(null, path,
								context.getTaskContext().getExecutionContext().getContentDescriptor(outpathkey));
					}
				});
			}

			@Override
			public void visit(LocalFileLocation loc) {
				SakerPath localpath = loc.getLocalPath();
				result[0] = localpath.toString();
				if (shouldCreateParentDirectory()) {
					try {
						LocalFileProvider.getInstance().createDirectories(localpath.getParent());
						//TODO we should probably invalidate the path and parents
					} catch (IOException e) {
						throw ObjectUtils.sneakyThrow(e);
					}
				}
				argcontext.addResultHandler(new ProcessResultHandler() {
					@Override
					public void handleProcessResult(ProcessResultContext context) throws Exception {
						ContentDescriptor cd = context.getTaskContext()
								.invalidateGetContentDescriptor(LocalFileProvider.getInstance().getPathKey(localpath));
						if (DirectoryContentDescriptor.INSTANCE.equals(cd)) {
							SakerLog.warning().out(context.getTaskContext()).println(
									"Output file at path: " + localpath + " is a directory. Expected regular file.");
						}
						context.getTaskContext().reportExecutionDependency(
								new LocalFileContentDescriptorExecutionProperty(UUID.randomUUID(), localpath), cd);
					}
				});
			}
		});
		return ImmutableUtils.singletonList(result[0]);
	}

	@Override
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		return InputFileProcessInvocationArgument.getTaskExecutionEnvironmentSelectorForFileLocation(file);
	}

	protected boolean shouldCreateParentDirectory() {
		return false;
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
		OutputFileProcessInvocationArgument other = (OutputFileProcessInvocationArgument) obj;
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
