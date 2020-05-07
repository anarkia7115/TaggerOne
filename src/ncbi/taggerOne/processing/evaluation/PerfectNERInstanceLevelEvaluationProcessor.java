package ncbi.taggerOne.processing.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.vector.Vector;

public class PerfectNERInstanceLevelEvaluationProcessor extends EvaluationProcessor {

	private static final long serialVersionUID = 1L;

	private Map<String, NormalizationModelPredictor> normalizationTrainingPredictionModels;

	private Set<String> goldSet;
	private Set<String> predictedSet;

	public PerfectNERInstanceLevelEvaluationProcessor(String scoreDetailPrefix, Map<String, NormalizationModelPredictor> normalizationTrainingPredictionModels) {
		super(scoreDetailPrefix);
		this.normalizationTrainingPredictionModels = normalizationTrainingPredictionModels;
		reset();
	}

	@Override
	public void reset() {
		goldSet = new HashSet<String>();
		predictedSet = new HashSet<String>();
	}

	@Override
	public double score() {
		ScoreKeeper score = new ScoreKeeper();
		score.update(goldSet, predictedSet);
		return score.getF();
	}

	@Override
	public String scoreDetail() {
		ScoreKeeper score = new ScoreKeeper();
		score.update(goldSet, predictedSet);
		return String.format("%s\tINSTANCE+PERFECT_NER\t%s\t%s", scoreDetailPrefix, T1Constants.OVERALL_EVALUATION, score.scoreDetail());
	}

	public void process(TextInstance input) {
		// This calculates micro-averaged
		List<AnnotatedSegment> targetAnnotation = input.getTargetStateSequence();
		for (AnnotatedSegment segment : targetAnnotation) {
			String entityClass = segment.getEntityClass();
			if (!entityClass.equals(T1Constants.NONENTITY_STATE)) {
				Set<Entity> entities = segment.getEntities();
				for (Entity entity : entities) {
					// Add gold
					String goldStr = input.getSourceId() + "\t" + entity.getPrimaryIdentifier();
					// logger.info("PerfectNERInstanceLevelEvaluationProcessor2 adding gold: " + goldStr);
					goldSet.add(goldStr);
				}
				// Add predicted
				NormalizationModelPredictor normalizationModelPredictor = normalizationTrainingPredictionModels.get(entityClass);
				Vector<String> mentionVector = segment.getMentionName().getVector();
				if (mentionVector != null) {
					RankedList<Entity> bestEntities = new RankedList<Entity>(1);
					normalizationModelPredictor.findBest(mentionVector, bestEntities);
					if (bestEntities.size() > 0) {
						Entity entity = bestEntities.getObject(0);
						String predStr = input.getSourceId() + "\t" + entity.getPrimaryIdentifier();
						// logger.info("PerfectNERInstanceLevelEvaluationProcessor2 adding pred: " + predStr);
						predictedSet.add(predStr);
					}
				}
			}
		}
	}
}
