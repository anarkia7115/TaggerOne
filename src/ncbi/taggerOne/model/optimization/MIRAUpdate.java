package ncbi.taggerOne.model.optimization;

import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.lexicon.Index;
import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.model.normalization.NormalizationModelUpdater;
import ncbi.taggerOne.model.optimization.QuadraticProgram.QPConstraint;
import ncbi.taggerOne.model.recognition.RecognitionModelPredictor;
import ncbi.taggerOne.model.recognition.RecognitionModelUpdater;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.matrix.Matrix;
import ncbi.taggerOne.util.matrix.SparseMatrix;
import ncbi.taggerOne.util.vector.SparseVector;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.util.Profiler;

/*
 * Uses MIRA (the margin-infused relaxed algorithm) to update the recognition and normalization models. As an online optimization algorithm, it updates one example at a time. The basic idea is to determine the smallest update that will correctly classify the instance, with a margin equal to the loss, and then take a step in that direction. It is possible that the update will cause the scores for both the target sequence and predicted sequence to be less than zero. While this may seem odd, this can be the best way to set the scores for the instance, especially when the model has default / initialization parameters that are nonzero (such as cosine similarity for the normalization model).   
 */
public class MIRAUpdate implements OnlineOptimizer {

	private static final Logger logger = LoggerFactory.getLogger(MIRAUpdate.class);

	private Lexicon lexicon;
	private Map<String, int[]> mentionIndexToNameIndex;
	private Dictionary<String> recognitionFeatureSet;
	private RecognitionModelPredictor recognitionModelPredictor;
	private RecognitionModelUpdater recognitionModelUpdater;
	private Map<String, NormalizationModelPredictor> normalizationPredictionModels;
	private Map<String, NormalizationModelUpdater> normalizationUpdaterModels;

	private double regularization;
	private double maxStepSize;
	private int topNLabelings;
	private int topNNormalization;
	private boolean enforceNonNegativeDiagonal;
	private long solverTimeout;

	public MIRAUpdate(Lexicon lexicon, Dictionary<String> recognitionFeatureSet, RecognitionModelPredictor recognitionModelPredictor, RecognitionModelUpdater recognitionModelUpdater,
			Map<String, NormalizationModelPredictor> normalizationPredictionModels, Map<String, NormalizationModelUpdater> normalizationUpdaterModels, double regularization, double maxStepSize,
			long solverTimeout, int topNLabelings, int topNNormalization, boolean enforceNonNegativeDiagonal) {
		this.lexicon = lexicon;
		this.recognitionFeatureSet = recognitionFeatureSet;
		this.recognitionModelPredictor = recognitionModelPredictor;
		this.recognitionModelUpdater = recognitionModelUpdater;
		this.normalizationPredictionModels = normalizationPredictionModels;
		this.normalizationUpdaterModels = normalizationUpdaterModels;
		this.regularization = regularization;
		this.solverTimeout = solverTimeout;
		this.maxStepSize = maxStepSize;
		this.topNLabelings = topNLabelings;
		this.topNNormalization = topNNormalization;
		this.enforceNonNegativeDiagonal = enforceNonNegativeDiagonal;
		initMentionNameToNameIndex();
	}

	public void initMentionNameToNameIndex() {
		// TODO Move this function to Index
		mentionIndexToNameIndex = new HashMap<String, int[]>();
		for (String entityType : normalizationPredictionModels.keySet()) {
			Index index = lexicon.getIndex(entityType);
			Dictionary<String> mentionVectorSpace = index.getMentionVectorSpace();
			Dictionary<String> nameVectorSpace = index.getNameVectorSpace();
			int[] mentionIndexToNameIndexArray = new int[mentionVectorSpace.size()];
			mentionIndexToNameIndex.put(entityType, mentionIndexToNameIndexArray);
			Arrays.fill(mentionIndexToNameIndexArray, -1);
			Set<String> vectorSpaceElements = new HashSet<String>();
			vectorSpaceElements.addAll(mentionVectorSpace.getElements());
			vectorSpaceElements.addAll(nameVectorSpace.getElements());
			for (String element : vectorSpaceElements) {
				int mentionIndex = mentionVectorSpace.getIndex(element);
				int nameIndex = nameVectorSpace.getIndex(element);
				if (mentionIndex >= 0 && nameIndex >= 0) {
					mentionIndexToNameIndexArray[mentionIndex] = nameIndex;
				}
			}
		}
	}

