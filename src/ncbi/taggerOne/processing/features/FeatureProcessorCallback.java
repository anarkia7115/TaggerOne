package ncbi.taggerOne.processing.features;

import java.io.Serializable;

import ncbi.taggerOne.util.vector.Vector;

public interface FeatureProcessorCallback extends Serializable {

	public void callback(String featureName, double featureValue, Vector<String> featureVector);

}