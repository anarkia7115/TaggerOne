package ncbi.taggerOne.processing.textInstance;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.model.optimization.OnlineOptimizer;
import ncbi.taggerOne.processing.TrainingProgressTracker;
import ncbi.taggerOne.processing.analysis.ErrorAnalyzer;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;
import ncbi.util.Profiler;

public class AnnotationModelTrainingIteration extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(AnnotationModelTrainer.class);
	private static final long serialVersionUID = 1L;

	protected Map<String, NormalizationModelPredictor> normalizationPredictionModels;
	protected OnlineOptimizer optimizer;
	protected Annotator annotator;
	protected TrainingProgressTracker callback;

	public AnnotationModelTrainingIteration(Annotator annotator, Map<String, NormalizationModelPredictor> normalizationPredictionModels, OnlineOptimizer optimizer, TrainingProgressTracker callback) {
		this.normalizationPredictionModels = normalizationPredictionModels;
		this.optimizer = optimizer;
		this.annotator = annotator; // Training only requires the top result
		this.callback = callback;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("AnnotationModelTrainingIteration.process()");
		long start = System.currentTimeMillis();
		logger.info(input.getInstanceId() + ": Getting target state sequence; elapsed=" + (System.currentTimeMillis() - start));
		List<AnnotatedSegment> targetStateSequence = input.getTargetStateSequence();
		logger.info(input.getInstanceId() + ": Getting predicted state sequences; elapsed=" + (System.currentTimeMillis() - start));
		RankedList<List<AnnotatedSegment>> predictedStateSequences = annotator.getPredictedStateSequences(input);
		List<AnnotatedSegment> predictedStateSequence = predictedStateSequences.getObject(0);
		if (!targetStateSequence.equals(predictedStateSequence)) {
			// Issue training updates for all transitions in the target and predicted paths
			double targetScore = annotator.scoreStateSequence(targetStateSequence);
			double predictedScore = predictedStateSequences.getValue(0);
			logger.info(input.getInstanceId() + ": Updating; target score = " + String.format(T1Constants.SCORING_FORMAT, targetScore) + ", predicted score = "
					+ String.format(T1Constants.SCORING_FORMAT, predictedScore) + ", difference = " + String.format(T1Constants.SCORING_FORMAT, predictedScore - targetScore));
			double predictedScoreRecheck = annotator.scoreStateSequence(predictedStateSequence);
			if (Math.abs(predictedScoreRecheck - predictedScore) > T1Constants.EPSILON) {
				logger.error("Scoring error: predictedScore != recheck");
				logger.error("Input length in tokens: " + input.getTokens().size());
				logger.error("" + input.getInstanceId());
				logger.error("" + input.getText());
				logger.error("predicted score = " + predictedScore);
				logger.error("predicted score recheck = " + annotator.scoreStateSequence(predictedStateSequence));
				RankedList<List<AnnotatedSegment>> predictedStateSequences2 = annotator.getPredictedStateSequences(input);
				List<AnnotatedSegment> predictedStateSequence2 = predictedStateSequences2.getObject(0);
				double predictedScore2 = predictedStateSequences2.getValue(0);
				logger.error("predictedStateSequence==predictedStateSequence2 = " + (predictedStateSequence.equals(predictedStateSequence2)));
				logger.error("predicted score 2 = " + predictedScore2);
				logger.error("predictedStateSequence= " + AnnotatedSegment.visualizeStates(predictedStateSequence));
				logger.error("predictedStateSequence2= " + AnnotatedSegment.visualizeStates(predictedStateSequence2));
				logger.error("end");
			}
			if (targetScore > predictedScore + T1Constants.EPSILON) {
				logger.error("Scoring error: targetScore > predictedScore");
				logger.error("Input length in tokens: " + input.getTokens().size());
				logger.error("" + input.getInstanceId());
				logger.error("" + input.getText());
				logger.error("target score = " + targetScore);
				logger.error("target score recheck = " + annotator.scoreStateSequence(targetStateSequence));
				logger.error("predicted score = " + predictedScore);
				logger.error("predicted score recheck = " + annotator.scoreStateSequence(predictedStateSequence));
				RankedList<List<AnnotatedSegment>> predictedStateSequences2 = annotator.getPredictedStateSequences(input);
				List<AnnotatedSegment> predictedStateSequence2 = predictedStateSequences2.getObject(0);
				double predictedScore2 = predictedStateSequences2.getValue(0);
				logger.error("predictedStateSequence==predictedStateSequence2 = " + (predictedStateSequence.equals(predictedStateSequence2)));
				logger.error("predicted score 2 = " + predictedScore2);
				StringBuilder states = new StringBuilder();
				for (AnnotatedSegment segment : targetStateSequence) {
					states.append("(" + segment.getText() + ")" + segment.getEntityClass() + " ");
				}
				logger.error("target   = " + states.toString().trim());
				states = new StringBuilder();
				for (AnnotatedSegment segment : predictedStateSequences.getObject(0)) {
					states.append("(" + segment.getText() + ")" + segment.getEntityClass() + " ");
				}
				logger.error("predicted= " + states.toString().trim());
				logger.error("end");
			}
			ErrorAnalyzer.visualizeErrors(targetStateSequence, predictedStateSequence, normalizationPredictionModels);
			optimizer.update(targetStateSequence, predictedStateSequence);

			// Log updated scores
			targetScore = annotator.scoreStateSequence(targetStateSequence);
			predictedScore = annotator.scoreStateSequence(predictedStateSequence);
			logger.info(input.getInstanceId() + ": After update; target score = " + String.format(T1Constants.SCORING_FORMAT, targetScore) + ", predicted score = "
					+ String.format(T1Constants.SCORING_FORMAT, predictedScore) + ", difference = " + String.format(T1Constants.SCORING_FORMAT, predictedScore - targetScore));
			logger.info(input.getInstanceId() + ": Updates complete; elapsed=" + (System.currentTimeMillis() - start));
			if (callback != null) {
				callback.incrementUpdates();
			}
		}
		// Increment iterations in all models
		if (callback != null) {
			callback.incrementInstances();
		}
		Profiler.stop("AnnotationModelTrainingIteration.process()");
	}
}
