package ncbi.taggerOne.processing.stoppingCriteria;

import ncbi.taggerOne.processing.TrainingProgressTracker;


public class ConvergenceStoppingCriteria extends StoppingCriteria {

	// TODO Would this be more or less sensitive to noise if it used the training set hinge loss?

	private static final long serialVersionUID = 1L;

	public ConvergenceStoppingCriteria(int maxIterations, TrainingProgressTracker callback) {
		super(maxIterations, callback);
	}

	@Override
	public boolean stop() {
		if (super.stop()) {
			return true;
		}
		if (callback.getIteration() == 0) {
			return false;
		}
		return callback.getUpdates() == 0;
	}

}