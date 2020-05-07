package ncbi.taggerOne.processing.features.segment;

import java.util.List;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.processing.string.StringProcessor;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.vector.Vector;

public class SurroundingCharactersFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String prefix;
	private StringProcessor stringProcessor;

	public SurroundingCharactersFeatureProcessor(String prefix, StringProcessor stringProcessor) {
		this.prefix = prefix;
		this.stringProcessor = stringProcessor;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		String inputText = input.getText();
		List<Segment> segments = input.getSegments();
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			Vector<String> featureVector = segment.getFeatures();

			// Handle left
			int lcharIndex = segment.getStartChar() - 1;
			String lchar = "S";
			if (lcharIndex >= 0) {
				lchar = stringProcessor.process(inputText.substring(lcharIndex, lcharIndex + 1));
			}
			featureProcessorCallback.callback(prefix + "L=" + lchar, 1.0, featureVector);

			// Handle right
			int rcharIndex = segment.getEndChar();
			String rchar = "E";
			if (rcharIndex < inputText.length()) {
				rchar = stringProcessor.process(inputText.substring(rcharIndex, rcharIndex + 1));
			}
			featureProcessorCallback.callback(prefix + "R=" + rchar, 1.0, featureVector);
		}
	}

}