	@Override
	public void update(List<AnnotatedSegment> targetStateSequence, List<AnnotatedSegment> predictedStateSequence) {
		Profiler.start("MIRAUpdate.update()");
		Profiler.start("MIRAUpdate.update()@setup");

		logger.info("Updating sequence");
		logger.info("\ttarget= " + AnnotatedSegment.visualizeStates(targetStateSequence));
		logger.info("\tprediction= " + AnnotatedSegment.visualizeStates(predictedStateSequence));

		QuadraticProgram qp = new QuadraticProgram(lexicon, recognitionFeatureSet, recognitionModelPredictor.getEntityClassStates(), mentionIndexToNameIndex, normalizationPredictionModels.keySet(),
				regularization, maxStepSize, solverTimeout);

		// Prepare base constraints
		QPConstraint baseConstraint = new QPConstraint(qp);
		baseConstraint.addPath(targetStateSequence, true);
		baseConstraint.addPath(predictedStateSequence, false);

		double targetScore = scoreStateSequenceNER(targetStateSequence);
		double predictedScore = scoreStateSequenceNER(predictedStateSequence);
		double loss = targetScore - predictedScore; // Use a margin equal to the loss
		logger.info("\tNER loss = " + loss);
		baseConstraint.addBValue(2.0 * loss);

		List<QPConstraint> constraints = new ArrayList<QPConstraint>();
		constraints.add(baseConstraint);

		logger.info("Base:");
		for (int i = 0; i < constraints.size(); i++) {
			logger.info("\t" + i + ": loss = " + constraints.get(i).getB());
		}

		// Add normalization constraint terms from NER FNs
		addNERFNNormalizationTerms(targetStateSequence, predictedStateSequence, constraints);

		logger.info("After adding FNs:");
		for (int i = 0; i < constraints.size(); i++) {
			logger.info("\t" + i + ": loss = " + constraints.get(i).getB());
		}

		// Add normalization constraint terms from NER FPs
		addNERFPNormalizationTerms(targetStateSequence, predictedStateSequence, constraints);
		logger.info("After adding FPs:");
		for (int i = 0; i < constraints.size(); i++) {
			logger.info("\t" + i + ": loss = " + constraints.get(i).getB());
		}
		// Add normalization constraint terms from NER is correct but normalization is not
		addIncorrectNormalizationTerms(targetStateSequence, predictedStateSequence, constraints);
		logger.info("After adding incorrect normalizations:");
		for (int i = 0; i < constraints.size(); i++) {
			logger.info("\t" + i + ": loss = " + constraints.get(i).getB());
		}
		// Add normalization only constraints
		if (topNNormalization > 0) {
			addNormalizationOnlyConstraints(targetStateSequence, qp, constraints);
			logger.info("After adding normalization only:");
			for (int i = 0; i < constraints.size(); i++) {
				logger.info("\t" + i + ": loss = " + constraints.get(i).getB());
			}
		}

		// Add constraints to the program
		for (QPConstraint constraint : constraints) {
			if (regularization != 0.0) {
				constraint.addSlackVariable();
			}
			qp.addConstraint(constraint);
		}

		// Minimize the program
		qp = qp.getMinimalQP();
		logger.info("\tNumber of constraints: " + qp.getConstraintCount());
		logger.info("\tNumber of variables: " + qp.getVariableCount());

		// Add non-negative diagonal constraints
		if (enforceNonNegativeDiagonal) {
			qp.addNonNegativeCosineSimNormalizationConstraints(normalizationUpdaterModels);
		}

		Profiler.stop("MIRAUpdate.update()@setup");

		if (qp.getConstraintCount() == 0) {
			logger.warn("QP has 0 constraints");
			Profiler.stop("MIRAUpdate.update()");
			return;
		}
		if (qp.getVariableCount() == 0) {
			logger.warn("QP has 0 variables");
			Profiler.stop("MIRAUpdate.update()");
			return;
		}

		// Prepare delta variables
		Profiler.start("MIRAUpdate.update()@delta");
		Dictionary<String> entityClassStates = recognitionModelPredictor.getEntityClassStates();
		@SuppressWarnings("unchecked")
		Vector<String>[] featureWeightUpdates = new SparseVector[entityClassStates.size()];
		Dictionary<String> featureSet = recognitionModelUpdater.getFeatureSet();
		for (int state = 0; state < entityClassStates.size(); state++) {
			featureWeightUpdates[state] = new SparseVector<String>(featureSet);
		}
		TObjectDoubleMap<String> cosineSimUpdates = new TObjectDoubleHashMap<String>();
		Map<String, Matrix<String, String>> normalizationTypeToWeightUpdates = new HashMap<String, Matrix<String, String>>();
		for (String entityType : normalizationUpdaterModels.keySet()) {
			Index index = lexicon.getIndex(entityType);
			Matrix<String, String> normalizationWeightUpdate = new SparseMatrix<String, String>(index.getMentionVectorSpace(), index.getNameVectorSpace());
			normalizationTypeToWeightUpdates.put(entityType, normalizationWeightUpdate);
		}
		Profiler.stop("MIRAUpdate.update()@delta");

		// Solve and apply
		Profiler.start("MIRAUpdate.update()@solve");
		boolean success = qp.solve(featureWeightUpdates, cosineSimUpdates, normalizationTypeToWeightUpdates);
		Profiler.stop("MIRAUpdate.update()@solve");

		if (!success) {
			logger.warn("QP solution failed");
			Profiler.stop("MIRAUpdate.update()");
			return;
		}

		Profiler.start("MIRAUpdate.update()@apply");
		recognitionModelUpdater.update(featureWeightUpdates);
		for (String entityType : normalizationUpdaterModels.keySet()) {
			NormalizationModelUpdater normalizationModelUpdater = normalizationUpdaterModels.get(entityType);
			double cosineSimUpdate = cosineSimUpdates.get(entityType);
			Matrix<String, String> normalizationWeightUpdate = normalizationTypeToWeightUpdates.get(entityType);
			normalizationModelUpdater.update(cosineSimUpdate, normalizationWeightUpdate);
		}
		Profiler.stop("MIRAUpdate.update()@apply");
		Profiler.stop("MIRAUpdate.update()");
	}

