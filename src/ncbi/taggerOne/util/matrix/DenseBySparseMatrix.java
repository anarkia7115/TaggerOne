package ncbi.taggerOne.util.matrix;

import java.io.Serializable;

import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.SparseVector;
import ncbi.taggerOne.util.vector.Vector;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public class DenseBySparseMatrix<R extends Serializable, C extends Serializable> extends Matrix<R, C> {

	private static final long serialVersionUID = 1L;

	protected SparseVector<C>[] values;

	public static final MatrixFactory factory = new MatrixFactory() {
		@Override
		public <R extends Serializable, C extends Serializable> Matrix<R, C> create(Dictionary<R> rowDictionary, Dictionary<C> columnDictionary) {
			return new DenseBySparseMatrix<R, C>(rowDictionary, columnDictionary);
		}
	};

	@SuppressWarnings("unchecked")
	public DenseBySparseMatrix(Dictionary<R> rowDictionary, Dictionary<C> columnDictionary) {
		super(rowDictionary, columnDictionary);
		values = new SparseVector[numRows];
	}

	@Override
	public double get(int rowIndex, int columnIndex) {
		checkIndices(rowIndex, columnIndex);
		SparseVector<C> row = values[rowIndex];
		if (row == null) {
			return 0.0;
		}
		return row.get(columnIndex);
	}

	public Vector<C> getRowVector(int rowIndex) {
		return values[rowIndex];
	}

	public void incrementRow(int rowIndex, Vector<C> rowVector) {
		SparseVector<C> row = values[rowIndex];
		if (row == null) {
			row = new SparseVector<C>(columnDictionary);
			values[rowIndex] = row;
		}
		row.increment(rowVector);
	}

	@Override
	public void set(int rowIndex, int columnIndex, double value) {
		checkIndices(rowIndex, columnIndex);
		SparseVector<C> row = values[rowIndex];
		if (row == null) {
			row = new SparseVector<C>(columnDictionary);
			values[rowIndex] = row;
		}
		row.set(columnIndex, value);
	}

	@Override
	public void increment(Matrix<R, C> matrix) {
		checkDimensions(matrix);
		if (matrix instanceof SparseMatrix) {
			SparseMatrix<R, C> sparseMatrix = (SparseMatrix<R, C>) matrix;
			ObjectIterator<it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry> iterator = sparseMatrix.values.long2DoubleEntrySet().fastIterator();
			while (iterator.hasNext()) {
				it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry entry = iterator.next();
				long jointIndex = entry.getLongKey();
				int rowIndex = SparseMatrix.getRow(jointIndex);
				SparseVector<C> row = values[rowIndex];
				if (row == null) {
					row = new SparseVector<C>(columnDictionary);
					values[rowIndex] = row;
				}
				int columnIndex = SparseMatrix.getColumn(jointIndex);
				row.increment(columnIndex, entry.getDoubleValue());
			}
		} else if (matrix instanceof DenseBySparseMatrix) {
			DenseBySparseMatrix<R, C> sparseMatrix = (DenseBySparseMatrix<R, C>) matrix;
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				Vector<C> row2 = sparseMatrix.values[rowIndex];
				if (row2 != null) {
					SparseVector<C> row1 = values[rowIndex];
					if (row1 == null) {
						row1 = new SparseVector<C>(columnDictionary);
						values[rowIndex] = row1;
					}
					row1.increment(row2);
				}
			}
		} else {
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				for (int columnIndex = 0; columnIndex < numColumns; columnIndex++) {
					double matrixValue = matrix.get(rowIndex, columnIndex);
					if (matrixValue != 0.0) {
						SparseVector<C> row = values[rowIndex];
						if (row == null) {
							row = new SparseVector<C>(columnDictionary);
							values[rowIndex] = row;
						}
						row.increment(columnIndex, matrixValue);
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
			ObjectIterator<it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry> iterator = sparseMatrix.values.long2DoubleEntrySet().fastIterator();
			while (iterator.hasNext()) {
				it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry entry = iterator.next();
				long jointIndex = entry.getLongKey();
				int rowIndex = SparseMatrix.getRow(jointIndex);
				SparseVector<C> row = values[rowIndex];
				if (row == null) {
					row = new SparseVector<C>(columnDictionary);
					values[rowIndex] = row;
				}
				int columnIndex = SparseMatrix.getColumn(jointIndex);
				row.increment(columnIndex, value * entry.getDoubleValue());
			}
		} else if (matrix instanceof DenseBySparseMatrix) {
			DenseBySparseMatrix<R, C> sparseMatrix = (DenseBySparseMatrix<R, C>) matrix;
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				Vector<C> row2 = sparseMatrix.values[rowIndex];
				if (row2 != null) {
					SparseVector<C> row1 = values[rowIndex];
					if (row1 == null) {
						row1 = new SparseVector<C>(columnDictionary);
						values[rowIndex] = row1;
					}
					row1.increment(value, row2);
				}
			}
		} else {
			for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
				for (int columnIndex = 0; columnIndex < numColumns; columnIndex++) {
					double matrixValue = matrix.get(rowIndex, columnIndex);
					if (matrixValue != 0.0) {
						SparseVector<C> row = values[rowIndex];
						if (row == null) {
							row = new SparseVector<C>(columnDictionary);
							values[rowIndex] = row;
						}
						row.increment(columnIndex, value * matrixValue);
					}
				}
			}
		}
	}
}
