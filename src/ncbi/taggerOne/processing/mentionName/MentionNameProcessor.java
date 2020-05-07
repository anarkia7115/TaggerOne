package ncbi.taggerOne.processing.mentionName;

import java.io.Serializable;

import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;

public abstract class MentionNameProcessor implements Serializable {

	private static final long serialVersionUID = 1L;

	public MentionNameProcessor() {
		// Empty
	}

	public void process(String entityType, Lexicon lexicon) {
		for (Entity entity : lexicon.getEntities(entityType)) {
			process(entity);
		}
	}

	public void process(Entity entity) {
		for (MentionName name : entity.getNames()) {
			process(name);
		}
	}

	public abstract void process(MentionName entityName);

}
