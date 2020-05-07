package ncbi.taggerOne.processing.postProcessing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;

public class FilterByEntityID extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(FilterByEntityID.class);
	private static final long serialVersionUID = 1L;

	private Set<Entity> entitiesToFilter;
	private Lexicon lexicon;

	public FilterByEntityID(Lexicon lexicon, Set<Entity> entitiesToFilter) {
		this.lexicon = lexicon;
		this.entitiesToFilter = entitiesToFilter;
	}

	private void add(String entityID) {
		Entity entity = lexicon.getEntity(entityID);
		if (entity != null) {
			entitiesToFilter.add(entity);
		} else {
			logger.warn("Could not find entity \"" + entityID + "\"");
		}
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
				// TODO Only remove the ID filtered
				boolean filter = containsAny(entitiesToFilter, segment.getEntities());
				if (filter) {
					logger.info("FILTER MATCH: " + segment.getText());
				} else {
					filteredAnnotation.add(segment);
				}
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