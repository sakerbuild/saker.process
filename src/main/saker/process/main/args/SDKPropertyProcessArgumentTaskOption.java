package saker.process.main.args;

import saker.sdk.support.api.SDKPropertyReference;

public class SDKPropertyProcessArgumentTaskOption extends ProcessArgumentTaskOption {

	private SDKPropertyReference propertyReference;

	public SDKPropertyProcessArgumentTaskOption(SDKPropertyReference propertyReference) {
		this.propertyReference = propertyReference;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	public SDKPropertyReference getPropertyReference() {
		return propertyReference;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + propertyReference + "]";
	}
}
