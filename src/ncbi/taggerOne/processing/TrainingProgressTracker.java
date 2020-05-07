package ncbi.taggerOne.processing;

import java.io.Serializable;

public class TrainingProgressTracker implements Serializable {

	// TODO Improve concurrency

	private static final long serialVersionUID = 1L;

	protected int updates;
	protected int instances;
	protected int iteration;

	public TrainingProgressTracker() {
		this.updates = 0;
		this.instances = 0;
		this.iteration = 0;
	}

	public void resetUpdates() {
		updates = 0;
	}

	public void incrementUpdates() {
		updates++;
	}

	public void incrementInstances() {
		instances++;
	}

	public void incrementIteration() {
		iteration++;
	}

	public int getUpdates() {
		return updates;
	}

	public void resetInstances() {
		instances = 0;
	}

	public int getInstances() {
		return instances;
	}

	public int getIteration() {
		return iteration;
	}
}