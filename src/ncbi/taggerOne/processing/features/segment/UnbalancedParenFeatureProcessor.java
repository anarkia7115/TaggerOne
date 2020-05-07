package ncbi.taggerOne.processing.features.segment;

import java.util.List;
import java.util.Stack;

import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessorCallback;
import ncbi.taggerOne.types.Segment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.vector.Vector;

public class UnbalancedParenFeatureProcessor implements FeatureProcessor {

	private static final long serialVersionUID = 1L;

	private String featureName;

	public UnbalancedParenFeatureProcessor(String featureName) {
		this.featureName = featureName;
	}

	@Override
	public void process(TextInstance input, FeatureProcessorCallback featureProcessorCallback) {
		List<Segment> segments = input.getSegments();
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			// A single token is allowed to be unbalanced
			if (segment.getTokens().size() > 1 && !isBalanced(segment.getText())) {
				Vector<String> featureVector = segment.getFeatures();
				featureProcessorCallback.callback(featureName, 1.0, featureVector);
			}
		}
	}

	// TODO PERFORMANCE Profile this version versus using chars and a StringBuilder
	public static boolean isBalanced(String s) {
		Stack<Character> stack = new Stack<Character>();
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '(')
				stack.push('(');
			else if (s.charAt(i) == '{')
				stack.push('{');
			else if (s.charAt(i) == '[')
				stack.push('[');
			else if (s.charAt(i) == ')') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '(')
					return false;
			} else if (s.charAt(i) == '}') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '{')
					return false;
			} else if (s.charAt(i) == ']') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '[')
					return false;
			}
			// ignore all other characters
		}
		return stack.isEmpty();
	}
}
