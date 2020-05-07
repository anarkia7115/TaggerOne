package ncbi.taggerOne.model.normalization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ncbi.taggerOne.lexicon.Index;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.matrix.DenseBySparseMatrix;
import ncbi.taggerOne.util.vector.DenseVector;
import ncbi.taggerOne.util.vector.SparseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;
import ncbi.util.Profiler;

public class CompiledNormalizationModel implements NormalizationModelPredictor {

	private static final long serialVersionUID = 1L;

	private Index index;
	private Dictionary<String> mentionVectorSpace;
	private Dictionary<String> nameVectorSpace;
	private Dictionary<Vector<String>> nameVectorDictionary;
	private DenseBySparseMatrix<String, String> weights; // rows indexed by mention vector space, columns indexed by name vector space

	private Vector<String> highestVector;
	private int[] indexOfHighestVector;
	private DenseBySparseMatrix<String, Vector<String>> shortcutMatrix; // rows indexed by mention vector space, columns indexed by name vector dictionary

	public CompiledNormalizationModel(Index index, Dictionary<String> mentionVectorSpace, Dictionary<String> nameVectorSpace, DenseBySparseMatrix<String, String> weights) {
		this.mentionVectorSpace = mentionVectorSpace;
		this.nameVectorSpace = nameVectorSpace;
		this.weights = weights;
		this.index = index;
		this.nameVectorDictionary = index.getNameVectorDictionary();
		// Initialize lexiconMatrix
		DenseBySparseMatrix<String, Vector<String>> lexiconMatrix = new DenseBySparseMatrix<String, Vector<String>>(nameVectorSpace, nameVectorDictionary);
		for (int nameVectorIndex = 0; nameVectorIndex < nameVectorDictionary.size(); nameVectorIndex++) {
			Vector<String> nameVector = nameVectorDictionary.getElement(nameVectorIndex);
			VectorIterator iterator = nameVector.getIterator();
			while (iterator.next()) {
				int nameVectorSpaceIndex = iterator.getIndex();
				double value = iterator.getValue();
				lexiconMatrix.set(nameVectorSpaceIndex, nameVectorIndex, value);
			}
		}
		// Initialize shortcutMatrix
		shortcutMatrix = new DenseBySparseMatrix<String, Vector<String>>(mentionVectorSpace, nameVectorDictionary);
		for (int mentionVectorSpaceIndex = 0; mentionVectorSpaceIndex < mentionVectorSpace.size(); mentionVectorSpaceIndex++) {
			Vector<String> nameVectorEquivalent = weights.getRowVector(mentionVectorSpaceIndex);
			if (nameVectorEquivalent != null) {
				Vector<Vector<String>> nameScores = convertNameVectorToNameScores(lexiconMatrix, nameVectorEquivalent);
				shortcutMatrix.incrementRow(mentionVectorSpaceIndex, nameScores);
			}
		}
		lexiconMatrix = null;
		// Initialize highestVector
		this.highestVector = new DenseVector<String>(mentionVectorSpace);
		this.indexOfHighestVector = new int[mentionVectorSpace.size()];
		for (int i = 0; i < mentionVectorSpace.size(); i++) {
			double highest = 0.0;
			int highestIndex = -1;
			Vector<Vector<String>> shortcutVector = shortcutMatrix.getRowVector(i);
			if (shortcutVector != null) {
				VectorIterator shortcutIterator = shortcutVector.getIterator();
				while (shortcutIterator.next()) {
					double value = shortcutIterator.getValue();
					if (highest < value) {
						highest = value;
						highestIndex = shortcutIterator.getIndex();
					}
				}
			}
			highestVector.set(i, highest);
			indexOfHighestVector[i] = highestIndex;
		}
	}

	private Vector<Vector<String>> convertNameVectorToNameScores(DenseBySparseMatrix<String, Vector<String>> lexiconMatrix, Vector<String> nameVectorEquivalent) {
		Profiler.start("CompiledNormalizationModel.convertNameVectorToNameScores()");
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
		Profiler.stop("CompiledNormalizationModel.convertNameVectorToNameScores()");
		return nameVectorScores;
	}

	@Override
	public NormalizationModelPredictor compile() {
		return this;
	}

	@Override
	public double getScoreBound(Vector<String> mentionVector) {
		Profiler.start("CompiledNormalizationModel.getHighestScore()");
		double highest = 0.0;
		if (mentionVector != null) {
			highest = mentionVector.dotProduct(highestVector);
		}
		Profiler.stop("CompiledNormalizationModel.getHighestScore()");
		return highest;
	}

	public DenseBySparseMatrix<String, String> getWeights() {
		return weights;
	}

	public DenseBySparseMatrix<String, Vector<String>> getShortcutMatrix() {
		return shortcutMatrix;
	}

