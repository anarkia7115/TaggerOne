package ncbi.taggerOne.processing.mentionName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.VectorFactory;

public class TokenListToVectorConverter extends MentionNameProcessor {

	private static final Logger logger = LoggerFactory.getLogger(TokenListToVectorConverter.class);
	private static final long serialVersionUID = 1L;

	private boolean warnOnEmptyVector;
	private Dictionary<String> vectorSpace;
	private VectorFactory vectorFactory;

	public TokenListToVectorConverter(VectorFactory vectorFactory, Dictionary<String> vectorSpace, boolean warnOnEmptyVector) {
		this.vectorFactory = vectorFactory;
		this.vectorSpace = vectorSpace;
		this.warnOnEmptyVector = warnOnEmptyVector;
	}

	@Override
	public void process(MentionName entityName) {
		if (!vectorSpace.isFrozen()) {
			throw new IllegalStateException("Cannot convert to vector until Dictionary is frozen");
		}
		Vector<String> nameVector = vectorFactory.create(vectorSpace);
		for (String token : entityName.getTokens()) {
			int index = vectorSpace.getIndex(token);
			if (index >= 0) {
				// TODO Implement a warning for unknown tokens
				// Add one to previous value
				nameVector.increment(index, 1.0);
			}
		}
		if (nameVector.isEmpty()) {
			entityName.setVector(null);
			if (warnOnEmptyVector) {
				logger.warn("name \"" + entityName.getName() + "\" resulted in an empty vector");
			}

		} else {
			entityName.setVector(nameVector);
		}
		// FIXME
		// if (!entityName.isLabel()) {
		// entityName.setTokens(null);
		// }
	}

}
