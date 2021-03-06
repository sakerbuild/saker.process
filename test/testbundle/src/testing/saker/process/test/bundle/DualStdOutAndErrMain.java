package testing.saker.process.test.bundle;

import java.util.Arrays;

import saker.process.api.CollectingProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class DualStdOutAndErrMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}
		pb.setCommand(Arrays.asList("java", "-jar", args[0]));
		CollectingProcessIOConsumer stdout = new CollectingProcessIOConsumer();
		CollectingProcessIOConsumer stderr = new CollectingProcessIOConsumer();
		pb.setStandardOutputConsumer(stdout);
		pb.setStandardErrorConsumer(stderr);

		try (SakerProcess proc = pb.start()) {
			proc.processIO();
			if (!stdout.getOutputString().equals("print-stdout")) {
				throw new AssertionError();
			}
			if (!stderr.getOutputString().equals("print-stderr")) {
				throw new AssertionError();
			}
			System.out.println("----- stdout -----");
			System.out.println(stdout.getOutputString());
			System.out.println("----- stderr -----");
			System.out.println(stderr.getOutputString());
			System.out.println("-----  end   -----");
			int exitcode = proc.waitFor();
			if (exitcode != 0) {
				throw new AssertionError(exitcode);
			}
			System.out.println("Exit code: " + exitcode);
		}
	}
}
