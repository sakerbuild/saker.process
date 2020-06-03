package saker.process.impl.args;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.process.api.args.ProcessInitializationContext;
import saker.process.api.args.ProcessInvocationArgument;
import saker.sdk.support.api.SDKPropertyReference;
import saker.sdk.support.api.SDKSupportUtils;

public class SDKPropertyProcessInvocationArgument implements ProcessInvocationArgument, Externalizable {
	private static final long serialVersionUID = 1L;

	private SDKPropertyReference propertyReference;

	/**
	 * For {@link Externalizable}.
	 */
	public SDKPropertyProcessInvocationArgument() {
	}

	public SDKPropertyProcessInvocationArgument(SDKPropertyReference propertyReference) {
		Objects.requireNonNull(propertyReference, "property reference");
		this.propertyReference = propertyReference;
	}

	@Override
	public List<String> getArguments(ProcessInitializationContext argcontext) throws Exception {
		String prop = SDKSupportUtils.getSDKPropertyReferenceProperty(propertyReference, argcontext.getSDKs());
		return ImmutableUtils.singletonList(prop);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(propertyReference);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		propertyReference = (SDKPropertyReference) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((propertyReference == null) ? 0 : propertyReference.hashCode());
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
		SDKPropertyProcessInvocationArgument other = (SDKPropertyProcessInvocationArgument) obj;
		if (propertyReference == null) {
			if (other.propertyReference != null)
				return false;
		} else if (!propertyReference.equals(other.propertyReference))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + propertyReference + "]";
	}
}
