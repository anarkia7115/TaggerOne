package ncbi.taggerOne.processing.textInstance;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.types.TextInstance;
import ncbi.util.ProgressReporter;

public class TextInstanceProcessingPipeline extends TextInstanceProcessor {

	// TODO PERFORMANCE Implement a parallel version: use a concurrent framework to process each instance as a job
	// TODO Convert this to add processors one at a time and then be locked for use - improves clarity when creating the pipeline
	private static final Logger logger = LoggerFactory.getLogger(TextInstanceProcessingPipeline.class);

	private static final long serialVersionUID = 1L;

	private ProgressReporter reporter;
	private List<TextInstanceProcessor> processors;

	public TextInstanceProcessingPipeline(ProgressReporter reporter, List<TextInstanceProcessor> processors) {
		this.reporter = reporter;
		this.processors = processors;
	}

	public TextInstanceProcessingPipeline(ProgressReporter reporter, TextInstanceProcessor... processors) {
		this(reporter, Arrays.asList(processors));
	}

	public TextInstanceProcessingPipeline(List<TextInstanceProcessor> processors) {
		this(null, processors);
	}

	public TextInstanceProcessingPipeline(TextInstanceProcessor... processors) {
		this(null, Arrays.asList(processors));
	}

	@Override
	public void process(TextInstance input) {
		for (TextInstanceProcessor p : processors) {
			logger.debug("Processing: " + p.getClass().getName());
			p.process(input);
			logger.debug("Processed: " + p.getClass().getName());
		}
	}

	@Override
	public void processAll(List<TextInstance> input) {
		if (reporter != null) {
			reporter.startBatch(input.size());
		}
		for (int instanceIndex = 0; instanceIndex < input.size(); instanceIndex++) {
			TextInstance instance = input.get(instanceIndex);

			logger.info("Processing " + instance.getSourceId());
			process(instance);
			logger.info("Processed " + instance.getSourceId());
			if (reporter != null) {
				reporter.reportCompletion(instanceIndex);
			}
		}
		if (reporter != null) {
			reporter.completeBatch();
		}
	}

	@Override
	public void reset() {
		for (TextInstanceProcessor p : processors) {
			p.reset();
		}
	}

	public List<TextInstanceProcessor> getProcessors() {
		return processors;
	}

}
