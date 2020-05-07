package ncbi.taggerOne.processing.postProcessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.AbbreviationResolver;
import ncbi.taggerOne.util.RankedList;
import ncbi.util.Profiler;

public class AbbreviationPostProcessing extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(AbbreviationPostProcessing.class);
	private static final long serialVersionUID = 1L;

	private AbbreviationResolver abbreviationResolver;
	private int changeThreshold;
	private int addThreshold;
	private boolean dropIfNoExpandedPrediction;

	public AbbreviationPostProcessing(AbbreviationResolver abbreviationResolver, int changeThreshold, int addThreshold, boolean dropIfNoExpandedPrediction) {
		this.abbreviationResolver = abbreviationResolver;
		this.changeThreshold = changeThreshold;
		this.addThreshold = addThreshold;
		this.dropIfNoExpandedPrediction = dropIfNoExpandedPrediction;
	}

	@Override
	public void processAll(List<TextInstance> input) {
		Profiler.start("AbbreviationPostProcessing.processAll()");

		// For each abstract, get mentions and their count as each type
		Map<String, TObjectIntMap<Set<Entity>>> mentionTextToEntityCounts = new HashMap<String, TObjectIntMap<Set<Entity>>>();
		for (TextInstance instance : input) {
			List<AnnotatedSegment> predictedAnnotation = instance.getPredictedAnnotations().getObject(0);
			for (AnnotatedSegment segment : predictedAnnotation) {
				String mentionText = segment.getText();
				String hashKey = instance.getSourceId() + "\t" + mentionText;
				TObjectIntMap<Set<Entity>> entityCountsForMention = mentionTextToEntityCounts.get(hashKey);
				if (entityCountsForMention == null) {
					entityCountsForMention = new TObjectIntHashMap<Set<Entity>>(1, Constants.DEFAULT_LOAD_FACTOR, 0);
					mentionTextToEntityCounts.put(hashKey, entityCountsForMention);
				}
				Set<Entity> entities = segment.getEntities();
				entityCountsForMention.adjustOrPutValue(entities, 1, 1);
			}
		}
		logger.debug("Resolved " + mentionTextToEntityCounts.size() + " mentions");

		// Change types of existing annotations as needed
		for (TextInstance instance : input) {
			List<AnnotatedSegment> predictedAnnotationsCopy = new ArrayList<AnnotatedSegment>(instance.getPredictedAnnotations().getObject(0));
			List<AnnotatedSegment> targetAnnotations = instance.getTargetAnnotation();
			List<AnnotatedSegment> newAnnotations = new ArrayList<AnnotatedSegment>();
			for (Segment segment : instance.getSegments()) {
				String mentionText = segment.getText();
				String expandedText = abbreviationResolver.expandAbbreviations(instance.getSourceId(), mentionText);
				if (!mentionText.contains("(") && !mentionText.contains(")") && !expandedText.equals(mentionText)) {

					// Determine the currently-predicted type and count
					AnnotatedSegment predictedSegment = find(segment, predictedAnnotationsCopy);
					String predictedType = null;
					if (predictedSegment != null) {
						predictedType = predictedSegment.getEntityClass();
					}
					// Determine the predicted type for the expanded text
					int expandedTypeCount = 0;
					String expandedType = null;
					Set<Entity> expandedEntitySet = null;
					String hashKey = instance.getSourceId() + "\t" + expandedText;
					TObjectIntMap<Set<Entity>> entityCountsForMention = mentionTextToEntityCounts.get(hashKey);
					if (entityCountsForMention != null) {
						expandedTypeCount = vectorSum(entityCountsForMention);
						expandedEntitySet = vectorMax(entityCountsForMention);
						expandedType = expandedEntitySet.iterator().next().getType();
					}
					// Determine if any current predictions overlap this segment
					AnnotatedSegment overlapSegment = overlap(segment, predictedAnnotationsCopy);
					String overlapType = null;
					if (overlapSegment != null) {
						overlapType = overlapSegment.getEntityClass();
					}
					// Determine the target annotation for this segment (so the logging will reflect how well this
					// post-processor is performing)
					String targetType = null;
					if (targetAnnotations != null) {
						AnnotatedSegment targetSegment = find(segment, targetAnnotations);
						if (targetSegment != null) {
							targetType = targetSegment.getEntityClass();
						}
					}
					String logText = instance.getSourceId() + "\t" + mentionText + "\t" + expandedText + "\t" + expandedTypeCount + "\t" + predictedType + "\t" + overlapType + "\t" + expandedType + "\t" + targetType;

					// Decide what to do
					if (predictedType == null) {
						// This segment is currently not predicted as anything
						if (expandedType != null) {
							// The expanded text has a prediction, check if we should add this one
							if (expandedTypeCount >= addThreshold && overlapSegment == null) {
								AnnotatedSegment newAnnotation = segment.getAnnotatedCopy(expandedType);
								newAnnotation.setEntities(new HashSet<Entity>(expandedEntitySet));
								newAnnotations.add(newAnnotation);
								logger.info("Abbreviation post-processing adding (#1):\t" + logText);
							} else {
								logger.info("Abbreviation post-processing ignoring (#1):\t" + logText);
							}
						} else {
							// There is no prediction for the expanded type, ignore silently
						}
					} else {
						// This segment is currently has a prediction: should it be changed or dropped?
						if (expandedType == null) {
							// There is no prediction for the expanded type
							if (dropIfNoExpandedPrediction) {
								predictedAnnotationsCopy.remove(predictedSegment);
								logger.info("Abbreviation post-processing removing (#3):\t" + logText);
							} else {
								logger.info("Abbreviation post-processing ignoring (#3):\t" + logText);
							}
						} else if (!expandedType.equals(predictedType)) {
							// Check if we should change the current prediction to the expanded text prediction
							if (expandedTypeCount >= changeThreshold) {
								predictedAnnotationsCopy.remove(predictedSegment);
								AnnotatedSegment newAnnotation = segment.getAnnotatedCopy(expandedType);
								newAnnotation.setEntities(new HashSet<Entity>(expandedEntitySet));
								newAnnotations.add(newAnnotation);
								logger.info("Abbreviation post-processing adding (#2):\t" + logText);
							} else {
								logger.info("Abbreviation post-processing ignoring (#2):\t" + logText);
							}
						} else {
							// Currently predicted type agrees with expanded type, ignore silently
						}
					}
				}
			}
			// Add remaining annotations
			newAnnotations.addAll(predictedAnnotationsCopy);
			Collections.sort(newAnnotations);
			// Finalize
			double score = instance.getPredictedAnnotations().getValue(0);
			RankedList<List<AnnotatedSegment>> newAnnotationsList = new RankedList<List<AnnotatedSegment>>(1);
			newAnnotationsList.add(score, newAnnotations);
			instance.setPredictedAnnotations(newAnnotationsList);
		}

		Profiler.stop("AbbreviationPostProcessing.processAll()");
	}

	private static int vectorSum(TObjectIntMap<Set<Entity>> entityCountsForMention) {
		int sum = 0;
		for (Set<Entity> key : entityCountsForMention.keySet()) {
			int count = entityCountsForMention.get(key);
			sum += count;
		}
		return sum;
	}

	private static Set<Entity> vectorMax(TObjectIntMap<Set<Entity>> entityCountsForMention) {
		int maxValue = 0;
		Set<Entity> maxEntitySet = null;
		for (Set<Entity> key : entityCountsForMention.keySet()) {
			int count = entityCountsForMention.get(key);
			if (count > maxValue) {
				maxValue = count;
				maxEntitySet = key;
			}
		}
		return maxEntitySet;
	}

	private static AnnotatedSegment find(Segment segment, List<AnnotatedSegment> states) {
		// TODO PERFORMANCE and DESIGN
		for (AnnotatedSegment s : states) {
			if (s.getStartChar() == segment.getStartChar() && s.getEndChar() == segment.getEndChar()) {
				return s;
			}
		}
		return null;
	}

	private static AnnotatedSegment overlap(Segment segment, List<AnnotatedSegment> states) {
		// TODO PERFORMANCE and DESIGN
		for (AnnotatedSegment s : states) {
			if (s.overlaps(segment)) {
				return s;
			}
		}
		return null;
	}

	@Override
	public void process(TextInstance input) {
		throw new RuntimeException("Not allowed");
	}
}
