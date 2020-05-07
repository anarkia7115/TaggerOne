package ncbi.taggerOne.processing.string;

import java.util.regex.Pattern;

@Deprecated
public class NumberClassStringProcessor implements StringProcessor {

	// TODO Replace this class with PatternProcessor
	
	private static final long serialVersionUID = 1L;

	private boolean brief;

	private Pattern p;

	public NumberClassStringProcessor(boolean brief) {
		this.brief = brief;
	}

	@Override
	public String process(String str) {
		// TODO REFACTOR into a generic pattern-application class, with predefined examples
		if (p == null) {
			if (brief) {
				p = Pattern.compile("[0-9]+");
			} else {
				p = Pattern.compile("[0-9]");
			}
		}
		return p.matcher(str).replaceAll("0");
	}

}
