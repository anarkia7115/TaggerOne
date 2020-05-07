package ncbi.taggerOne.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ncbi.taggerOne.util.RankedList;

/*
 * Class representing a single textual segment for processing.
 */
public class TextInstance {

	private String sourceId;
	private String instanceId;
	private String text;
	private int offset;

	private List<Token> tokens;
	private List<Segment> segments;

	// TODO Decide if an explicit StateSequence data type be useful? (Largely a list of AnnotatedSegment, but with boundary validation)

	private List<AnnotatedSegment> targetAnnotation;
	private List<AnnotatedSegment> targetStateSequence;
	private RankedList<List<AnnotatedSegment>> predictedStateSequences;
	private RankedList<List<AnnotatedSegment>> predictedAnnotations;

	public TextInstance(String instanceId, String sourceId, String text, int offset) {
		if (instanceId == null) {
			throw new IllegalArgumentException("Instance ID cannot be null");
		}
		this.instanceId = instanceId;
		if (sourceId == null) {
			throw new IllegalArgumentException("Source ID cannot be null");
		}
		this.sourceId = sourceId;
		if (text == null) {
			throw new IllegalArgumentException("Text cannot be null");
		}
		this.text = text;
		if (offset < 0) {
			throw new IllegalArgumentException("Offset cannot be negative: " + offset);
		}
		this.offset = offset;
	}

	public String getSourceId() {
		return sourceId;
	}

	public String getInstanceId() {
		return instanceId;
	}

	public int getOffset() {
		return offset;
	}

	public String getText() {
		return text;
	}

	public List<Token> getTokens() {
		return tokens;
	}

	public void setTokens(List<Token> tokens) {
		this.tokens = tokens;
	}

	public List<Segment> getSegments() {
		return segments;
	}

	public void setSegments(List<Segment> segments) {
		this.segments = segments;
	}

	public List<Segment> getSegmentsStartingAt(int index) {
		if (segments == null) {
			throw new IllegalStateException("Segments must be set first");
		}
		List<Segment> segmentsStartingAtIndex = new ArrayList<Segment>();
		for (Segment segment : segments) {
			if (segment.getStartIndex() == index) {
				segmentsStartingAtIndex.add(segment);
			}
		}
		return segmentsStartingAtIndex;
	}

	public List<Segment> getSegmentsEndingAt(int index) {
		if (segments == null) {
			throw new IllegalStateException("Segments must be set first");
		}
		List<Segment> segmentsEndingAtIndex = new ArrayList<Segment>();
		for (Segment segment : segments) {
			if (segment.getEndIndex() == index) {
				segmentsEndingAtIndex.add(segment);
			}
		}
		return segmentsEndingAtIndex;
	}

	public List<AnnotatedSegment> getTargetAnnotation() {
		return targetAnnotation;
	}

	public void setTargetAnnotation(List<AnnotatedSegment> targetAnnotation) {
		this.targetAnnotation = targetAnnotation;
		if (this.targetAnnotation != null) {
			// Ensures that equals() works properly when comparing target and predictions
			Collections.sort(this.targetAnnotation);
		}
	}

	public List<AnnotatedSegment> getTargetStateSequence() {
		return targetStateSequence;
	}

	public void setTargetStateSequence(List<AnnotatedSegment> targetStateSequence) {
		this.targetStateSequence = targetStateSequence;
	}

	public RankedList<List<AnnotatedSegment>> getPredictedStates() {
		return predictedStateSequences;
	}

	public void setPredictedStates(RankedList<List<AnnotatedSegment>> states) {
		this.predictedStateSequences = states;
	}

	public RankedList<List<AnnotatedSegment>> getPredictedAnnotations() {
		return predictedAnnotations;
	}

	public void setPredictedAnnotations(RankedList<List<AnnotatedSegment>> predictedAnnotations) {
		this.predictedAnnotations = predictedAnnotations;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + sourceId.hashCode();
		result = prime * result + instanceId.hashCode();
		result = prime * result + text.hashCode();
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
		TextInstance other = (TextInstance) obj;
		if (!sourceId.equals(other.sourceId))
			return false;
		if (!instanceId.equals(other.instanceId))
			return false;
		if (!text.equals(other.text))
			return false;
		return true;
	}
}