package ncbi.taggerOne.processing.textInstance;

import java.util.ArrayList;
import java.util.List;

import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.util.Profiler;

/*
 * Converts from tokens to semi-markov segments
 */
public class Segmenter extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	private int maxLength;

	public Segmenter(int maxLength) {
		this.maxLength = maxLength;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("Segmenter.process()");
		List<Token> inputTokens = input.getTokens();
		List<Segment> segments = new ArrayList<Segment>();
		for (int i = 0; i < inputTokens.size(); i++) {
			Token start = inputTokens.get(i);
			for (int j = i; j < (Math.min(i + maxLength, inputTokens.size())); j++) {
				Token end = inputTokens.get(j);
				List<Token> tokens = inputTokens.subList(i, j + 1);
				segments.add(new Segment(start.getSourceText(), start.getStartChar(), end.getEndChar(), tokens));
			}
		}
		input.setSegments(segments);
		Profiler.stop("Segmenter.process()");
	}
}