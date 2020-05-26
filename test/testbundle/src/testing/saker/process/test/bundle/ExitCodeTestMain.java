package testing.saker.process.test.bundle;

import java.util.Arrays;

import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class ExitCodeTestMain {
	public static final String UUID = "6ec7c56a-25d6-484f-b511-794382fea874";

	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}

		int expectedec = Integer.parseInt(args[1]);

		pb.setCommand(Arrays.asList("java", "-jar", args[0], args[1]));

		try (SakerProcess proc = pb.start()) {
			proc.processIO();
			int exitcode = proc.waitFor();
			System.setProperty(UUID, Integer.toString(exitcode));
			if (expectedec != exitcode) {
				throw new AssertionError(exitcode + " with expected: " + expectedec);
			}
			System.out.println("Exit code: " + exitcode);
		}
	}
}
