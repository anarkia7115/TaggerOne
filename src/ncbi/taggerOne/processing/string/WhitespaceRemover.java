package ncbi.taggerOne.processing.string;

import java.util.regex.Pattern;

@Deprecated
public class WhitespaceRemover implements StringProcessor {

	// TODO Replace this class with PatternProcessor
	
	private static final long serialVersionUID = 1L;

	private Pattern pattern = Pattern.compile("\\s");
	private String replacement;

	public WhitespaceRemover(String replacement) {
		this.replacement = replacement;
	}

	@Override
	public String process(String str) {
		return pattern.matcher(str).replaceAll(replacement);
	}

}
