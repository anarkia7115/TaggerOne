package ncbi.taggerOne.processing.textInstance;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.util.Profiler;

/*
 * Extracts the set of features for TextInstances
 */
public class FeatureSetExtractor extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	private Dictionary<String> featureSet;
	private List<FeatureProcessor> featureProcessors;
	private FeatureProcessorCallback callback;

	public FeatureSetExtractor(Dictionary<String> featureSet, List<FeatureProcessor> featureProcessors) {
		this.featureSet = featureSet;
		this.callback = new FeatureSetExtractorCallback(featureSet);
		this.featureProcessors = featureProcessors;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("FeatureSetExtractor.process()");
		if (featureSet.isFrozen()) {
			throw new IllegalStateException("Cannot extract features after Dictionary is frozen");
		}
		for (FeatureProcessor featureProcessor : featureProcessors) {
			String featureProcessorName = featureProcessor.getClass().getName();
			Profiler.start("FeatureSetExtractor.process()@" + featureProcessorName);
			featureProcessor.process(input, callback);
			Profiler.stop("FeatureSetExtractor.process()@" + featureProcessorName);
		}
		Profiler.stop("FeatureSetExtractor.process()");
	}

	private class FeatureSetExtractorCallback implements FeatureProcessorCallback {

		private static final long serialVersionUID = 1L;

		private Dictionary<String> featureSet;

		public FeatureSetExtractorCallback(Dictionary<String> featureSet) {
			this.featureSet = featureSet;
		}

		@Override
		public void callback(String featureName, double featureValue, Vector<String> featureVector) {
			featureSet.addElement(featureName);
		}

	}
}