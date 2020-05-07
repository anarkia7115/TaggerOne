package ncbi.taggerOne.processing.features;

import java.io.Serializable;

import ncbi.taggerOne.types.TextInstance;

/*
 * Extracts a single class of features 
 */
public interface FeatureProcessor extends Serializable {

	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback);

}