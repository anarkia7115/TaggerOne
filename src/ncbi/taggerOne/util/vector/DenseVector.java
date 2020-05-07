package ncbi.taggerOne.util.vector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ncbi.taggerOne.util.Dictionary;

/*
 * An array of values. Used in conjunction with a Dictionary.
 */
public class DenseVector<E extends Serializable> extends Vector<E> {

	private static final long serialVersionUID = 1L;

	public static final VectorFactory factory = new VectorFactory() {
		private static final long serialVersionUID = 1L;

		@Override
		public <E extends Serializable> Vector<E> create(Dictionary<E> dictionary) {
			return new DenseVector<E>(dictionary);
		}
	};

	double[] values; // package-level visibility for increased performance

	public DenseVector(Dictionary<E> dictionary) {
		super(dictionary);
		values = new double[dimensions];
	}

	private DenseVector(Dictionary<E> dictionary, double[] values) {
		super(dictionary);
		this.values = values;
	}

	public boolean isEmpty() {
		for (int i = 0; i < values.length; i++) {
			if (values[i] != 0.0) {
				return false;
			}
		}
		return true;
	}

	@Override
	public double get(int index) {
		checkIndex(index);
		return values[index];
	}

	@Override
	public void set(int index, double value) {
		checkIndex(index);
		values[index] = value;
	}

	@Override
	public void increment(int index, double value) {
		checkIndex(index);
		values[index] += value;
	}

	@Override
	public void increment(double factor, Vector<E> vector) {
		checkDimensions(vector);
		if (vector instanceof SparseVector) {
			VectorIterator iterator = vector.getIterator();
			while (iterator.next()) {
				int index = iterator.getIndex();
				double value = iterator.getValue();
				values[index] += factor * value;
			}
		} else {
			for (int i = 0; i < dimensions; i++) {
				values[i] += factor * vector.get(i);
			}
		}
	}

	@Override
	public void increment(Vector<E> vector) {
		checkDimensions(vector);
		if (vector instanceof SparseVector) {
			VectorIterator iterator = vector.getIterator();
			while (iterator.next()) {
				int index = iterator.getIndex();
				double value = iterator.getValue();
				values[index] += value;
			}
		} else {
			for (int i = 0; i < dimensions; i++) {
				values[i] += vector.get(i);
			}
		}
	}

	@Override
	public double length() {
		double length = 0.0;
		for (int i = 0; i < dimensions; i++) {
			length += values[i] * values[i];
		}
		return Math.sqrt(length);
	}

	@Override
	public void normalize() {
		double length = length();
		for (int i = 0; i < dimensions; i++) {
			values[i] = values[i] / length;
		}
	}

	@Override
	public double dotProduct(Vector<E> vector) {
		checkDimensions(vector);
		// TODO PERFORMANCE Fix this to take advantage of sparsity in the other vector
		double sum = 0.0;
		for (int i = 0; i < dimensions; i++) {
			sum += values[i] * vector.get(i);
		}
		return sum;
	}

	@Override
	public VectorIterator getIterator() {
		return new DenseVectorIterator(values);
	}

	private class DenseVectorIterator implements VectorIterator {

		private double[] values;
		private int currentIndex;

		public DenseVectorIterator(double[] values) {
			this.values = values;
			this.currentIndex = -1;
		}

		@Override
		public boolean next() {
			currentIndex++;
			while (currentIndex < values.length && values[currentIndex] == 0.0) {
				currentIndex++;
			}
			return currentIndex < values.length;
		}

		@Override
		public int getIndex() {
			return currentIndex;
		}

		@Override
		public double getValue() {
			return values[currentIndex];
		}

	}

	@Override
	public String visualize() {
		List<String> elements = new ArrayList<String>();
		for (int i = 0; i < values.length; i++) {
			if (values[i] != 0.0) {
				elements.add(i + ":" + dictionary.getElement(i) + "=" + values[i]);
			}
		}
		return elements.toString();
	}

	@Override
	public Vector<E> copy() {
		double[] newValues = new double[values.length];
		System.arraycopy(values, 0, newValues, 0, values.length);
		return new DenseVector<E>(dictionary, newValues);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Arrays.hashCode(values);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		DenseVector<?> other = (DenseVector<?>) obj;
		if (!Arrays.equals(values, other.values))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DenseVector " + visualize();
	}
}