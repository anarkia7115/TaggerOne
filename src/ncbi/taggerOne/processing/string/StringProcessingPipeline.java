package ncbi.taggerOne.processing.string;

import java.util.Arrays;
import java.util.List;

public class StringProcessingPipeline implements StringProcessor {

	private static final long serialVersionUID = 1L;

	private List<StringProcessor> processors;

	public StringProcessingPipeline(List<StringProcessor> processors) {
		this.processors = processors;
	}

	public StringProcessingPipeline(StringProcessor... processors) {
		this.processors = Arrays.asList(processors);
	}

	@Override
	public String process(String str) {
		String processed = str;
		for (StringProcessor p : processors) {
			processed = p.process(processed);
		}
		return processed;
	}

}
