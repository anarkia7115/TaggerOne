package ncbi.taggerOne.processing.features.segment;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.util.Profiler;

public class SegmentPatternFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String featureName;
	private Pattern pattern;
	private transient MatcherThreadLocal matcherThreadLocal;

	// Performance note: Reusing the matcher is only very slightly faster 
	// Performance note: It is faster to use only one pattern per instance than to set up all patterns in a single instance. The reason is unclear, but may have to do with JIT inlining.

	private class MatcherThreadLocal extends ThreadLocal<Matcher> {

		private Pattern pattern;

		public MatcherThreadLocal(Pattern pattern) {
			this.pattern = pattern;
		}

		@Override
		protected Matcher initialValue() {
			return pattern.matcher("");
		}
	}

	public SegmentPatternFeatureProcessor(String prefix, Pattern pattern) {
		this.featureName = prefix;
		this.pattern = pattern;
		initThreadLocals();
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		initThreadLocals();
	}

	private void initThreadLocals() {
		this.matcherThreadLocal = new MatcherThreadLocal(this.pattern);
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		Profiler.start("SegmentPatternFeatureProcessor.process()@featureName=" + featureName);
		List<Segment> segments = input.getSegments();
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			Matcher m = matcherThreadLocal.get().reset(segment.getText());
			if (m.matches()) {
				Vector<String> featureVector = segment.getFeatures();
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}
		}
		Profiler.stop("SegmentPatternFeatureProcessor.process()@featureName=" + featureName);
	}
}
