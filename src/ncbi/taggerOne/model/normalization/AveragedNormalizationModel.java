package ncbi.taggerOne.model.normalization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ncbi.taggerOne.lexicon.Index;
import ncbi.taggerOne.processing.TrainingProgressTracker;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.matrix.DenseBySparseMatrix;
import ncbi.taggerOne.util.matrix.Matrix;
import ncbi.taggerOne.util.vector.SparseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;
import ncbi.util.Profiler;

public class AveragedNormalizationModel extends NormalizationModel {

	private static final long serialVersionUID = 1L;

	protected double[] cosineSimWeight2;
	protected DenseBySparseMatrix<String, String> weights2;

	public AveragedNormalizationModel(Index index, Dictionary<String> mentionVectorSpace, Dictionary<String> nameVectorSpace, TrainingProgressTracker trainingProgressTracker) {
		super(index, mentionVectorSpace, nameVectorSpace, trainingProgressTracker);
		cosineSimWeight2 = new double[1];
		weights2 = new DenseBySparseMatrix<String, String>(mentionVectorSpace, nameVectorSpace); // weights2 is initially empty
	}

	public AveragedNormalizationModel(double[] cosineSimWeight, DenseBySparseMatrix<String, String> weights, DenseBySparseMatrix<String, String> weights2, Index index,
			Dictionary<String> mentionVectorSpace, Dictionary<String> nameVectorSpace, TrainingProgressTracker trainingProgressTracker) {
		super(index, mentionVectorSpace, nameVectorSpace, cosineSimWeight, weights, trainingProgressTracker);
		cosineSimWeight2 = new double[1];
		this.weights2 = weights2;
		if (weights.numRows() != mentionVectorSpace.size()) {
			throw new IllegalArgumentException("Number of rows in matrix must match size of mention vector space");
		}
		if (weights.numColumns() != nameVectorSpace.size()) {
			throw new IllegalArgumentException("Number of columns in matrix must match size of name vector space");
		}
	}

	public NormalizationModelPredictor getTrainingPredictor() {
		return new NormalizationModel(index, mentionVectorSpace, nameVectorSpace, cosineSimWeight, weights, trainingProgress);
	}

	public NormalizationModelPredictor compile() {
		DenseBySparseMatrix<String, String> compiledWeights = new DenseBySparseMatrix<String, String>(mentionVectorSpace, nameVectorSpace);
		double factor = -1.0 / trainingProgress.getInstances();
		compiledWeights.increment(weights);
		compiledWeights.increment(factor, weights2);
		for (int mentionIndex = 0; mentionIndex < mentionIndexToNameIndex.length; mentionIndex++) {
			int nameIndex = mentionIndexToNameIndex[mentionIndex];
			if (nameIndex >= 0) {
				double weight = compiledWeights.get(mentionIndex, nameIndex) + cosineSimWeight[0] + factor * cosineSimWeight2[0];
				compiledWeights.set(mentionIndex, nameIndex, weight);
			}
		}
		// return new FasterCompiledNormalizationModel(index, mentionVectorSpace, nameVectorSpace, compiledWeights);
		return new CompiledNormalizationModel(index, mentionVectorSpace, nameVectorSpace, compiledWeights);
	}

	public void visualizeScore(Vector<String> mentionVector, Vector<String> nameVector) {
		Profiler.start("AveragedNormalizationModel.visualizeScore()");
		int instances = trainingProgress.getInstances();
		List<String> scoreLines = new ArrayList<String>();
		if (mentionVectorSpace != null && nameVectorSpace != null && mentionVector != null) {
			for (int i = 0; i < mentionVector.dimensions(); i++) {
				if (mentionVector.get(i) != 0.0) {
					String mentionElement = mentionVectorSpace.getElement(i);
					for (int j = 0; j < nameVector.dimensions(); j++) {
						if (nameVector.get(j) != 0.0) {
							String nameElement = nameVectorSpace.getElement(j);
							double weight1 = weights.get(i, j);
							double weight2 = weights2.get(i, j);
							double weight = weight1 - weight2 / instances;
							if (j == mentionIndexToNameIndex[i]) {
								weight += cosineSimWeight[0] - cosineSimWeight2[0] / instances;
							}
							if (weight != 0.0) {
								// scoreLines.add("\t\t\t" + mentionElement + "\t" + mentionVector.get(i) + "\t" + nameElement + "\t" + nameVector.get(j) + "\t" + weight);
								scoreLines.add("\t\t\t" + mentionElement + "\t" + mentionVector.get(i) + "\t" + nameElement + "\t" + nameVector.get(j) + "\t" + weight + "\t=\t" + weight1 + "\t-\t"
										+ weight2 + "\t/\t" + trainingProgress.getInstances());
							}
						}
					}
				}
			}
		}
		Collections.sort(scoreLines);
		for (String line : scoreLines) {
			System.out.println(line);
		}
		Profiler.stop("AveragedNormalizationModel.visualizeScore()");
	}

