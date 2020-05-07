package ncbi.taggerOne.processing.string;

import java.util.Locale;

public class LowerCaseStringProcessor implements StringProcessor {

	private static final long serialVersionUID = 1L;

	public LowerCaseStringProcessor() {
		// Empty
	}

	@Override
	public String process(String str) {
		return str.toLowerCase(Locale.US);
	}

}
