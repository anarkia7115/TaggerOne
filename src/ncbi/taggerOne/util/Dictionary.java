package ncbi.taggerOne.util;

import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Dictionary<E extends Serializable> implements Serializable {

	private static final long serialVersionUID = 1L;

	// Performance note: The Trove TObjectIntMap is faster than the fastutil Object2IntOpenHashMap or the built-in HashMap<String, Integer>

	private boolean frozen;
	private List<E> indexToElement;
	private TObjectIntMap<E> elementToIndex;
	private int hashCode;

	public Dictionary() {
		frozen = false;
		indexToElement = new ArrayList<E>();
		elementToIndex = new TObjectIntHashMap<E>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Integer.MIN_VALUE);
		hashCode = Integer.MIN_VALUE;
	}

	public int addElement(E element) {
		if (frozen) {
			throw new IllegalStateException("Cannot add to a frozen Dictionary");
		}
		int index = elementToIndex.get(element);
		if (index != Integer.MIN_VALUE) {
			return index;
		}
		index = indexToElement.size();
		elementToIndex.put(element, index);
		indexToElement.add(element);
		return index;
	}

	public int getIndex(E element) {
		// Note Integer.MIN_VALUE is the no entry value
		return elementToIndex.get(element);
	}

	public E getElement(int index) {
		return indexToElement.get(index);
	}

	public List<E> getElements() {
		return Collections.unmodifiableList(indexToElement);
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void freeze() {
		if (!frozen) {
			frozen = true;
			hashCode = hashCodeInternal();
		}
	}

	public int size() {
		return indexToElement.size();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		@SuppressWarnings("rawtypes")
		Dictionary other = (Dictionary) obj;
		if (!indexToElement.equals(other.indexToElement))
			return false;
		return true;
	}

	private int hashCodeInternal() {
		final int prime = 31;
		int result = 1;
		result = prime * result + indexToElement.hashCode();
		return result;
	}

	@Override
	public int hashCode() {
		if (frozen) {
			return hashCode;
		}
		return hashCodeInternal();
	}

}
