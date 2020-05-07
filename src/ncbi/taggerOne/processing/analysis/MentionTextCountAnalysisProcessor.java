package ncbi.taggerOne.processing.analysis;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;

public class MentionTextCountAnalysisProcessor extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(MentionTextCountAnalysisProcessor.class);
	private static final long serialVersionUID = 1L;

	private TObjectIntMap<String> annotatedCounts;
	private TObjectIntMap<String> predictedCounts;
	private TObjectIntMap<String> tpCounts;
	private TObjectIntMap<String> fpCounts;
	private TObjectIntMap<String> fnCounts;

	public MentionTextCountAnalysisProcessor() {
		annotatedCounts = new TObjectIntHashMap<String>();
		predictedCounts = new TObjectIntHashMap<String>();
		tpCounts = new TObjectIntHashMap<String>();
		fpCounts = new TObjectIntHashMap<String>();
		fnCounts = new TObjectIntHashMap<String>();
	}

	@Override
	public void process(TextInstance input) {

		List<AnnotatedSegment> targetAnnotation = input.getTargetAnnotation();
		List<AnnotatedSegment> predictedAnnotation = input.getPredictedAnnotations().getObject(0);

		for (AnnotatedSegment segment1 : targetAnnotation) {
			boolean found = false;
			for (AnnotatedSegment segment2 : predictedAnnotation) {
				if (segment1.getStartChar() == segment2.getStartChar() && segment1.getEndChar() == segment2.getEndChar() && segment1.getEntityClass().equals(segment2.getEntityClass())) {
					found = true;
				}
			}
			String mentionText = segment1.getText();
			annotatedCounts.adjustOrPutValue(mentionText, 1, 1);
			if (found) {
				tpCounts.adjustOrPutValue(mentionText, 1, 1);
			} else {
				fnCounts.adjustOrPutValue(mentionText, 1, 1);
			}
		}

		for (AnnotatedSegment segment1 : predictedAnnotation) {
			boolean found = false;
			for (AnnotatedSegment segment2 : targetAnnotation) {
				if (segment1.getStartChar() == segment2.getStartChar() && segment1.getEndChar() == segment2.getEndChar() && segment1.getEntityClass().equals(segment2.getEntityClass())) {
					found = true;
				}
			}
			String mentionText = segment1.getText();
			predictedCounts.adjustOrPutValue(mentionText, 1, 1);
			if (!found) {
				fpCounts.adjustOrPutValue(mentionText, 1, 1);
			}
		}
	}

	public void visualize() {
		logger.info("Mention entity count analysis processor:");
		logger.info("Common false negative mention texts:");
		// int maxAnnotatedCount = 0;
		TIntIntHashMap annotatedCountToTPCount = new TIntIntHashMap();
		TIntIntHashMap annotatedCountToFNCount = new TIntIntHashMap();
		for (String mentionText : annotatedCounts.keySet()) {
			int count = annotatedCounts.get(mentionText);
			annotatedCountToTPCount.adjustOrPutValue(count, tpCounts.get(mentionText), tpCounts.get(mentionText));
			annotatedCountToFNCount.adjustOrPutValue(count, fnCounts.get(mentionText), fnCounts.get(mentionText));
			// if (count > maxAnnotatedCount) {
			// maxAnnotatedCount = count;
			// }
			if (fnCounts.get(mentionText) > 4) {
				logger.info(mentionText + "\tTP\t" + tpCounts.get(mentionText) + "\tFN\t" + fnCounts.get(mentionText));
			}
		}
		// logger.info("Maximum annotated count: " + maxAnnotatedCount);
		// logger.info("Number of times mention text annotated / number of TPs / number of FPs:");
		// for (int i = 1; i <= maxAnnotatedCount; i++) {
		// if (annotatedCountToTPCount.get(i) + annotatedCountToFNCount.get(i) > 0) {
		// logger.info(i + "\t" + annotatedCountToTPCount.get(i) + "\t" + annotatedCountToFNCount.get(i));
		// }
		// }

		logger.info("Common false positive mention texts:");
		// int maxPredictedCount = 0;
		TIntIntHashMap predictedCountToTPCount = new TIntIntHashMap();
		TIntIntHashMap predictedCountToFPCount = new TIntIntHashMap();
		for (String mentionText : predictedCounts.keySet()) {
			int count = predictedCounts.get(mentionText);
			predictedCountToTPCount.adjustOrPutValue(count, tpCounts.get(mentionText), tpCounts.get(mentionText));
			predictedCountToFPCount.adjustOrPutValue(count, fpCounts.get(mentionText), fpCounts.get(mentionText));
			// if (count > maxPredictedCount) {
			// maxPredictedCount = count;
			// }
			if (fpCounts.get(mentionText) > 4) {
				logger.info(mentionText + "\tTP\t" + tpCounts.get(mentionText) + "\tFP\t" + fpCounts.get(mentionText));
			}
		}
		// logger.info("Maximum predicted count: " + maxPredictedCount);
		// logger.info("Number of times mention text found / number of TPs / number of FPs:");
		// for (int i = 1; i <= maxPredictedCount; i++) {
		// if (predictedCountToTPCount.get(i) + predictedCountToFPCount.get(i) > 0) {
		// logger.info(i + "\t" + predictedCountToTPCount.get(i) + "\t" + predictedCountToFPCount.get(i));
		// }
		// }
	}
}