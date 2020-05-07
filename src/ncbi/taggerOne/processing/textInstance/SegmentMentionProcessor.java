package ncbi.taggerOne.processing.textInstance;

import ncbi.taggerOne.processing.mentionName.MentionNameProcessor;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.util.Profiler;

public class SegmentMentionProcessor extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	private MentionNameProcessor processor;

	public SegmentMentionProcessor(MentionNameProcessor processor) {
		this.processor = processor;
	}

	public MentionNameProcessor getProcessor() {
		return processor;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("SegmentMentionProcessor.process()");
		for (Segment segment : input.getSegments()) {
			MentionName mentionName = segment.getMentionName();
			String mentionProcessorName = processor.getClass().getName();
			Profiler.start("SegmentMentionProcessor.process()@" + mentionProcessorName);
			processor.process(mentionName);
			Profiler.stop("SegmentMentionProcessor.process()@" + mentionProcessorName);
		}
		Profiler.stop("SegmentMentionProcessor.process()");
	}

}
