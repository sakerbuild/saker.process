package testing.saker.process.test.bundle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import saker.build.file.path.SakerPath;
import saker.process.api.CollectingProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class FileInputMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}
		pb.setCommand(Arrays.asList("java", "-jar", args[0]));
		pb.setStandardInputFile(SakerPath.valueOf(args[1]));
		pb.setStandardErrorMerge(true);
		CollectingProcessIOConsumer outcollector = new CollectingProcessIOConsumer();
		pb.setStandardOutputConsumer(outcollector);

		try (SakerProcess proc = pb.start()) {
			proc.processIO();
			int exitcode = proc.waitFor();
			if (exitcode != 0) {
				throw new AssertionError(exitcode);
			}
			byte[] expectedbytes = Files.readAllBytes(Paths.get(args[1]));
			if (!Arrays.equals(expectedbytes, outcollector.getByteArray())) {
				throw new AssertionError("Content mismatch: " + new String(expectedbytes, StandardCharsets.UTF_8)
						+ " and " + outcollector.getOutputString());
			}
			System.out.println("Exit code: " + exitcode);
		}
	}
}