	private double scoreStateSequenceNER(List<AnnotatedSegment> stateSequence) {
		Profiler.start("MIRAUpdate.scoreStateSequenceNER()");
		double score = 0.0;
		for (AnnotatedSegment nextSegment : stateSequence) {
			score += recognitionModelPredictor.predict(nextSegment.getEntityClass(), nextSegment);
		}
		Profiler.stop("MIRAUpdate.scoreStateSequenceNER()");
		return score;
	}

	private void addNERFNNormalizationTerms(List<AnnotatedSegment> targetStateSequence, List<AnnotatedSegment> predictedStateSequence, List<QPConstraint> baseConstraints) {
		List<QPConstraint> constraints = new ArrayList<QPConstraint>();
		constraints.addAll(baseConstraints);
		for (AnnotatedSegment annotatedSegment : targetStateSequence) {
			String entityType = annotatedSegment.getEntityClass();
			if (!entityType.equals(T1Constants.NONENTITY_STATE)) {
				Vector<String> mentionVector = annotatedSegment.getMentionName().getVector();
				if (mentionVector != null) {
					// This segment should be predicted to be normalized
					AnnotatedSegment predictedSegment = findNER(annotatedSegment, predictedStateSequence);
					if (predictedSegment == null) {
						NormalizationModelPredictor normalizationModelPredictor = normalizationPredictionModels.get(entityType);
						List<QPConstraint> newConstraints = new ArrayList<QPConstraint>();
						for (Entity entity : annotatedSegment.getEntities()) {
							Vector<String> correctNameVector = normalizationModelPredictor.findBestName(mentionVector, entity).getVector();
							double loss = normalizationModelPredictor.scoreNameVector(mentionVector, correctNameVector);
							logger.info("\tAdding NER FN normalization: mention = " + mentionVector.visualize() + ", type = " + entityType + ", name = " + correctNameVector.visualize() + ", loss = "
									+ loss);
							for (QPConstraint constraint2 : constraints) {
								QPConstraint constraintCopy = constraint2.copy();
								constraintCopy.addNormalization(entityType, mentionVector, correctNameVector, true);
								constraintCopy.addBValue(2.0 * loss);
								newConstraints.add(constraintCopy);
							}
						}
						constraints = newConstraints;
					}
				}
			}
		}
		baseConstraints.clear();
		baseConstraints.addAll(constraints);
	}

