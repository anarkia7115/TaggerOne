package ncbi.taggerOne.processing.features.segment;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.processing.string.StringProcessor;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;

public class StartEndTokenFeatureProcessor implements FeatureProcessor {

	// TODO Replace this class with a proper window

	private static final long serialVersionUID = 1L;

	private String prefix;
	private StringProcessor stringProcessor;

	public StartEndTokenFeatureProcessor(String prefix, StringProcessor stringProcessor) {
		this.prefix = prefix;
		this.stringProcessor = stringProcessor;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Segment> segments = input.getSegments();
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			List<Token> tokens = segment.getTokens();
			Vector<String> featureVector = segment.getFeatures();

			String firstText = tokens.get(0).getText();
			String lastText = tokens.get(tokens.size() - 1).getText();

			if (stringProcessor != null) {
				firstText = stringProcessor.process(firstText);
				lastText = stringProcessor.process(lastText);
			}

			featureProcessorCallback.callback(prefix + "@S=" + firstText, 1.0, featureVector);
			featureProcessorCallback.callback(prefix + "@E=" + lastText, 1.0, featureVector);
		}
	}
}
