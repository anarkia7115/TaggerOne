package ncbi.taggerOne.lexicon;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;

public class Lexicon implements Serializable {

	private static final long serialVersionUID = 1L;

	private Entity nonEntity;
	private Map<String, Set<Entity>> typeToEntities;
	private Map<String, Entity> identifierToEntity;
	private Map<String, Entity> unknownEntities;
	private Map<String, Index> typeToIndex;

	public Lexicon(Dictionary<String> types) {
		if (!types.isFrozen()) {
			throw new IllegalStateException("Type dictionary must first be frozen");
		}
		if (types.getIndex(T1Constants.NONENTITY_STATE) >= 0) {
			throw new IllegalArgumentException("Type dictionary may not contain nonentity type");
		}
		typeToEntities = new HashMap<String, Set<Entity>>();
		identifierToEntity = new HashMap<String, Entity>();

		// Add non-entity
		nonEntity = new Entity(T1Constants.NONENTITY_STATE, T1Constants.NONENTITY_STATE, new MentionName(true, T1Constants.NON_ENTITY_NAME_TOKEN));
		typeToEntities.put(T1Constants.NONENTITY_STATE, new HashSet<Entity>());
		addEntity(nonEntity);

		// Add unknown entities (type but no identity)
		unknownEntities = new HashMap<String, Entity>();
		for (int typeIndex = 0; typeIndex < types.size(); typeIndex++) {
			String type = types.getElement(typeIndex);
			typeToEntities.put(type, new HashSet<Entity>());

			// Add the unknown entity
			Entity unknownEntity = new Entity(type, T1Constants.UNKNOWN_ENTITY_ID_PREFIX + type, new MentionName(true, T1Constants.UNKNOWN_ENTITY_NAME_TOKEN));
			unknownEntities.put(type, unknownEntity);
			addEntity(unknownEntity);
		}
	}

	public Entity getNonEntity() {
		return nonEntity;
	}

	public Entity getUnknownEntity(String type) {
		return unknownEntities.get(type);
	}

	public void addEntity(Entity entity) {
		if (typeToIndex != null) {
			throw new IllegalStateException("Cannot add entities after index creation");
		}
		String type = entity.getType();
		typeToEntities.get(type).add(entity);
		for (String identifier : entity.getIdentifiers()) {
			if (identifierToEntity.containsKey(identifier)) {
				throw new IllegalArgumentException("Lexicon already contains identifier " + identifier);
			}
			identifierToEntity.put(identifier, entity);
		}
	}

	public Set<String> getTypes() {
		return typeToEntities.keySet();
	}

	public Set<Entity> getEntities(String type) {
		return typeToEntities.get(type);
	}

	public Entity getEntity(String identifier) {
		return identifierToEntity.get(identifier);
	}

	public void createIndexes(Dictionary<String> mentionVectorSpace, Map<String, Dictionary<String>> nameVectorSpaces) {
		if (typeToIndex != null) {
			return;
		}
		typeToIndex = new HashMap<String, Index>();
		for (String type : typeToEntities.keySet()) {
			if (!type.equals(T1Constants.NONENTITY_STATE)) {
				Dictionary<String> nameVectorSpace = nameVectorSpaces.get(type);
				Index index = new Index(mentionVectorSpace, nameVectorSpace, typeToEntities.get(type), unknownEntities.get(type));
				typeToIndex.put(type, index);
			}
		}
	}

	public Index getIndex(String type) {
		if (typeToIndex == null) {
			throw new IllegalStateException("Must first create indexes");
		}
		return typeToIndex.get(type);
	}
}
