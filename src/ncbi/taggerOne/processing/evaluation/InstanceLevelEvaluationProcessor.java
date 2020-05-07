package ncbi.taggerOne.processing.evaluation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;

public class InstanceLevelEvaluationProcessor extends EvaluationProcessor {

	// TODO Make this track only a specific entity type

	private static final long serialVersionUID = 1L;

	private Set<String> goldSet;
	private Set<String> predictedSet;
	private Map<String, Set<String>> typeToGoldSet;
	private Map<String, Set<String>> typeToPredictedSet;

	public InstanceLevelEvaluationProcessor(String scoreDetailPrefix) {
		super(scoreDetailPrefix);
		// TODO Micro-averaged vs. macro-averaged?
		reset();
	}

	@Override
	public void reset() {
		goldSet = new HashSet<String>();
		predictedSet = new HashSet<String>();
		typeToGoldSet = new HashMap<String, Set<String>>();
		typeToPredictedSet = new HashMap<String, Set<String>>();
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
		StringBuilder detail = new StringBuilder();
		detail.append(String.format("%s\tINSTANCE\t%s\t%s%n", scoreDetailPrefix, T1Constants.OVERALL_EVALUATION, score.scoreDetail()));
		Set<String> entityTypes = new HashSet<String>();
		entityTypes.addAll(typeToGoldSet.keySet());
		entityTypes.addAll(typeToPredictedSet.keySet());
		for (String entityType : entityTypes) {
			score = new ScoreKeeper();
			Set<String> goldSetForType = typeToGoldSet.get(entityType);
			if (goldSetForType == null) {
				goldSetForType = new HashSet<String>();
			}
			Set<String> predictedSetForType = typeToPredictedSet.get(entityType);
			if (predictedSetForType == null) {
				predictedSetForType = new HashSet<String>();
			}
			score.update(goldSetForType, predictedSetForType);
			detail.append(String.format("%s\tINSTANCE\t%s\t%s%n", scoreDetailPrefix, entityType, score.scoreDetail()));
		}
		return detail.toString().trim();
	}

	public void process(TextInstance input) {
		List<AnnotatedSegment> targetAnnotation = input.getTargetAnnotation();
		List<AnnotatedSegment> predictedAnnotation = input.getPredictedAnnotations().getObject(0);

		for (AnnotatedSegment segment : targetAnnotation) {
			Set<Entity> entities = segment.getEntities();
			for (Entity entity : entities) {
				String goldStr = input.getSourceId() + "\t" + entity.getPrimaryIdentifier();
				goldSet.add(goldStr);
				Set<String> goldSetForType = typeToGoldSet.get(entity.getType());
				if (goldSetForType == null) {
					goldSetForType = new HashSet<String>();
					typeToGoldSet.put(entity.getType(), goldSetForType);
				}
				goldSetForType.add(goldStr);
			}
		}

		for (AnnotatedSegment segment : predictedAnnotation) {
			Set<Entity> entities = segment.getEntities();
			for (Entity entity : entities) {
				String predictedStr = input.getSourceId() + "\t" + entity.getPrimaryIdentifier();
				predictedSet.add(predictedStr);
				Set<String> predictedSetForType = typeToPredictedSet.get(entity.getType());
				if (predictedSetForType == null) {
					predictedSetForType = new HashSet<String>();
					typeToPredictedSet.put(entity.getType(), predictedSetForType);
				}
				predictedSetForType.add(predictedStr);
			}
		}
	}
}
