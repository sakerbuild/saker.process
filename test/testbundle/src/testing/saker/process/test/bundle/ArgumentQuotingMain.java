package testing.saker.process.test.bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class ArgumentQuotingMain {
	private static final char[] TEST_CHARS = { '\'', '\"', ' ', 'a', '\\' };
	private static final Random RANDOM = new Random();

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
		for (int i = 0; i < 2000; i++) {
			int len = RANDOM.nextInt(5) + 1;
			char[] chars = new char[len];
			for (int j = 0; j < chars.length; j++) {
				chars[j] = TEST_CHARS[RANDOM.nextInt(TEST_CHARS.length)];
			}
			command.add(new String(chars));
		}
		pb.setCommand(command);

		List<String> quotedargs = command.subList(3, command.size());

		try (SakerProcess proc = pb.start()) {
			proc.processIO();
			System.out.println("----- stdout -----");
			String stdoutstr = stdout.getOutputString();
			List<String> stdoutlines = Arrays.asList(stdoutstr.split("\r\n|\r|\n"));
			if (!stdoutlines.equals(quotedargs)) {
				if (stdoutlines.size() != quotedargs.size()) {
					throw new AssertionError(
							"Quoting size mismatch. " + stdoutlines.size() + " - " + quotedargs.size());
				}
				throw new AssertionError("Quoting mismatch.");
			}
			System.out.println(stdoutstr);
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
