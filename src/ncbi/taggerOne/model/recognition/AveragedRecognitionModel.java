package ncbi.taggerOne.model.recognition;

import ncbi.taggerOne.processing.TrainingProgressTracker;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.DenseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.util.Profiler;

public class AveragedRecognitionModel extends RecognitionModel {

	private static final long serialVersionUID = 1L;

	protected Vector<String>[] featureWeights2;

	@SuppressWarnings("unchecked")
	public AveragedRecognitionModel(Dictionary<String> featureSet, Dictionary<String> entityClassStates, TrainingProgressTracker trainingProgress) {
		super(featureSet, entityClassStates, trainingProgress);
		featureWeights2 = new DenseVector[entityClassStates.size()];
		for (int i = 0; i < entityClassStates.size(); i++) {
			featureWeights2[i] = new DenseVector<String>(featureSet);
		}
	}

	public String visualizeFeature(String featureName, String stateName) {
		StringBuilder visualization = new StringBuilder();
		int featureIndex = featureSet.getIndex(featureName);
		visualization.append(featureName + "\t" + featureIndex + "\t");
		int stateIndex = entityClassStates.getIndex(stateName);
		visualization.append(stateName + "\t" + stateIndex + "\t");
		visualization.append(trainingProgress.getInstances() + "\t");
		double weight1 = featureWeights[stateIndex].get(featureIndex);
		double weight2 = featureWeights2[stateIndex].get(featureIndex);
		double weight = weight1 - weight2 / trainingProgress.getInstances();
		visualization.append(weight1 + "\t" + weight2 + "\t" + weight);
		return visualization.toString();
	}

	public RecognitionModelPredictor getTrainingPredictor() {
		return new RecognitionModel(featureSet, entityClassStates, featureWeights, trainingProgress);
	}

	@SuppressWarnings("unchecked")
	@Override
	public RecognitionModelPredictor compile() {
		double factor = -1.0 / trainingProgress.getInstances();
		Vector<String>[] compiledFeatureWeights = new Vector[entityClassStates.size()];
		for (int i = 0; i < entityClassStates.size(); i++) {
			compiledFeatureWeights[i] = new DenseVector<String>(featureSet);
			compiledFeatureWeights[i].increment(featureWeights[i]);
			compiledFeatureWeights[i].increment(factor, featureWeights2[i]);
		}
		return new RecognitionModel(featureSet, entityClassStates, compiledFeatureWeights, trainingProgress);
	}

	@Override
	public double predict(String toState, Segment segment) {
		Profiler.start("AveragedRecognitionModel.predict()");
		int toStateIndex = entityClassStates.getIndex(toState);
		Vector<String> featureVector = segment.getFeatures();
		double score = featureVector.dotProduct(featureWeights[toStateIndex]) - featureVector.dotProduct(featureWeights2[toStateIndex]) / trainingProgress.getInstances();
		for (Token token : segment.getTokens()) {
			featureVector = token.getFeatures();
			score += featureVector.dotProduct(featureWeights[toStateIndex]) - featureVector.dotProduct(featureWeights2[toStateIndex]) / trainingProgress.getInstances();
		}
		Profiler.stop("AveragedRecognitionModel.predict()");
		return score;
	}

	@Override
	public void update(Vector<String>[] featureWeightUpdates) {
		Profiler.start("AveragedRecognitionModel.update2()");
		for (int i = 0; i < entityClassStates.size(); i++) {
			featureWeights[i].increment(featureWeightUpdates[i]);
			featureWeights2[i].increment(trainingProgress.getInstances(), featureWeightUpdates[i]);
		}
		Profiler.stop("AveragedRecognitionModel.update2()");
	}
}
