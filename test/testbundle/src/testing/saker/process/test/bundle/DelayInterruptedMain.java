package testing.saker.process.test.bundle;

import java.io.InterruptedIOException;
import java.util.Arrays;

import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class DelayInterruptedMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}

		CollectingProcessIOConsumer stdout = new CollectingProcessIOConsumer();
		CollectingProcessIOConsumer stderr = new CollectingProcessIOConsumer();
		pb.setCommand(Arrays.asList("java", "-jar", args[0]));
		pb.setStandardOutputConsumer(stdout);
		pb.setStandardErrorConsumer(stderr);

		Thread mainthread = Thread.currentThread();
		Thread interruptingthread = new Thread(() -> {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
			mainthread.interrupt();
		});
		interruptingthread.setDaemon(true);
		interruptingthread.start();
		SakerProcess proc = pb.start();
		try {
			proc.processIO();
			throw new AssertionError("Interruption unnoticed.");
		} catch (InterruptedIOException e) {
			//clear the flag so we can wait for
			Thread.interrupted();
		}

		if (stdout.getOutputString().isEmpty()) {
			throw new AssertionError("No stdout was captured");
		}
		if (stderr.getOutputString().isEmpty()) {
			throw new AssertionError("No stderr was captured");
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
