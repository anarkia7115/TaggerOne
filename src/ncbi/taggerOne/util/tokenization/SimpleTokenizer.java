package ncbi.taggerOne.util.tokenization;

public class SimpleTokenizer extends Tokenizer {

	private static final long serialVersionUID = 1L;

	private String text;
	private int startIndex;
	private int endIndex;

	public SimpleTokenizer() {
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
		while (endIndex < text.length() && !found) {
			char currentChar = text.charAt(endIndex);
			if (Character.isSpaceChar(currentChar)) {
				startIndex++;
			} else if (Character.isLetter(currentChar) || Character.isDigit(currentChar)) {
				char nextChar = 0;
				if (endIndex + 1 < text.length()) {
					nextChar = text.charAt(endIndex + 1);
				}
				if (!Character.isLetter(nextChar) && !Character.isDigit(nextChar)) {
					found = true;
				}
			} else if (Tokenizer.isPunctuation(currentChar)) {
				found = true;
			}
			endIndex++;
		}
		if (!found && endIndex > startIndex && endIndex == text.length()) {
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
