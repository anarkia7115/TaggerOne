package ncbi.taggerOne.util.tokenization;

import java.io.Serializable;
import java.util.regex.Pattern;

public abstract class Tokenizer implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final Pattern punctPattern = Pattern.compile("\\p{P}|\\p{S}");

	public abstract void reset(String text);

	public abstract boolean nextToken();

	public abstract int startChar();

	public abstract int endChar();

	// TODO Convert these to use int and code points
	public static boolean isPunctuation(char ch) {
		StringBuilder s = new StringBuilder();
		s.append(ch);
		return Tokenizer.punctPattern.matcher(s.toString()).find();
	}

	// TODO TEST Verify this correctly handles Unicode characters && is independent of the current Locale
	public static boolean isLowerCaseLetter(char ch) {
		if (!Character.isLetter(ch)) {
			return false;
		}
		return ch == Character.toLowerCase(ch);
	}

	// TODO TEST Verify this correctly handles Unicode characters && is independent of the current Locale
	public static boolean isUpperCaseLetter(char ch) {
		if (!Character.isLetter(ch)) {
			return false;
		}
		return ch == Character.toUpperCase(ch);
	}

}
