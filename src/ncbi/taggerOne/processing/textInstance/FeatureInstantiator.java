package ncbi.taggerOne.processing.textInstance;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.VectorFactory;
import ncbi.util.Profiler;

/*
 * Instantiates the set of features for TextInstances
 */
public class FeatureInstantiator extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	private VectorFactory vectorFactory;
	private Dictionary<String> featureSet;
	private List<FeatureProcessor> featureProcessors;
	private FeatureProcessorCallback callback;

	public FeatureInstantiator(VectorFactory vectorFactory, Dictionary<String> featureSet, List<FeatureProcessor> featureProcessors) {
		this.vectorFactory = vectorFactory;
		this.featureSet = featureSet;
		this.callback = new StandardFeatureInstantiatorCallback(featureSet);
		this.featureProcessors = featureProcessors;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("FeatureInstantiator.process()");
		if (!featureSet.isFrozen()) {
			throw new IllegalStateException("Cannot instantiate features until Dictionary is frozen");
		}
		Profiler.start("FeatureInstantiator.process()@create");
		// Set the feature vector for each segment
		for (Token token : input.getTokens()) {
			token.setFeatures(vectorFactory.create(featureSet));
		}
		for (Segment segment : input.getSegments()) {
			segment.setFeatures(vectorFactory.create(featureSet));
		}
		Profiler.stop("FeatureInstantiator.process()@create");
		// Instantiate the features
		for (FeatureProcessor featureProcessor : featureProcessors) {
			String featureProcessorName = featureProcessor.getClass().getName();
			Profiler.start("FeatureInstantiator.process()@" + featureProcessorName);
			featureProcessor.process(input, callback);
			Profiler.stop("FeatureInstantiator.process()@" + featureProcessorName);
		}
		Profiler.stop("FeatureInstantiator.process()");
	}

	private static class StandardFeatureInstantiatorCallback implements FeatureProcessorCallback {

		private static final long serialVersionUID = 1L;

		private Dictionary<String> featureSet;

		public StandardFeatureInstantiatorCallback(Dictionary<String> featureSet) {
			this.featureSet = featureSet;
		}

		@Override
		public void callback(String featureName, double featureValue, Vector<String> featureVector) {
			int index = featureSet.getIndex(featureName);
			// TODO Implement some sort of warning for unknown features
			if (index >= 0) {
				featureVector.increment(index, featureValue);
			}
		}
	}
}