package testing.saker.process.test.bundle;

import java.util.Arrays;

import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class NonConsumedStdOutAndErrMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}
		pb.setCommand(Arrays.asList("java", "-jar", args[0]));
		SakerProcess proc = pb.start();
		proc.processIO();

		int exitcode = proc.waitFor();
		if (exitcode != 0) {
			throw new AssertionError(exitcode);
		}
		System.out.println("Exit code: " + exitcode);
	}
}
