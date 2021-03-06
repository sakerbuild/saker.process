package saker.process.impl.args;

import java.io.Externalizable;
import java.util.NavigableSet;

import saker.build.file.path.WildcardPath;
import saker.std.api.file.location.FileLocation;

public final class ParentDirectoryCreateOutputFileProcessInvocationArgument
		extends OutputFileProcessInvocationArgument {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public ParentDirectoryCreateOutputFileProcessInvocationArgument() {
	}

	public ParentDirectoryCreateOutputFileProcessInvocationArgument(FileLocation file) throws NullPointerException {
		super(file);
	}

	public ParentDirectoryCreateOutputFileProcessInvocationArgument(FileLocation file,
			NavigableSet<WildcardPath> filesOfInterest) throws NullPointerException {
		super(file, filesOfInterest);
	}

	@Override
	protected boolean shouldCreateParentDirectory() {
		return true;
	}
}