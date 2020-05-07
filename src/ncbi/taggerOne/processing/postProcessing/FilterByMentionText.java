package ncbi.taggerOne.processing.postProcessing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;

public class FilterByMentionText extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(FilterByMentionText.class);
	private static final long serialVersionUID = 1L;

	private Set<String> mentionTextsToFilter;

	public FilterByMentionText(String... mentionTextsToFilter) {
		this.mentionTextsToFilter = new HashSet<String>(Arrays.asList(mentionTextsToFilter));
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
				boolean filter = mentionTextsToFilter.contains(segment.getText()); // TODO Processing?
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
}