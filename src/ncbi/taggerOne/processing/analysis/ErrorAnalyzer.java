package ncbi.taggerOne.processing.analysis;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.util.StaticUtilMethods;

public class ErrorAnalyzer extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(ErrorAnalyzer.class);
	private static final long serialVersionUID = 1L;

	private Map<String, NormalizationModelPredictor> normalizationPredictorModels;

	public ErrorAnalyzer(Map<String, NormalizationModelPredictor> normalizationPredictorModels) {
		this.normalizationPredictorModels = normalizationPredictorModels;
	}

	@Override
	public void process(TextInstance input) {
		List<AnnotatedSegment> targetAnnotation = input.getTargetAnnotation();
		List<AnnotatedSegment> predictedAnnotation = input.getPredictedAnnotations().getObject(0);
		if (!targetAnnotation.equals(predictedAnnotation)) {
			logger.info(input.getInstanceId() + "\t" + input.getText());
			for (AnnotatedSegment segment : targetAnnotation) {
				if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
					AnnotatedSegment found = find(segment, predictedAnnotation);
					if (found != null) {
						visualizeSegmentError("TP", found, found.getMentionName().getVector(), normalizationPredictorModels);
					}
				}
			}
			boolean hasErrors = false;
			for (AnnotatedSegment segment : targetAnnotation) {
				if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
					AnnotatedSegment found = find(segment, predictedAnnotation);
					if (found == null) {
						// This segment is an error, try to find the segment that we were supposed to find
						hasErrors = true;
						Vector<String> mentionVector = segment.getMentionName().getVector();
						Segment found2 = find2(segment, input.getSegments());
						if (found2 != null) {
							mentionVector = found2.getMentionName().getVector();
						}
						visualizeSegmentError("FN", segment, mentionVector, normalizationPredictorModels);
					}
				}
			}
			for (AnnotatedSegment segment : predictedAnnotation) {
				if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
					AnnotatedSegment found = find(segment, targetAnnotation);
					if (found == null) {
						hasErrors = true;
						visualizeSegmentError("FP", segment, segment.getMentionName().getVector(), normalizationPredictorModels);
					}
				}
			}

			if (!hasErrors) {
				logger.error("Target <> predicted, but only true positives");
				logger.error("Target == predicted: " + targetAnnotation.equals(predictedAnnotation));
				logger.error("target: " + AnnotatedSegment.visualizeStates(targetAnnotation));
				logger.error("predicted: " + AnnotatedSegment.visualizeStates(predictedAnnotation));
				throw new RuntimeException("Target <> predicted, but only true positives");
			}
		}
	}

	public static void visualizeErrors(List<AnnotatedSegment> target, List<AnnotatedSegment> predicted, Map<String, ? extends NormalizationModelPredictor> normalizationPredictorModels) {
		for (AnnotatedSegment segment : target) {
			if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
				AnnotatedSegment found = find(segment, predicted);
				if (found != null) {
					visualizeSegmentError("TP", found, normalizationPredictorModels);
				}
			}
		}
		boolean hasErrors = false;
		for (AnnotatedSegment segment : target) {
			if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
				AnnotatedSegment found = find(segment, predicted);
				if (found == null) {
					// This segment is an error, try to find the segment that we were supposed to find
					hasErrors = true;
					visualizeSegmentError("FN", segment, normalizationPredictorModels);
				}
			}
		}
		for (AnnotatedSegment segment : predicted) {
			if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
				AnnotatedSegment found = find(segment, target);
				if (found == null) {
					hasErrors = true;
					visualizeSegmentError("FP", segment, normalizationPredictorModels);
				}
			}
		}

		if (!hasErrors) {
			logger.error("Target <> predicted, but only true positives");
			logger.error("Target == predicted: " + target.equals(predicted));
			logger.error("target: " + AnnotatedSegment.visualizeStates(target));
			logger.error("predicted: " + AnnotatedSegment.visualizeStates(predicted));
			throw new RuntimeException("Target <> predicted, but only true positives");
		}
	}

	private static void visualizeSegmentError(String type, AnnotatedSegment segment, Map<String, ? extends NormalizationModelPredictor> normalizationPredictorModels) {
		for (Entity entity : segment.getEntities()) {
			StringBuilder output = new StringBuilder();
			output.append("\t");
			output.append(type);
			output.append("\t");
			output.append(segment.getEntityClass());
			output.append("\t");
			output.append(segment.getStartChar());
			output.append("\t");
			output.append(segment.getEndChar());
			output.append("\t");
			output.append(segment.getText());
			output.append("\t");
			output.append(Entity.visualizePrimaryIdentifiers(segment.getEntities()));
			output.append("\t");
			output.append(entity.getPrimaryName().getName());
			NormalizationModelPredictor normalizationPredictionModel = normalizationPredictorModels.get(segment.getEntityClass());
			MentionName mention = segment.getMentionName();
			Vector<String> mentionVector = mention.getVector();
			if (mentionVector == null) {
				logger.info(output.toString());
			} else {
				MentionName name = normalizationPredictionModel.findBestName(mentionVector, entity);
				double score = normalizationPredictionModel.scoreNameVector(mentionVector, name.getVector());
				output.append("\t");
				output.append(name.getName());
				output.append("\t");
				output.append(String.format(T1Constants.SCORING_FORMAT, score));
				logger.info(output.toString());
				Vector<String> nameVector = name.getVector();
				normalizationPredictionModel.visualizeScore(mentionVector, nameVector);
			}
		}
	}

	private static void visualizeSegmentError(String type, AnnotatedSegment segment, Vector<String> mentionVector, Map<String, ? extends NormalizationModelPredictor> normalizationPredictorModels) {
		for (Entity entity : segment.getEntities()) {
			StringBuilder output = new StringBuilder();
			output.append("\t");
			output.append(type);
			output.append("\t");
			output.append(segment.getEntityClass());
			output.append("\t");
			output.append(segment.getStartChar());
			output.append("\t");
			output.append(segment.getEndChar());
			output.append("\t");
			output.append(segment.getText());
			output.append("\t");
			output.append(Entity.visualizePrimaryIdentifiers(segment.getEntities()));
			output.append("\t");
			output.append(entity.getPrimaryName().getName());
			NormalizationModelPredictor normalizationPredictionModel = normalizationPredictorModels.get(segment.getEntityClass());
			if (mentionVector == null) {
				logger.info(output.toString());
			} else {
				MentionName name = normalizationPredictionModel.findBestName(mentionVector, entity);
				double score = normalizationPredictionModel.scoreNameVector(mentionVector, name.getVector());
				output.append("\t");
				output.append(name.getName());
				output.append("\t");
				output.append(String.format(T1Constants.SCORING_FORMAT, score));
				logger.info(output.toString());
				Vector<String> nameVector = name.getVector();
				normalizationPredictionModel.visualizeScore(mentionVector, nameVector);
			}
		}
	}

	// TODO This design will cause confusing output when there is more than one entity, and there are both entities found and not found
	private static AnnotatedSegment find(AnnotatedSegment segment, List<AnnotatedSegment> states) {
		// TODO PERFORMANCE and DESIGN
		for (AnnotatedSegment s : states) {
			if (s.getStartChar() == segment.getStartChar() && s.getEndChar() == segment.getEndChar() && StaticUtilMethods.equalElements(s.getEntities(), segment.getEntities())) {
				return s;
			}
		}
		return null;
	}

	private static Segment find2(AnnotatedSegment segment, List<Segment> states) {
		// TODO PERFORMANCE and DESIGN
		for (Segment s : states) {
			if (s.getStartChar() == segment.getStartChar() && s.getEndChar() == segment.getEndChar()) {
				return s;
			}
		}
		return null;
	}
}