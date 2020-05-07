package ncbi.taggerOne.model.normalization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.lexicon.Index;
import ncbi.taggerOne.processing.TrainingProgressTracker;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.matrix.DenseBySparseMatrix;
import ncbi.taggerOne.util.matrix.Matrix;
import ncbi.taggerOne.util.vector.SparseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;
import ncbi.util.Profiler;

public class NormalizationModel implements NormalizationModelPredictor, NormalizationModelUpdater, Serializable {

	private static final Logger logger = LoggerFactory.getLogger(NormalizationModel.class);
	private static final long serialVersionUID = 1L;

	protected TrainingProgressTracker trainingProgress;
	protected Index index;

	protected Dictionary<String> mentionVectorSpace;
	protected Dictionary<String> nameVectorSpace;
	protected Dictionary<Vector<String>> nameVectorDictionary;

	protected double[] cosineSimWeight;
	protected DenseBySparseMatrix<String, String> weights; // rows indexed by mention vector space, columns indexed by name vector space
	protected DenseBySparseMatrix<String, Vector<String>> lexiconMatrix; // rows indexed by name vector space, columns indexed by name vectors
	protected int[] mentionIndexToNameIndex;

	public NormalizationModel(Index index, Dictionary<String> mentionVectorSpace, Dictionary<String> nameVectorSpace, TrainingProgressTracker trainingProgress) {
		this.index = index;
		this.mentionVectorSpace = mentionVectorSpace;
		this.nameVectorSpace = nameVectorSpace;
		this.nameVectorDictionary = index.getNameVectorDictionary();
		// Prepare weights vectors; must be non-zero so words not observed in training data will be used
		cosineSimWeight = new double[1];
		cosineSimWeight[0] = 1.0;
		weights = new DenseBySparseMatrix<String, String>(mentionVectorSpace, nameVectorSpace);
		initDataStructures();
		this.trainingProgress = trainingProgress;
	}

	protected NormalizationModel(Index index, Dictionary<String> mentionVectorSpace, Dictionary<String> nameVectorSpace, double[] cosineSimWeight, DenseBySparseMatrix<String, String> weights, TrainingProgressTracker trainingProgress) {
		this.index = index;
		this.mentionVectorSpace = mentionVectorSpace;
		this.nameVectorSpace = nameVectorSpace;
		this.nameVectorDictionary = index.getNameVectorDictionary();
		this.cosineSimWeight = cosineSimWeight;
		this.weights = weights;
		initDataStructures();
		this.trainingProgress = trainingProgress;
	}

	private void initDataStructures() {
		mentionIndexToNameIndex = new int[mentionVectorSpace.size()];
		Arrays.fill(mentionIndexToNameIndex, -1);
		Set<String> vectorSpaceElements = new HashSet<String>();
		vectorSpaceElements.addAll(mentionVectorSpace.getElements());
		vectorSpaceElements.addAll(nameVectorSpace.getElements());
		for (String element : vectorSpaceElements) {
			int mentionIndex = mentionVectorSpace.getIndex(element);
			int nameIndex = nameVectorSpace.getIndex(element);
			if (mentionIndex >= 0 && nameIndex >= 0) {
				mentionIndexToNameIndex[mentionIndex] = nameIndex;
			}
		}
		lexiconMatrix = new DenseBySparseMatrix<String, Vector<String>>(nameVectorSpace, nameVectorDictionary);
		for (int nameVectorIndex = 0; nameVectorIndex < nameVectorDictionary.size(); nameVectorIndex++) {
			Vector<String> nameVector = nameVectorDictionary.getElement(nameVectorIndex);
			VectorIterator iterator = nameVector.getIterator();
			while (iterator.next()) {
				int nameVectorSpaceIndex = iterator.getIndex();
				double value = iterator.getValue();
				lexiconMatrix.set(nameVectorSpaceIndex, nameVectorIndex, value);
			}
		}
	}

	public NormalizationModelPredictor compile() {
		DenseBySparseMatrix<String, String> compiledWeights = new DenseBySparseMatrix<String, String>(mentionVectorSpace, nameVectorSpace);
		compiledWeights.increment(weights);
		for (int mentionIndex = 0; mentionIndex < mentionIndexToNameIndex.length; mentionIndex++) {
			int nameIndex = mentionIndexToNameIndex[mentionIndex];
			if (nameIndex >= 0) {
				double weight = compiledWeights.get(mentionIndex, nameIndex) + cosineSimWeight[0];
				compiledWeights.set(mentionIndex, nameIndex, weight);
			}
		}
		// return new FasterCompiledNormalizationModel(index, mentionVectorSpace, nameVectorSpace, compiledWeights);
		return new CompiledNormalizationModel(index, mentionVectorSpace, nameVectorSpace, compiledWeights);
	}

