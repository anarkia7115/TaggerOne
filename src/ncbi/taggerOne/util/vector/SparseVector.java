package ncbi.taggerOne.util.vector;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.io.Serializable;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.util.Dictionary;
import ncbi.util.Profiler;

public class SparseVector<E extends Serializable> extends Vector<E> {

	private static final Logger logger = LoggerFactory.getLogger(SparseVector.class);
	private static final long serialVersionUID = 1L;

	public static final VectorFactory factory = new VectorFactory() {

		private static final long serialVersionUID = 1L;

		@Override
		public <E extends Serializable> Vector<E> create(Dictionary<E> dictionary) {
			return new SparseVector<E>(dictionary);
		}
	};

	Int2DoubleOpenHashMap values; // package-level visibility for increased performance
	private int hashCode;

	public SparseVector(Dictionary<E> dictionary) {
		super(dictionary);
		values = new Int2DoubleOpenHashMap(); // TODO OPTIMIZE load factor
		values.defaultReturnValue(0.0);
		hashCode = Integer.MIN_VALUE;
	}

	protected SparseVector(Dictionary<E> dictionary, Int2DoubleOpenHashMap values) {
		super(dictionary);
		this.values = values;
		hashCode = Integer.MIN_VALUE;
	}

	@Override
	public int cardinality() {
		return values.size();
	}

	@Override
	public boolean isEmpty() {
		return values.isEmpty();
	};

	public double get(int index) {
		checkIndex(index);
		// Note 0.0 is the no entry value
		return values.get(index);
	}

	@Override
	public void set(int index, double value) {
		hashCode = Integer.MIN_VALUE;
		checkIndex(index);
		if (value == 0.0) {
			values.remove(index);
		} else {
			values.put(index, value);
		}
	}

	@Override
	public void increment(int index, double value) {
		if (value == 0.0) {
			return;
		}
		hashCode = Integer.MIN_VALUE;
		checkIndex(index);
		values.addTo(index, value);
	}

	@Override
	public void increment(double factor, Vector<E> vector) {
		checkDimensions(vector);
		hashCode = Integer.MIN_VALUE;
		if (vector instanceof SparseVector) {
			SparseVector<E> sparseOther = (SparseVector<E>) vector;
			ObjectIterator<Entry> iterator = sparseOther.values.int2DoubleEntrySet().fastIterator();
			while (iterator.hasNext()) {
				Entry entry = iterator.next();
				values.addTo(entry.getIntKey(), factor * entry.getDoubleValue());
			}
		} else {
			VectorIterator iterator = vector.getIterator();
			while (iterator.next()) {
				int index = iterator.getIndex();
				double value = iterator.getValue();
				values.addTo(index, factor * value);
			}
		}
	}

	@Override
	public void increment(Vector<E> vector) {
		checkDimensions(vector);
		hashCode = Integer.MIN_VALUE;
		if (vector instanceof SparseVector) {
			SparseVector<E> sparseOther = (SparseVector<E>) vector;
			ObjectIterator<Entry> iterator = sparseOther.values.int2DoubleEntrySet().fastIterator();
			while (iterator.hasNext()) {
				Entry entry = iterator.next();
				values.addTo(entry.getIntKey(), entry.getDoubleValue());
			}
		} else {
			VectorIterator iterator = vector.getIterator();
			while (iterator.next()) {
				int index = iterator.getIndex();
				double value = iterator.getValue();
				values.addTo(index, value);
			}
		}
	}

	@Override
	public double length() {
		Profiler.start("SparseVector.length()");
		// TODO PERFORMANCE Cache this result
		// Considering the indices in sorted order ensures that the length for equal vectors are exactly equal
		// When considered in arbitrary order, they frequently differ in the last digit because of rounding
		// Since the length is used for normalizing the vector to length 1.0, this does (rarely) cause equal vectors to become unequal
		// This implementation has been tested for performance, operating at 0.0003 ms per call
		double length = 0.0;
		int[] indices = values.keySet().toIntArray();
		Arrays.sort(indices);
		for (int i = 0; i < indices.length; i++) {
			double value = values.get(indices[i]);
			length += value * value;
		}
		length = Math.sqrt(length);
		Profiler.stop("SparseVector.length()");
		return length;
	}

	@Override
	public void normalize() {
		hashCode = Integer.MIN_VALUE;
		double length = length();
		ObjectIterator<Entry> iterator = values.int2DoubleEntrySet().fastIterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			double newValue = entry.getDoubleValue() / length;
			entry.setValue(newValue);
		}
	}

	@Override
	public double dotProduct(Vector<E> vector) {
		checkDimensions(vector);
		double sum = 0.0;
		ObjectIterator<Entry> iterator = values.int2DoubleEntrySet().fastIterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			int index = entry.getIntKey();
			sum += entry.getDoubleValue() * vector.get(index);
		}
		return sum;
	}

	public VectorIterator getIterator() {
		return new SparseVectorIterator(values);
	}

	private class SparseVectorIterator implements VectorIterator {

		private ObjectIterator<Entry> iterator;
		private int currentIndex;
		private double currentValue;

		public SparseVectorIterator(Int2DoubleOpenHashMap values) {
			iterator = values.int2DoubleEntrySet().fastIterator();
		}

		@Override
		public boolean next() {
			if (iterator.hasNext()) {
				Entry currentEntry = iterator.next();
				currentIndex = currentEntry.getIntKey();
				currentValue = currentEntry.getDoubleValue();
				return true;
			}
			return false;
		}

		@Override
		public int getIndex() {
			return currentIndex;
		}

		@Override
		public double getValue() {
			return currentValue;
		}

	}

	@Override
	public String visualize() {
		StringBuilder str = new StringBuilder("[");
		int[] indices = values.keySet().toIntArray();
		Arrays.sort(indices);
		for (int i = 0; i < indices.length; i++) {
			if (i > 0) {
				str.append(", ");
			}
			int index = indices[i];
			str.append(index);
			str.append(":");
			str.append(dictionary.getElement(index));
			str.append("=");
			str.append(values.get(indices[i]));
		}
		str.append("]");
		return str.toString();
	}

	@Override
	public Vector<E> copy() {
		return new SparseVector<E>(dictionary, new Int2DoubleOpenHashMap(values));
	}

	@Override
	public int hashCode() {
		if (hashCode == Integer.MIN_VALUE) {
			final int prime = 31;
			int result = super.hashCode();
			int[] indices = values.keySet().toIntArray();
			Arrays.sort(indices);
			for (int i = 0; i < indices.length; i++) {
				result = prime * result + indices[i];
				long v = Double.doubleToLongBits(values.get(indices[i]));
				result = prime * result + (int) (v ^ (v >>> 32));
			}
			hashCode = result;
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		SparseVector<?> other = (SparseVector<?>) obj;
		if (values.size() != other.values.size()) {
			return false;
		}
		int[] indices = values.keySet().toIntArray();
		for (int i = 0; i < indices.length; i++) {
			if (!other.values.containsKey(indices[i])) {
				return false;
			}
		}
		for (int i = 0; i < indices.length; i++) {
			double value1 = values.get(indices[i]);
			double value2 = other.values.get(indices[i]);
			if (value1 != value2) {
				double difference = Math.abs(value1 - value2);
				if (difference < T1Constants.EPSILON) {
					logger.warn("Returning Vector.equals()=false for difference of " + difference + " for v1=" + toString() + ", v2=" + other.toString());
				}
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return "SparseVector " + visualize();
	}
}