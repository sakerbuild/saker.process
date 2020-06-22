package testing.saker.process.test.bundle;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.process.api.CollectingProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class LotsOfDataMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}

		CollectingProcessIOConsumer stdout = new CollectingProcessIOConsumer();
		CollectingProcessIOConsumer stderr = new CollectingProcessIOConsumer();
		pb.setStandardOutputConsumer(stdout);
		pb.setStandardErrorConsumer(stderr);

		List<String> command = new ArrayList<>();
		command.add("java");
		command.add("-jar");
		command.add(args[0]);
		pb.setCommand(command);

		try (SakerProcess proc = pb.start()) {
			proc.processIO();
			int exitcode = proc.waitFor();
			if (exitcode != 0) {
				throw new AssertionError(exitcode);
			}
			System.out.println("Exit code: " + exitcode);
		}
		UnsyncByteArrayOutputStream mycontents = new UnsyncByteArrayOutputStream();
		try (PrintStream ps = new PrintStream(mycontents)) {
			Random r = new Random(123456);
			for (int i = 0; i < 100000; i++) {
				ps.println(r.nextLong());
			}
		}
		byte[] mycontentsarray = mycontents.toByteArray();
		if (!Arrays.equals(mycontentsarray, stdout.getByteArray())) {
			throw new AssertionError("stdout");
		}
		if (!Arrays.equals(mycontentsarray, stderr.getByteArray())) {
			throw new AssertionError("stderr");
		}
	}
}
