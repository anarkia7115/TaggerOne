package ncbi.taggerOne.model.recognition;

import java.io.Serializable;

import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.Vector;

public interface RecognitionModelUpdater extends Serializable {

	public void update(Vector<String>[] featureWeights);

	public Dictionary<String> getFeatureSet();

}
