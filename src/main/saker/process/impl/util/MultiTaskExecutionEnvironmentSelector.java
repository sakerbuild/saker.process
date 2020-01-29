package saker.process.impl.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.AnyTaskExecutionEnvironmentSelector;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class MultiTaskExecutionEnvironmentSelector implements TaskExecutionEnvironmentSelector, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<? extends TaskExecutionEnvironmentSelector> selectors;

	/**
	 * For {@link Externalizable}.
	 */
	public MultiTaskExecutionEnvironmentSelector() {
	}

	private MultiTaskExecutionEnvironmentSelector(Set<? extends TaskExecutionEnvironmentSelector> selectors) {
		this.selectors = selectors;
	}

	public static TaskExecutionEnvironmentSelector create(
			Set<? extends TaskExecutionEnvironmentSelector> selectors) {
		if (selectors.isEmpty()) {
			return AnyTaskExecutionEnvironmentSelector.INSTANCE;
		}
		if (selectors.size() == 1) {
			return selectors.iterator().next();
		}
		return new MultiTaskExecutionEnvironmentSelector(ImmutableUtils.makeImmutableHashSet(selectors));
	}

	@Override
	public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment env) {
		Map<EnvironmentProperty<?>, Object> qualifierproperties = new HashMap<>();
		for (TaskExecutionEnvironmentSelector s : selectors) {
			EnvironmentSelectionResult res = s.isSuitableExecutionEnvironment(env);
			if (res == null) {
				//unsuitable
				return null;
			}
			qualifierproperties.putAll(res.getQualifierEnvironmentProperties());
		}
		return new EnvironmentSelectionResult(qualifierproperties);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, selectors);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		selectors = SerialUtils.readExternalImmutableHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((selectors == null) ? 0 : selectors.hashCode());
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
		MultiTaskExecutionEnvironmentSelector other = (MultiTaskExecutionEnvironmentSelector) obj;
		if (selectors == null) {
			if (other.selectors != null)
				return false;
		} else if (!selectors.equals(other.selectors))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + selectors;
	}

}
