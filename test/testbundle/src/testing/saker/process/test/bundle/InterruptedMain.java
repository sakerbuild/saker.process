package testing.saker.process.test.bundle;

import java.io.InterruptedIOException;
import java.util.Arrays;

import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class InterruptedMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}
		//start with interrupted thread
		Thread.currentThread().interrupt();

		pb.setCommand(Arrays.asList("java", "-version"));
		SakerProcess proc = pb.start();
		CollectingProcessIOConsumer stdout = new CollectingProcessIOConsumer();
		CollectingProcessIOConsumer stderr = new CollectingProcessIOConsumer();
		try {
			proc.processIO(stdout, stderr);
			throw new AssertionError("Interruption unnoticed.");
		} catch (InterruptedIOException e) {
			//clear the flag so we can wait for
			Thread.interrupted();
		}

		System.out.println("InterruptedMain.main() " + proc.waitFor());
	}
}
