package ncbi.taggerOne.processing.features.segment;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.processing.features.token.POSFeatureProcessor.POSTaggerFactory;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;
import dragon.nlp.Sentence;
import dragon.nlp.Word;
import dragon.nlp.tool.Tagger;

public class SurroundingPOSFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String prefix;
	private int window;
	private POSTaggerFactory posTaggerFactory;

	public SurroundingPOSFeatureProcessor(String prefix, int window, POSTaggerFactory taggerFactory) {
		this.prefix = prefix;
		this.window = window;
		this.posTaggerFactory = taggerFactory;
	}

	public POSTaggerFactory getTaggerFactory() {
		return posTaggerFactory;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Token> inputTokens = input.getTokens();
		int size = inputTokens.size();
		Sentence posSentence = new dragon.nlp.Sentence();
		for (int tokenIndex = 0; tokenIndex < size; tokenIndex++) {
			posSentence.addWord(new Word(inputTokens.get(tokenIndex).getText()));
		}
		Tagger tagger = posTaggerFactory.getTagger();
		tagger.tag(posSentence);

		List<Segment> segments = input.getSegments();
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			List<Token> tokens = segment.getTokens();
			Vector<String> featureVector = segment.getFeatures();

			int startIndex = inputTokens.indexOf(tokens.get(0)); // TODO PERFORMANCE
			for (int index = 1; index <= window; index++) {
				int currentIndex = startIndex - index;
				String previousText = "<START>";
				if (currentIndex >= 0) {
					previousText = Integer.toString(posSentence.getWord(currentIndex).getPOSIndex());
				}
				String featureName = prefix + "@-" + index + "=" + previousText;
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}

			int endIndex = inputTokens.indexOf(tokens.get(tokens.size() - 1)); // TODO PERFORMANCE
			for (int index = 1; index <= window; index++) {
				int currentIndex = endIndex + index;
				String nextText = "<END>";
				if (currentIndex < inputTokens.size()) {
					nextText = Integer.toString(posSentence.getWord(currentIndex).getPOSIndex());
				}
				String featureName = prefix + "@+" + index + "=" + nextText;
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}

		}
	}
}
