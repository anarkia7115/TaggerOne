package ncbi.taggerOne.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final int DEFAULT_CAPACITY = 10;
	public static final float DEFAULT_LOAD_FACTOR = 0.5f;

	private int initialCapacity;
	private float loadFactor;
	private int maxSize;
	private transient Map<K, V> cache;

	public LRUCache(int initialCapacity, float loadFactor, int maxSize) {
		if (initialCapacity <= 0) {
			throw new IllegalArgumentException("Initial capacity must be greater than 0");
		}
		if (maxSize <= 0) {
			throw new IllegalArgumentException("Max size must be greater than 0");
		}
		this.initialCapacity = initialCapacity;
		this.loadFactor = loadFactor;
		this.maxSize = maxSize;
		cache = Collections.synchronizedMap(new LRULinkedHashMap<K, V>(initialCapacity, loadFactor, maxSize));
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		this.cache = new LRULinkedHashMap<K, V>(initialCapacity, loadFactor, maxSize);
	}

	private static class LRULinkedHashMap<K, V> extends LinkedHashMap<K, V> {

		private static final long serialVersionUID = 1L;

		private int maxSize;

		public LRULinkedHashMap(int initialCapacity, float loadFactor, int maxSize) {
			super(initialCapacity, loadFactor, true);
			this.maxSize = maxSize;
		}

		protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
			return size() > maxSize;
		}

	}

	public int getMaxSize() {
		return maxSize;
	}

	public void clear() {
		cache.clear();
	}

	public V get(Object key) {
		return cache.get(key);
	}

	public V put(K key, V value) {
		if (key == null) {
			throw new IllegalArgumentException("Key may not be null");
		}
		if (value == null) {
			throw new IllegalArgumentException("Value may not be null");
		}
		return cache.put(key, value);
	}

	public V remove(Object key) {
		return cache.remove(key);
	}

	public int size() {
		return cache.size();
	}
}
