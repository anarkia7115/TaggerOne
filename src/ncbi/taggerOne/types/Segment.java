package ncbi.taggerOne.types;

import java.util.ArrayList;
import java.util.List;

public class Segment extends Span {

	protected List<Token> tokens;
	protected MentionName mentionName;

	public Segment(TextInstance sourceText, int startChar, int endChar, List<Token> tokens) {
		super(sourceText, startChar, endChar);
		// TODO Make sure tokens are adjacent
		// TODO Make sure startChar matches start span
		// TODO Make sure endChar matches end span
		if (tokens == null) {
			this.tokens = null;
		} else {
			// Ensure token list is independent
			this.tokens = new ArrayList<Token>(tokens);
		}
		this.mentionName = new MentionName(getText());
	}

	public AnnotatedSegment getAnnotatedCopy(String entityClass) {
		AnnotatedSegment annotatedCopy = new AnnotatedSegment(sourceText, startChar, endChar, tokens, entityClass);
		annotatedCopy.mentionName = mentionName;
		annotatedCopy.features = features;
		return annotatedCopy;
	}

	public List<Token> getTokens() {
		return tokens;
	}

	public int getStartIndex() {
		return tokens.get(0).getIndex();
	}

	public int getEndIndex() {
		int lastIndex = tokens.size() - 1;
		return tokens.get(lastIndex).getIndex();
	}

	public MentionName getMentionName() {
		return mentionName;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((mentionName == null) ? 0 : mentionName.hashCode());
		result = prime * result + ((tokens == null) ? 0 : tokens.hashCode());
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
		Segment other = (Segment) obj;
		if (mentionName == null) {
			if (other.mentionName != null)
				return false;
		} else if (!mentionName.equals(other.mentionName))
			return false;
		if (tokens == null) {
			if (other.tokens != null)
				return false;
		} else if (!tokens.equals(other.tokens))
			return false;
		return true;
	}
}
