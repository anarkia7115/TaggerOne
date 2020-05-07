package ncbi.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StaticUtilMethods {

	private StaticUtilMethods() {
		// Make uninstantiable
	}

	public static boolean containsAny(Set<String> set1, Set<String> set2) {
		for (String element : set1) {
			if (set2.contains(element)) {
				return true;
			}
		}
		return false;
	}

	public static Set<String> getStringSet(String str) {
		String[] split = str.split(",");
		Set<String> strSet = new HashSet<String>();
		for (int i = 0; i < split.length; i++) {
			strSet.add(split[i]);
		}
		return strSet;
	}

	public static List<String> getStringList(String str) {
		String[] split = str.split(",");
		List<String> strList = new ArrayList<String>();
		for (int i = 0; i < split.length; i++) {
			strList.add(split[i]);
		}
		return strList;
	}

	public static Map<String, String> getStringMap(String str) {
		String[] split = str.split(",");
		Map<String, String> strMap = new HashMap<String, String>();
		for (int i = 0; i < split.length; i++) {
			String[] fields = split[i].split("->");
			strMap.put(fields[0], fields[1]);
		}
		return strMap;
	}

	/*
	 * Returns TRUE iff the two sets contain exactly the same set of elements. This is useful for determining if the content of two Sets is the same, even if the type of the Set is different.
	 */
	public static <E> boolean equalElements(Set<E> s1, Set<E> s2) {
		if (s1.size() != s2.size()) {
			return false;
		}
		for (E e : s1) {
			if (!s2.contains(e)) {
				return false;
			}
		}
		return true;
	}

}
