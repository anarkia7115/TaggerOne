package ncbi.taggerOne.util;

import java.io.Serializable;

public class RankedList<E> implements Serializable {

	private static final long serialVersionUID = 1L;

	private double[] values;
	private E[] objects;
	private int size;

	@SuppressWarnings("unchecked")
	public RankedList(int maxSize) {
		size = 0;
		values = new double[maxSize];
		// The following is safe because all types have Object as a superclass
		objects = (E[]) new Object[maxSize];
	}

	public void add(double value, E obj) {
		int index = size;
		while ((index > 0) && (value > values[index - 1])) {
			if (index < objects.length) {
				values[index] = values[index - 1];
				objects[index] = objects[index - 1];
			}
			index--;
		}
		if (index < objects.length) {
			values[index] = value;
			objects[index] = obj;
			if (size < objects.length) {
				size++;
			}
		}
	}

	public boolean check(double value) {
		if (size < objects.length) {
			return true;
		}
		if (value > values[size - 1]) {
			return true;
		}
		return false;
	}

	public int find(E obj) {
		for (int i = 0; i < size; i++) {
			if (objects[i].equals(obj)) {
				return i;
			}
		}
		return -1;
	}

	public void clear() {
		size = 0;
	}

	public E getObject(int rank) {
		if (rank >= size) {
			throw new IndexOutOfBoundsException();
		}
		return objects[rank];
	}

	public double getValue(int rank) {
		if (rank >= size) {
			throw new IndexOutOfBoundsException();
		}
		return values[rank];
	}

	public int maxSize() {
		return objects.length;
	}

	public int size() {
		return size;
	}
}