package ncbi.taggerOne.processing.features.token;

import java.io.Serializable;
import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;
import dragon.nlp.Sentence;
import dragon.nlp.Word;
import dragon.nlp.tool.HeppleTagger;
import dragon.nlp.tool.Tagger;

public class POSFeatureProcessor implements FeatureProcessor {
	private static final long serialVersionUID = 1L;

	private String prefix;
	private POSTaggerFactory posTaggerFactory;

	public POSFeatureProcessor(String prefix, POSTaggerFactory taggerFactory) {
		this.prefix = prefix;
		this.posTaggerFactory = taggerFactory;
	}

	public POSTaggerFactory getTaggerFactory() {
		return posTaggerFactory;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Token> tokens = input.getTokens();
		int size = tokens.size();
		Sentence posSentence = new dragon.nlp.Sentence();
		for (int tokenIndex = 0; tokenIndex < size; tokenIndex++) {
			posSentence.addWord(new Word(tokens.get(tokenIndex).getText()));
		}
		try {
			Tagger tagger = posTaggerFactory.getTagger();
			tagger.tag(posSentence);
			for (int tokenIndex = 0; tokenIndex < size; tokenIndex++) {
				Token token = tokens.get(tokenIndex);
				String posFeatureName = prefix + "=" + posSentence.getWord(tokenIndex).getPOSIndex();
				Vector<String> featureVector = token.getFeatures();
				featureProcessorCallback.callback(posFeatureName, 1.0, featureVector);
			}
		} catch (RuntimeException e) {
			throw new RuntimeException("Error POS tagging " + input.getInstanceId() + " token list = " + tokens, e);
		}
	}

	public static interface POSTaggerFactory extends Serializable {

		public Tagger getTagger();

	}

	public static class HepplePOSTaggerFactory implements POSTaggerFactory {

		// TODO Set this up to allow a configuration point

		private static final long serialVersionUID = 1L;

		private String directory;
		private transient Tagger tagger;

		public HepplePOSTaggerFactory(String directory) {
			this.directory = directory;
			this.tagger = null;
		}

		@Override
		public Tagger getTagger() {
			if (tagger == null) {
				tagger = new HeppleTagger(directory);
			}
			return tagger;
		}

		public String getDirectory() {
			return directory;
		}

		public void setDirectory(String directory) {
			this.directory = directory;
		}
	}
}
