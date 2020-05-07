package ncbi.taggerOne.processing.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.vector.Vector;

public class AmbiguityAnalyzer extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(AmbiguityAnalyzer.class);
	private static final int MAX_ENTITIES = 10;
	private static final long serialVersionUID = 1L;

	private Map<String, NormalizationModelPredictor> normalizationPredictionModels;

	public AmbiguityAnalyzer(Map<String, NormalizationModelPredictor> normalizationPredictionModels) {
		this.normalizationPredictionModels = normalizationPredictionModels;
	}

	@Override
	public void process(TextInstance input) {
		analyze(input.getTargetStateSequence(), "Target");
		RankedList<List<AnnotatedSegment>> predictedAnnotations = input.getPredictedAnnotations();
		if (predictedAnnotations != null) {
			analyze(predictedAnnotations.getObject(0), "Predicted");
		}
	}

	public void analyze(List<AnnotatedSegment> annotation, String type) {
		for (AnnotatedSegment segment : annotation) {
			String entityClass = segment.getEntityClass();
			if (!entityClass.equals(T1Constants.NONENTITY_STATE)) {
				NormalizationModelPredictor normalizationModelPredictor = normalizationPredictionModels.get(entityClass);
				Vector<String> mentionVector = segment.getMentionName().getVector();
				if (mentionVector != null) {
					RankedList<Entity> bestEntities = new RankedList<Entity>(MAX_ENTITIES);
					normalizationModelPredictor.findBest(mentionVector, bestEntities);
					if (bestEntities.size() > 0) {
						Set<Entity> entities = new HashSet<Entity>();
						double score = bestEntities.getValue(0);
						for (int i = 0; i < bestEntities.size(); i++) {
							if (bestEntities.getValue(i) + T1Constants.EPSILON >= score) {
								Entity entity = bestEntities.getObject(i);
								entities.add(entity);
							}
						}
						String mentionVectorStr = segment.getMentionName().getName();
						logger.trace(type + " name vector \"" + mentionVectorStr + "\" returned entities: " + Entity.visualizePrimaryIdentifiers(entities));
						if (entities != null && entities.size() > 1) {
							logger.info(type + " name vector \"" + mentionVectorStr + "\" is ambiguous between: " + Entity.visualizePrimaryIdentifiers(entities));
						}
					}
				}

			}
		}
	}
}
