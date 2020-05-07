package ncbi.taggerOne.processing.textInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ncbi.taggerOne.processing.mentionName.MentionNameProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.AbbreviationResolver;
import ncbi.util.Profiler;

public class AnnotationToStateConverter extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	// TODO DESIGN Remove these to their own processors
	private AbbreviationResolver abbreviationResolver;
	private MentionNameProcessor processor;
	private Entity nonEntity;

	public AnnotationToStateConverter(AbbreviationResolver abbreviationResolver, MentionNameProcessor processor, Entity nonEntity) {
		this.abbreviationResolver = abbreviationResolver;
		this.processor = processor;
		this.nonEntity = nonEntity;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("AnnotationToStateConverter.process()");
		// Convert annotations to states
		// TODO What to do with mismatched boundaries
		List<AnnotatedSegment> stateSegments = new ArrayList<AnnotatedSegment>();
		List<Token> inputTokens = input.getTokens();
		int tokenIndex = 0;
		while (tokenIndex < inputTokens.size()) {
			Token token = inputTokens.get(tokenIndex);
			AnnotatedSegment segment = getOverlapping(token, input.getTargetAnnotation());
			if (segment == null) {
				// Not present in target annotation; this is a non-entity segment
				AnnotatedSegment annotatedSegment = new AnnotatedSegment(input, token.getStartChar(), token.getEndChar(), Collections.singletonList(token), Collections.singleton(nonEntity));
				Segment segment2 = findSegment(input, token.getStartChar(), token.getEndChar());
				annotatedSegment.setFeatures(segment2.getFeatures());
				MentionName mentionName = annotatedSegment.getMentionName();
				if (abbreviationResolver != null) {
					abbreviationResolver.expand(input.getSourceId(), mentionName);
				}
				processor.process(mentionName);
				stateSegments.add(annotatedSegment);
				tokenIndex++;
			} else {
				// Present in target annotation; this is an entity segment
				List<Token> tokens = new ArrayList<Token>();
				tokens.add(token);
				Token lastToken = token;
				boolean overlapping = true;
				tokenIndex++;
				while (tokenIndex < inputTokens.size() && overlapping) {
					Token token2 = inputTokens.get(tokenIndex);
					if (token2.overlaps(segment)) {
						lastToken = token2;
						tokens.add(token2);
						tokenIndex++;
					} else {
						overlapping = false;
					}
				}
				AnnotatedSegment annotatedSegment = new AnnotatedSegment(input, token.getStartChar(), lastToken.getEndChar(), tokens, segment.getEntities());
				Segment segment2 = findSegment(input, token.getStartChar(), lastToken.getEndChar());
				annotatedSegment.setFeatures(segment2.getFeatures());
				MentionName mentionName = annotatedSegment.getMentionName();
				if (abbreviationResolver != null) {
					abbreviationResolver.expand(input.getSourceId(), mentionName);
				}
				processor.process(mentionName);
				stateSegments.add(annotatedSegment);
			}
		}
		input.setTargetStateSequence(stateSegments);
		Profiler.stop("AnnotationToStateConverter.process()");
	}

	private static AnnotatedSegment getOverlapping(Token span, List<AnnotatedSegment> segments) {
		// TODO What to do with multiple overlapping
		for (AnnotatedSegment segment : segments) {
			if (span.overlaps(segment)) {
				return segment;
			}
		}
		return null;
	}

	private static Segment findSegment(TextInstance input, int startChar, int endChar) {
		List<Segment> segments = input.getSegments();
		// TODO PERFORMANCE and DESIGN
		for (Segment s : segments) {
			if (s.getStartChar() == startChar && s.getEndChar() == endChar) {
				return s;
			}
		}
		// This can be caused by either the tokenization or the max segment length not being long enough
		String text = input.getText().substring(startChar, endChar);
		throw new RuntimeException("Segment corresponding to annotation \"" + text
				+ "\" not found; verify maximum segment length is sufficient and that tokenization is not causing mismatched boundaries");
	}

}
