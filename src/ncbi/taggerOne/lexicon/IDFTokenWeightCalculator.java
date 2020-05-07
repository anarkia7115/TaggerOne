package ncbi.taggerOne.lexicon;

import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.DenseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;

public class IDFTokenWeightCalculator implements Serializable {

	private static final long serialVersionUID = 1L;

	private Vector<String> weights;

	public IDFTokenWeightCalculator(Dictionary<String> mentionVectorSpace, Map<String, Dictionary<String>> nameVectorSpaces, Lexicon lexicon) {
		TObjectIntMap<String> frequencies = new TObjectIntHashMap<String>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0);
		int size = 0;
		for (String entityType : nameVectorSpaces.keySet()) {
			Dictionary<String> nameVectorSpace = nameVectorSpaces.get(entityType);
			Set<Entity> entities = lexicon.getEntities(entityType);
			for (Entity entity : entities) {
				for (MentionName name : entity.getNames()) {
					Vector<String> nameVector = name.getVector();
					if (nameVector != null) {
						VectorIterator iterator = nameVector.getIterator();
						while (iterator.next()) {
							int index = iterator.getIndex();
							String element = nameVectorSpace.getElement(index);
							frequencies.adjustOrPutValue(element, 1, 1);
						}

						size++;
					}
				}
			}
		}
		double sizeDouble = size;
		Vector<String> mentionWeights = new DenseVector<String>(mentionVectorSpace);
		for (int mentionIndex = 0; mentionIndex < mentionVectorSpace.size(); mentionIndex++) {
			String mentionElement = mentionVectorSpace.getElement(mentionIndex);
			int frequency = frequencies.get(mentionElement);
			double weight = Math.log(sizeDouble / (frequency + 1));
			mentionWeights.set(mentionIndex, weight);
		}
		this.weights = mentionWeights;
	}

	public Vector<String> getWeights() {
		return weights;
	}
}
