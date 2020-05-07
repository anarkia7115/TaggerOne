package ncbi.taggerOne.processing.features.token;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;

public class CharNGramFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String prefix;
	private int size;

	// TODO Add StringProcessor capability

	public CharNGramFeatureProcessor(String prefix, int size) {
		super();
		this.prefix = prefix;
		this.size = size;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Token> tokens = input.getTokens();
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			Vector<String> featureVector = token.getFeatures();
			String tokenText = ">" + token.getText() + "<";
			for (int k = 0; k < (tokenText.length() - size) + 1; k++) {
				String charNGram = tokenText.substring(k, k + size);
				String featureName = prefix + "=" + charNGram;
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}
		}
	}
}
