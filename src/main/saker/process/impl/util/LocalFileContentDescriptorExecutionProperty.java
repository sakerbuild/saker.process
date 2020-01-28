/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.process.impl.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;

public class LocalFileContentDescriptorExecutionProperty
		implements ExecutionProperty<ContentDescriptor>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Object tag;
	private SakerPath path;

	/**
	 * For {@link Externalizable}.
	 */
	public LocalFileContentDescriptorExecutionProperty() {
	}

	public LocalFileContentDescriptorExecutionProperty(Object tag, SakerPath path) {
		this.tag = tag;
		this.path = path;
	}

	@Override
	public ContentDescriptor getCurrentValue(ExecutionContext executioncontext) {
		try {
			ContentDescriptor result = executioncontext
					.getContentDescriptor(LocalFileProvider.getInstance().getPathKey(path));
			return result;
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(tag);
		out.writeObject(path);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		tag = in.readObject();
		path = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((tag == null) ? 0 : tag.hashCode());
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
		LocalFileContentDescriptorExecutionProperty other = (LocalFileContentDescriptorExecutionProperty) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (tag == null) {
			if (other.tag != null)
				return false;
		} else if (!tag.equals(other.tag))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[path=" + path + "]";
	}

}