	private void addNERFPNormalizationTerms(List<AnnotatedSegment> targetStateSequence, List<AnnotatedSegment> predictedStateSequence, List<QPConstraint> baseConstraints) {
		List<QPConstraint> constraints = new ArrayList<QPConstraint>();
		constraints.addAll(baseConstraints);
		for (AnnotatedSegment predictedSegment : predictedStateSequence) {
			String entityType = predictedSegment.getEntityClass();
			if (!entityType.equals(T1Constants.NONENTITY_STATE)) {
				Vector<String> mentionVector = predictedSegment.getMentionName().getVector();
				if (mentionVector != null) {
					// This segment was predicted as normalized
					AnnotatedSegment annotatedSegment = findNER(predictedSegment, targetStateSequence);
					if (annotatedSegment == null) {
						// Predicted segment is an FP, all names it finds are incorrect
						NormalizationModelPredictor normalizationModelPredictor = normalizationPredictionModels.get(entityType);
						RankedList<Entity> bestEntities = new RankedList<Entity>(topNLabelings);
						normalizationModelPredictor.findBest(mentionVector, bestEntities);
						List<QPConstraint> newConstraints = new ArrayList<QPConstraint>();
						for (int rank = 0; rank < bestEntities.size(); rank++) {
							Entity entity = bestEntities.getObject(rank);
							Vector<String> nameVector = normalizationModelPredictor.findBestName(mentionVector, entity).getVector();
							double loss = -1.0 * normalizationModelPredictor.scoreNameVector(mentionVector, nameVector);
							for (QPConstraint constraint2 : constraints) {
								QPConstraint constraintCopy = constraint2.copy();
								logger.info("\tAdding NER FP normalization: mention = " + mentionVector.visualize() + ", type = " + entityType + ", name = " + nameVector.visualize() + ", loss = "
										+ loss);
								constraintCopy.addNormalization(entityType, mentionVector, nameVector, false);
								constraintCopy.addBValue(2.0 * loss);
								newConstraints.add(constraintCopy);
							}
						}
						constraints = newConstraints;
					}
				}
			}
		}
		baseConstraints.clear();
		baseConstraints.addAll(constraints);
	}

