package ncbi.taggerOne.util.matrix;

import java.io.Serializable;

import ncbi.taggerOne.util.Dictionary;

public abstract class Matrix<R extends Serializable, C extends Serializable> implements Serializable {

	private static final long serialVersionUID = 1L;

	protected Dictionary<R> rowDictionary;
	protected Dictionary<C> columnDictionary;
	protected int numRows;
	protected int numColumns;

	public Matrix(Dictionary<R> rowDictionary, Dictionary<C> columnDictionary) {
		if (rowDictionary == null || columnDictionary == null) {
			throw new IllegalArgumentException("Dictionaries cannot be null");
		}
		if (!rowDictionary.isFrozen() || !columnDictionary.isFrozen()) {
			throw new IllegalArgumentException("Dictionaries must be frozen");
		}
		this.rowDictionary = rowDictionary;
		this.columnDictionary = columnDictionary;
		this.numRows = rowDictionary.size();
		this.numColumns = columnDictionary.size();
	}

	public Dictionary<R> getRowDictionary() {
		return rowDictionary;
	}

	public Dictionary<C> getColumnDictionary() {
		return columnDictionary;
	}

	public int numColumns() {
		return numColumns;
	}

	public int numRows() {
		return numRows;
	}

	protected void checkIndices(int rowIndex, int columnIndex) {
		if (rowIndex < 0 || rowIndex >= numRows) {
			throw new IndexOutOfBoundsException("Row index must be at least 0 and less than " + numRows);
		}
		if (columnIndex < 0 || columnIndex >= numColumns) {
			throw new IndexOutOfBoundsException("Column index must be at least 0 and less than " + numColumns);
		}
	}

	protected void checkDimensions(Matrix<R, C> matrix) {
		if (numRows != matrix.numRows) {
			throw new IllegalArgumentException("Matrices must have the same number of rows: " + numRows + " != " + matrix.numRows);
		}
		if (numColumns != matrix.numColumns) {
			throw new IllegalArgumentException("Matrices must have the same number of columns: " + numColumns + " != " + matrix.numColumns);
		}
	}

	public abstract double get(int rowIndex, int columnIndex);

	public abstract void set(int rowIndex, int columnIndex, double value);

	public abstract void increment(Matrix<R, C> matrix);

	public abstract void increment(double value, Matrix<R, C> matrix);

}
