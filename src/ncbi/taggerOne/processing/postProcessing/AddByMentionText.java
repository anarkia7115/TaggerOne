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
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;

public class AddByMentionText extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(AddByMentionText.class);
	private static final long serialVersionUID = 1L;

	private Map<String, Entity> mentionTextsToEntity;
	private Lexicon lexicon;

	public AddByMentionText(Lexicon lexicon, Map<String, Entity> mentionTextsToEntity) {
		this.lexicon = lexicon;
		this.mentionTextsToEntity = mentionTextsToEntity;
	}

	private void add(String mentionText, String entityID) {
		Entity entity = lexicon.getEntity(entityID);
		if (entity != null) {
			mentionTextsToEntity.put(mentionText, entity);
		} else {
			logger.warn("Could not find entity \"" + entityID + "\"");
		}
	}

	@Override
	public void process(TextInstance input) {
		// TODO OPTIMIZE
		for (Segment segment : input.getSegments()) {
			String segmentText = segment.getText();
			if (mentionTextsToEntity.containsKey(segmentText)) {
				logger.info("ADDING mention: " + segmentText.toString());
				Entity entity = mentionTextsToEntity.get(segmentText);
				AnnotatedSegment annotatedSegment = segment.getAnnotatedCopy(entity.getType());
				annotatedSegment.setEntities(Collections.singleton(entity));
				RankedList<List<AnnotatedSegment>> predictedAnnotationRankedList = input.getPredictedAnnotations();
				RankedList<List<AnnotatedSegment>> newAnnotationRankedList = new RankedList<List<AnnotatedSegment>>(predictedAnnotationRankedList.size());
				int size = predictedAnnotationRankedList.size();
				for (int i = 0; i < size; i++) {
					List<AnnotatedSegment> predictedAnnotations = predictedAnnotationRankedList.getObject(i);
					List<AnnotatedSegment> newAnnotations = new ArrayList<AnnotatedSegment>();
					for (AnnotatedSegment predictedAnnotation : predictedAnnotations) {
						if (predictedAnnotation.getStartChar() != annotatedSegment.getStartChar() || predictedAnnotation.getEndChar() != annotatedSegment.getEndChar()) {
							newAnnotations.add(predictedAnnotation);
						}
					}
					newAnnotations.add(annotatedSegment);
					Collections.sort(newAnnotations);
					newAnnotationRankedList.add(predictedAnnotationRankedList.getValue(i), newAnnotations);
				}
				input.setPredictedAnnotations(newAnnotationRankedList);
			}
		}
	}
}
