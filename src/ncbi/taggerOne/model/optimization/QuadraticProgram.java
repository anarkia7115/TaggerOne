package ncbi.taggerOne.model.optimization;

import gnu.trove.impl.Constants;
import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.lexicon.Index;
import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.model.normalization.NormalizationModelUpdater;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.matrix.Matrix;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;
import ncbi.util.Profiler;

import org.ojalgo.matrix.store.PrimitiveDenseStore;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.optimisation.convex.ConvexSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuadraticProgram {

	private static final Logger logger = LoggerFactory.getLogger(QuadraticProgram.class);

	// TODO PERFORMANCE Every use of Map.keys() should take better advantage of sparsity
	// TODO PERFORMANCE Consider implementing an int matrix type to replace uses of TLongIntMap

	private Lexicon lexicon;
	private Dictionary<String> recognitionFeatureSet;
	private Dictionary<String> entityClassStates;
	private Map<String, int[]> mentionIndexToNameIndex;
	private TLongIntMap recognitionVars;
	private TObjectIntMap<String> cosineSimVars;
	private Map<String, TLongIntMap> normalizationTypeToVars;
	private TIntIntMap slackVars;
	private int varCount;
	private List<QPConstraint> constraints;
	private double regularization;
	private double maxStepSize;
	private long solverTimeout;

	public QuadraticProgram(Lexicon lexicon, Dictionary<String> recognitionFeatureSet, Dictionary<String> entityClassStates, Map<String, int[]> mentionIndexToNameIndex, Set<String> entityTypes,
			double regularization, double maxStepSize, long solverTimeout) {
		this.lexicon = lexicon;
		this.recognitionFeatureSet = recognitionFeatureSet;
		this.entityClassStates = entityClassStates;
		this.mentionIndexToNameIndex = mentionIndexToNameIndex;
		this.recognitionVars = new TLongIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Constants.DEFAULT_LONG_NO_ENTRY_VALUE, -1);
		this.cosineSimVars = new TObjectIntHashMap<String>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
		this.normalizationTypeToVars = new HashMap<String, TLongIntMap>();
		for (String entityType : entityTypes) {
			normalizationTypeToVars.put(entityType, new TLongIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Constants.DEFAULT_LONG_NO_ENTRY_VALUE, -1));
		}
		this.slackVars = new TIntIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Constants.DEFAULT_INT_NO_ENTRY_VALUE, -1);
		this.varCount = 0;
		this.constraints = new ArrayList<QPConstraint>();
		if (regularization < 0.0 || !Double.isFinite(regularization)) {
			throw new IllegalArgumentException("Regularization must be nonnegative and finite: " + regularization);
		}
		this.regularization = regularization;
		if (maxStepSize < 0.0 || !Double.isFinite(maxStepSize)) {
			throw new IllegalArgumentException("Max step size must be nonnegative and finite: " + maxStepSize);
		}
		this.maxStepSize = maxStepSize;
		this.solverTimeout = solverTimeout;
	}

	public int getConstraintCount() {
		return constraints.size();
	}

	public int getVariableCount() {
		return varCount;
	}

	public Dictionary<String> getEntityClassStates() {
		return entityClassStates;
	}

	public int[] getMentionIndexToNameIndex(String entityType) {
		return mentionIndexToNameIndex.get(entityType);
	}

	public List<QPConstraint> getConstraints() {
		return constraints;
	}

	public int getRecognitionVar(int state, int index) {
		long joint = getJointIndex(state, index);
		int var = recognitionVars.get(joint);
		if (var < 0) {
			var = varCount;
			recognitionVars.put(joint, var);
			varCount++;
		}
		return var;
	}

	public int getCosineSimVar(String entityType) {
		int var = cosineSimVars.get(entityType);
		if (var < 0) {
			var = varCount;
			cosineSimVars.put(entityType, var);
			varCount++;
		}
		return var;
	}

	public int getNormalizationVar(String entityType, int rowIndex, int colIndex) {
		TLongIntMap normalizationVars = normalizationTypeToVars.get(entityType);
		long joint = getJointIndex(rowIndex, colIndex);
		int var = normalizationVars.get(joint);
		if (var < 0) {
			var = varCount;
			normalizationVars.put(joint, var);
			varCount++;
		}
		return var;
	}

	public int getSlackVar(int index) {
		if (regularization == 0.0) {
			throw new IllegalStateException("Slack variables only available if regularization > 0.0");
		}
		int var = slackVars.get(index);
		if (var < 0) {
			var = varCount;
			slackVars.put(index, var);
			varCount++;
		}
		return var;
	}

	public int getSlackVar() {
		if (regularization == 0.0) {
			throw new IllegalStateException("Slack variables only available if regularization > 0.0");
		}
		int index = slackVars.size();
		int var = varCount;
		slackVars.put(index, var);
		varCount++;
		return var;
	}

	private boolean isSlackVar(int variableIndex) {
		int[] slackVarKeys = slackVars.keys();
		for (int i = 0; i < slackVarKeys.length; i++) {
			int index = slackVarKeys[i];
			int var = slackVars.get(index);
			if (variableIndex == var) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("static-method")
	private long getJointIndex(int row, int column) {
		// The column mask is not needed as long as column is non-negative
		// return (((long) row) << 32) | (column & 0xffffffffL);
		return (((long) row) << 32) | column;
	}

	@SuppressWarnings("static-method")
	private int getRow(long jointIndex) {
		return (int) (jointIndex >> 32);
	}

	@SuppressWarnings("static-method")
	private int getColumn(long jointIndex) {
		return (int) jointIndex;
	}

	public void addConstraint(QPConstraint constraint) {
		constraints.add(constraint);
	}

	public QuadraticProgram getMinimalQP() {
		Profiler.start("QP.getMinimalQP()");
		boolean[] hasNonZeroCoefficient = new boolean[varCount];
		for (QPConstraint constraint : constraints) {
			if (!constraint.markNonZeroCoefficients(hasNonZeroCoefficient)) {
				logger.warn("constraint has no non-zero coefficients");
			}
		}
		QuadraticProgram minimalQP = convertQP(hasNonZeroCoefficient);
		Profiler.stop("QP.getMinimalQP()");
		return minimalQP;
	}

	public QuadraticProgram convertQP(boolean[] retainVariables) {
		QuadraticProgram convertedQP = new QuadraticProgram(lexicon, recognitionFeatureSet, entityClassStates, mentionIndexToNameIndex, normalizationTypeToVars.keySet(), regularization, maxStepSize,
				solverTimeout);

		// Create mapping from old variable indices to new
		int[] mapping = new int[varCount];
		for (int j = 0; j < varCount; j++) {
			mapping[j] = -1;
			if (retainVariables[j]) {
				mapping[j] = convertedQP.varCount;
				convertedQP.varCount++;
			}
		}
		logger.info("\tMinimal QP has " + convertedQP.varCount + " variables out of " + varCount);

		// Convert recognition vars
		long[] keys = recognitionVars.keys();
		for (int i = 0; i < keys.length; i++) {
			long joint = keys[i];
			int var = recognitionVars.get(joint);
			int newVar = mapping[var];
			if (newVar != -1) {
				convertedQP.recognitionVars.put(joint, newVar);
			}
		}

		// Convert cosine sim vars
		for (String entityType : cosineSimVars.keySet()) {
			int var = cosineSimVars.get(entityType);
			int newVar = mapping[var];
			if (newVar != -1) {
				convertedQP.cosineSimVars.put(entityType, newVar);
			}
		}

		// Convert normalization vars
		for (String entityType : normalizationTypeToVars.keySet()) {
			TLongIntMap normalizationVars = normalizationTypeToVars.get(entityType);
			TLongIntMap newNormalizationVars = convertedQP.normalizationTypeToVars.get(entityType);
			keys = normalizationVars.keys();
			for (int i = 0; i < keys.length; i++) {
				long joint = keys[i];
				int var = normalizationVars.get(joint);
				int newVar = mapping[var];
				if (newVar != -1) {
					newNormalizationVars.put(joint, newVar);
				}
			}
		}

		// Convert slack vars
		int[] slackVarKeys = slackVars.keys();
		for (int i = 0; i < slackVarKeys.length; i++) {
			int index = slackVarKeys[i];
			int var = slackVars.get(index);
			int newVar = mapping[var];
			if (newVar != -1) {
				convertedQP.slackVars.put(index, newVar);
			}
		}

		// Convert constraints
		for (QPConstraint constraint : constraints) {
			QPConstraint newConstraint = constraint.mapConstraint(convertedQP, mapping);
			convertedQP.constraints.add(newConstraint);
		}
		return convertedQP;
	}

	public boolean solve(Vector<String>[] featureWeightUpdates, TObjectDoubleMap<String> cosineSimUpdates, Map<String, Matrix<String, String>> normalizationTypeToWeightUpdates) {
		Profiler.start("QP.solve()");

		// TODO PERFORMANCE Can we create the stores directly?

		if (logger.isTraceEnabled()) {
			logger.trace("QP variable descriptions:");
			String[] varDesc = getVariableDescriptions();
			for (int i = 0; i < varDesc.length; i++) {
				logger.trace(i + "\t" + varDesc[i]);
			}
		}

		Profiler.start("QP.solve()@setup");

		// Constrain any slack variables to be non-negative
		int[] slackVarKeys = slackVars.keys();
		for (int i = 0; i < slackVarKeys.length; i++) {
			int index = slackVarKeys[i];
			int var = slackVars.get(index);
			QPConstraint diagonal = new QPConstraint(this);
			diagonal.addRaw(var, -1.0);
			addConstraint(diagonal);
		}

		// Get A and B
		int constraintCount = constraints.size();
		double[][] aiArray = new double[constraintCount][varCount];
		double[][] biArray = new double[constraintCount][1];
		for (int i = 0; i < constraintCount; i++) {
			QPConstraint constraint = constraints.get(i);
			constraint.copyCoefficients(aiArray[i]);
			biArray[i][0] = constraint.getB();
		}
		if (logger.isTraceEnabled()) {
			logger.trace("\tConstraints, in form Ax <= b:");
			for (int i = 0; i < constraintCount; i++) {
				logger.trace("\t" + Arrays.toString(aiArray[i]) + " <= " + biArray[i][0]);
			}
		}

		// Create Q, C and data structures
		double[][] qArray = new double[varCount][varCount];
		double[][] cArray = new double[1][varCount];
		for (int index = 0; index < varCount; index++) {
			qArray[index][index] = 2.0;
			cArray[0][index] = 0.0;
		}
		// Set the slack variable values
		for (int i = 0; i < slackVarKeys.length; i++) {
			int index = slackVarKeys[i];
			int var = slackVars.get(index);
			qArray[var][var] = 0.0;
			cArray[0][var] = -regularization;
		}
		final PrimitiveDenseStore qStore = PrimitiveDenseStore.FACTORY.rows(qArray);
		final PrimitiveDenseStore cStore = PrimitiveDenseStore.FACTORY.rows(cArray);
		final PrimitiveDenseStore aiStore = PrimitiveDenseStore.FACTORY.rows(aiArray);
		final PrimitiveDenseStore biStore = PrimitiveDenseStore.FACTORY.rows(biArray);
		Profiler.stop("QP.solve()@setup");

		// Solve
		Profiler.start("QP.solve()@solve");
		SolverThread solverThread = new SolverThread(qStore, cStore, aiStore, biStore);
		solverThread.start();
		try {
			solverThread.join(solverTimeout);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Optimisation.Result result = solverThread.getResult();
		if (result == null) {
			solverThread.interrupt();
		}
		solverThread = null;
		Profiler.stop("QP.solve()@solve");

		// Profiler.start("QP.solve()@solve");
		// ConvexSolver solver = new ConvexSolver.Builder(qStore, cStore.transpose()).inequalities(aiStore, biStore).build();
		// logger.info("\tSolving QP");
		// long elapsed = -System.currentTimeMillis();
		// Optimisation.Result result = solver.solve();
		// elapsed += System.currentTimeMillis();
		// logger.info("\tQP solution took " + elapsed + " ms");
		// Profiler.stop("QP.solve()@solve");

		// Check for failures
		if (result == null) {
			logger.warn("QP solution exceeded timeout");
			logger.warn("QP variable descriptions:");
			String[] varDesc = getVariableDescriptions();
			for (int i = 0; i < varDesc.length; i++) {
				logger.warn(i + "\t" + varDesc[i]);
			}
			logger.warn("\tConstraints, in form Ax <= b:");
			for (int i = 0; i < constraintCount; i++) {
				logger.warn("\t" + Arrays.toString(aiArray[i]) + " <= " + biArray[i][0]);
			}
			Profiler.stop("QP.solve()");
			return false;
		} else if (result.getState().isFailure()) {
			logger.warn("QP solution failed; state = " + result.getState().toString());
			logger.warn("QP variable descriptions:");
			String[] varDesc = getVariableDescriptions();
			for (int i = 0; i < varDesc.length; i++) {
				logger.warn(i + "\t" + varDesc[i]);
			}
			logger.warn("\tConstraints, in form Ax <= b:");
			for (int i = 0; i < constraintCount; i++) {
				logger.warn("\t" + Arrays.toString(aiArray[i]) + " <= " + biArray[i][0]);
			}
			Profiler.stop("QP.solve()");
			return false;
		}

		if (logger.isTraceEnabled()) {
			logger.trace("QP solution:");
			String[] varDesc = getVariableDescriptions();
			for (int i = 0; i < varDesc.length; i++) {
				logger.trace(i + "\t" + result.doubleValue(i) + "\t" + varDesc[i]);
			}
		}

		Profiler.start("QP.solve()@finalization");
		logger.info("\tSolution state = " + result.getState().toString());

		// Determine scaling
		double sqrlen = 0.0;
		for (int i = 0; i < varCount; i++) {
			if (!isSlackVar(i)) {
				sqrlen += result.doubleValue(i) * result.doubleValue(i);
			}
		}
		double length = Math.sqrt(sqrlen);
		logger.info("\tSolution length = " + length);
		double stepMultiplier = 1.0;
		if (maxStepSize != 0.0 && length > maxStepSize) {
			stepMultiplier = maxStepSize / length;
		}
		logger.info("\tStep multiplier = " + stepMultiplier);

		// Copy solution
		double sqrSum = 0.0;
		sqrSum += copyRecognitionResult(result, stepMultiplier, featureWeightUpdates);
		sqrSum += copyCosineSimResult(result, stepMultiplier, cosineSimUpdates);
		sqrSum += copyNormalizationResult(result, stepMultiplier, normalizationTypeToWeightUpdates);
		logger.info("\tSize of full update = " + Math.sqrt(sqrSum));
		checkSlackResult(result);

		Profiler.stop("QP.solve()@finalization");
		Profiler.stop("QP.solve()");
		return true;
	}

	private static class SolverThread extends Thread {

		private static final Logger logger = LoggerFactory.getLogger(SolverThread.class);

		private PrimitiveDenseStore qStore;
		private PrimitiveDenseStore cStore;
		private PrimitiveDenseStore aiStore;
		private PrimitiveDenseStore biStore;

		private Optimisation.Result result;

		public SolverThread(PrimitiveDenseStore qStore, PrimitiveDenseStore cStore, PrimitiveDenseStore aiStore, PrimitiveDenseStore biStore) {
			this.qStore = qStore;
			this.cStore = cStore;
			this.aiStore = aiStore;
			this.biStore = biStore;
			this.result = null;
			setDaemon(true);
		}

		public Optimisation.Result getResult() {
			return result;
		}

		@Override
		public void run() {
			logger.info("\tSolving QP");
			ConvexSolver solver = new ConvexSolver.Builder(qStore, cStore.transpose()).inequalities(aiStore, biStore).build();
			long elapsed = -System.currentTimeMillis();
			result = solver.solve();
			elapsed += System.currentTimeMillis();
			logger.info("\tQP solution took " + elapsed + " ms");
		}
	}

	private String[] getVariableDescriptions() {
		String[] varDesc = new String[varCount];

		// Get recognition variable descriptions
		long[] keys = recognitionVars.keys();
		for (int i = 0; i < keys.length; i++) {
			long joint = keys[i];
			int state = getRow(joint);
			int index = getColumn(joint);
			int var = recognitionVars.get(joint);
			String stateText = entityClassStates.getElement(state);
			String featureText = recognitionFeatureSet.getElement(index);
			varDesc[var] = "Recognition feature for state " + stateText + " (" + state + ") feature " + featureText + " (" + index + ")";
		}

		// Get cosine sim variable descriptions
		for (Object entityTypeObj : cosineSimVars.keys()) {
			int var = cosineSimVars.get(entityTypeObj);
			varDesc[var] = "Cosine sim feature for type " + entityTypeObj;
		}

		// Get normalization variable descriptions
		for (String entityType : normalizationTypeToVars.keySet()) {
			TLongIntMap normalizationVars = normalizationTypeToVars.get(entityType);
			Index index = lexicon.getIndex(entityType);
			Dictionary<String> mentionVectorSpace = index.getMentionVectorSpace();
			Dictionary<String> nameVectorSpace = index.getNameVectorSpace();
			keys = normalizationVars.keys();
			for (int i = 0; i < keys.length; i++) {
				long joint = keys[i];
				int row = getRow(joint);
				int col = getColumn(joint);
				int var = normalizationVars.get(joint);
				String mentionElement = mentionVectorSpace.getElement(row);
				String nameElement = nameVectorSpace.getElement(col);
				varDesc[var] = "Normalization feature for type " + entityType + " from mention token " + mentionElement + " (" + row + ") to name token " + nameElement + " (" + col + ")";
			}
		}

		// Get slack variable descriptions
		int[] slackVarKeys = slackVars.keys();
		for (int i = 0; i < slackVarKeys.length; i++) {
			int index = slackVarKeys[i];
			int var = slackVars.get(index);
			varDesc[var] = "Slack variable " + index;
		}

		return varDesc;
	}

	public void addNonNegativeSlackVariableConstraints() {
		int[] slackVarKeys = slackVars.keys();
		for (int i = 0; i < slackVarKeys.length; i++) {
			int index = slackVarKeys[i];
			int var = slackVars.get(index);
			QPConstraint diagonal = new QPConstraint(this);
			diagonal.addRaw(var, -1.0);
			addConstraint(diagonal);
		}
	}

	public void addNonNegativeCosineSimNormalizationConstraints(Map<String, NormalizationModelUpdater> normalizationUpdaterModels) {
		for (String entityType : cosineSimVars.keySet()) {
			int var = cosineSimVars.get(entityType);
			System.out.println("Variable " + var + " is cosine sim variable for type " + entityType);
			QPConstraint diagonal = new QPConstraint(this);
			diagonal.addRaw(var, -1.0);
			NormalizationModelUpdater normalizationModelUpdater = normalizationUpdaterModels.get(entityType);
			double weight = normalizationModelUpdater.getCosineSimWeight();
			diagonal.addBValue(weight);
			logger.info("\tAdding non-negative cosine similarity constraint for type \"" + entityType + "\"");
			addConstraint(diagonal);
		}
	}

	private double copyRecognitionResult(Result result, double stepSize, Vector<String>[] featureWeightUpdates) {
		double sqrSum = 0.0;
		long[] keys = recognitionVars.keys();
		for (int i = 0; i < keys.length; i++) {
			long joint = keys[i];
			int state = getRow(joint);
			int index = getColumn(joint);
			int var = recognitionVars.get(joint);
			double value = stepSize * result.doubleValue(var);
			sqrSum += value * value;
			featureWeightUpdates[state].set(index, value);
		}
		logger.info("\tSize of recognition update = " + Math.sqrt(sqrSum));
		return sqrSum;
	}

	private double copyCosineSimResult(Result result, double stepSize, TObjectDoubleMap<String> cosineSimUpdates) {
		double totalSqrSum = 0.0;
		for (String entityType : cosineSimVars.keySet()) {
			int var = cosineSimVars.get(entityType);
			double value = stepSize * result.doubleValue(var);
			cosineSimUpdates.put(entityType, value);
			double sqrSum = value * value;
			logger.info("\tSize of cosine sim update for type " + entityType + " = " + Math.sqrt(sqrSum));
			totalSqrSum += sqrSum;
		}
		return totalSqrSum;
	}

	private double copyNormalizationResult(Result result, double stepSize, Map<String, Matrix<String, String>> normalizationTypeToWeightUpdates) {
		double totalSqrSum = 0.0;
		for (String entityType : normalizationTypeToVars.keySet()) {
			TLongIntMap normalizationVars = normalizationTypeToVars.get(entityType);
			Matrix<String, String> normalizationWeightUpdates = normalizationTypeToWeightUpdates.get(entityType);
			double sqrSum = 0.0;
			long[] keys = normalizationVars.keys();
			for (int i = 0; i < keys.length; i++) {
				long index = keys[i];
				int row = getRow(index);
				int col = getColumn(index);
				int var = normalizationVars.get(index);
				double value = stepSize * result.doubleValue(var);
				sqrSum += value * value;
				normalizationWeightUpdates.set(row, col, value);
			}
			logger.info("\tSize of normalization update for type " + entityType + " = " + Math.sqrt(sqrSum));
			totalSqrSum += sqrSum;
		}
		return totalSqrSum;
	}

	private void checkSlackResult(Result result) {
		double sum = 0.0;
		int[] keys = slackVars.keys();
		for (int i = 0; i < keys.length; i++) {
			int index = keys[i];
			int var = slackVars.get(index);
			sum += result.doubleValue(var);
		}
		logger.info("\tSize of slack loading = " + sum);
	}

	public static class QPConstraint {

		// TODO PERFORMANCE Replace the coefficients TIntDoubleMap with a SparseVector
		private QuadraticProgram qp;
		private TIntDoubleMap coefficients;
		private double b;

		public QPConstraint(QuadraticProgram qp) {
			this.qp = qp;
			coefficients = new TIntDoubleHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Constants.DEFAULT_INT_NO_ENTRY_VALUE, 0.0);
			b = 0.0;
		}

		public QPConstraint mapConstraint(QuadraticProgram newQP, int[] mapping) {
			QPConstraint mappedConstraint = new QPConstraint(newQP);
			int[] keys = coefficients.keys();
			for (int i = 0; i < keys.length; i++) {
				int key = keys[i];
				int newKey = mapping[key];
				if (newKey != -1) {
					double value = coefficients.get(key);
					mappedConstraint.coefficients.put(newKey, value);
				}
			}
			mappedConstraint.b = b;
			return mappedConstraint;
		}

		public boolean markNonZeroCoefficients(boolean[] hasNonZeroCoefficient) {
			boolean constraintHasNonZeroCoefficient = false;
			int[] keys = coefficients.keys();
			for (int i = 0; i < keys.length; i++) {
				int key = keys[i];
				double value = coefficients.get(key);
				if (Math.abs(value) >= T1Constants.EPSILON) {
					hasNonZeroCoefficient[key] = true;
					constraintHasNonZeroCoefficient = true;
				}
			}
			return constraintHasNonZeroCoefficient;
		}

		public double getCoefficient(int index) {
			return coefficients.get(index);
		}

		public void copyCoefficients(double[] coefficientsArray) {
			int[] keys = coefficients.keys();
			for (int i = 0; i < keys.length; i++) {
				int key = keys[i];
				double value = coefficients.get(key);
				coefficientsArray[key] = value;
			}
		}

		public double getB() {
			return b;
		}

		public void addRaw(int index, double coefficient) {
			updateCoefficient(index, coefficient, false);
		}

		public void addBValue(double bValue) {
			b += bValue;
		}

		public void addPath(List<AnnotatedSegment> section, boolean isCorrect) {
			for (AnnotatedSegment nextSegment : section) {
				// Update state transition
				String toState = nextSegment.getEntityClass();
				// Update recognition
				updateRecognitionVectorCoefficients(toState, nextSegment.getFeatures(), isCorrect);
				for (Token token : nextSegment.getTokens()) {
					updateRecognitionVectorCoefficients(toState, token.getFeatures(), isCorrect);
				}
			}
		}

		public void addNormalization(String entityType, Vector<String> m, Vector<String> n, boolean isCorrect) {
			VectorIterator mentionIterator = m.getIterator();
			while (mentionIterator.next()) {
				int rowIndex = mentionIterator.getIndex();
				double mentionValue = mentionIterator.getValue();

				// Update cosine similarity value
				int cosSimColumnIndex = qp.getMentionIndexToNameIndex(entityType)[rowIndex];
				if (cosSimColumnIndex >= 0) {
					double value = mentionValue * n.get(cosSimColumnIndex);
					if (Math.abs(value) >= T1Constants.EPSILON) {
						int var = qp.getCosineSimVar(entityType);
						if (isCorrect) {
							coefficients.adjustOrPutValue(var, -value, -value);
						} else {
							coefficients.adjustOrPutValue(var, value, value);
						}
					}
				}

				// Update W matrix value
				VectorIterator nameIterator = n.getIterator();
				while (nameIterator.next()) {
					int columnIndex = nameIterator.getIndex();
					double nameValue = nameIterator.getValue();
					double value = mentionValue * nameValue;
					if (Math.abs(value) >= T1Constants.EPSILON) {
						int var = qp.getNormalizationVar(entityType, rowIndex, columnIndex);
						// Update value
						if (isCorrect) {
							coefficients.adjustOrPutValue(var, -value, -value);
						} else {
							coefficients.adjustOrPutValue(var, value, value);
						}
					}
				}
			}
		}

		public QPConstraint copy() {
			QPConstraint copy = new QPConstraint(qp);
			int[] keys = coefficients.keys();
			for (int i = 0; i < keys.length; i++) {
				int key = keys[i];
				double value = coefficients.get(key);
				copy.coefficients.put(key, value);
			}
			copy.b = b;
			return copy;
		}

		private void updateRecognitionVectorCoefficients(String state, Vector<String> recognitionVector, boolean isCorrect) {
			Dictionary<String> entityClassStates = qp.getEntityClassStates();
			int stateIndex = entityClassStates.getIndex(state);
			VectorIterator recognitionVectorIterator = recognitionVector.getIterator();
			while (recognitionVectorIterator.next()) {
				int index = recognitionVectorIterator.getIndex();
				double value = recognitionVectorIterator.getValue();
				if (Math.abs(value) >= T1Constants.EPSILON) {
					// Get index of QP variable
					int var = qp.getRecognitionVar(stateIndex, index);
					// Update value
					if (isCorrect) {
						coefficients.adjustOrPutValue(var, -value, -value);
					} else {
						coefficients.adjustOrPutValue(var, value, value);
					}
				}
			}
		}

		public void addSlackVariable() {
			int var = qp.getSlackVar();
			coefficients.adjustOrPutValue(var, -1.0, -1.0);
		}

		private void updateCoefficient(int index, double value, boolean reverseSign) {
			if (Math.abs(value) < T1Constants.EPSILON) {
				return;
			}
			if (reverseSign) {
				coefficients.adjustOrPutValue(index, -value, -value);
			} else {
				coefficients.adjustOrPutValue(index, value, value);
			}
		}
	}
}