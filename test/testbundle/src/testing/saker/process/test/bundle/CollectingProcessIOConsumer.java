package testing.saker.process.test.bundle;

import java.io.IOException;
import java.nio.ByteBuffer;

import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.process.api.ProcessIOConsumer;

public class CollectingProcessIOConsumer implements ProcessIOConsumer {
	private UnsyncByteArrayOutputStream out = new UnsyncByteArrayOutputStream();

	@Override
	public void handleOutput(ByteBuffer bytes) throws IOException {
		out.write(bytes);
	}

	public String getOutputString() {
		return out.toString();
	}

}
