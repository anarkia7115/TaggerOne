package ncbi.taggerOne.processing.string;

import ncbi.taggerOne.util.tokenization.Tokenizer;

@Deprecated
public class PunctuationRemover implements StringProcessor {

	// TODO Replace this class with PatternProcessor
	
	private static final long serialVersionUID = 1L;

	private String replacement;

	public PunctuationRemover(String replacement) {
		this.replacement = replacement;
	}

	@Override
	public String process(String str) {
		return Tokenizer.punctPattern.matcher(str).replaceAll(replacement);
	}

}