	@Override
	public double getScoreBound(Vector<String> mentionVector) {
		Profiler.start("AveragedNormalizationModel.getScoreBound()");
		if (mentionVector == null) {
			return 0.0;
		}

		// TODO PERFORMANCE Convert this to calculate the value directly (the problem is how to know if "v - v2 / i" is positive when all we have is v and v2

		double scoreBound = 0.0;
		Vector<String> nameVectorEquivalent = convertMentionVectorToNameVectorEquivalent(mentionVector);
		VectorIterator iterator = nameVectorEquivalent.getIterator();
		while (iterator.next()) {
			double value = iterator.getValue();
			if (value > 0.0) {
				scoreBound += value;
			}
		}

		Profiler.stop("AveragedNormalizationModel.getScoreBound()");
		return scoreBound;
	}

	@Override
	public double getWeight(int mentionIndex, int nameIndex) {
		int instances = trainingProgress.getInstances();
		double weight = weights.get(mentionIndex, nameIndex) - weights2.get(mentionIndex, nameIndex) / instances;
		if (nameIndex == mentionIndexToNameIndex[mentionIndex]) {
			weight += cosineSimWeight[0] - cosineSimWeight2[0] / instances;
		}
		return weight;
	}

	protected Vector<String> convertMentionVectorToNameVectorEquivalent(Vector<String> mentionVector) {
		Profiler.start("AveragedNormalizationModel.convertMentionVectorToNameVectorEquivalent()");
		SparseVector<String> nameVectorEquivalent = new SparseVector<String>(nameVectorSpace);
		// Converts a mention vector to a name vector
		int instances = trainingProgress.getInstances();
		VectorIterator mentionIterator = mentionVector.getIterator();
		while (mentionIterator.next()) {
			int mentionIndex = mentionIterator.getIndex();
			double mentionValue = mentionIterator.getValue();
			// Add value from cosine sim
			int nameIndex = mentionIndexToNameIndex[mentionIndex];
			if (nameIndex >= 0) {
				double weight = cosineSimWeight[0] - cosineSimWeight2[0] / instances;
				nameVectorEquivalent.increment(nameIndex, mentionValue * weight);
			}
			// Add values from weights matrix
			Vector<String> nameVector = weights.getRowVector(mentionIndex);
			if (nameVector != null) {
				nameVectorEquivalent.increment(mentionValue, nameVector);
			}
			nameVector = weights2.getRowVector(mentionIndex);
			if (nameVector != null) {
				nameVectorEquivalent.increment(-mentionValue / instances, nameVector);
			}
		}
		Profiler.stop("AveragedNormalizationModel.convertMentionVectorToNameVectorEquivalent()");
		return nameVectorEquivalent;
	}

	@Override
	protected double score(Vector<String> mentionVector, Vector<String> nameVector) {
		Profiler.start("AveragedNormalizationModel.score()");
		int instances = trainingProgress.getInstances();
		double score = 0.0;
		VectorIterator mentionIterator = mentionVector.getIterator();
		while (mentionIterator.next()) {
			int mentionIndex = mentionIterator.getIndex();
			double mentionValue = mentionIterator.getValue();
			VectorIterator nameIterator = nameVector.getIterator();
			while (nameIterator.next()) {
				int nameIndex = nameIterator.getIndex();
				double weight = weights.get(mentionIndex, nameIndex) - weights2.get(mentionIndex, nameIndex) / instances;
				if (nameIndex == mentionIndexToNameIndex[mentionIndex]) {
					weight += cosineSimWeight[0] - cosineSimWeight2[0] / instances;
				}
				if (weight != 0.0) {
					double nameValue = nameIterator.getValue();
					score += mentionValue * weight * nameValue;
				}
			}
		}
		Profiler.stop("AveragedNormalizationModel.score()");
		return score;
	}

	@Override
	public void update(double cosineSimWeight, Matrix<String, String> weightUpdates) {
		Profiler.start("AveragedNormalizationModel.update()");
		int instances = trainingProgress.getInstances();
		this.cosineSimWeight[0] += cosineSimWeight;
		this.cosineSimWeight2[0] += instances * cosineSimWeight;
		weights.increment(weightUpdates);
		weights2.increment(instances, weightUpdates);
		Profiler.stop("AveragedNormalizationModel.update()");
	}
}
