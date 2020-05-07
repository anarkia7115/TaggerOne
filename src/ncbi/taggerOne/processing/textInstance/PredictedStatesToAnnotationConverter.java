package ncbi.taggerOne.processing.textInstance;

import java.util.ArrayList;
import java.util.List;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;

public class PredictedStatesToAnnotationConverter extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	public PredictedStatesToAnnotationConverter() {
		// Empty
	}

	@Override
	public void process(TextInstance input) {
		RankedList<List<AnnotatedSegment>> predictedStates = input.getPredictedStates();
		RankedList<List<AnnotatedSegment>> predictedAnnotations = new RankedList<List<AnnotatedSegment>>(predictedStates.maxSize());
		for (int i = 0; i < predictedStates.size(); i++) {
			List<AnnotatedSegment> predictedStatesAtIndex = predictedStates.getObject(i);
			List<AnnotatedSegment> predictedAnnotationsAtIndex = new ArrayList<AnnotatedSegment>();
			for (AnnotatedSegment segment : predictedStatesAtIndex) {
				if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
					// TODO Do we want a copy here?
					predictedAnnotationsAtIndex.add(segment);
				}
			}
			predictedAnnotations.add(predictedStates.getValue(i), predictedAnnotationsAtIndex);
		}
		input.setPredictedAnnotations(predictedAnnotations);
	}

}
