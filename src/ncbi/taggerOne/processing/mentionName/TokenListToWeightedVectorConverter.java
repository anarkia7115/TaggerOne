package ncbi.taggerOne.processing.mentionName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.VectorFactory;
import ncbi.util.Profiler;

public class TokenListToWeightedVectorConverter extends MentionNameProcessor {

	private static final Logger logger = LoggerFactory.getLogger(TokenListToWeightedVectorConverter.class);
	private static final long serialVersionUID = 1L;

	private boolean dontNormalizeVectors;
	private boolean warnOnEmptyVector;
	private Dictionary<String> vectorSpace;
	private VectorFactory vectorFactory;
	private Vector<String> weights;

	public TokenListToWeightedVectorConverter(VectorFactory vectorFactory, Dictionary<String> vectorSpace, Vector<String> weights, boolean dontNormalizeVectors, boolean warnOnEmptyVector) {
		if (vectorSpace.size() != weights.dimensions()) {
			throw new IllegalArgumentException("Vector space and weights do not have the same dimensions");
		}
		this.vectorFactory = vectorFactory;
		this.vectorSpace = vectorSpace;
		this.weights = weights;
		this.warnOnEmptyVector = warnOnEmptyVector;
		this.dontNormalizeVectors = dontNormalizeVectors;
	}

	@Override
	public void process(MentionName entityName) {
		Profiler.start("TokenListToWeightedVectorConverter.process()");
		if (!vectorSpace.isFrozen()) {
			throw new IllegalStateException("Cannot convert to vector until Dictionary is frozen");
		}
		Vector<String> nameVector = vectorFactory.create(vectorSpace);
		for (String token : entityName.getTokens()) {
			int index = vectorSpace.getIndex(token);
			if (index < 0) {
				// Warning is very high volume (every segment)
				// logger.info("WARNING: name \"" + entityName.getName() + "\" contains an unknown token: \"" + token + "\"");
				index = vectorSpace.getIndex(T1Constants.UNKNOWN_TOKEN);
			}
			if (index >= 0) {
				// Add weight for index to previous value
				nameVector.increment(index, weights.get(index));
			}
		}
		if (nameVector.isEmpty()) {
			entityName.setVector(null);
			if (warnOnEmptyVector) {
				logger.warn("name \"" + entityName.getName() + "\" resulted in an empty vector");
			}
		} else {
			if (!dontNormalizeVectors) {
				nameVector.normalize();
			}
			entityName.setVector(nameVector);
		}
		if (!entityName.isLabel()) {
			entityName.setTokens(null);
		}
		Profiler.stop("TokenListToWeightedVectorConverter.process()");
	}
}