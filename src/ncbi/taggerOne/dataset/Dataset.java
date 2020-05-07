package ncbi.taggerOne.dataset;

import java.util.List;

import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.types.TextInstance;

public interface Dataset {

	// TODO How to handle a dataset that will not all fit in memory?

	public void setArgs(Lexicon lexicon, String... args);

	public List<TextInstance> getInstances();

	public enum Usage {
		IDENTIFY, RECOGNIZE, IGNORE;
	}

}
