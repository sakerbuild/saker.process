package saker.process.impl.args;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.UUID;

import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
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
	 * The interested output files if it is a directory.
	 */
	private NavigableSet<WildcardPath> filesOfInterest;

	/**
	 * For {@link Externalizable}.
	 */
	public OutputFileProcessInvocationArgument() {
	}

	public OutputFileProcessInvocationArgument(FileLocation file) throws NullPointerException {
		this(file, null);
	}

	public OutputFileProcessInvocationArgument(FileLocation file, NavigableSet<WildcardPath> filesOfInterest)
			throws NullPointerException {
		Objects.requireNonNull(file, "file");
		this.file = file;
		this.filesOfInterest = filesOfInterest;
	}

	@Override
	public List<String> getArguments(ProcessInitializationContext argcontext) throws Exception {
		String[] result = { null };
		file.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				Path mirrorpath;
				try {
					mirrorpath = getExecutionFileArguments(argcontext, loc);
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
				result[0] = mirrorpath.toString();
			}

			@Override
			public void visit(LocalFileLocation loc) {
				SakerPath localpath = loc.getLocalPath();
				result[0] = localpath.toString();
				try {
					getLocalFileArguments(argcontext, localpath);
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

		});
		return ImmutableUtils.singletonList(result[0]);
	}

	private Path getExecutionFileArguments(ProcessInitializationContext argcontext, ExecutionFileLocation loc)
			throws Exception {
		//TODO handle filesOfInterest
		Path mirrorpath;
		SakerPath path = loc.getPath();
		if (shouldCreateParentDirectory()) {
			SakerDirectory dir = argcontext.getTaskContext().getTaskUtilities()
					.resolveDirectoryAtAbsolutePathCreate(loc.getPath().getParent());
			mirrorpath = argcontext.getTaskContext().mirror(dir, DirectoryVisitPredicate.nothing())
					.resolve(path.getFileName());
		} else {
			mirrorpath = argcontext.getTaskContext().getExecutionContext().toMirrorPath(path);
		}
		argcontext.addResultHandler(new ProcessResultHandler() {
			@Override
			public void handleProcessResult(ProcessResultContext context) throws Exception {
				ProviderHolderPathKey outpathkey = LocalFileProvider.getInstance().getPathKey(mirrorpath);
				SakerDirectory outdir = context.getTaskContext().getTaskUtilities()
						.resolveDirectoryAtPath(path.getParent());
				if (outdir == null) {
					throw new FileNotFoundException("Output directory for output file not found: " + path.getParent());
				}
				//XXX don't retrieve the content descriptor in a second call, but use a variant in utils if added
				context.getTaskContext().getTaskUtilities().addSynchronizeInvalidatedProviderPathFileToDirectory(outdir,
						outpathkey, path.getFileName());
				ContentDescriptor cd = context.getTaskContext().getExecutionContext().getContentDescriptor(outpathkey);
				if (DirectoryContentDescriptor.INSTANCE.equals(cd)) {
					SakerLog.warning().out(context.getTaskContext())
							.println("Output file at path: " + path + " is a directory. Expected regular file.");
				}
				context.getTaskContext().reportOutputFileDependency(null, path, cd);
			}
		});
		return mirrorpath;
	}

	private void getLocalFileArguments(ProcessInitializationContext argcontext, SakerPath localpath) throws Exception {
		//TODO handle filesOfInterest
		if (shouldCreateParentDirectory()) {
			LocalFileProvider.getInstance().createDirectories(localpath.getParent());
			//TODO we should probably invalidate the path and parents
		}
		argcontext.addResultHandler(new ProcessResultHandler() {
			@Override
			public void handleProcessResult(ProcessResultContext context) throws Exception {
				ContentDescriptor cd = context.getTaskContext()
						.invalidateGetContentDescriptor(LocalFileProvider.getInstance().getPathKey(localpath));
				if (DirectoryContentDescriptor.INSTANCE.equals(cd)) {
					SakerLog.warning().out(context.getTaskContext()).println(
							"Output file at local path: " + localpath + " is a directory. Expected regular file.");
				}
				context.getTaskContext().reportExecutionDependency(
						new LocalFileContentDescriptorExecutionProperty(UUID.randomUUID(), localpath), cd);
			}
		});
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
		SerialUtils.writeExternalCollection(out, filesOfInterest);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		file = (FileLocation) in.readObject();
		filesOfInterest = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((file == null) ? 0 : file.hashCode());
		result = prime * result + ((filesOfInterest == null) ? 0 : filesOfInterest.hashCode());
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
		if (filesOfInterest == null) {
			if (other.filesOfInterest != null)
				return false;
		} else if (!filesOfInterest.equals(other.filesOfInterest))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (file != null ? "file=" + file + ", " : "")
				+ (filesOfInterest != null ? "filesOfInterest=" + filesOfInterest : "") + "]";
	}

}
