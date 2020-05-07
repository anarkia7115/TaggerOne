package ncbi.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

public abstract class SimpleComparator<T> implements Comparator<T>, Serializable {

	private static final long serialVersionUID = 1L;

	@Override
	public abstract int compare(T o1, T o2);

	@Override
	public Comparator<T> reversed() {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public Comparator<T> thenComparing(Comparator<? super T> other) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public <U> Comparator<T> thenComparing(Function<? super T, ? extends U> keyExtractor, Comparator<? super U> keyComparator) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public <U extends Comparable<? super U>> Comparator<T> thenComparing(Function<? super T, ? extends U> keyExtractor) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public Comparator<T> thenComparingInt(ToIntFunction<? super T> keyExtractor) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public Comparator<T> thenComparingLong(ToLongFunction<? super T> keyExtractor) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public Comparator<T> thenComparingDouble(ToDoubleFunction<? super T> keyExtractor) {
		throw new RuntimeException("Not implemented");
	}

}
