package ncbi.taggerOne.model.recognition;

import java.io.Serializable;

import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.util.Dictionary;

public interface RecognitionModelPredictor extends Serializable {

	public Dictionary<String> getEntityClassStates();

	public double predict(String toState, Segment segment);

	public Dictionary<String> getFeatureSet();

	public void visualize();
	
	public RecognitionModelPredictor compile();
	
}