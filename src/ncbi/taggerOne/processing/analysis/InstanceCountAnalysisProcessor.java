package ncbi.taggerOne.processing.analysis;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;

public class InstanceCountAnalysisProcessor extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(InstanceCountAnalysisProcessor.class);
	private static final long serialVersionUID = 1L;

	private TObjectIntMap<String> annotatedCounts;
	private TObjectIntMap<String> predictedCounts;
	private TObjectIntMap<String> tpCounts;
	private TObjectIntMap<String> fpCounts;
	private TObjectIntMap<String> fnCounts;

	public InstanceCountAnalysisProcessor() {
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

		Set<String> targetEntityIds = new HashSet<String>();
		for (AnnotatedSegment segment1 : targetAnnotation) {
			if (!segment1.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
				for (Entity entity : segment1.getEntities()) {
					targetEntityIds.add(entity.getPrimaryIdentifier());
				}
			}
		}
		Set<String> predictedEntityIds = new HashSet<String>();
		for (AnnotatedSegment segment1 : predictedAnnotation) {
			if (!segment1.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
				for (Entity entity : segment1.getEntities()) {
					predictedEntityIds.add(entity.getPrimaryIdentifier());
				}
			}
		}
		for (String entityId : targetEntityIds) {
			boolean found = predictedEntityIds.contains(entityId);
			annotatedCounts.adjustOrPutValue(entityId, 1, 1);
			if (found) {
				tpCounts.adjustOrPutValue(entityId, 1, 1);
			} else {
				fnCounts.adjustOrPutValue(entityId, 1, 1);
			}
		}
		for (String entityId : predictedEntityIds) {
			boolean found = targetEntityIds.contains(entityId);
			predictedCounts.adjustOrPutValue(entityId, 1, 1);
			if (!found) {
				fpCounts.adjustOrPutValue(entityId, 1, 1);
			}
		}
	}

	public void visualize() {
		logger.info("Instance count analysis processor:");
		int maxAnnotatedCount = 0;
		TIntIntHashMap annotatedCountToTPCount = new TIntIntHashMap();
		TIntIntHashMap annotatedCountToFNCount = new TIntIntHashMap();
		for (String entityId : annotatedCounts.keySet()) {
			int count = annotatedCounts.get(entityId);
			annotatedCountToTPCount.adjustOrPutValue(count, tpCounts.get(entityId), tpCounts.get(entityId));
			annotatedCountToFNCount.adjustOrPutValue(count, fnCounts.get(entityId), fnCounts.get(entityId));
			if (count > maxAnnotatedCount) {
				maxAnnotatedCount = count;
			}
			if (fnCounts.get(entityId) > 4) {
				logger.info(entityId + "\tTP\t" + tpCounts.get(entityId) + "\tFN\t" + fnCounts.get(entityId));
			}
		}
		logger.info("Maximum annotated count: " + maxAnnotatedCount);
		logger.info("Annotated counts:");
		for (int i = 1; i <= maxAnnotatedCount; i++) {
			if (annotatedCountToTPCount.get(i) + annotatedCountToFNCount.get(i) > 0) {
				logger.info(i + "\t" + annotatedCountToTPCount.get(i) + "\t" + annotatedCountToFNCount.get(i));
			}
		}

		int maxPredictedCount = 0;
		TIntIntHashMap predictedCountToTPCount = new TIntIntHashMap();
		TIntIntHashMap predictedCountToFPCount = new TIntIntHashMap();
		for (String entityId : predictedCounts.keySet()) {
			int count = predictedCounts.get(entityId);
			predictedCountToTPCount.adjustOrPutValue(count, tpCounts.get(entityId), tpCounts.get(entityId));
			predictedCountToFPCount.adjustOrPutValue(count, fpCounts.get(entityId), fpCounts.get(entityId));
			if (count > maxPredictedCount) {
				maxPredictedCount = count;
			}
			if (fpCounts.get(entityId) > 4) {
				logger.info(entityId + "\tTP\t" + tpCounts.get(entityId) + "\tFP\t" + fpCounts.get(entityId));
			}
		}
		logger.info("Maximum predicted count: " + maxPredictedCount);
		logger.info("Predicted counts:");
		for (int i = 1; i <= maxPredictedCount; i++) {
			if (predictedCountToTPCount.get(i) + predictedCountToFPCount.get(i) > 0) {
				logger.info(i + "\t" + predictedCountToTPCount.get(i) + "\t" + predictedCountToFPCount.get(i));
			}
		}
	}
}