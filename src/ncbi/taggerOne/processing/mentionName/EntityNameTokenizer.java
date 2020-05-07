package ncbi.taggerOne.processing.mentionName;

import java.util.ArrayList;
import java.util.List;

import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.tokenization.Tokenizer;

public class EntityNameTokenizer extends MentionNameProcessor {

	private static final long serialVersionUID = 1L;

	private Tokenizer tokenizer;

	public EntityNameTokenizer(Tokenizer tokenizer) {
		this.tokenizer = tokenizer;
	}

	@Override
	public void process(MentionName entityName) {
		if (entityName.isLabel()) {
			return;
		}
		String nameText = entityName.getName();
		List<String> tokens = new ArrayList<String>();
		tokenizer.reset(nameText);
		while (tokenizer.nextToken()) {
			tokens.add(nameText.substring(tokenizer.startChar(), tokenizer.endChar()));
		}
		entityName.setTokens(tokens);
	}
}
