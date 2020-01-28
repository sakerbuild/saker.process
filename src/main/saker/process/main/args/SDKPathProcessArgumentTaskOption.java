package saker.process.main.args;

import saker.sdk.support.api.SDKPathReference;

public class SDKPathProcessArgumentTaskOption extends ProcessArgumentTaskOption {

	private SDKPathReference pathReference;

	public SDKPathProcessArgumentTaskOption(SDKPathReference pathReference) {
		this.pathReference = pathReference;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	public SDKPathReference getPathReference() {
		return pathReference;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + pathReference + "]";
	}
}
