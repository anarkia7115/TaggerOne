package ncbi.taggerOne.processing.features.token;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.processing.string.StringProcessor;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;

public class TokenFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String prefix;
	private StringProcessor stringProcessor;
	private boolean outputIfProcessedSame;

	public TokenFeatureProcessor(String prefix, StringProcessor stringProcessor, boolean outputIfProcessedSame) {
		this.prefix = prefix;
		this.stringProcessor = stringProcessor;
		this.outputIfProcessedSame = outputIfProcessedSame;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Token> tokens = input.getTokens();
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			Vector<String> featureVector = token.getFeatures();
			String tokenText = token.getText();
			String processedText = tokenText;
			if (stringProcessor != null) {
				processedText = stringProcessor.process(tokenText);
				if (!outputIfProcessedSame && processedText.equals(tokenText)) {
					processedText = null;
				}
			}
			if (processedText != null) {
				String featureName = prefix + "=" + processedText;
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}
		}
	}
}
