package ncbi.taggerOne.processing.features.segment;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.processing.string.StringProcessor;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;

public class SurroundingTokensFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String prefix;
	private int window;
	private StringProcessor stringProcessor;

	public SurroundingTokensFeatureProcessor(String prefix, int window, StringProcessor stringProcessor) {
		this.prefix = prefix;
		this.window = window;
		this.stringProcessor = stringProcessor;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Token> inputTokens = input.getTokens();
		List<Segment> segments = input.getSegments();
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			Vector<String> featureVector = segment.getFeatures();

			int startIndex = segment.getStartIndex();
			for (int index = 1; index <= window; index++) {
				int currentIndex = startIndex - index;
				String previousText = "<START>";
				if (currentIndex >= 0) {
					previousText = inputTokens.get(currentIndex).getText();
					if (stringProcessor != null) {
						previousText = stringProcessor.process(previousText);
					}
				}
				String featureName = prefix + "@-" + index + "=" + previousText;
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}

			int endIndex = segment.getEndIndex();
			for (int index = 1; index <= window; index++) {
				int currentIndex = endIndex + index;
				String nextText = "<END>";
				if (currentIndex < inputTokens.size()) {
					nextText = inputTokens.get(currentIndex).getText();
					if (stringProcessor != null) {
						nextText = stringProcessor.process(nextText);
					}
				}
				String featureName = prefix + "@+" + index + "=" + nextText;
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}

		}
	}
}
