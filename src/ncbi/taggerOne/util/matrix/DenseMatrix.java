package ncbi.taggerOne.util.matrix;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.io.Serializable;
import java.math.BigInteger;

import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;

public class DenseMatrix<R extends Serializable, C extends Serializable> extends Matrix<R, C> {

	private static final long serialVersionUID = 1L;

	public static final MatrixFactory factory = new MatrixFactory() {
		@Override
		public <R extends Serializable, C extends Serializable> Matrix<R, C> create(Dictionary<R> rowDictionary, Dictionary<C> columnDictionary) {
			return new DenseMatrix<R, C>(rowDictionary, columnDictionary);
		}
	};

	private double[] values;

	public DenseMatrix(Dictionary<R> rowDictionary, Dictionary<C> columnDictionary) {
		super(rowDictionary, columnDictionary);
		// Check for overflow
		BigInteger bigNumRows = BigInteger.valueOf(numRows);
		BigInteger bigNumColumns = BigInteger.valueOf(numColumns);
		BigInteger bigSize = bigNumRows.multiply(bigNumColumns);
		if (bigSize.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
			throw new IllegalArgumentException("Matrix with rows=" + numRows + " and colums=" + numColumns + " exceeds maximum DenseMatrix size; use a SparseMatrix instead.");
		}
		int size = numRows * numColumns;
		values = new double[size];
	}

	@Override
	public double get(int rowIndex, int columnIndex) {
		checkIndices(rowIndex, columnIndex);
		int index = rowIndex * numColumns + columnIndex;
		return values[index];
	}

	@Override
	public void set(int rowIndex, int columnIndex, double value) {
		checkIndices(rowIndex, columnIndex);
		int index = rowIndex * numColumns + columnIndex;
		values[index] = value;
	}

	@Override
	public void increment(Matrix<R, C> matrix) {
		checkDimensions(matrix);
		if (matrix instanceof SparseMatrix) {
			SparseMatrix<R, C> sparseMatrix = (SparseMatrix<R, C>) matrix;
			ObjectIterator<Entry> iterator = sparseMatrix.values.long2DoubleEntrySet().fastIterator();
			while (iterator.hasNext()) {
				Entry entry = iterator.next();
				long jointIndex = entry.getLongKey();
				int rowIndex = SparseMatrix.getRow(jointIndex);
				int columnIndex = SparseMatrix.getColumn(jointIndex);
				int index = rowIndex * numColumns + columnIndex;
				values[index] += entry.getDoubleValue();
			}
		} else if (matrix instanceof DenseBySparseMatrix) {
			DenseBySparseMatrix<R, C> sparseMatrix = (DenseBySparseMatrix<R, C>) matrix;
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				Vector<C> row2 = sparseMatrix.values[rowIndex];
				if (row2 != null) {
					VectorIterator iterator = row2.getIterator();
					while (iterator.next()) {
						int columnIndex = iterator.getIndex();
						int index = rowIndex * numColumns + columnIndex;
						values[index] += iterator.getValue();
					}
				}
			}
		} else {
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				for (int columnIndex = 0; columnIndex < numColumns; columnIndex++) {
					double matrixValue = matrix.get(rowIndex, columnIndex);
					if (matrixValue != 0.0) {
						int index = rowIndex * numColumns + columnIndex;
						values[index] += matrixValue;
					}
				}
			}
		}
	}

	@Override
	public void increment(double value, Matrix<R, C> matrix) {
		checkDimensions(matrix);
		if (matrix instanceof SparseMatrix) {
			SparseMatrix<R, C> sparseMatrix = (SparseMatrix<R, C>) matrix;
			ObjectIterator<Entry> iterator = sparseMatrix.values.long2DoubleEntrySet().fastIterator();
			while (iterator.hasNext()) {
				Entry entry = iterator.next();
				long jointIndex = entry.getLongKey();
				int rowIndex = SparseMatrix.getRow(jointIndex);
				int columnIndex = SparseMatrix.getColumn(jointIndex);
				int index = rowIndex * numColumns + columnIndex;
				values[index] += value * entry.getDoubleValue();
			}
		} else if (matrix instanceof DenseBySparseMatrix) {
			DenseBySparseMatrix<R, C> sparseMatrix = (DenseBySparseMatrix<R, C>) matrix;
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				Vector<C> row2 = sparseMatrix.values[rowIndex];
				if (row2 != null) {
					VectorIterator iterator = row2.getIterator();
					while (iterator.next()) {
						int columnIndex = iterator.getIndex();
						int index = rowIndex * numColumns + columnIndex;
						values[index] += value * iterator.getValue();
					}
				}
			}
		} else {
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				for (int columnIndex = 0; columnIndex < numColumns; columnIndex++) {
					double matrixValue = matrix.get(rowIndex, columnIndex);
					if (matrixValue != 0.0) {
						int index = rowIndex * numColumns + columnIndex;
						values[index] += value * matrixValue;
					}
				}
			}
		}
	}
}
