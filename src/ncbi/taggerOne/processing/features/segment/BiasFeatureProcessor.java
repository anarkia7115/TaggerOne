package ncbi.taggerOne.processing.features.segment;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.vector.Vector;

public class BiasFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String featureName;

	public BiasFeatureProcessor(String featureName) {
		this.featureName = featureName;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Segment> segments = input.getSegments();
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			Vector<String> featureVector = segment.getFeatures();
			featureProcessorCallback.callback(featureName, 1.0, featureVector);
		}
	}

}
