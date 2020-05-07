package ncbi.taggerOne.processing.textInstance;

import java.util.List;

import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;

public class MaxTargetAnnotationLength extends TextInstanceProcessor {
	private static final long serialVersionUID = 1L;

	private int maxLength;

	public MaxTargetAnnotationLength() {
		maxLength = 0;
	}

	@Override
	public void process(TextInstance input) {
		// TODO What to do with mismatched boundaries
		List<Token> tokens = input.getTokens();
		for (AnnotatedSegment segment : input.getTargetAnnotation()) {
			int length = 0;
			for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
				if (segment.overlaps(tokens.get(tokenIndex))) {
					length++;
				}
			}
			if (length > maxLength) {
				maxLength = length;
			}
		}
	}

	public int getMaxLength() {
		return maxLength;
	}
}