package ncbi.taggerOne.util.vector;

import java.io.Serializable;

import ncbi.taggerOne.util.Dictionary;

public interface VectorFactory extends Serializable {

	public <E extends Serializable> Vector<E> create(Dictionary<E> dictionary);

}
