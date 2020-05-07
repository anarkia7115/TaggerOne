package ncbi.taggerOne.processing.string;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class StopWordRemover implements StringProcessor {

	public static final Set<String> DEFAULT_STOP_WORDS = new HashSet<String>(Arrays.asList("a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it", "no", "not",
			"of", "on", "or", "such", "that", "the", "their", "then", "there", "these", "they", "this", "to", "was", "will", "with"));

	private static final long serialVersionUID = 1L;

	private Set<String> stopWords;

	public StopWordRemover(Set<String> stopWords) {
		this.stopWords = stopWords;
	}

	@Override
	public String process(String str) {
		String str2 = str.toLowerCase(Locale.US);
		if (stopWords.contains(str2)) {
			return "";
		}
		return str;
	}
}
