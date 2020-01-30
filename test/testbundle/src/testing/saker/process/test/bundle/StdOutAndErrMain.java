package testing.saker.process.test.bundle;

import java.util.Arrays;

import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class StdOutAndErrMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}
		pb.setCommand(Arrays.asList("java", "-version"));
		CollectingProcessIOConsumer stdout = new CollectingProcessIOConsumer();
		CollectingProcessIOConsumer stderr = new CollectingProcessIOConsumer();
		pb.setStandardOutputConsumer(stdout);
		pb.setStandardErrorConsumer(stderr);

		SakerProcess proc = pb.start();
		proc.processIO();
		if (!stdout.getOutputString().isEmpty()) {
			throw new AssertionError("stdout was written to");
		}
		System.out.println(stderr.getOutputString());
		int exitcode = proc.waitFor();
		if (exitcode != 0) {
			throw new AssertionError(exitcode);
		}
		System.out.println("Exit code: " + exitcode);
	}
}