	@Override
	public void findBest(Vector<String> mentionVector, RankedList<Entity> bestEntities) {
		Profiler.start("CompiledNormalizationModel.findBest()");
		double unknownScore = scoreEntity(mentionVector, index.getUnknownEntity());
		bestEntities.add(unknownScore, index.getUnknownEntity());
		if (mentionVector.cardinality() == 1 && bestEntities.maxSize() == 1) {
			Profiler.start("CompiledNormalizationModel.findBest()@FAST");
			VectorIterator mentionIterator = mentionVector.getIterator();
			mentionIterator.next();
			int mentionIndex = mentionIterator.getIndex();
			int nameVectorIndex = indexOfHighestVector[mentionIndex];
			if (nameVectorIndex != -1) {
				double mentionValue = mentionIterator.getValue();
				Vector<String> nameVector = nameVectorDictionary.getElement(nameVectorIndex);
				bestEntities.add(mentionValue * highestVector.get(mentionIndex), index.getEntities(nameVector).iterator().next());
			}
			Profiler.stop("CompiledNormalizationModel.findBest()@FAST");
			Profiler.stop("CompiledNormalizationModel.findBest()");
			return;
		}
		Profiler.start("CompiledNormalizationModel.findBest()@1");
		SparseVector<Vector<String>> nameVectorScores = new SparseVector<Vector<String>>(nameVectorDictionary);
		VectorIterator mentionIterator = mentionVector.getIterator();
		while (mentionIterator.next()) {
			int mentionIndex = mentionIterator.getIndex();
			double mentionValue = mentionIterator.getValue();
			Vector<Vector<String>> shortcutVector = shortcutMatrix.getRowVector(mentionIndex);
			if (shortcutVector != null) {
				nameVectorScores.increment(mentionValue, shortcutVector);
			}
		}
		Profiler.stop("CompiledNormalizationModel.findBest()@1");
		// TODO PERFORMANCE 2% speedup possible if
		Profiler.start("CompiledNormalizationModel.findBest()@2");
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
		Profiler.stop("CompiledNormalizationModel.findBest()@2");
		Profiler.stop("CompiledNormalizationModel.findBest()");
	}

	@Override
	public MentionName findBestName(Vector<String> mentionVector, Entity entity) {
		Profiler.start("CompiledNormalizationModel.findBestName()");
		if (mentionVector.dimensions() != mentionVectorSpace.size()) {
			throw new IllegalArgumentException("Mention vector dimensions are not equal");
		}
		MentionName bestName = null;
		double bestScore = Double.NEGATIVE_INFINITY; // Always pick a name
		for (MentionName name : entity.getNames()) {
			Vector<String> nameVector = name.getVector();
			double score = 0.0;
			if (nameVector != null) {
				score = score(mentionVector, nameVector);
				if (score > bestScore) {
					bestScore = score;
					bestName = name;
				}
			}
		}
		Profiler.stop("CompiledNormalizationModel.findBestName()");
		return bestName;
	}

	@Override
	public double scoreEntity(Vector<String> mentionVector, Entity entity) {
		Profiler.start("CompiledNormalizationModel.scoreEntity()");
		if (mentionVector.dimensions() != mentionVectorSpace.size()) {
			throw new IllegalArgumentException("Mention vector dimensions are not equal");
		}
		double bestScore = Double.NEGATIVE_INFINITY; // Always pick a name
		for (MentionName name : entity.getNames()) {
			Vector<String> nameVector = name.getVector();
			double score = 0.0;
			if (nameVector != null) {
				score = score(mentionVector, nameVector);
				if (score > bestScore) {
					bestScore = score;
				}
			}
		}
		Profiler.stop("CompiledNormalizationModel.scoreEntity()");
		return bestScore;
	}

	@Override
	public double scoreNameVector(Vector<String> mentionVector, Vector<String> nameVector) {
		Profiler.start("CompiledNormalizationModel.scoreNameVector()");
		if (mentionVector.dimensions() != mentionVectorSpace.size()) {
			throw new IllegalArgumentException("Mention vector dimensions are not equal");
		}
		if (nameVector.dimensions() != nameVectorSpace.size()) {
			throw new IllegalArgumentException("Name vector dimensions are not equal");
		}
		double score = score(mentionVector, nameVector);
		Profiler.stop("CompiledNormalizationModel.scoreNameVector()");
		return score;
	}

	@Override
	public void visualizeScore(Vector<String> mentionVector, Vector<String> nameVector) {
		Profiler.start("CompiledNormalizationModel.visualizeScore()");
		List<String> scoreLines = new ArrayList<String>();
		VectorIterator mentionIterator = mentionVector.getIterator();
		while (mentionIterator.next()) {
			int mentionIndex = mentionIterator.getIndex();
			VectorIterator nameIterator = nameVector.getIterator();
			while (nameIterator.next()) {
				int nameIndex = nameIterator.getIndex();
				double weight = weights.get(mentionIndex, nameIndex);
				if (weight != 0.0) {
					String mentionElement = mentionVectorSpace.getElement(mentionIndex);
					String nameElement = nameVectorSpace.getElement(nameIndex);
					double mentionValue = mentionIterator.getValue();
					double nameValue = nameIterator.getValue();
					scoreLines.add("\t\t\t" + mentionElement + "\t" + mentionValue + "\t" + nameElement + "\t" + nameValue + "\t" + weight);
				}
			}
		}
		Collections.sort(scoreLines);
		for (String line : scoreLines) {
			System.out.println(line);
		}
		Profiler.stop("CompiledNormalizationModel.visualizeScore()");
	}

	private double score(Vector<String> mentionVector, Vector<String> nameVector) {
		Profiler.start("CompiledNormalizationModel.score()");
		double score = 0.0;
		VectorIterator mentionIterator = mentionVector.getIterator();
		while (mentionIterator.next()) {
			int mentionIndex = mentionIterator.getIndex();
			double mentionValue = mentionIterator.getValue();
			VectorIterator nameIterator = nameVector.getIterator();
			while (nameIterator.next()) {
				int nameIndex = nameIterator.getIndex();
				double jointWeight = weights.get(mentionIndex, nameIndex);
				if (jointWeight != 0.0) {
					double nameValue = nameIterator.getValue();
					score += mentionValue * jointWeight * nameValue;
				}
			}
		}
		Profiler.stop("CompiledNormalizationModel.score()");
		return score;
	}
}
