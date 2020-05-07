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

public class PerfectNERAnnotationLevelEvaluationProcessor extends EvaluationProcessor {

	private static final long serialVersionUID = 1L;

	private Map<String, NormalizationModelPredictor> normalizationPredictionModels;

	private ScoreKeeper score;

	public PerfectNERAnnotationLevelEvaluationProcessor(String scoreDetailPrefix, Map<String, NormalizationModelPredictor> normalizationPredictionModels) {
		super(scoreDetailPrefix);
		this.normalizationPredictionModels = normalizationPredictionModels;
		reset();
	}

	@Override
	public void process(TextInstance input) {
		List<AnnotatedSegment> targetAnnotation = input.getTargetStateSequence();
		Set<String> targetEntities = new HashSet<String>();
		for (AnnotatedSegment segment : targetAnnotation) {
			String entityClass = segment.getEntityClass();
			if (!entityClass.equals(T1Constants.NONENTITY_STATE)) {
				Set<Entity> entities = segment.getEntities();
				for (Entity entity : entities) {
					String text = input.getSourceId() + "\t" + segment.getStartChar() + "\t" + segment.getEndChar() + "\t" + entity.getPrimaryIdentifier();
					// logger.info("PerfectNERAnnotationLevelEvaluationProcessor\tTarget\t" + text);
					targetEntities.add(text);
				}
			}
		}

		Set<String> predictedEntities = new HashSet<String>();
		for (AnnotatedSegment segment : targetAnnotation) {
			String entityClass = segment.getEntityClass();
			if (!entityClass.equals(T1Constants.NONENTITY_STATE)) {
				NormalizationModelPredictor normalizationModelPredictor = normalizationPredictionModels.get(entityClass);
				Vector<String> mentionVector = segment.getMentionName().getVector();
				if (mentionVector != null) {
					RankedList<Entity> bestEntities = new RankedList<Entity>(1);
					normalizationModelPredictor.findBest(mentionVector, bestEntities);
					if (bestEntities.size() > 0) {
						Entity entity = bestEntities.getObject(0);
						String text = input.getSourceId() + "\t" + segment.getStartChar() + "\t" + segment.getEndChar() + "\t" + entity.getPrimaryIdentifier();
						// logger.info("PerfectNERAnnotationLevelEvaluationProcessor\tPredicted\t" + text);
						predictedEntities.add(text);
					}
				}
			}
		}

		score.update(targetEntities, predictedEntities);
	}

	@Override
	public void reset() {
		score = new ScoreKeeper();
	}

	@Override
	public double score() {
		return score.getF();
	}

	@Override
	public String scoreDetail() {
		return String.format("%s\tBOUNDARY+ID+PERFECT_NER\t%s\t%s", scoreDetailPrefix, T1Constants.OVERALL_EVALUATION, score.scoreDetail());
	}

}
