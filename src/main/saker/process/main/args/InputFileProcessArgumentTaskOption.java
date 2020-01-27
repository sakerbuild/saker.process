package saker.process.main.args;

import saker.std.api.file.location.FileLocation;

public class InputFileProcessArgumentTaskOption extends ProcessArgumentTaskOption {

	private FileLocation file;

	public InputFileProcessArgumentTaskOption(FileLocation file) {
		this.file = file;
	}

	public FileLocation getFile() {
		return file;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + file + "]";
	}

}
