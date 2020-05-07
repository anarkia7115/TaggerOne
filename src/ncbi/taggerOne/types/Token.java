package ncbi.taggerOne.types;

public class Token extends Span {

	private int index;

	public Token(TextInstance sourceText, int startChar, int endChar, int index) {
		super(sourceText, startChar, endChar);
		if (index < 0) {
			throw new IllegalArgumentException("index must be at least 0: index = " + index);
		}
		this.index = index;
	}

	public int getIndex() {
		return index;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + index;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Token other = (Token) obj;
		if (index != other.index)
			return false;
		return true;
	}

}
