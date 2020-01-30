package testing.saker.process.test.bundle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class StdOutAndErrMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}
		pb.setCommand(Arrays.asList("java", "-version"));
		SakerProcess proc = pb.start();
		try (UnsyncByteArrayOutputStream stdout = new UnsyncByteArrayOutputStream();
				UnsyncByteArrayOutputStream stderr = new UnsyncByteArrayOutputStream()) {
			proc.processIO(new ProcessIOConsumer() {
				@Override
				public void handleOutput(ByteBuffer bytes) throws IOException {
					stdout.write(bytes);
				}
			}, new ProcessIOConsumer() {
				@Override
				public void handleOutput(ByteBuffer bytes) throws IOException {
					stderr.write(bytes);
				}
			});
			if (!stdout.isEmpty()) {
				throw new AssertionError("stdout was written to");
			}
			System.out.println(stderr);
			System.out.println("StdOutAndErrMain.main() " + proc.waitFor());
		}
	}
}
