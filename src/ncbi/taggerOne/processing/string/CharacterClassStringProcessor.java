package ncbi.taggerOne.processing.string;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Deprecated
public class CharacterClassStringProcessor implements StringProcessor {

	// TODO Replace this class with PatternProcessor
	
	private static final long serialVersionUID = 1L;

	private boolean brief;

	// Use LinkedHashMap so patterns are applied in order
	private static final Map<Pattern, String> briefPatterns = new LinkedHashMap<Pattern, String>();
	private static final Map<Pattern, String> fullPatterns = new LinkedHashMap<Pattern, String>();

	static {
		briefPatterns.put(Pattern.compile("[A-Z]+"), "A");
		briefPatterns.put(Pattern.compile("[a-z]+"), "a");
		briefPatterns.put(Pattern.compile("[0-9]+"), "0");
		briefPatterns.put(Pattern.compile("\\s+"), "_");
		briefPatterns.put(Pattern.compile("[^Aa0_]+"), "x");
		fullPatterns.put(Pattern.compile("[A-Z]"), "A");
		fullPatterns.put(Pattern.compile("[a-z]"), "a");
		fullPatterns.put(Pattern.compile("[0-9]"), "0");
		fullPatterns.put(Pattern.compile("\\s"), "_");
		fullPatterns.put(Pattern.compile("[^Aa0_]"), "x");
	}

	public CharacterClassStringProcessor(boolean brief) {
		this.brief = brief;
	}

	@Override
	public String process(String str) {
		// TODO REFACTOR into a generic pattern-application class with predefined examples
		String result = str;
		if (brief) {
			for (Pattern pattern : briefPatterns.keySet()) {
				String replacement = briefPatterns.get(pattern);
				result = pattern.matcher(result).replaceAll(replacement);
			}
		} else {
			for (Pattern pattern : fullPatterns.keySet()) {
				String replacement = fullPatterns.get(pattern);
				result = pattern.matcher(result).replaceAll(replacement);
			}
		}
		return result;
	}

}
