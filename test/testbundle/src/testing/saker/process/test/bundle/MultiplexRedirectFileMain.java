package testing.saker.process.test.bundle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class MultiplexRedirectFileMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}
		pb.setCommand(Arrays.asList("java", "-jar", args[0]));
		ProcessIOConsumer redirectconsumer = ProcessIOConsumer.redirectFile(SakerPath.valueOf(args[1]));
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			pb.setStandardOutputConsumer(new ProcessIOConsumer() {
				@Override
				public void handleOutput(ByteBuffer bytes) throws IOException {
					bytes.mark();
					baos.write(bytes);
					bytes.reset();
					redirectconsumer.handleOutput(bytes);
				}

				@Override
				public void close() throws IOException {
					redirectconsumer.close();
				}
			});

			try (SakerProcess proc = pb.start()) {
				proc.processIO();

				if (!"print-stdout".equals(baos.toString())) {
					throw new AssertionError("content mismatch: " + baos);
				}

				int exitcode = proc.waitFor();
				if (exitcode != 0) {
					throw new AssertionError(exitcode);
				}

				System.out.println("Exit code: " + exitcode);
			}
		}
	}
}
