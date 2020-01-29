package saker.process.impl.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.MultiPathContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.path.WildcardPath.ItemLister;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.TransformingNavigableMap;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class LocalDirectoryContentDescriptorExecutionProperty
		implements ExecutionProperty<MultiPathContentDescriptor>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Object tag;
	private SakerPath path;
	private NavigableSet<WildcardPath> filesOfInterest;

	/**
	 * For {@link Externalizable}.
	 */
	public LocalDirectoryContentDescriptorExecutionProperty() {
	}

	public LocalDirectoryContentDescriptorExecutionProperty(Object tag, SakerPath path,
			NavigableSet<WildcardPath> filesOfInterest) {
		Objects.requireNonNull(path, "path");
		this.tag = tag;
		this.path = path;
		this.filesOfInterest = ImmutableUtils.makeImmutableNavigableSet(filesOfInterest);
	}

	public LocalDirectoryContentDescriptorExecutionProperty(Object tag, SakerPath path) {
		Objects.requireNonNull(path, "path");
		this.tag = tag;
		this.path = path;
		this.filesOfInterest = null;
	}

	@Override
	public MultiPathContentDescriptor getCurrentValue(ExecutionContext executioncontext) throws Exception {
		// XXX should use some bulk method to query the content descriptors for the files
		LocalFileProvider fp = LocalFileProvider.getInstance();
		NavigableMap<SakerPath, ? extends BasicFileAttributes> files;
		if (filesOfInterest == null) {
			files = new TransformingNavigableMap<SakerPath, BasicFileAttributes, SakerPath, BasicFileAttributes>(
					fp.getDirectoryEntriesRecursively(path)) {
				@Override
				protected Entry<SakerPath, BasicFileAttributes> transformEntry(SakerPath key,
						BasicFileAttributes value) {
					return ImmutableUtils.makeImmutableMapEntry(path.append(key), value);
				}
			};
		} else {
			files = WildcardPath.getItems(filesOfInterest, ItemLister.forFileProvider(fp, path));
		}
		SortedMap<SakerPath, ContentDescriptor> contents = new TreeMap<>();
		for (Entry<SakerPath, ? extends BasicFileAttributes> entry : files.entrySet()) {
			contents.put(path, executioncontext.getContentDescriptor(fp.getPathKey(entry.getKey())));
		}
		return new MultiPathContentDescriptor(contents);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(tag);
		out.writeObject(path);
		SerialUtils.writeExternalCollection(out, filesOfInterest);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		tag = in.readObject();
		path = (SakerPath) in.readObject();
		filesOfInterest = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((filesOfInterest == null) ? 0 : filesOfInterest.hashCode());
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
		LocalDirectoryContentDescriptorExecutionProperty other = (LocalDirectoryContentDescriptorExecutionProperty) obj;
		if (filesOfInterest == null) {
			if (other.filesOfInterest != null)
				return false;
		} else if (!filesOfInterest.equals(other.filesOfInterest))
			return false;
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
		return getClass().getSimpleName() + "[" + (path != null ? "path=" + path + ", " : "")
				+ (filesOfInterest != null ? "filesOfInterest=" + filesOfInterest : "") + "]";
	}

}
