package ncbi.taggerOne.processing.string;

public class Trimmer implements StringProcessor {

	private static final long serialVersionUID = 1L;

	public Trimmer() {
		// Empty
	}

	@Override
	public String process(String str) {
		return str.trim();
	}

}
