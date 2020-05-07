package ncbi.taggerOne.processing.mentionName;

import java.util.Arrays;
import java.util.List;

import ncbi.taggerOne.types.MentionName;
import ncbi.util.Profiler;

public class MentionNameProcessingPipeline extends MentionNameProcessor {

	private static final long serialVersionUID = 1L;

	private List<MentionNameProcessor> processors;

	public MentionNameProcessingPipeline(List<MentionNameProcessor> processors) {
		this.processors = processors;
	}

	public MentionNameProcessingPipeline(MentionNameProcessor... processors) {
		this(Arrays.asList(processors));
	}

	@Override
	public void process(MentionName entityName) {
		Profiler.start("MentionNameProcessingPipeline.process()");
		for (MentionNameProcessor p : processors) {
			String mentionProcessorName = p.getClass().getName();
			Profiler.start("MentionNameProcessingPipeline.process()@" + mentionProcessorName);
			p.process(entityName);
			Profiler.stop("MentionNameProcessingPipeline.process()@" + mentionProcessorName);
		}
		Profiler.stop("MentionNameProcessingPipeline.process()");
	}
}
