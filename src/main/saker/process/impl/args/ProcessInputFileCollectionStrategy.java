package saker.process.impl.args;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableMap;

import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public class ProcessInputFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath path;

	/**
	 * For {@link Externalizable}.
	 */
	public ProcessInputFileCollectionStrategy() {
	}

	public ProcessInputFileCollectionStrategy(SakerPath path) {
		this.path = path;
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
			TaskDirectoryContext taskdirectorycontext) {
		SakerFile f = SakerPathFiles.resolveAtAbsolutePath(executiondirectorycontext, path);
		if (f == null) {
			return Collections.emptyNavigableMap();
		}
		if (f instanceof SakerDirectory) {
			NavigableMap<SakerPath, SakerFile> res = ((SakerDirectory) f).getFilesRecursiveByPath(path,
					DirectoryVisitPredicate.everything());
			res.put(path, f);
			return res;
		}
		return ImmutableUtils.singletonNavigableMap(path, f);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(path);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		path = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
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
		ProcessInputFileCollectionStrategy other = (ProcessInputFileCollectionStrategy) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (path != null ? "path=" + path : "") + "]";
	}

}
