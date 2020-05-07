package ncbi.taggerOne.processing.postProcessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.vector.DenseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;

public class ConsistencyPostProcessing extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(ConsistencyPostProcessing.class);
	private static final long serialVersionUID = 1L;

	private Lexicon lexicon;
	private double changeThreshold;
	private double addThreshold;
	private Dictionary<String> entityTypes;

	public ConsistencyPostProcessing(Lexicon lexicon, Dictionary<String> entityTypes, int changeThreshold, int addThreshold) {
		this.lexicon = lexicon;
		this.entityTypes = entityTypes;
		this.changeThreshold = changeThreshold;
		this.addThreshold = addThreshold;
	}

	@Override
	public void processAll(List<TextInstance> input) {

		// For each abstract, get mentions and their count as each type
		Map<String, Vector<String>> mentionTextToCounts = new HashMap<String, Vector<String>>();
		for (TextInstance instance : input) {
			List<AnnotatedSegment> predictedAnnotation = instance.getPredictedAnnotations().getObject(0);
			for (AnnotatedSegment segment : predictedAnnotation) {
				String mentionText = segment.getText();
				String hashKey = instance.getSourceId() + "\t" + mentionText;
				Vector<String> countsForMention = mentionTextToCounts.get(hashKey);
				if (countsForMention == null) {
					countsForMention = new DenseVector<String>(entityTypes);
					mentionTextToCounts.put(hashKey, countsForMention);
				}
				int entityTypeIndex = entityTypes.getIndex(segment.getEntityClass());
				countsForMention.increment(entityTypeIndex, 1.0);
			}
		}

		// Change types of existing annotations as needed
		for (TextInstance instance : input) {
			List<AnnotatedSegment> predictedAnnotationsCopy = new ArrayList<AnnotatedSegment>(instance.getPredictedAnnotations().getObject(0));
			List<AnnotatedSegment> targetAnnotations = instance.getTargetAnnotation();
			List<AnnotatedSegment> newAnnotations = new ArrayList<AnnotatedSegment>();
			for (Segment segment : instance.getSegments()) {
				String mentionText = segment.getText();
				String hashKey = instance.getSourceId() + "\t" + mentionText;
				// logger.info("Consistency checking counts for " + hashKey);
				Vector<String> countsForMention = mentionTextToCounts.get(hashKey);
				if (countsForMention != null) {
					double sum = vectorSum(countsForMention);
					int maxIndex = vectorMax(countsForMention);
					String newType = entityTypes.getElement(maxIndex);
					AnnotatedSegment predictedSegment = find(segment, predictedAnnotationsCopy);
					String predictedType = null;
					if (predictedSegment != null) {
						predictedType = predictedSegment.getEntityClass();
					}
					if (!newType.equals(predictedType)) {
						AnnotatedSegment overlapSegment = overlap(segment, predictedAnnotationsCopy);
						String overlapType = null;
						if (overlapSegment != null) {
							overlapType = overlapSegment.getEntityClass();
						}
						AnnotatedSegment targetSegment = find(segment, targetAnnotations);
						String targetType = null;
						if (targetSegment != null) {
							targetType = targetSegment.getEntityClass();
						}
						String logText = instance.getSourceId() + "\t" + segment.getText() + "\t" + sum + "\t" + countsForMention.visualize() + "\t" + predictedType + "\t" + overlapType + "\t" + newType + "\t" + targetType;
						if (sum >= changeThreshold && predictedType != null) {
							// This segment is currently predicted as a mention of another type: change it
							predictedAnnotationsCopy.remove(predictedSegment);
							AnnotatedSegment newAnnotation = segment.getAnnotatedCopy(newType);
							newAnnotation.setEntities(Collections.singleton(lexicon.getUnknownEntity(newType)));
							newAnnotations.add(newAnnotation);
							logger.info("Consistency post-processing adding (#1):\t" + logText);
						} else if (sum >= addThreshold && overlapSegment == null) {
							// This segment is currently not predicted as anything: add it
							AnnotatedSegment newAnnotation = segment.getAnnotatedCopy(newType);
							newAnnotation.setEntities(Collections.singleton(lexicon.getUnknownEntity(newType)));
							newAnnotations.add(newAnnotation);
							logger.info("Consistency post-processing adding (#2):\t" + logText);
						} else {
							logger.info("Consistency post-processing ignoring:\t" + logText);
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
	}

	private static double vectorSum(Vector<String> vector) {
		double sum = 0.0;
		VectorIterator iterator = vector.getIterator();
		while (iterator.next()) {
			sum += iterator.getValue();
		}
		return sum;
	}

	private static int vectorMax(Vector<String> vector) {
		double maxValue = 0.0;
		int maxIndex = -1;
		VectorIterator iterator = vector.getIterator();
		while (iterator.next()) {
			if (iterator.getValue() > maxValue) {
				maxValue = iterator.getValue();
				maxIndex = iterator.getIndex();
			}
		}
		return maxIndex;
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
