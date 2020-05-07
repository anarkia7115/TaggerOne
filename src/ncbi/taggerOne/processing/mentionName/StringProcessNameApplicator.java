package ncbi.taggerOne.processing.mentionName;

import java.util.Arrays;
import java.util.List;

import ncbi.taggerOne.processing.string.StringProcessor;
import ncbi.taggerOne.types.MentionName;

public class StringProcessNameApplicator extends MentionNameProcessor {

	private static final long serialVersionUID = 1L;

	private List<StringProcessor> processors;

	public StringProcessNameApplicator(List<StringProcessor> processors) {
		this.processors = processors;
	}

	public StringProcessNameApplicator(StringProcessor... processors) {
		this(Arrays.asList(processors));
	}

	@Override
	public void process(MentionName entityName) {
		if (entityName.isLabel()) {
			return;
		}
		String name = entityName.getName();
		for (StringProcessor p : processors) {
			name = p.process(name);
		}
		entityName.setName(name);
	}
}
