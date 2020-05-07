package ncbi.taggerOne.processing.postProcessing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;

public class FalseModifierRemover extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(FalseModifierRemover.class);
	private static final long serialVersionUID = 1L;

	private Set<String> falseModifiersToFilter;

	public FalseModifierRemover(Set<String> falseModifiersToFilter) {
		this.falseModifiersToFilter = falseModifiersToFilter;
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
				boolean startsWithFalseModifier = false;
				for (String falseModifier : falseModifiersToFilter) {
					if (segment.getText().startsWith(falseModifier)) {
						startsWithFalseModifier = true;
					}
				}
				if (startsWithFalseModifier) {
					// TODO Check if normalization would be higher without the false modifier
					logger.info("FALSE_MODIFIER: " + segment.getText());
				}
			}
			filteredAnnotationRankedList.add(predictedAnnotationRankedList.getValue(i), filteredAnnotation);
		}
		// TODO input.setPredictedAnnotations(filteredAnnotationRankedList);
	}
}