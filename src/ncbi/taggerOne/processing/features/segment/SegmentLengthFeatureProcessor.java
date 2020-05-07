package ncbi.taggerOne.processing.features.segment;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.vector.Vector;

public class SegmentLengthFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String prefix;
	private double maxLength;

	public SegmentLengthFeatureProcessor(String prefix, int maxLength) {
		super();
		this.prefix = prefix;
		this.maxLength = maxLength;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Segment> segments = input.getSegments();
		String featureName = prefix + "=";
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			List<Token> tokens = segment.getTokens();
			Vector<String> featureVector = segment.getFeatures();
			double length = tokens.size() / maxLength;
			featureProcessorCallback.callback(featureName, length, featureVector);
		}
	}
}
