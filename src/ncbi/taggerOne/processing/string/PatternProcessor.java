package ncbi.taggerOne.processing.string;

import java.util.regex.Pattern;

import ncbi.taggerOne.util.tokenization.Tokenizer;

public class PatternProcessor implements StringProcessor {

	private static final long serialVersionUID = 1L;

	private Pattern[] patterns;
	private String[] replacements;

	// TODO Define additional constructors or a add/freeze mechanism to make this more useful
	public PatternProcessor(Pattern[] patterns, String[] replacements) {
		if (patterns.length != replacements.length) {
			throw new IllegalArgumentException("Number of patterns (" + patterns.length + ") does not equal number of replacements (" + replacements.length + ")");
		}
		this.patterns = patterns;
		this.replacements = replacements;
		// TODO Verify the same size
	}

	@Override
	public String process(String str) {
		return process(str, patterns, replacements);
	}

	static String process(String str, Pattern[] patterns, String[] replacements) {
		String result = str;
		for (int i = 0; i < patterns.length; i++) {
			result = patterns[i].matcher(result).replaceAll(replacements[i]);
		}
		return result;
	}

	public static Pattern[] PUNCTUATION_PATTERNS = { Tokenizer.punctPattern };
	public static Pattern[] WHITESPACE_PATTERNS = { Pattern.compile("\\s") };

	public static String[] EMPTY_REPLACEMENT = { "" };
	public static String[] SPACE_REPLACEMENT = { " " };

	public static Pattern[] NUMBER_CLASS_PATTERNS = { Pattern.compile("[0-9]") };
	public static Pattern[] BRIEF_NUMBER_CLASS_PATTERNS = { Pattern.compile("[0-9]+") };
	public static String[] NUMBER_CLASS_REPLACEMENTS = { "0" };

	public static Pattern[] CHARACTER_CLASS_PATTERNS = { Pattern.compile("[A-Z]"), Pattern.compile("[a-z]"), Pattern.compile("[0-9]"), Pattern.compile("\\s"), Pattern.compile("[^Aa0_]") };
	public static Pattern[] BRIEF_CHARACTER_CLASS_PATTERNS = { Pattern.compile("[A-Z]+"), Pattern.compile("[a-z]+"), Pattern.compile("[0-9]+"), Pattern.compile("\\s+"), Pattern.compile("[^Aa0_]+") };
	public static String[] CHARACTER_CLASS_REPLACEMENTS = { "A", "a", "0", "_", "x" };

}
