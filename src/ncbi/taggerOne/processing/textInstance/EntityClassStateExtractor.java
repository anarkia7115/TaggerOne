package ncbi.taggerOne.processing.textInstance;

import java.util.List;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.Dictionary;

public class EntityClassStateExtractor extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	private Dictionary<String> classes;

	public EntityClassStateExtractor(Dictionary<String> classes) {
		if (classes.isFrozen()) {
			throw new IllegalStateException("Cannot extract classes after Dictionary is frozen");
		}
		this.classes = classes;
		classes.addElement(T1Constants.NONENTITY_STATE);
	}

	@Override
	public void process(TextInstance input) {
		if (classes.isFrozen()) {
			throw new IllegalStateException("Cannot extract classes after Dictionary is frozen");
		}

		// Extract the entity classes
		List<AnnotatedSegment> annotations = input.getTargetAnnotation();
		for (AnnotatedSegment segment : annotations) {
			classes.addElement(segment.getEntityClass());
		}
	}
}
