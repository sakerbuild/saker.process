package saker.process.impl.args;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.process.api.args.ProcessInitializationContext;
import saker.process.api.args.ProcessInvocationArgument;

public class JoinProcessInvocationArgument implements ProcessInvocationArgument, Externalizable {
	private static final long serialVersionUID = 1L;

	private String prefix;
	private String delimiter;
	private String suffix;
	private List<ProcessInvocationArgument> args;

	/**
	 * For {@link Externalizable}.
	 */
	public JoinProcessInvocationArgument() {
	}

	public JoinProcessInvocationArgument(List<ProcessInvocationArgument> args) {
		Objects.requireNonNull(args, "arguments");
		this.args = args;
	}

	public JoinProcessInvocationArgument(String prefix, String delimiter, List<ProcessInvocationArgument> args,
			String suffix) {
		Objects.requireNonNull(args, "arguments");
		this.prefix = prefix;
		this.delimiter = delimiter;
		this.args = args;
		this.suffix = suffix;
	}

	@Override
	public List<String> getArguments(ProcessInitializationContext argcontext) throws Exception {
		StringBuilder sb = new StringBuilder();
		if (prefix != null) {
			sb.append(prefix);
		}
		boolean first = true;
		for (ProcessInvocationArgument arg : args) {
			List<String> argres = arg.getArguments(argcontext);
			if (argres == null) {
				continue;
			}
			if (delimiter != null) {
				for (String a : argres) {
					if (!first) {
						sb.append(delimiter);
					} else {
						first = false;
					}
					sb.append(a);
				}
			} else {
				for (String a : argres) {
					sb.append(a);
				}
			}
		}
		if (suffix != null) {
			sb.append(suffix);
		}
		return ImmutableUtils.singletonList(sb.toString());
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(prefix);
		out.writeObject(delimiter);
		out.writeObject(suffix);
		SerialUtils.writeExternalCollection(out, args);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		prefix = (String) in.readObject();
		delimiter = (String) in.readObject();
		suffix = (String) in.readObject();
		args = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((args == null) ? 0 : args.hashCode());
		result = prime * result + ((delimiter == null) ? 0 : delimiter.hashCode());
		result = prime * result + ((prefix == null) ? 0 : prefix.hashCode());
		result = prime * result + ((suffix == null) ? 0 : suffix.hashCode());
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
		JoinProcessInvocationArgument other = (JoinProcessInvocationArgument) obj;
		if (args == null) {
			if (other.args != null)
				return false;
		} else if (!args.equals(other.args))
			return false;
		if (delimiter == null) {
			if (other.delimiter != null)
				return false;
		} else if (!delimiter.equals(other.delimiter))
			return false;
		if (prefix == null) {
			if (other.prefix != null)
				return false;
		} else if (!prefix.equals(other.prefix))
			return false;
		if (suffix == null) {
			if (other.suffix != null)
				return false;
		} else if (!suffix.equals(other.suffix))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (prefix != null ? "prefix=" + prefix + ", " : "")
				+ (args != null ? "args=" + args + ", " : "")
				+ (delimiter != null ? "delimiter=" + delimiter + ", " : "")
				+ (suffix != null ? "suffix=" + suffix : "") + "]";
	}

}
