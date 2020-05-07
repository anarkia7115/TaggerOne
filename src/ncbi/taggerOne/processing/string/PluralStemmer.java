package ncbi.taggerOne.processing.string;

import java.util.regex.Pattern;

/*
 * This class implements the "S stemmer" algorithm for converting plural nouns to singular.
 * See: Donna Hartman, How effective is suffixing? Journal of the American Society for Information Science. 01/1991; 42(1):7-15.
 */
public class PluralStemmer implements StringProcessor {

	private static final long serialVersionUID = 1L;

	private static final Pattern p1 = Pattern.compile("ies$");
	private static final Pattern p2 = Pattern.compile("es$");
	private static final Pattern p3 = Pattern.compile("s$");

	public PluralStemmer() {
		// Empty
	}

	@Override
	public String process(String str) {
		if (!str.endsWith("s")) {
			return str;
		}
		// If word ends in "ies" but not "eies" or "aies" then "ies" --> "y"
		if (str.endsWith("ies") && !str.endsWith("eies") && !str.endsWith("aies")) {
			// return str.replaceAll("ies$", "y");
			return p1.matcher(str).replaceAll("y");
		}
		// If a word ends in "es" but not "aes" "ees" or "oes" --> "es" --> "e"
		if (str.endsWith("es") && !str.endsWith("aes") && !str.endsWith("ees") && !str.endsWith("oes")) {
			// return str.replaceAll("es$", "e");
			return p2.matcher(str).replaceAll("e");
		}
		// If a word ends in "s" but not "us" or "ss" then "s" --> null
		if (!str.endsWith("us") && !str.endsWith("ss")) {
			// return str.replaceAll("s$", "");
			return p3.matcher(str).replaceAll("");
		}
		return str;
	}

}