	@Override
	public double getScoreBound(Vector<String> mentionVector) {
		Profiler.start("NormalizationModel.getScoreBound()");
		if (mentionVector == null) {
			return 0.0;
		}

		/*
		 * This calculates an upper bound on the score for the highest-scoring name in the lexicon. This upper bound method must allow for changing weights. It therefore "translates" the mentionVector through the weight matrix into the
		 * equivalent of a name vector. We then make use of the fact that the highest weight for any name token in a name vector is 1.0. This means that an upper bound on highest possible normalization score would be the sum of the POSITIVE
		 * weights in the name vector equivalent.
		 */

		double scoreBound = 0.0;
		// Converts a mention vector to a name vector
		VectorIterator mentionIterator = mentionVector.getIterator();
		while (mentionIterator.next()) {
			int mentionIndex = mentionIterator.getIndex();
			double mentionValue = mentionIterator.getValue();
			// Add value from cosine sim
			int nameIndex = mentionIndexToNameIndex[mentionIndex];
			if (nameIndex >= 0) {
				double weight = mentionValue * cosineSimWeight[0];
				if (weight > 0.0) {
					scoreBound += weight;
				}
			}
			// Add values from weights matrix
			Vector<String> nameVector = weights.getRowVector(mentionIndex);
			if (nameVector != null) {
				VectorIterator nameIterator = nameVector.getIterator();
				while (nameIterator.next()) {
					double weight = mentionValue * nameIterator.getValue();
					if (weight > 0.0) {
						scoreBound += weight;
					}
				}
			}
		}
		Profiler.stop("NormalizationModel.getScoreBound()");
		return scoreBound;
	}

	@Override
	public double getCosineSimWeight() {
		return cosineSimWeight[0];
	}

	@Override
	public double getWeight(int mentionIndex, int nameIndex) {
		double weight = weights.get(mentionIndex, nameIndex);
		if (nameIndex == mentionIndexToNameIndex[mentionIndex]) {
			weight += cosineSimWeight[0];
		}
		return weight;
	}

	@Override
	public void findBest(Vector<String> mentionVector, RankedList<Entity> bestEntities) {
		Profiler.start("NormalizationModel.findBest()");
		Vector<String> nameVectorEquivalent = convertMentionVectorToNameVectorEquivalent(mentionVector);
		Vector<Vector<String>> nameVectorScores = convertNameVectorToNameScores(nameVectorEquivalent);
		double unknownScore = scoreEntity(mentionVector, index.getUnknownEntity());
		bestEntities.add(unknownScore, index.getUnknownEntity());
		VectorIterator nameVectorIterator = nameVectorScores.getIterator();
		while (nameVectorIterator.next()) {
			double score = nameVectorIterator.getValue();
			if (bestEntities.check(score)) {
				int nameVectorIndex = nameVectorIterator.getIndex();
				Vector<String> nameVector = nameVectorDictionary.getElement(nameVectorIndex);
				Set<Entity> entities = index.getEntities(nameVector);
				for (Entity entity : entities) {
					bestEntities.add(score, entity);
				}
			}
		}
		Profiler.stop("NormalizationModel.findBest()");
	}

	protected Vector<String> convertMentionVectorToNameVectorEquivalent(Vector<String> mentionVector) {
		Profiler.start("NormalizationModel.convertMentionVectorToNameVectorEquivalent()");
		SparseVector<String> nameVectorEquivalent = new SparseVector<String>(nameVectorSpace);
		// Converts a mention vector to a name vector
		VectorIterator mentionIterator = mentionVector.getIterator();
		while (mentionIterator.next()) {
			int mentionIndex = mentionIterator.getIndex();
			double mentionValue = mentionIterator.getValue();
			// Add value from cosine sim
			int nameIndex = mentionIndexToNameIndex[mentionIndex];
			if (nameIndex >= 0) {
				nameVectorEquivalent.increment(nameIndex, mentionValue * cosineSimWeight[0]);
			}
			// Add values from weights matrix
			Vector<String> nameVector = weights.getRowVector(mentionIndex);
			if (nameVector != null) {
				nameVectorEquivalent.increment(mentionValue, nameVector);
			}
		}
		Profiler.stop("NormalizationModel.convertMentionVectorToNameVectorEquivalent()");
		return nameVectorEquivalent;
	}

	private Vector<Vector<String>> convertNameVectorToNameScores(Vector<String> nameVectorEquivalent) {
		Profiler.start("NormalizationModel.convertNameVectorToNameScores()");
		SparseVector<Vector<String>> nameVectorScores = new SparseVector<Vector<String>>(nameVectorDictionary);
		VectorIterator nameVectorSpaceIndexIterator = nameVectorEquivalent.getIterator();
		while (nameVectorSpaceIndexIterator.next()) {
			int nameVectorSpaceIndex = nameVectorSpaceIndexIterator.getIndex();
			double nameVectorSpaceValue = nameVectorSpaceIndexIterator.getValue();
			Vector<Vector<String>> lexiconVector = lexiconMatrix.getRowVector(nameVectorSpaceIndex);
			if (lexiconVector != null) {
				nameVectorScores.increment(nameVectorSpaceValue, lexiconVector);
			}
		}
		Profiler.stop("NormalizationModel.convertNameVectorToNameScores()");
		return nameVectorScores;
	}

