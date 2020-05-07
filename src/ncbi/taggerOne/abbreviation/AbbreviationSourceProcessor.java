package ncbi.taggerOne.abbreviation;

import java.util.List;
import java.util.Map;

import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.AbbreviationResolver;
import ncbi.util.Profiler;

public class AbbreviationSourceProcessor extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	private List<AbbreviationSource> abbreviationSourceList;
	private AbbreviationResolver resolver;

	public AbbreviationSourceProcessor(List<AbbreviationSource> abbreviationSourceList, AbbreviationResolver resolver) {
		super();
		this.abbreviationSourceList = abbreviationSourceList;
		this.resolver = resolver;
	}

	@Override
	public void process(TextInstance input) {
		Profiler.start("AbbreviationSourceProcessor.process()");
		String sourceId = input.getSourceId();
		for (AbbreviationSource source : abbreviationSourceList) {
			Map<String, String> abbreviations = source.getAbbreviations(sourceId, input.getText());
			resolver.addAbbreviations(sourceId, abbreviations);
		}
		Profiler.stop("AbbreviationSourceProcessor.process()");
	}

}
