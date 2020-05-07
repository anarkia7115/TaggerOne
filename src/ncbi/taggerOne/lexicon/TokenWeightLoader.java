package ncbi.taggerOne.lexicon;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.DenseVector;
import ncbi.taggerOne.util.vector.Vector;

public class TokenWeightLoader implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(TokenWeightLoader.class);
	private static final long serialVersionUID = 1L;

	private Vector<String> nameWeights;
	private Vector<String> mentionWeights;

	public TokenWeightLoader(Dictionary<String> mentionVectorSpace, Dictionary<String> nameVectorSpace, String weightsFilename, int column, double defaultValue) {
		// Load weights
		double minWeight = Double.POSITIVE_INFINITY;
		double maxWeight = Double.NEGATIVE_INFINITY;
		TObjectDoubleMap<String> weightsMap = new TObjectDoubleHashMap<String>();
		BufferedReader weightsFile = null;
		try {
			weightsFile = new BufferedReader(new FileReader(weightsFilename));
			String line = weightsFile.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0) {
					String[] fields = line.split("\\t");
					String element = fields[0];
					String weightStr = fields[column];
					double weight = Double.parseDouble(weightStr);
					if (Double.isInfinite(weight)) {
						throw new IllegalArgumentException("Weight for " + element + " is infinite: " + weight);
					}
					if (Double.isNaN(weight)) {
						throw new IllegalArgumentException("Weight for " + element + " is NaN: " + weight);
					}
					if (weight > maxWeight) {
						maxWeight = weight;
					}
					if (weight < minWeight) {
						minWeight = weight;
					}
					weightsMap.put(element, weight);
				}
				line = weightsFile.readLine();
			}
			weightsFile.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				if (weightsFile != null) {
					weightsFile.close();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		logger.info("Loaded token weights; size = " + weightsMap.size() + ", min = " + minWeight + ", max = " + maxWeight);
		nameWeights = new DenseVector<String>(nameVectorSpace);
		for (int nameIndex = 0; nameIndex < nameVectorSpace.size(); nameIndex++) {
			String element = nameVectorSpace.getElement(nameIndex);
			if (weightsMap.containsKey(element)) {
				nameWeights.set(nameIndex, weightsMap.get(element));
			} else {
				logger.error("Weights file does not contain a weight for name element " + element);
				nameWeights.set(nameIndex, defaultValue);
			}
		}
		mentionWeights = new DenseVector<String>(mentionVectorSpace);
		for (int mentionIndex = 0; mentionIndex < mentionVectorSpace.size(); mentionIndex++) {
			String element = mentionVectorSpace.getElement(mentionIndex);
			if (weightsMap.containsKey(element)) {
				mentionWeights.set(mentionIndex, weightsMap.get(element));
			} else {
				mentionWeights.set(mentionIndex, defaultValue);
			}
		}
	}

	public Vector<String> getMentionWeights() {
		return mentionWeights;
	}

	public Vector<String> getNameWeights() {
		return nameWeights;
	}
}
