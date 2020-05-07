package ncbi.taggerOne.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A Trie (also known as a prefix tree) essentially maps from a list of objects (keys) to a value. The interface is very similar, therefore, to the interface for {@link Map}, except that the key is a
 * {@link List} of objects. This data structure allows searching in O(m) time, where m is the depth of the tree.
 */
public class Trie<K, V> implements Serializable {

	private static final long serialVersionUID = 1L;

	private TrieNode<K, V> root;

	public Trie() {
		root = new TrieNode<K, V>();
	}

	public V add(List<K> keys, V value) {
		return root.add(keys.iterator(), value);
	}

	public V get(List<K> keys) {
		return root.get(keys.iterator());
	}

	public int size() {
		return root.size();
	}

	private static class TrieNode<K, V> implements Serializable {

		private static final long serialVersionUID = 1L;

		private V value;
		private Map<K, TrieNode<K, V>> children;
		private int size;

		public TrieNode() {
			value = null;
			children = null;
			size = 0;
		}

		public V add(Iterator<K> keyIterator, V value) {
			if (keyIterator.hasNext()) {
				K nextKey = keyIterator.next();
				if (children == null) {
					children = new HashMap<K, TrieNode<K, V>>(1);
				}
				TrieNode<K, V> next = children.get(nextKey);
				if (next == null) {
					next = new TrieNode<K, V>();
					children.put(nextKey, next);
				}
				V oldValue = next.add(keyIterator, value);
				if (oldValue == null) {
					size++;
				}
				return oldValue;
			}
			V oldValue = this.value;
			this.value = value;
			if (oldValue == null) {
				size++;
			}
			return oldValue;
		}

		public V get(Iterator<K> keyIterator) {
			if (keyIterator.hasNext()) {
				K nextKey = keyIterator.next();
				if (children == null) {
					return null;
				}
				TrieNode<K, V> next = children.get(nextKey);
				if (next == null) {
					return null;
				}
				return next.get(keyIterator);
			}
			return value;
		}

		public int size() {
			return size;
		}
	}

}
