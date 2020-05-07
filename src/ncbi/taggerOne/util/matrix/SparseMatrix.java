package ncbi.taggerOne.util.matrix;

import java.io.Serializable;

import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public class SparseMatrix<R extends Serializable, C extends Serializable> extends Matrix<R, C> {

	private static final long serialVersionUID = 1L;

	public static final MatrixFactory factory = new MatrixFactory() {
		@Override
		public <R extends Serializable, C extends Serializable> Matrix<R, C> create(Dictionary<R> rowDictionary, Dictionary<C> columnDictionary) {
			return new SparseMatrix<R, C>(rowDictionary, columnDictionary);
		}
	};

	protected Long2DoubleOpenHashMap values;

	public SparseMatrix(Dictionary<R> rowDictionary, Dictionary<C> columnDictionary) {
		super(rowDictionary, columnDictionary);
		this.values = new Long2DoubleOpenHashMap();
	}

	@Override
	public double get(int rowIndex, int columnIndex) {
		checkIndices(rowIndex, columnIndex);
		long jointIndex = (((long) rowIndex) << 32) + columnIndex;
		return values.get(jointIndex);
	}

	@Override
	public void set(int rowIndex, int columnIndex, double value) {
		checkIndices(rowIndex, columnIndex);
		long jointIndex = (((long) rowIndex) << 32) + columnIndex;
		values.put(jointIndex, value);
	}

	protected static long getJointIndex(int row, int column) {
		// The column mask is not needed as long as column is non-negative
		// return (((long) row) << 32) | (column & 0xffffffffL);
		return (((long) row) << 32) + column;
	}

	protected static int getRow(long jointIndex) {
		return (int) (jointIndex >> 32);
	}

	protected static int getColumn(long jointIndex) {
		return (int) jointIndex;
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
				values.addTo(jointIndex, entry.getDoubleValue());
			}
		} else if (matrix instanceof DenseBySparseMatrix) {
			DenseBySparseMatrix<R, C> sparseMatrix = (DenseBySparseMatrix<R, C>) matrix;
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				Vector<C> row2 = sparseMatrix.values[rowIndex];
				if (row2 != null) {
					VectorIterator iterator = row2.getIterator();
					while (iterator.next()) {
						int columnIndex = iterator.getIndex();
						long jointIndex = getJointIndex(rowIndex, columnIndex);
						values.addTo(jointIndex, iterator.getValue());
					}
				}
			}
		} else {
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				for (int columnIndex = 0; columnIndex < numColumns; columnIndex++) {
					double matrixValue = matrix.get(rowIndex, columnIndex);
					if (matrixValue != 0.0) {
						long jointIndex = getJointIndex(rowIndex, columnIndex);
						values.addTo(jointIndex, matrixValue);
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
				double newValue = values.get(jointIndex) + value * entry.getDoubleValue();
				values.put(jointIndex, newValue);
			}
		} else if (matrix instanceof DenseBySparseMatrix) {
			DenseBySparseMatrix<R, C> sparseMatrix = (DenseBySparseMatrix<R, C>) matrix;
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				Vector<C> row2 = sparseMatrix.values[rowIndex];
				if (row2 != null) {
					VectorIterator iterator = row2.getIterator();
					while (iterator.next()) {
						int columnIndex = iterator.getIndex();
						long jointIndex = getJointIndex(rowIndex, columnIndex);
						values.addTo(jointIndex, value * iterator.getValue());
					}
				}
			}
		} else {
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				for (int columnIndex = 0; columnIndex < numColumns; columnIndex++) {
					double matrixValue = matrix.get(rowIndex, columnIndex);
					if (matrixValue != 0.0) {
						long jointIndex = getJointIndex(rowIndex, columnIndex);
						values.addTo(jointIndex, value * matrixValue);
					}
				}
			}
		}
	}
}
