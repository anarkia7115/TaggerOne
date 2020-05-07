package ncbi.taggerOne.types;

import ncbi.taggerOne.util.vector.Vector;

public class Span implements Comparable<Span> {

	protected TextInstance sourceText;
	protected int startChar;
	protected int endChar;
	protected Vector<String> features;

	public Span(TextInstance sourceText, int startChar, int endChar) {
		if (sourceText == null) {
			throw new IllegalArgumentException("sourceText may not be null");
		}
		this.sourceText = sourceText;
		if (startChar < 0) {
			throw new IllegalArgumentException("startChar must be at least 0: startChar = " + startChar);
		}
		if (endChar > sourceText.getText().length()) {
			throw new IllegalArgumentException("endChar may not be greater than text length: endChar = " + endChar);
		}
		int charLength = endChar - startChar;
		if (charLength < 1) {
			throw new IllegalArgumentException("Span length must be at least 1: startChar = " + startChar + ", endChar = " + endChar);
		}
		this.startChar = startChar;
		this.endChar = endChar;
	}

	public TextInstance getSourceText() {
		return sourceText;
	}

	public int getStartChar() {
		return startChar;
	}

	public int getEndChar() {
		return endChar;
	}

	public String getText() {
		return sourceText.getText().substring(startChar, endChar);
	}

	public boolean overlaps(Span span) {
		return startChar < span.endChar && span.startChar < endChar;
		// return sourceText.equals(span.sourceText) && startChar < span.endChar && span.startChar < endChar;
	}

	public boolean contains(Span span) {
		return startChar <= span.startChar && endChar >= span.endChar;
		// return sourceText.equals(span.sourceText) && startChar <= span.startChar && endChar >= span.endChar;
	}

	public Vector<String> getFeatures() {
		return features;
	}

	public void setFeatures(Vector<String> features) {
		this.features = features;
	}

	@Override
	public int compareTo(Span span) {
		int comparison = startChar - span.startChar;
		if (comparison != 0) {
			return comparison;
		}
		return endChar - span.endChar;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + sourceText.hashCode();
		result = prime * result + startChar;
		result = prime * result + endChar;
		result = prime * result + ((features == null) ? 0 : features.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Span other = (Span) obj;
		if (startChar != other.startChar)
			return false;
		if (endChar != other.endChar)
			return false;
		if (!sourceText.equals(other.sourceText))
			return false;
		if (features == null) {
			if (other.features != null)
				return false;
		} else if (!features.equals(other.features))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getText() + ":" + startChar + "->" + endChar;
	}

}