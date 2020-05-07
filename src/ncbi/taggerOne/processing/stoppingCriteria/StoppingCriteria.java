package ncbi.taggerOne.processing.stoppingCriteria;

import java.io.Serializable;

import ncbi.taggerOne.processing.TrainingProgressTracker;

public class StoppingCriteria implements Serializable {

	private static final long serialVersionUID = 1L;

	private int maxIterations;

	protected TrainingProgressTracker callback;

	public StoppingCriteria(int maxIterations, TrainingProgressTracker callback) {
		this.maxIterations = maxIterations;
		this.callback = callback;
	}

	public boolean stop() {
		return callback.getIteration() >= maxIterations;
	}

}