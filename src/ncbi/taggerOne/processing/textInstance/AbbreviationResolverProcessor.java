package ncbi.taggerOne.processing.textInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.AbbreviationResolver;
import ncbi.util.Profiler;

public class AbbreviationResolverProcessor extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(TextInstanceProcessingPipeline.class);

	private AbbreviationResolver abbreviationResolver;

	public AbbreviationResolverProcessor(AbbreviationResolver abbreviationResolver) {
		this.abbreviationResolver = abbreviationResolver;
	}

	public AbbreviationResolver getAbbreviationResolver() {
		return abbreviationResolver;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("AbbreviationResolverProcessor.process()");
		int i = 0;
		logger.debug(String.format("Segment size: %d", input.getSegments().size()));
		for (Segment segment : input.getSegments()) {
			// Do abbreviation pre-processing
			logger.debug(String.format("Doing Segment-%d", i));
			i++;
			MentionName mentionName = segment.getMentionName();
			String id = input.getSourceId();
			abbreviationResolver.expand(id, mentionName);
		}
		Profiler.stop("AbbreviationResolverProcessor.process()");
	}

}
