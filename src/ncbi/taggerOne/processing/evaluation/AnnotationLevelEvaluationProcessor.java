package ncbi.taggerOne.processing.evaluation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.util.StaticUtilMethods;

public class AnnotationLevelEvaluationProcessor extends EvaluationProcessor {

	private static final long serialVersionUID = 1L;

	private Condition[] conditions;
	private ScoreKeeper overallScore;
	private Map<String, ScoreKeeper> scoresByType;

	public AnnotationLevelEvaluationProcessor(String scoreDetailPrefix, Condition... conditions) {
		super(scoreDetailPrefix);
		this.conditions = conditions;
		reset();
	}

	@Override
	public void reset() {
		overallScore = new ScoreKeeper();
		scoresByType = new HashMap<String, ScoreKeeper>();
	}

	@Override
	public double score() {
		return overallScore.getF();
	}

	@Override
	public String scoreDetail() {
		StringBuilder detail = new StringBuilder();
		detail.append(String.format("%s\t%s\t%s\t%s%n", scoreDetailPrefix, Arrays.toString(conditions), T1Constants.OVERALL_EVALUATION, overallScore.scoreDetail()));
		for (String entityType : scoresByType.keySet()) {
			detail.append(String.format("%s\t%s\t%s\t%s%n", scoreDetailPrefix, Arrays.toString(conditions), entityType, scoresByType.get(entityType).scoreDetail()));
		}
		return detail.toString().trim();
	}

	@Override
	public void process(TextInstance input) {

		List<AnnotatedSegment> targetAnnotation = input.getTargetAnnotation();
		List<AnnotatedSegment> predictedAnnotation = input.getPredictedAnnotations().getObject(0);

		for (AnnotatedSegment segment : targetAnnotation) {
			if (find(segment, predictedAnnotation)) {
				overallScore.incrementTp();
				getScoreKeeper(segment.getEntityClass()).incrementTp();
			} else {
				overallScore.incrementFn();
				getScoreKeeper(segment.getEntityClass()).incrementFn();
			}
		}

		for (AnnotatedSegment segment : predictedAnnotation) {
			if (!find(segment, targetAnnotation)) {
				overallScore.incrementFp();
				getScoreKeeper(segment.getEntityClass()).incrementFp();
			}
		}
	}

	private ScoreKeeper getScoreKeeper(String entityType) {
		ScoreKeeper scoreKeeperForType = scoresByType.get(entityType);
		if (scoreKeeperForType == null) {
			scoreKeeperForType = new ScoreKeeper();
			scoresByType.put(entityType, scoreKeeperForType);
		}
		return scoreKeeperForType;
	}

	private boolean find(AnnotatedSegment segment1, List<AnnotatedSegment> annotations) {
		// TODO PERFORMANCE and DESIGN
		for (AnnotatedSegment segment2 : annotations) {
			boolean match = true;
			for (Condition condition : conditions) {
				if (!condition.check(segment1, segment2)) {
					match = false;
				}
			}
			if (match) {
				return true;
			}
		}
		return false;
	}

	public enum Condition {
		EXACT_BOUNDARY {
			@Override
			public boolean check(AnnotatedSegment segment1, AnnotatedSegment segment2) {
				return segment1.getStartChar() == segment2.getStartChar() && segment1.getEndChar() == segment2.getEndChar();
			}
		},
		OVERLAP_BOUNDARY {
			@Override
			public boolean check(AnnotatedSegment segment1, AnnotatedSegment segment2) {
				return segment1.getStartChar() < segment2.getEndChar() && segment2.getStartChar() < segment1.getEndChar();
			}
		},
		ENTITY_CLASS {
			@Override
			public boolean check(AnnotatedSegment segment1, AnnotatedSegment segment2) {
				return segment1.getEntityClass().equals(segment2.getEntityClass());
			}
		},
		ENTITY_ID {
			@Override
			public boolean check(AnnotatedSegment segment1, AnnotatedSegment segment2) {
				return StaticUtilMethods.equalElements(segment1.getEntities(), segment2.getEntities());
			}
		};

		public abstract boolean check(AnnotatedSegment segment1, AnnotatedSegment segment2);
	}
}
