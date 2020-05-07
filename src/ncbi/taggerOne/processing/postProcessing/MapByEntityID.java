package ncbi.taggerOne.processing.postProcessing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;

public class MapByEntityID extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(MapByEntityID.class);
	private static final long serialVersionUID = 1L;

	private Map<Entity, Entity> entityMap;
	private Lexicon lexicon;

	public MapByEntityID(Lexicon lexicon, Map<Entity, Entity> entityMap) {
		this.lexicon = lexicon;
		this.entityMap = entityMap;
	}

	private void add(String fromEntityID, String toEntityID) {
		Entity fromEntity = lexicon.getEntity(fromEntityID);
		if (fromEntity == null) {
			logger.warn("Could not find entity \"" + fromEntityID + "\"");
			return;
		}
		Entity toEntity = lexicon.getEntity(toEntityID);
		if (toEntity == null) {
			logger.warn("Could not find entity \"" + toEntityID + "\"");
			return;
		}
		entityMap.put(fromEntity, toEntity);
	}

	@Override
	public void process(TextInstance input) {
		RankedList<List<AnnotatedSegment>> predictedAnnotationRankedList = input.getPredictedAnnotations();
		int size = predictedAnnotationRankedList.size();
		RankedList<List<AnnotatedSegment>> filteredAnnotationRankedList = new RankedList<List<AnnotatedSegment>>(size);
		for (int i = 0; i < size; i++) {
			List<AnnotatedSegment> predictedAnnotation = predictedAnnotationRankedList.getObject(i);
			List<AnnotatedSegment> filteredAnnotation = new ArrayList<AnnotatedSegment>();
			for (AnnotatedSegment segment : predictedAnnotation) {
				AnnotatedSegment segmentCopy = segment.getAnnotatedCopy(segment.getEntityClass());
				boolean filter = containsAny(entityMap.keySet(), segment.getEntities());
				if (filter) {
					Set<Entity> predictedEntities = segment.getEntities();
					Set<Entity> mappedEntities = new HashSet<Entity>();
					for (Entity entity : predictedEntities) {
						if (entityMap.containsKey(entity)) {
							Entity mappedEntity = entityMap.get(entity);
							mappedEntities.add(mappedEntity);
						} else {
							mappedEntities.add(entity);
						}
					}
					segmentCopy.setEntities(mappedEntities);
				} else {
					segmentCopy.setEntities(segment.getEntities());
				}
				filteredAnnotation.add(segmentCopy);
			}
			filteredAnnotationRankedList.add(predictedAnnotationRankedList.getValue(i), filteredAnnotation);
		}
		input.setPredictedAnnotations(filteredAnnotationRankedList);
	}

	private static boolean containsAny(Set<Entity> set1, Set<Entity> set2) {
		for (Entity element : set1) {
			if (set2.contains(element)) {
				return true;
			}
		}
		return false;
	}
}
