package ncbi.taggerOne.processing.mentionName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import ncbi.taggerOne.processing.string.StringProcessor;
import ncbi.taggerOne.types.MentionName;
import ncbi.util.Profiler;

public class StringProcessTokenApplicator extends MentionNameProcessor {

	private static final long serialVersionUID = 1L;

	private List<StringProcessor> processors;

	public StringProcessTokenApplicator(List<StringProcessor> processors) {
		this.processors = processors;
	}

	public StringProcessTokenApplicator(StringProcessor... processors) {
		this(Arrays.asList(processors));
	}

	public void processOLD(MentionName entityName) {
		Profiler.start("StringProcessTokenApplicator.process()");
		if (entityName.isLabel()) {
			Profiler.stop("StringProcessTokenApplicator.process()");
			return;
		}
		List<String> tokensCopy = new ArrayList<String>(entityName.getTokens());
		for (StringProcessor p : processors) {
			String mentionProcessorName = p.getClass().getName();
			Profiler.start("StringProcessTokenApplicator.process()@" + mentionProcessorName);
			for (int i = 0; i < tokensCopy.size(); i++) {
				String token = tokensCopy.get(i);
				token = p.process(token);
				tokensCopy.set(i, token);
			}
			Profiler.stop("StringProcessTokenApplicator.process()@" + mentionProcessorName);
		}
		List<String> newTokens = new ArrayList<String>();
		for (String token : tokensCopy) {
			String newToken = token;
			if (newToken.length() > 0) {
				newTokens.add(newToken);
			}
		}
		entityName.setTokens(newTokens);
		Profiler.stop("StringProcessTokenApplicator.process()");
	}

	@Override
	public void process(MentionName entityName) {
		Profiler.start("StringProcessTokenApplicator.process()");
		if (entityName.isLabel()) {
			Profiler.stop("StringProcessTokenApplicator.process()");
			return;
		}
		List<String> tokensCopy = new LinkedList<String>(entityName.getTokens());
		for (StringProcessor p : processors) {
			String mentionProcessorName = p.getClass().getName();
			Profiler.start("StringProcessTokenApplicator.process()@" + mentionProcessorName);
			ListIterator<String> iterator = tokensCopy.listIterator();
			while (iterator.hasNext()) {
				String token = p.process(iterator.next());
				if (token.length() > 0) {
					iterator.set(token);
				} else {
					iterator.remove();
				}
			}
			Profiler.stop("StringProcessTokenApplicator.process()@" + mentionProcessorName);
		}
		entityName.setTokens(tokensCopy);
		Profiler.stop("StringProcessTokenApplicator.process()");
	}
}
