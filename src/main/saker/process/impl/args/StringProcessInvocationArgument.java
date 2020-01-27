package saker.process.impl.args;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.process.api.args.ProcessArgumentContext;
import saker.process.api.args.ProcessInvocationArgument;

public class StringProcessInvocationArgument implements ProcessInvocationArgument, Externalizable {
	private static final long serialVersionUID = 1L;

	private String argument;

	/**
	 * For {@link Externalizable}.
	 */
	public StringProcessInvocationArgument() {
	}

	public StringProcessInvocationArgument(String argument) throws NullPointerException {
		Objects.requireNonNull(argument, "argument");
		this.argument = argument;
	}

	@Override
	public List<String> getArguments(ProcessArgumentContext argcontext) {
		return ImmutableUtils.singletonList(argument);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(argument);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		argument = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((argument == null) ? 0 : argument.hashCode());
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
		StringProcessInvocationArgument other = (StringProcessInvocationArgument) obj;
		if (argument == null) {
			if (other.argument != null)
				return false;
		} else if (!argument.equals(other.argument))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + argument + "]";
	}

}
