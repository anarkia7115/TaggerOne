package ncbi.taggerOne.util.matrix;

import java.io.Serializable;

import ncbi.taggerOne.util.Dictionary;

public interface MatrixFactory {

	public <R extends Serializable, C extends Serializable> Matrix<R, C> create(Dictionary<R> rowDictionary, Dictionary<C> columnDictionary);

}
