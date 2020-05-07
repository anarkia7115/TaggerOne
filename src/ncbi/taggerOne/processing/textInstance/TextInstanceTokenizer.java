package ncbi.taggerOne.processing.textInstance;

import java.util.ArrayList;
import java.util.List;

import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.types.Token;
import ncbi.taggerOne.util.tokenization.Tokenizer;
import ncbi.util.Profiler;

/*
 * Converts an input text into individual tokens.
 */
public class TextInstanceTokenizer extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	private Tokenizer tokenizer;

	public TextInstanceTokenizer(Tokenizer tokenizer) {
		this.tokenizer = tokenizer;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("TextInstanceTokenizer.process()");
		String text = input.getText();
		tokenizer.reset(text);
		List<Token> tokens = new ArrayList<Token>();
		int index = 0;
		while (tokenizer.nextToken()) {
			Token token = new Token(input, tokenizer.startChar(), tokenizer.endChar(), index);
			tokens.add(token);
			index++;
		}
		input.setTokens(tokens);
		Profiler.stop("TextInstanceTokenizer.process()");
	}
}
