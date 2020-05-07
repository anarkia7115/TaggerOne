package ncbi.taggerOne.processing.textInstance;

import java.io.Serializable;
import java.util.List;

import ncbi.taggerOne.types.TextInstance;

public abstract class TextInstanceProcessor implements Serializable {

	private static final long serialVersionUID = 1L;

	public TextInstanceProcessor() {
		// Empty
	}

	public abstract void process(TextInstance input);

	public void processAll(List<TextInstance> input) {
		for (TextInstance instance : input) {
			process(instance);
		}
	}

	public void reset() {
		// Empty
	}

}