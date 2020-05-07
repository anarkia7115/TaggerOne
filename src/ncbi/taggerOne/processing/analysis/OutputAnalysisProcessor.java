package ncbi.taggerOne.processing.analysis;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;

public class OutputAnalysisProcessor extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(OutputAnalysisProcessor.class);
	private static final long serialVersionUID = 1L;

	public OutputAnalysisProcessor() {
		// Empty
	}

	@Override
	public void process(TextInstance input) {
		logger.info(input.getSourceId());
		logger.info(input.getInstanceId());
		logger.info(input.getText());
		List<AnnotatedSegment> predictedAnnotation = input.getPredictedAnnotations().getObject(0);
		for (AnnotatedSegment segment : predictedAnnotation) {
			logger.info(segment.getStartChar() + "\t" + segment.getEndChar() + "\t" + segment.getText() + "\t" + segment.getEntityClass());
		}

	}

}