	@Override
	public MentionName findBestName(Vector<String> mentionVector, Entity entity) {
		Profiler.start("NormalizationModel.findBestName()");
		if (mentionVector.dimensions() != mentionVectorSpace.size()) {
			throw new IllegalArgumentException("Mention vector dimensions are not equal");
		}
		MentionName bestName = null;
		double bestScore = Double.NEGATIVE_INFINITY; // Always pick a name
		for (MentionName name : entity.getNames()) {
			Vector<String> nameVector = name.getVector();
			double score = score(mentionVector, nameVector);
			if (score > bestScore) {
				bestScore = score;
				bestName = name;
			}
		}
		Profiler.stop("NormalizationModel.findBestName()");
		return bestName;
	}

	@Override
	public double scoreEntity(Vector<String> mentionVector, Entity entity) {
		Profiler.start("NormalizationModel.scoreEntity()");
		if (mentionVector.dimensions() != mentionVectorSpace.size()) {
			throw new IllegalArgumentException("Mention vector dimensions are not equal");
		}
		double bestScore = Double.NEGATIVE_INFINITY; // Always pick a name
		for (MentionName name : entity.getNames()) {
			Vector<String> nameVector = name.getVector();
			double score = score(mentionVector, nameVector);
			if (score > bestScore) {
				bestScore = score;
			}
		}
		Profiler.stop("NormalizationModel.scoreEntity()");
		return bestScore;
	}

	@Override
	public double scoreNameVector(Vector<String> mentionVector, Vector<String> nameVector) {
		Profiler.start("NormalizationModel.scoreNameVector()");
		if (mentionVector == null || nameVector == null) {
			Profiler.stop("NormalizationModel.scoreNameVector()");
			return 0.0;
		}
		if (mentionVector.dimensions() != mentionVectorSpace.size()) {
			throw new IllegalArgumentException("Mention vector dimensions are not equal");
		}
		if (nameVector.dimensions() != nameVectorSpace.size()) {
			throw new IllegalArgumentException("Name vector dimensions are not equal");
		}
		double score = score(mentionVector, nameVector);
		Profiler.stop("NormalizationModel.scoreNameVector()");
		return score;
	}

	@Override
	public void visualizeScore(Vector<String> mentionVector, Vector<String> nameVector) {
		Profiler.start("NormalizationModel.visualizeScore()");
		List<String> scoreLines = new ArrayList<String>();
		if (mentionVectorSpace != null && nameVectorSpace != null && mentionVector != null) {
			for (int i = 0; i < mentionVector.dimensions(); i++) {
				if (mentionVector.get(i) != 0.0) {
					String mentionElement = mentionVectorSpace.getElement(i);
					for (int j = 0; j < nameVector.dimensions(); j++) {
						if (nameVector.get(j) != 0.0) {
							String nameElement = nameVectorSpace.getElement(j);
							double weight = weights.get(i, j);
							if (j == mentionIndexToNameIndex[i]) {
								scoreLines.add("\t\t\t" + mentionElement + "\t" + mentionVector.get(i) + "\t" + nameElement + "\t" + nameVector.get(j) + "\t" + cosineSimWeight[0] + "\t" + weight);
							} else if (weight != 0.0) {
								scoreLines.add("\t\t\t" + mentionElement + "\t" + mentionVector.get(i) + "\t" + nameElement + "\t" + nameVector.get(j) + "\t0.0\t" + weight);
							}
						}
					}
				}
			}
		}
		Collections.sort(scoreLines);
		for (String line : scoreLines) {
			logger.info(line);
		}
		Profiler.stop("NormalizationModel.visualizeScore()");
	}

	protected double score(Vector<String> mentionVector, Vector<String> nameVector) {
		Profiler.start("NormalizationModel.score()");
		double score = 0.0;
		if (mentionVector != null && nameVector != null) {
			VectorIterator mentionIterator = mentionVector.getIterator();
			while (mentionIterator.next()) {
				int mentionIndex = mentionIterator.getIndex();
				double mentionValue = mentionIterator.getValue();
				VectorIterator nameIterator = nameVector.getIterator();
				while (nameIterator.next()) {
					int nameIndex = nameIterator.getIndex();
					double weight = weights.get(mentionIndex, nameIndex);
					if (nameIndex == mentionIndexToNameIndex[mentionIndex]) {
						weight += cosineSimWeight[0];
					}
					if (weight != 0.0) {
						double nameValue = nameIterator.getValue();
						score += mentionValue * weight * nameValue;
					}
				}
			}
		}
		Profiler.stop("NormalizationModel.score()");
		return score;
	}

	@Override
	public void update(double cosineSimWeight, Matrix<String, String> weightUpdates) {
		Profiler.start("NormalizationModel.update()");
		this.cosineSimWeight[0] += cosineSimWeight;
		weights.increment(weightUpdates);
		Profiler.stop("NormalizationModel.update()");
	}
}
