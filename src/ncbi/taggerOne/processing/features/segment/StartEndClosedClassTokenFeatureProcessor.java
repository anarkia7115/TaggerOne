package ncbi.taggerOne.processing.features.segment;

import java.util.List;
import java.util.Set;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.processing.string.LowerCaseStringProcessor;
import ncbi.taggerOne.processing.string.StringProcessor;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;

public class StartEndClosedClassTokenFeatureProcessor implements FeatureProcessor {

	// TODO Convert this to a token feature

	private static final long serialVersionUID = 1L;

	public static final String[] DEFAULT_CLOSED_CLASS = { "aboard", "about", "above", "across", "after", "against", "ago", "albeit", "all", "along", "alongside", "although", "always", "am", "amid",
			"among", "amongst", "an", "and", "any", "anybody", "anyhow", "anyone", "anything", "anytime", "anyway", "anywhere", "are", "around", "as", "astride", "at", "atop", "be", "because",
			"been", "before", "behind", "being", "below", "beneath", "beside", "besides", "between", "beyond", "billion", "billionth", "both", "both", "but", "by", "can", "cannot", "could",
			"despite", "did", "do", "does", "doing", "done", "down", "during", "each", "eight", "eighteen", "eighteenth", "eighth", "eightieth", "eighty", "either", "eleven", "eleventh", "en",
			"enough", "et", "every", "everybody", "everyone", "everything", "everywhere", "except", "few", "fewer", "fifteen", "fifteenth", "fifth", "fiftieth", "fifty", "first", "five", "for",
			"fortieth", "forty", "four", "fourteen", "fourteenth", "fourth", "from", "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him", "himself", "his", "how", "hundred",
			"hundredth", "if", "inside", "into", "is", "it", "its", "itself", "least", "less", "lest", "like", "little", "many", "may", "me", "might", "million", "millionth", "mine", "minus", "more",
			"most", "much", "must", "my", "myself", "near", "neither", "never", "next", "nine", "nineteen", "nineteenth", "ninetieth", "ninety", "ninth", "nobody", "none", "nor", "not", "nothing",
			"notwithstanding", "now", "nowhere", "of", "off", "on", "one", "one", "oneself", "onto", "opposite", "or", "our", "ours", "ourselves", "out", "outside", "over", "par", "past", "per",
			"plus", "post", "second", "seven", "seventeen", "seventeenth", "seventh", "seventieth", "seventy", "shall", "she", "should", "since", "six", "sixteen", "sixteenth", "sixth", "sixtieth",
			"sixty", "so", "some", "somebody", "somehow", "someone", "something", "sometime", "somewhere", "ten", "tenth", "than", "that", "the", "their", "theirs", "them", "themselves", "then",
			"there", "these", "they", "third", "thirteen", "thirteenth", "thirtieth", "thirty", "this", "those", "though", "thousand", "thousandth", "three", "through", "throughout", "till", "times",
			"to", "too", "toward", "towards", "twelfth", "twelve", "twentieth", "twenty", "two", "under", "underneath", "unless", "unlike", "until", "unto", "up", "upon", "us", "versus", "via",
			"was", "we", "were", "what", "when", "where", "whereas", "whether", "which", "which", "while", "who", "whom", "whose", "why", "will", "willing", "with", "within", "without", "worth",
			"would", "yes", "yet", "you", "your", "yours", "yourself", "yourselves", "zero" }; // Removed a, in, no

	private String prefix;
	private StringProcessor stringProcessor;
	private Set<String> closedSet;

	public StartEndClosedClassTokenFeatureProcessor(String prefix, Set<String> closedSet) {
		this.prefix = prefix;
		this.stringProcessor = new LowerCaseStringProcessor();
		this.closedSet = closedSet;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Segment> segments = input.getSegments();
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			List<Token> tokens = segment.getTokens();
			if (tokens.size() > 1) {
				Vector<String> featureVector = segment.getFeatures();

				String firstText = tokens.get(0).getText();
				firstText = stringProcessor.process(firstText);
				if (closedSet.contains(firstText)) {
					featureProcessorCallback.callback(prefix + "@S", 1.0, featureVector);
				}

				String lastText = tokens.get(tokens.size() - 1).getText();
				lastText = stringProcessor.process(lastText);
				if (closedSet.contains(lastText)) {
					featureProcessorCallback.callback(prefix + "@E", 1.0, featureVector);
				}
			}
		}
	}
}
