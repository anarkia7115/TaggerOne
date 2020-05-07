package ncbi.taggerOne.lexicon.loader;

import ncbi.taggerOne.lexicon.LexiconMappings;

public interface LexiconMappingsLoader {

	public void setArgs(String... args);

	public void loadIdentifiers(LexiconMappings lexiconMappings);

	public void loadIdentifierEquivalencies(LexiconMappings lexiconMappings);

	public void loadNames(LexiconMappings lexiconMappings);

}
