package ncbi.util;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProgressReporter implements Serializable {

	// TODO Integrate this with the logging facility so that it logs to a particular logger

	// TODO CONCURRENCY PERFORMANCE Use a read/write lock
	// TODO Implement reporting by number of elapsed seconds, not reporting batch size
	// TODO Implement averaging for time remaining: moving window, linear decay, ?
	// TODO Is there a way to integrate this with Profiler?

	private static final Logger logger = LoggerFactory.getLogger(ProgressReporter.class);
	private static final long serialVersionUID = 1L;

	private String name;
	private int reportingIncrement;

	private int totalJobs;
	private long startTime;

	public ProgressReporter(String name, int reportingIncrement) {
		this.name = name;
		this.reportingIncrement = reportingIncrement;
	}

	public void startBatch(int totalJobs) {
		this.totalJobs = totalJobs;
		this.startTime = System.currentTimeMillis();
	}

	// TODO REFACTOR So this class tracks the job index
	public void reportCompletion(int jobIndex) {
		if (jobIndex > 0 && (jobIndex % reportingIncrement) == 0) {
			long elapsed = System.currentTimeMillis() - startTime;
			double each = ((double) elapsed) / jobIndex;
			double remaining = (totalJobs - jobIndex) * each;
			// TODO Smooth this out
			// Options: moving window, linear decay,
			logger.info(name + ": " + jobIndex + "/" + totalJobs + ", " + ((long) each) + "ms each, " + ((long) remaining) + "ms remaining");
		}
	}

	public void completeBatch() {
		if (totalJobs > 0) {
			long elapsed = System.currentTimeMillis() - startTime;
			long each = elapsed / totalJobs;
			logger.info(name + " completed: " + each + "ms each, " + elapsed + "ms total");
		}
	}

}