	private void addIncorrectNormalizationTerms(List<AnnotatedSegment> targetStateSequence, List<AnnotatedSegment> predictedStateSequence, List<QPConstraint> baseConstraints) {
		List<QPConstraint> constraints = new ArrayList<QPConstraint>();
		constraints.addAll(baseConstraints);
		for (AnnotatedSegment annotatedSegment : targetStateSequence) {
			String entityType = annotatedSegment.getEntityClass();
			if (!entityType.equals(T1Constants.NONENTITY_STATE)) {
				Vector<String> mentionVector = annotatedSegment.getMentionName().getVector();
				if (mentionVector != null) {
					// This segment should be predicted to be normalized
					AnnotatedSegment predictedSegment = findNER(annotatedSegment, predictedStateSequence);
					if (predictedSegment != null) {
						// Found correct boundaries and correct type
						NormalizationModelPredictor normalizationModelPredictor = normalizationPredictionModels.get(entityType);
						Set<Entity> correctEntities = annotatedSegment.getEntities();
						// Get top incorrect name vectors
						int normalizationCount = topNLabelings;
						for (Entity entity : correctEntities) {
							normalizationCount += entity.getNames().size();
						}
						RankedList<Entity> bestEntities = new RankedList<Entity>(normalizationCount);
						normalizationModelPredictor.findBest(mentionVector, bestEntities);
						RankedList<Entity> bestEntities2 = new RankedList<Entity>(normalizationCount);
						for (int rank = 0; rank < bestEntities.size(); rank++) {
							Entity entity = bestEntities.getObject(rank);
							if (!correctEntities.contains(entity)) {
								bestEntities2.add(bestEntities.getValue(rank), entity);
							}
						}
						// Consider each correct entity
						List<QPConstraint> newConstraints = new ArrayList<QPConstraint>();
						for (Entity entity : correctEntities) {
							// Find highest-scoring name for this entity
							Vector<String> correctNameVector = null;
							double correctNameVectorScore = Double.NEGATIVE_INFINITY;
							for (MentionName name : entity.getNames()) {
								Vector<String> nameVector = name.getVector();
								double score = normalizationModelPredictor.scoreNameVector(mentionVector, nameVector);
								// TODO Consider adding margin
								if (score > correctNameVectorScore) {
									correctNameVectorScore = score;
									correctNameVector = nameVector;
								}
							}
							// Add a FN / FP pair for each incorrect name with a higher score than the correct name, up to topNLabelings
							if (correctNameVector != null) {
								int added = 0;
								for (int rank = 0; rank < bestEntities2.size() && added < topNLabelings; rank++) {
									double score = bestEntities2.getValue(rank);
									if (score > correctNameVectorScore) {
										Entity incorrectEntity = bestEntities2.getObject(rank);
										Vector<String> incorrectNameVector = normalizationModelPredictor.findBestName(mentionVector, incorrectEntity).getVector();
										double loss = correctNameVectorScore - normalizationModelPredictor.scoreNameVector(mentionVector, incorrectNameVector);
										logger.info("\tAdding incorrect normalization: mention = " + mentionVector.visualize() + ", type = " + ", correct name = " + correctNameVector.visualize()
												+ ", incorrect name = " + incorrectNameVector.visualize() + ", loss = " + loss);
										for (QPConstraint constraint2 : constraints) {
											QPConstraint constraintCopy = constraint2.copy();
											constraintCopy.addNormalization(entityType, mentionVector, correctNameVector, true);
											constraintCopy.addNormalization(entityType, mentionVector, incorrectNameVector, false);
											constraintCopy.addBValue(2.0 * loss);
											newConstraints.add(constraintCopy);
										}
										added++;
									}
								}
							}
						}
						// If newConstraints is not empty, use it instead
						if (!newConstraints.isEmpty()) {
							constraints = newConstraints;
						}
					}
				}
			}
		}
		baseConstraints.clear();
		baseConstraints.addAll(constraints);
	}

	private void addNormalizationOnlyConstraints(List<AnnotatedSegment> targetStateSequence, QuadraticProgram qp, List<QPConstraint> constraints) {
		TObjectDoubleMap<NormalizationConstraint> normalizationConstraints = new TObjectDoubleHashMap<NormalizationConstraint>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0);

