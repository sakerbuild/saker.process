package saker.process.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.process.api.ProcessIOConsumer;

public final class RedirectFileProcessIOConsumer implements ProcessIOConsumer {
	private SakerPath path;
	private WritableByteChannel channel;
	private boolean closed;

	public RedirectFileProcessIOConsumer(SakerPath path) {
		this.path = path;
	}

	@Override
	public void handleOutput(ByteBuffer bytes) throws IOException {
		if (channel == null) {
			channel = Files.newByteChannel(LocalFileProvider.toRealPath(path), StandardOpenOption.WRITE,
					StandardOpenOption.CREATE);
		}
		channel.write(bytes);
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		if (channel == null) {
			//the file wasn't opened. create an empty file
			try (FileChannel channel = FileChannel.open(LocalFileProvider.toRealPath(path), StandardOpenOption.WRITE,
					StandardOpenOption.CREATE)) {
				channel.truncate(0);
			}
		} else {
			channel.close();
		}
	}

	public void setClosed() {
		this.closed = true;
	}

	public SakerPath getPath() {
		return path;
	}
}
