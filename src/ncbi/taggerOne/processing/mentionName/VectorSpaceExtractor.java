package ncbi.taggerOne.processing.mentionName;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;

public class VectorSpaceExtractor extends MentionNameProcessor {

	private static final Logger logger = LoggerFactory.getLogger(VectorSpaceExtractor.class);
	private static final long serialVersionUID = 1L;
	private static final Pattern whiteSpacePattern = Pattern.compile("\\s");

	private Dictionary<String> vectorSpace;

	public VectorSpaceExtractor(Dictionary<String> featureSet) {
		this.vectorSpace = featureSet;
	}

	@Override
	public void process(MentionName entityName) {
		for (String token : entityName.getTokens()) {
			vectorSpace.addElement(token);
			Matcher matcher = whiteSpacePattern.matcher(token);
			if (matcher.find()) {
				logger.error("Processing name \"" + entityName.getName() + "\" resulted in a token containing whitespace: \"" + token + "\"");
			}

		}
	}
}
