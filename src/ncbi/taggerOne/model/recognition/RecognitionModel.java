package ncbi.taggerOne.model.recognition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.processing.TrainingProgressTracker;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.DenseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.util.Profiler;
import ncbi.util.SimpleComparator;

public class RecognitionModel implements RecognitionModelPredictor, RecognitionModelUpdater {

	private static final Logger logger = LoggerFactory.getLogger(RecognitionModel.class);
	private static final long serialVersionUID = 1L;

	protected TrainingProgressTracker trainingProgress;
	protected Dictionary<String> featureSet;
	protected Vector<String>[] featureWeights;

	protected Dictionary<String> entityClassStates;

	@SuppressWarnings("unchecked")
	public RecognitionModel(Dictionary<String> featureSet, Dictionary<String> entityClassStates, TrainingProgressTracker trainingProgress) {
		if (!featureSet.isFrozen()) {
			throw new IllegalStateException("featureSet must be frozen");
		}
		if (!entityClassStates.isFrozen()) {
			throw new IllegalStateException("entityClassStates must be frozen");
		}
		this.featureSet = featureSet;
		this.entityClassStates = entityClassStates;

		featureWeights = new DenseVector[entityClassStates.size()];
		for (int i = 0; i < entityClassStates.size(); i++) {
			featureWeights[i] = new DenseVector<String>(featureSet);
		}
		this.trainingProgress = trainingProgress;
	}

	protected RecognitionModel(Dictionary<String> featureSet, Dictionary<String> entityClassStates, Vector<String>[] featureWeights, TrainingProgressTracker trainingProgress) {
		this.featureSet = featureSet;
		this.featureWeights = featureWeights;
		this.entityClassStates = entityClassStates;
		this.trainingProgress = trainingProgress;
	}

	public Dictionary<String> getEntityClassStates() {
		return entityClassStates;
	}

	public Dictionary<String> getFeatureSet() {
		return featureSet;
	}

	public TrainingProgressTracker getTrainingProgress() {
		return trainingProgress;
	}

	@Override
	public void visualize() {
		for (int stateIndex = 0; stateIndex < entityClassStates.size(); stateIndex++) {
			logger.info("Features for state \"" + entityClassStates.getElement(stateIndex) + "\":");
			Vector<String> featureWeightsForState = featureWeights[stateIndex];
			List<String> featureNames = new ArrayList<String>(featureSet.getElements());
			Collections.sort(featureNames, new RecognitionFeatureComparator(featureWeightsForState));
			for (String featureName : featureNames) {
				int featureIndex = featureSet.getIndex(featureName);
				double featureWeight = featureWeightsForState.get(featureIndex);
				if (featureWeight != 0.0) {
					logger.info("\t" + featureName + "=" + featureWeight);
				}
			}
		}
	}

	@Override
	public RecognitionModelPredictor compile() {
		return this;
	}

	private class RecognitionFeatureComparator extends SimpleComparator<String> {

		private static final long serialVersionUID = 1L;

		private Vector<String> featureWeightsForState;

		public RecognitionFeatureComparator(Vector<String> featureWeightsForState) {
			this.featureWeightsForState = featureWeightsForState;
		}

		@Override
		public int compare(String featureName1, String featureName2) {
			int featureIndex1 = featureSet.getIndex(featureName1);
			int featureIndex2 = featureSet.getIndex(featureName2);
			double featureWeight1 = featureWeightsForState.get(featureIndex1);
			double featureWeight2 = featureWeightsForState.get(featureIndex2);
			return Double.compare(featureWeight2, featureWeight1);
		}
	}

	@Override
	public double predict(String toState, Segment segment) {
		Profiler.start("RecognitionModel.predict()");
		int toStateIndex = entityClassStates.getIndex(toState);
		double score = segment.getFeatures().dotProduct(featureWeights[toStateIndex]);
		for (Token token : segment.getTokens()) {
			score += token.getFeatures().dotProduct(featureWeights[toStateIndex]);
		}
		Profiler.stop("RecognitionModel.predict()");
		return score;
	}

	@Override
	public void update(Vector<String>[] featureWeightUpdates) {
		Profiler.start("RecognitionModel.update()");
		for (int i = 0; i < entityClassStates.size(); i++) {
			featureWeights[i].increment(featureWeightUpdates[i]);
		}
		Profiler.stop("RecognitionModel.update()");
	}
}
