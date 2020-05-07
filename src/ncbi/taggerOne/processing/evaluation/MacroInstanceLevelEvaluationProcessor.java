package ncbi.taggerOne.processing.evaluation;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;

public class MacroInstanceLevelEvaluationProcessor extends EvaluationProcessor {

	private static final long serialVersionUID = 1L;

	private Map<String, Set<Entity>> targetEntityMap;
	private Map<String, Set<Entity>> predictedEntityMap;

	public MacroInstanceLevelEvaluationProcessor(String scoreDetailPrefix) {
		super(scoreDetailPrefix);
		targetEntityMap = new HashMap<String, Set<Entity>>();
		predictedEntityMap = new HashMap<String, Set<Entity>>();
		reset();
	}

	public void process(TextInstance input) {
		String id = input.getSourceId();

		Set<Entity> targetEntities = targetEntityMap.get(id);
		if (targetEntities == null) {
			targetEntities = new HashSet<Entity>();
			targetEntityMap.put(id, targetEntities);
		}

		List<AnnotatedSegment> targetAnnotation = input.getTargetAnnotation();
		for (AnnotatedSegment segment : targetAnnotation) {
			Set<Entity> entities = segment.getEntities();
			for (Entity entity : entities) {
				targetEntities.add(entity);
			}
		}

		Set<Entity> predictedEntities = predictedEntityMap.get(id);
		if (predictedEntities == null) {
			predictedEntities = new HashSet<Entity>();
			predictedEntityMap.put(id, predictedEntities);
		}

		List<AnnotatedSegment> predictedAnnotation = input.getPredictedAnnotations().getObject(0);
		for (AnnotatedSegment segment : predictedAnnotation) {
			Set<Entity> entities = segment.getEntities();
			for (Entity entity : entities) {
				predictedEntities.add(entity);
			}
		}
	}

	@Override
	public void reset() {
		targetEntityMap.clear();
		predictedEntityMap.clear();
	}

	@Override
	public double score() {
		TDoubleList precisionValues = new TDoubleArrayList();
		TDoubleList recallValues = new TDoubleArrayList();
		TDoubleList fmeasureValues = new TDoubleArrayList();
		calculateScores(precisionValues, recallValues, fmeasureValues);
		double f = fmeasureValues.sum() / fmeasureValues.size();
		return f;
	}

	@Override
	public String scoreDetail() {
		TDoubleList precisionValues = new TDoubleArrayList();
		TDoubleList recallValues = new TDoubleArrayList();
		TDoubleList fmeasureValues = new TDoubleArrayList();
		calculateScores(precisionValues, recallValues, fmeasureValues);
		double p = precisionValues.sum() / precisionValues.size();
		double r = recallValues.sum() / recallValues.size();
		double f = fmeasureValues.sum() / fmeasureValues.size();
		return String.format("%s\tMACRO_INSTANCE\t%s\t\t\t\t\t\t\tp\t%.6f\tr\t%.6f\tf\t%.6f", scoreDetailPrefix, T1Constants.OVERALL_EVALUATION, p, r, f);
	}

	private void calculateScores(TDoubleList precisionValues, TDoubleList recallValues, TDoubleList fmeasureValues) {
		Set<String> evaluationIds = new HashSet<String>();
		evaluationIds.addAll(targetEntityMap.keySet());
		evaluationIds.addAll(predictedEntityMap.keySet());
		for (String id : evaluationIds) {
			Set<Entity> targetEntities = targetEntityMap.get(id);
			if (targetEntities == null) {
				targetEntities = new HashSet<Entity>();
			}
			Set<Entity> predictedEntities = predictedEntityMap.get(id);
			if (predictedEntities == null) {
				predictedEntities = new HashSet<Entity>();
			}
			ScoreKeeper score = new ScoreKeeper();
			score.update(targetEntities, predictedEntities);
			precisionValues.add(score.getP());
			recallValues.add(score.getR());
			fmeasureValues.add(score.getF());
		}
	}
}
