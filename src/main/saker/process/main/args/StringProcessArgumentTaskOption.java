package saker.process.main.args;

public class StringProcessArgumentTaskOption extends ProcessArgumentTaskOption {
	private String argument;

	public StringProcessArgumentTaskOption(String arg) {
		this.argument = arg;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	public String getArgument() {
		return argument;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + argument + "]";
	}

}
