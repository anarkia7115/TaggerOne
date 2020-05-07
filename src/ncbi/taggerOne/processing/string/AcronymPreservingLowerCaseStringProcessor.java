package ncbi.taggerOne.processing.string;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AcronymPreservingLowerCaseStringProcessor implements StringProcessor {

	private static final long serialVersionUID = 1L;

	private static Pattern upperCasePattern = Pattern.compile("\\p{javaUpperCase}+");
	private int maxAcronymLength;

	public AcronymPreservingLowerCaseStringProcessor(int maxAcronymLength) {
		this.maxAcronymLength = maxAcronymLength;
	}

	@Override
	public String process(String str) {
		if (str.length() <= maxAcronymLength) {
			Matcher matcher = upperCasePattern.matcher(str);
			if (matcher.matches()) {
				return str;
			}
		}
		return str.toLowerCase(Locale.US);
	}

}
