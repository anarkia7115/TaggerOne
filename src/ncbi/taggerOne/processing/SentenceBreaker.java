package ncbi.taggerOne.processing;

import java.io.Serializable;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;

public class SentenceBreaker implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(SentenceBreaker.class);
	private static final long serialVersionUID = 1L;

	public SentenceBreaker() {
		// Empty
	}

	public List<TextInstance> breakSentences(List<TextInstance> instances) {
		List<TextInstance> sentenceInstances = new ArrayList<TextInstance>();
		for (TextInstance instance : instances) {
			sentenceInstances.addAll(breakSentences(instance));
		}
		logger.info("Document instances = " + instances.size() + " sentence instances = " + sentenceInstances.size());
		return sentenceInstances;
	}

	public List<TextInstance> breakSentences(TextInstance instance) {
		// TODO Verify id, text and target annotations set
		List<TextInstance> sentenceInstances = new ArrayList<TextInstance>();
		BreakIterator bi = BreakIterator.getSentenceInstance(Locale.US);
		String text = instance.getText();
		bi.setText(text);
		int start = 0;
		int counter = 0;
		int offset = instance.getOffset();
		while (bi.next() != BreakIterator.DONE) {
			String sentenceText = text.substring(start, bi.current());
			int depth = getParenDepth(sentenceText);
			if (depth <= 0) {
				sentenceInstances.add(getSentenceInstance(instance, offset + start, counter, sentenceText));
				counter++;
				start = bi.current();
			}
		}
		return sentenceInstances;
	}

	private static int getParenDepth(String text) {
		int depth = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '(')
				depth++;
			if (text.charAt(i) == ')')
				depth--;
		}
		return depth;
	}

	private static TextInstance getSentenceInstance(TextInstance instance, int offset, int counter, String sentenceText) {
		String sourceId = instance.getSourceId();
		String counterText = Integer.toString(counter);
		while (counterText.length() < 2) {
			counterText = "0" + counterText;
		}
		String instanceId = instance.getInstanceId() + "-" + counterText;
		TextInstance sentenceInstance = new TextInstance(instanceId, sourceId, sentenceText, offset);
		List<AnnotatedSegment> originalTargetAnnotation = instance.getTargetAnnotation();
		if (originalTargetAnnotation != null) {
			List<AnnotatedSegment> targetAnnotations = new ArrayList<AnnotatedSegment>();
			for (AnnotatedSegment originalAnnotation : originalTargetAnnotation) {
				AnnotatedSegment newAnnotation = getSentenceAnnotation(originalAnnotation, sentenceInstance, offset);
				if (newAnnotation != null) {
					targetAnnotations.add(newAnnotation);
				}
			}
			sentenceInstance.setTargetAnnotation(targetAnnotations);
		}
		return sentenceInstance;
	}

	private static AnnotatedSegment getSentenceAnnotation(AnnotatedSegment annotation, TextInstance sentenceInstance, int offset) {
		int newStartChar = annotation.getStartChar() - offset;
		int newEndChar = annotation.getEndChar() - offset;
		int length = sentenceInstance.getText().length();
		// Check if within this sentence
		if (newEndChar <= 0 || newStartChar >= length) {
			// No overlap - ignore
			return null;
		}
		if (newStartChar >= 0 && newEndChar <= length) {
			// Contained within sentence
			return new AnnotatedSegment(sentenceInstance, newStartChar, newEndChar, null, annotation.getEntities());
		}
		// Overlap
		logger.warn("Ignoring annotation \"" + annotation.getText() + "\" from document " + annotation.getSourceText().getSourceId() + ", which overlaps a sentence boundary.");
		return null;
	}
}
