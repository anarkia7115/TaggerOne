package ncbi.taggerOne.processing.features.token;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;

public class TokenPatternFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String featureName;
	private Pattern pattern;

	public TokenPatternFeatureProcessor(String prefix, Pattern pattern) {
		this.featureName = prefix;
		this.pattern = pattern;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Token> tokens = input.getTokens();
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			Vector<String> featureVector = token.getFeatures();
			String tokenText = token.getText();
			Matcher m = pattern.matcher(tokenText);
			if (m.matches()) {
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}
		}
	}
}
