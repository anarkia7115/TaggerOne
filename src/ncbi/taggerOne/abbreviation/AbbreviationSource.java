package ncbi.taggerOne.abbreviation;

import java.io.Serializable;
import java.util.Map;

public interface AbbreviationSource extends Serializable {

	public void setArgs(String... args);

	public Map<String, String> getAbbreviations(String id, String text);

}
