package ncbi.taggerOne.model.optimization;

import java.util.List;

import ncbi.taggerOne.types.AnnotatedSegment;

public interface OnlineOptimizer {

	public void update(List<AnnotatedSegment> targetStateSequence, List<AnnotatedSegment> predictedStateSequence);

}
