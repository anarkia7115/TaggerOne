package ncbi.taggerOne.lexicon;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.DenseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;

public class TokenWeightCalculator implements Serializable {

	// TODO REFACTOR This class was originally derived from the Index class and needs to be streamlined

	private static final Logger logger = LoggerFactory.getLogger(TokenWeightCalculator.class);
	private static final long serialVersionUID = 1L;

	private Map<Vector<String>, Set<Entity>> vectorToEntityMap;
	private Dictionary<Vector<String>> nameVectors;
	private Dictionary<String> mentionVectorSpace;
	private Dictionary<String> nameVectorSpace;
	private List<TIntSet> nameElementToVectorIndices;
	private List<TIntSet> mentionElementToVectorIndices;

	public TokenWeightCalculator(Dictionary<String> mentionVectorSpace, Dictionary<String> nameVectorSpace, Set<Entity> entities) {
		logger.info("Number of entities: " + entities.size());
		this.mentionVectorSpace = mentionVectorSpace;
		this.nameVectorSpace = nameVectorSpace;
		// TODO Verify entities are all one type
		vectorToEntityMap = new HashMap<Vector<String>, Set<Entity>>();
		for (Entity entity : entities) {
			for (MentionName name : entity.getNames()) {
				Vector<String> nameVector = name.getVector();
				if (nameVector != null) {
					Set<Entity> entitySet = vectorToEntityMap.get(nameVector);
					if (entitySet == null) {
						entitySet = new HashSet<Entity>();
						vectorToEntityMap.put(nameVector, entitySet);
					}
					entitySet.add(entity);
				}
			}
		}
		List<Vector<String>> vectors = new ArrayList<Vector<String>>(vectorToEntityMap.keySet());
		nameVectors = new Dictionary<Vector<String>>();
		for (Vector<String> vector : vectors) {
			nameVectors.addElement(vector);
		}
		// Create nameElementToVectorIndices
		nameElementToVectorIndices = new ArrayList<TIntSet>(nameVectorSpace.size());
		for (int nameElementIndex = 0; nameElementIndex < nameVectorSpace.size(); nameElementIndex++) {
			nameElementToVectorIndices.add(new TIntHashSet());
		}
		for (int nameVectorIndex = 0; nameVectorIndex < nameVectors.size(); nameVectorIndex++) {
			Vector<String> nameVector = nameVectors.getElement(nameVectorIndex);
			VectorIterator iterator = nameVector.getIterator();
			while (iterator.next()) {
				int nameElementIndex = iterator.getIndex();
				TIntSet nameVectorIndexSet = nameElementToVectorIndices.get(nameElementIndex);
				nameVectorIndexSet.add(nameVectorIndex);
			}
		}
		// Create mentionElementToVectorIndices
		mentionElementToVectorIndices = new ArrayList<TIntSet>(mentionVectorSpace.size());
		for (int mentionElementIndex = 0; mentionElementIndex < mentionVectorSpace.size(); mentionElementIndex++) {
			mentionElementToVectorIndices.add(new TIntHashSet());
		}
	}

	public Vector<String> getMentionWeights() {
		double size = nameVectors.size();
		Vector<String> mentionWeights = new DenseVector<String>(mentionVectorSpace);
		for (int mentionIndex = 0; mentionIndex < mentionVectorSpace.size(); mentionIndex++) {
			String mentionElement = mentionVectorSpace.getElement(mentionIndex);
			int nameIndex = nameVectorSpace.getIndex(mentionElement);
			int frequency = 0;
			if (nameIndex >= 0) {
				frequency = nameElementToVectorIndices.get(nameIndex).size();
			}
			double weight = Math.log(size / (frequency + 1));
			mentionWeights.set(mentionIndex, weight);
			// logger.info("XXX Mention element = " + mentionElement + ", weight = " + weight);
		}
		return mentionWeights;
	}

	public Vector<String> getNameWeights() {
		double size = nameVectors.size();
		Vector<String> nameWeights = new DenseVector<String>(nameVectorSpace);
		for (int nameIndex = 0; nameIndex < nameVectorSpace.size(); nameIndex++) {
			int frequency = nameElementToVectorIndices.get(nameIndex).size();
			double weight = Math.log(size / (frequency + 1));
			nameWeights.set(nameIndex, weight);
			// logger.info("XXX Name element = " + nameVectorSpace.getElement(nameIndex) + ", weight = " + weight);
		}
		return nameWeights;
	}
}
