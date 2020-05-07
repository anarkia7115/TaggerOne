package ncbi.taggerOne.lexicon;

import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;
import ncbi.util.SimpleComparator;

/* 
 * This class orders entities in order of decreasing frequency, typically according to training data. This is useful for a simple form of disambiguating between two entities that share a synonym. 
 */
public class EntityFrequencyComparator extends SimpleComparator<Entity> implements Serializable {

	private static final long serialVersionUID = 1L;

	private TObjectIntMap<Entity> entityFrequencies;

	public EntityFrequencyComparator() {
		entityFrequencies = new TObjectIntHashMap<Entity>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0);
	}

	public void updateFrequenciesFromTargetAnnotations(List<TextInstance> instances) {
		for (TextInstance instance : instances) {
			updateFrequencies(instance.getTargetAnnotation());
		}
	}

	public void updateFrequencies(List<AnnotatedSegment> targetAnnotations) {
		for (AnnotatedSegment segment : targetAnnotations) {
			if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
				Set<Entity> entities = segment.getEntities();
				for (Entity entitiy : entities) {
					updateFrequency(entitiy, 1);
				}
			}
		}
	}

	public void updateFrequency(Entity entity, int frequency) {
		entityFrequencies.adjustOrPutValue(entity, frequency, frequency);
	}

	@Override
	public int compare(Entity e1, Entity e2) {
		int f1 = entityFrequencies.get(e1);
		int f2 = entityFrequencies.get(e2);
		// TODO Detect and report remaining ambiguity
		return f2 - f1;
	}
}
