package saker.process.impl.args;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Objects;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.process.api.args.ProcessArgumentContext;
import saker.process.api.args.ProcessInvocationArgument;
import saker.sdk.support.api.SDKPathReference;
import saker.sdk.support.api.SDKReference;

public class SDKPathProcessInvocationArgument implements ProcessInvocationArgument, Externalizable {
	private static final long serialVersionUID = 1L;

	private SDKPathReference pathReference;

	/**
	 * For {@link Externalizable}.
	 */
	public SDKPathProcessInvocationArgument() {
	}

	public SDKPathProcessInvocationArgument(SDKPathReference pathReference) {
		Objects.requireNonNull(pathReference, "path reference");
		this.pathReference = pathReference;
	}

	@Override
	public List<String> getArguments(ProcessArgumentContext argcontext) throws Exception {
		SDKReference sdk = argcontext.getSDKs().get(pathReference.getSDKName());
		if (sdk == null) {
			throw new IllegalArgumentException("SDK not found with name: " + pathReference.getSDKName()
					+ " for process invocation argument: " + this);
		}
		SakerPath path = pathReference.getPath(sdk);
		if (path == null) {
			throw new IllegalArgumentException("SDK path not found for reference: " + pathReference);
		}
		return ImmutableUtils.singletonList(path.toString());
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(pathReference);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		pathReference = (SDKPathReference) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pathReference == null) ? 0 : pathReference.hashCode());
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
		SDKPathProcessInvocationArgument other = (SDKPathProcessInvocationArgument) obj;
		if (pathReference == null) {
			if (other.pathReference != null)
				return false;
		} else if (!pathReference.equals(other.pathReference))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + pathReference + "]";
	}
}
