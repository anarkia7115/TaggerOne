package ncbi.taggerOne.util.tokenization;

public class FinerTokenizer extends Tokenizer {

	private static final long serialVersionUID = 1L;

	private String text;
	private int startIndex;
	private int endIndex;

	public FinerTokenizer() {
		text = null;
		startIndex = 0;
		endIndex = 0;
	}

	@Override
	public void reset(String text) {
		this.text = text;
		startIndex = 0;
		endIndex = 0;
	}

	@Override
	public boolean nextToken() {
		if (endIndex >= text.length()) {
			return false;
		}
		boolean found = false;
		startIndex = endIndex;
		int currentIndex = endIndex;
		while (currentIndex < text.length() && !found) {
			char currentChar = text.charAt(currentIndex);
			char nextChar = 0;
			if (currentIndex + 1 < text.length()) {
				nextChar = text.charAt(currentIndex + 1);
			}
			if (Character.isSpaceChar(currentChar)) {
				startIndex++;
				currentIndex++;
			} else if (Character.isLetter(currentChar) && !Character.isLetter(nextChar)) {
				endIndex = currentIndex + 1;
				found = true;
			} else if (Tokenizer.isLowerCaseLetter(currentChar) && Tokenizer.isUpperCaseLetter(nextChar)) {
				endIndex = currentIndex + 1;
				found = true;
			} else if (Character.isDigit(currentChar) && !Character.isDigit(nextChar)) {
				endIndex = currentIndex + 1;
				found = true;
			} else if (Tokenizer.isPunctuation(currentChar)) {
				endIndex = currentIndex + 1;
				found = true;
			} else {
				currentIndex++;
			}
		}
		if (!found && currentIndex > startIndex && currentIndex == text.length()) {
			endIndex = text.length();
			found = true;
		}
		return found;
	}

	@Override
	public int startChar() {
		return startIndex;
	}

	@Override
	public int endChar() {
		return endIndex;
	}
}
