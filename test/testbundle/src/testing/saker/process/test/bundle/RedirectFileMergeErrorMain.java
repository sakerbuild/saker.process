package testing.saker.process.test.bundle;

import java.util.Arrays;

import saker.build.file.path.SakerPath;
import saker.process.api.ProcessIOConsumer;
import saker.process.api.SakerProcess;
import saker.process.api.SakerProcessBuilder;

public class RedirectFileMergeErrorMain {
	public static void main(String[] args) throws Exception {
		SakerProcessBuilder pb = SakerProcessBuilder.create();
		if (!pb.getClass().getSimpleName().equals("NativeSakerProcessBuilder")) {
			throw new AssertionError("Unexpected class of process builder: " + pb);
		}
		pb.setCommand(Arrays.asList("java", "-jar", args[0]));
		pb.setStandardOutputConsumer(ProcessIOConsumer.redirectFile(SakerPath.valueOf(args[1])));
		pb.setStandardErrorMerge(true);

		try (SakerProcess proc = pb.start()) {
			proc.processIO();
			int exitcode = proc.waitFor();
			if (exitcode != 0) {
				throw new AssertionError(exitcode);
			}
			System.out.println("Exit code: " + exitcode);
		}
	}
}
