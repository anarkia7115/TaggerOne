package ncbi.taggerOne.processing.analysis;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;

public class HTMLAnalysisFile extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(HTMLAnalysisFile.class);
	private static final long serialVersionUID = 1L;

	protected Lexicon lexicon;
	protected Map<String, NormalizationModelPredictor> normalizationPredictorModels;
	private BufferedWriter inContextAnalysisFile;

	public HTMLAnalysisFile(String filename, Lexicon lexicon, Map<String, NormalizationModelPredictor> normalizationPredictorModels) {
		this.lexicon = lexicon;
		this.normalizationPredictorModels = normalizationPredictorModels;
		try {
			inContextAnalysisFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), T1Constants.UTF8_FORMAT));
			inContextAnalysisFile.write("<html><body>");
			inContextAnalysisFile.newLine();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void process(TextInstance input) {
		try {
			inContextAnalysisFile.write(input.getSourceId());
			inContextAnalysisFile.write("<br>");
			inContextAnalysisFile.newLine();
			Set<AnnotatedSegment> mentionsFoundCorrect = new HashSet<AnnotatedSegment>();
			Set<AnnotatedSegment> mentionsFoundIncorrect = new HashSet<AnnotatedSegment>();
			Set<AnnotatedSegment> mentionsNotFound = new HashSet<AnnotatedSegment>();
			separateSegments(input, mentionsFoundCorrect, mentionsFoundIncorrect, mentionsNotFound);
			inContextAnalysisFile.write(getTextAnalysis(input, mentionsFoundCorrect, mentionsFoundIncorrect, mentionsNotFound));
			inContextAnalysisFile.write("<br>");
			inContextAnalysisFile.newLine();
			inContextAnalysisFile.write("<table border=\"1\" cellpadding=\"3\" cellspacing=\"0\" style=\"width:100%\">");
			inContextAnalysisFile.newLine();
			Set<Entity> annotatedEntities = getEntities(input.getTargetAnnotation());
			Set<Entity> predictedEntities = getEntities(input.getPredictedAnnotations().getObject(0));
			List<SegmentError> segmentErrors = new ArrayList<SegmentError>();
			for (AnnotatedSegment segment : mentionsNotFound) {
				segmentErrors.add(new SegmentError("FN", segment));
			}
			for (AnnotatedSegment segment : mentionsFoundIncorrect) {
				segmentErrors.add(new SegmentError("FP", segment));
			}
			Collections.sort(segmentErrors);
			for (SegmentError segmentError : segmentErrors) {
				inContextAnalysisFile.write(segmentError.getAnalysis(annotatedEntities, predictedEntities));
				inContextAnalysisFile.newLine();
			}
			inContextAnalysisFile.write("</table>");
			inContextAnalysisFile.newLine();
			inContextAnalysisFile.write("<br>");
			inContextAnalysisFile.newLine();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void close() {
		try {
			inContextAnalysisFile.write("</body></html>");
			inContextAnalysisFile.newLine();
			inContextAnalysisFile.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Set<Entity> getEntities(List<AnnotatedSegment> segments) {
		Set<Entity> annotatedEntities = new HashSet<Entity>();
		for (AnnotatedSegment segment : segments) {
			annotatedEntities.addAll(segment.getEntities());
		}
		return annotatedEntities;
	}

	private static void separateSegments(TextInstance input, Set<AnnotatedSegment> mentionsFoundCorrect, Set<AnnotatedSegment> mentionsFoundIncorrect, Set<AnnotatedSegment> mentionsNotFound) {
		// List<AnnotatedSegment> mentionsAllowed = input.getTargetAnnotation();
		List<AnnotatedSegment> mentionsAllowed = new ArrayList<AnnotatedSegment>();
		for (AnnotatedSegment annotatedSegment : input.getTargetStateSequence()) {
			if (!annotatedSegment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
				mentionsAllowed.add(annotatedSegment);
			}
		}
		List<AnnotatedSegment> mentionsFound = input.getPredictedAnnotations().getObject(0);
		mentionsNotFound.addAll(mentionsAllowed);
		for (AnnotatedSegment mention : mentionsFound) {
			boolean found = false;
			if (mentionsNotFound.contains(mention)) {
				mentionsNotFound.remove(mention);
				mentionsFoundCorrect.add(mention);
				found = true;
			} else if (mentionsAllowed.contains(mention)) {
				mentionsFoundCorrect.add(mention);
				found = true;
				for (AnnotatedSegment mentionRequired : new HashSet<AnnotatedSegment>(mentionsNotFound)) {
					if (mention.overlaps(mentionRequired)) {
						mentionsNotFound.remove(mentionRequired);
					}
				}
			}
			if (!found) {
				mentionsFoundIncorrect.add(mention);
			}
		}
	}

	private static String getTextAnalysis(TextInstance input, Set<AnnotatedSegment> mentionsFoundCorrect, Set<AnnotatedSegment> mentionsFoundIncorrect, Set<AnnotatedSegment> mentionsNotFound) {
		// Need to handle five cases:
		// --------------------------
		// Token is not part of any mention
		// Token is part of a mention from mentionsFoundCorrect
		// Token is part of a mention from mentionsFoundIncorrect
		// Token is part of a mention from mentionsNotFound
		// Token is part of a mention from BOTH mentionsFoundIncorrect and mentionsNotFound

		StringBuffer analysis = new StringBuffer();
		FontColor currentColor = FontColor.Black;
		List<Token> tokens = input.getTokens();
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			boolean inFoundCorrect = false;
			for (AnnotatedSegment mention : mentionsFoundCorrect) {
				inFoundCorrect |= mention.contains(token);
			}
			boolean inFoundIncorrect = false;
			for (AnnotatedSegment mention : mentionsFoundIncorrect) {
				inFoundIncorrect |= mention.contains(token);
			}
			boolean inNotFound = false;
			for (AnnotatedSegment mention : mentionsNotFound) {
				inNotFound |= mention.contains(token);
			}
			if (inFoundCorrect) {
				if (inFoundIncorrect || inNotFound) {
					logger.warn("inFoundIncorrect: " + inFoundIncorrect);
					logger.warn("inNotFound: " + inNotFound);
					logger.warn(input.getSourceId());
					logger.warn(input.getText());
				}
				assert !inFoundIncorrect;
				assert !inNotFound;
				analysis.append(currentColor.changeColor(FontColor.Green));
				currentColor = FontColor.Green;
			} else if (inFoundIncorrect && inNotFound) {
				analysis.append(currentColor.changeColor(FontColor.Purple));
				currentColor = FontColor.Purple;
			} else if (inFoundIncorrect) {
				analysis.append(currentColor.changeColor(FontColor.Red));
				currentColor = FontColor.Red;
			} else if (inNotFound) {
				analysis.append(currentColor.changeColor(FontColor.Blue));
				currentColor = FontColor.Blue;
			} else {
				analysis.append(currentColor.changeColor(FontColor.Black));
				currentColor = FontColor.Black;
			}
			analysis.append(tokens.get(i).getText());
		}
		analysis.append(currentColor.changeColor(FontColor.Black));
		return analysis.toString();
	}

	private enum FontColor {
		Black, Blue, Green, Red, Purple;

		@Override
		public String toString() {
			return name().toLowerCase();
		}

		public String changeColor(FontColor newColor) {
			StringBuffer str = new StringBuffer();
			if (!equals(newColor) && !equals(Black))
				str.append("</font>");
			str.append(" ");
			if (!equals(newColor) && !newColor.equals(Black))
				str.append("<font color=\"" + newColor.toString() + "\">");
			return str.toString();
		}
	}

	private class SegmentError implements Comparable<SegmentError> {
		private String errorType;
		private AnnotatedSegment segment;

		public SegmentError(String errorType, AnnotatedSegment segment) {
			this.errorType = errorType;
			this.segment = segment;
		}

		@Override
		public int compareTo(SegmentError error2) {
			int compareTo = segment.compareTo(error2.segment);
			if (compareTo != 0) {
				return compareTo;
			}
			return errorType.compareTo(error2.errorType);
		}

		public String getAnalysis(Set<Entity> annotatedEntities, Set<Entity> predictedEntities) {
			StringBuilder output = new StringBuilder();
			for (Entity entity : segment.getEntities()) {
				output.append("<tr><td>");
				output.append(segment.getStartChar());
				output.append("</td><td>");
				output.append(segment.getEndChar());
				output.append("</td><td>");
				output.append(segment.getEntityClass());
				output.append("</td><td>");
				output.append(errorType);
				output.append("</td><td>");
				output.append(segment.getText());
				output.append("</td><td>");
				MentionName mention = segment.getMentionName();
				Vector<String> mentionVector = mention.getVector();
				if (mentionVector == null) {
					output.append("null");
				} else {
					output.append(mentionVector.visualize());
				}
				output.append("</td><td>");
				if (annotatedEntities.contains(entity)) {
					if (predictedEntities.contains(entity)) {
						output.append("<font color=\"green\">");
					} else {
						output.append("<font color=\"blue\">");
					}
				} else {
					if (predictedEntities.contains(entity)) {
						output.append("<font color=\"red\">");
					} else {
						output.append("<font color=\"black\">");
					}
				}
				output.append(entity.getPrimaryIdentifier());
				output.append("</font></td><td>");
				output.append(entity.getPrimaryName().getName());
				NormalizationModelPredictor normalizationPredictionModel = normalizationPredictorModels.get(segment.getEntityClass());
				if (mentionVector == null) {
					output.append("</td><td></td><td></td></tr>");
				} else {
					MentionName name = normalizationPredictionModel.findBestName(mentionVector, entity);
					Vector<String> nameVector = name.getVector();
					double score = 0.0;
					if (nameVector != null) {
						score = normalizationPredictionModel.scoreNameVector(mentionVector, nameVector);
					}
					output.append("</td><td>");
					output.append(name.getName());
					output.append("</td><td>");
					output.append(String.format(T1Constants.SCORING_FORMAT, score));
					output.append("</td></tr>");
				}
			}
			return output.toString();
		}
	}
}