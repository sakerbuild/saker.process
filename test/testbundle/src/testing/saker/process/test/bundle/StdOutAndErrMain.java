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
		SakerProcess proc = pb.start();
		CollectingProcessIOConsumer stdout = new CollectingProcessIOConsumer();
		CollectingProcessIOConsumer stderr = new CollectingProcessIOConsumer();
		proc.processIO(stdout, stderr);
		if (!stdout.getOutputString().isEmpty()) {
			throw new AssertionError("stdout was written to");
		}
		System.out.println(stderr.getOutputString());
		System.out.println("StdOutAndErrMain.main() " + proc.waitFor());
	}
}