		// Find normalizable segments in the target state sequence
		for (AnnotatedSegment segment : targetStateSequence) {
			String entityType = segment.getEntityClass();
			if (!entityType.equals(T1Constants.NONENTITY_STATE)) {
				Vector<String> mentionVector = segment.getMentionName().getVector();
				if (mentionVector != null) {
					// Prepare correct and incorrect normalization
					NormalizationModelPredictor normalizationModelPredictor = normalizationPredictionModels.get(entityType);
					int numNames = 0;
					for (Entity entity : segment.getEntities()) {
						numNames += entity.getNames().size();
					}
					for (Entity entity : segment.getEntities()) {
						Vector<String> correctNameVector = normalizationModelPredictor.findBestName(mentionVector, entity).getVector();
						double correctNameVectorScore = normalizationModelPredictor.scoreNameVector(mentionVector, correctNameVector);
						// We don't just use topNNormalization because the other names for the same concept may also appear
						int maxBestNameVectors = topNNormalization + numNames;
						RankedList<Entity> bestEntities = new RankedList<Entity>(maxBestNameVectors);
						normalizationModelPredictor.findBest(mentionVector, bestEntities);
						int added = 0;
						for (int rank = 0; rank < bestEntities.size() && added < topNNormalization; rank++) {
							// Verify the potentially wrong name has a higher score than the correct name
							// TODO Consider adding margin
							if (bestEntities.getValue(rank) >= correctNameVectorScore) {
								Entity entityFound = bestEntities.getObject(rank);
								Vector<String> nameVector = normalizationModelPredictor.findBestName(mentionVector, entityFound).getVector();
								// Check that the name is not a synonym for any of the correct entities
								if (!segment.getEntities().contains(entityFound)) {
									// mentionVectors that appear twice appear as only one constraint but with double the loss
									logger.info("\tAdding normalization only constraint: mention = " + mentionVector.visualize() + ", type = " + ", correct name = " + correctNameVector.visualize()
											+ ", incorrect name = " + nameVector.visualize());
									NormalizationConstraint normalizationOnlyConstraint = new NormalizationConstraint(entityType, mentionVector, correctNameVector, nameVector);
									double loss = correctNameVectorScore - normalizationModelPredictor.scoreNameVector(mentionVector, nameVector);
									normalizationConstraints.adjustOrPutValue(normalizationOnlyConstraint, loss, loss);
									added++;
								}
							}
						}
					}
				}
			}
		}

		// Add normalization only constraints
		for (NormalizationConstraint constraint : normalizationConstraints.keySet()) {
			QPConstraint qpConstraint = constraint.getQPConstraint(qp);
			double loss = normalizationConstraints.get(constraint);
			qpConstraint.addBValue(2.0 * loss);
			constraints.add(qpConstraint);
		}
	}

	private static class NormalizationConstraint {
		private String entityType;
		private Vector<String> mentionVector;
		private Vector<String> correctNameVector;
		private Vector<String> incorrectNameVector;

		public NormalizationConstraint(String entityType, Vector<String> mentionVector, Vector<String> correctNameVector, Vector<String> incorrectNameVector) {
			this.entityType = entityType;
			this.mentionVector = mentionVector;
			this.correctNameVector = correctNameVector;
			this.incorrectNameVector = incorrectNameVector;
		}

		public QPConstraint getQPConstraint(QuadraticProgram qp) {
			QPConstraint normalizationOnlyConstraint = new QPConstraint(qp);
			normalizationOnlyConstraint.addNormalization(entityType, mentionVector, correctNameVector, true);
			normalizationOnlyConstraint.addNormalization(entityType, mentionVector, incorrectNameVector, false);
			return normalizationOnlyConstraint;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((correctNameVector == null) ? 0 : correctNameVector.hashCode());
			result = prime * result + ((entityType == null) ? 0 : entityType.hashCode());
			result = prime * result + ((incorrectNameVector == null) ? 0 : incorrectNameVector.hashCode());
			result = prime * result + ((mentionVector == null) ? 0 : mentionVector.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			NormalizationConstraint other = (NormalizationConstraint) obj;
			if (correctNameVector == null) {
				if (other.correctNameVector != null)
					return false;
			} else if (!correctNameVector.equals(other.correctNameVector))
				return false;
			if (entityType == null) {
				if (other.entityType != null)
					return false;
			} else if (!entityType.equals(other.entityType))
				return false;
			if (incorrectNameVector == null) {
				if (other.incorrectNameVector != null)
					return false;
			} else if (!incorrectNameVector.equals(other.incorrectNameVector))
				return false;
			if (mentionVector == null) {
				if (other.mentionVector != null)
					return false;
			} else if (!mentionVector.equals(other.mentionVector))
				return false;
			return true;
		}

	}

	private static AnnotatedSegment findNER(AnnotatedSegment segment, List<AnnotatedSegment> annotations) {
		for (AnnotatedSegment s : annotations) {
			if (s.getStartChar() == segment.getStartChar() && s.getEndChar() == segment.getEndChar() && s.getEntityClass().equals(segment.getEntityClass())) {
				return s;
			}
		}
		return null;
	}

}
