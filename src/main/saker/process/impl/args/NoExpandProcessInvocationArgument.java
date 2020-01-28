package saker.process.impl.args;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.process.api.args.ProcessInitializationContext;
import saker.process.api.args.ProcessInvocationArgument;

public class NoExpandProcessInvocationArgument implements ProcessInvocationArgument, Externalizable {
	private static final long serialVersionUID = 1L;

	private List<? extends ProcessInvocationArgument> args;

	/**
	 * For {@link Externalizable}.
	 */
	public NoExpandProcessInvocationArgument() {
	}

	public NoExpandProcessInvocationArgument(ProcessInvocationArgument arg) {
		Objects.requireNonNull(arg, "argument");
		this.args = ImmutableUtils.singletonList(arg);
	}

	public NoExpandProcessInvocationArgument(Collection<? extends ProcessInvocationArgument> args) {
		Objects.requireNonNull(args, "arguments");
		if (ObjectUtils.hasNull(args)) {
			throw new NullPointerException("Arguments contain null.");
		}
		this.args = ImmutableUtils.makeImmutableList(args);
	}

	@Override
	public List<String> getArguments(ProcessInitializationContext argcontext) throws Exception {
		for (ProcessInvocationArgument a : args) {
			a.getArguments(argcontext);
		}
		return Collections.emptyList();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, args);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		args = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((args == null) ? 0 : args.hashCode());
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
		NoExpandProcessInvocationArgument other = (NoExpandProcessInvocationArgument) obj;
		if (args == null) {
			if (other.args != null)
				return false;
		} else if (!args.equals(other.args))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + args;
	}

}
