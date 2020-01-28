package saker.process.impl.run;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;

import saker.process.api.args.ProcessArgumentContext;
import saker.process.api.args.ProcessInvocationArgument;
import saker.process.api.args.ProcessResultContext;
import saker.process.api.args.ProcessResultHandler;

public class OutputBindingProcessInvocationArgument implements ProcessInvocationArgument, Externalizable {
	private static final long serialVersionUID = 1L;

	private ProcessInvocationArgument arg;
	private String name;
	private Object value;

	/**
	 * For {@link Externalizable}.
	 */
	public OutputBindingProcessInvocationArgument() {
	}

	public OutputBindingProcessInvocationArgument(ProcessInvocationArgument arg, String name, Object value) {
		this.arg = arg;
		this.name = name;
		this.value = value;
	}

	@Override
	public List<String> getArguments(ProcessArgumentContext argcontext) throws Exception {
		List<String> result = arg.getArguments(argcontext);
		argcontext.addResultHandler(new ProcessResultHandler() {
			@Override
			public void handleProcessResult(ProcessResultContext context) throws Exception {
				context.bindOutput(name, value);
			}
		});
		return result;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(arg);
		out.writeUTF(name);
		out.writeObject(value);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		arg = (ProcessInvocationArgument) in.readObject();
		name = in.readUTF();
		value = in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((arg == null) ? 0 : arg.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
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
		OutputBindingProcessInvocationArgument other = (OutputBindingProcessInvocationArgument) obj;
		if (arg == null) {
			if (other.arg != null)
				return false;
		} else if (!arg.equals(other.arg))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (arg != null ? "arg=" + arg + ", " : "")
				+ (name != null ? "name=" + name + ", " : "") + (value != null ? "value=" + value : "") + "]";
	}

}
