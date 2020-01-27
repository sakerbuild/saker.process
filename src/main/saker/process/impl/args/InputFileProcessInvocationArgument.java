package saker.process.impl.args;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Objects;

import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities.MirroredFileContents;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.process.api.args.ProcessArgumentContext;
import saker.process.api.args.ProcessInvocationArgument;
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
	public List<String> getArguments(ProcessArgumentContext argcontext) throws Exception {
		String[] result = { null };
		file.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				//TODO support directories
				TaskContext taskcontext = argcontext.getTaskContext();
				try {
					MirroredFileContents filecontents = taskcontext.getTaskUtilities().mirrorFileAtPathContents(loc.getPath());
					taskcontext.reportInputFileDependency(null, loc.getPath(), filecontents.getContents());
					result[0]=filecontents.getPath().toString();
				} catch (NullPointerException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

			@Override
			public void visit(LocalFileLocation loc) {
				// TODO support local file input
				FileLocationVisitor.super.visit(loc);
			}
		});
		return ImmutableUtils.singletonList(result[0]);
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
