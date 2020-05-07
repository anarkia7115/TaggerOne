package ncbi.taggerOne.model.normalization;

import java.io.Serializable;

import ncbi.taggerOne.util.matrix.Matrix;

public interface NormalizationModelUpdater extends Serializable {

	public double getWeight(int mentionIndex, int nameIndex);

	public double getCosineSimWeight();

	public void update(double cosineSimWeight, Matrix<String, String> weights);

}
