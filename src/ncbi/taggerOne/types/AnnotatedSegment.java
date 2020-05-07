package ncbi.taggerOne.types;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ncbi.taggerOne.T1Constants;
import ncbi.util.StaticUtilMethods;

/*
 * Represents a span with annotations. These annotations can either be target or predicted.
 */
public class AnnotatedSegment extends Segment {

	protected String entityClass;
	protected Set<Entity> entities;

	public AnnotatedSegment(TextInstance text, int startChar, int endChar, List<Token> tokens, Set<Entity> entities) {
		super(text, startChar, endChar, tokens);
		if (entities == null || entities.size() == 0) {
			throw new IllegalArgumentException("Must provide at least one entity");
		}
		this.entityClass = entities.iterator().next().getType();
		setEntities(entities);
	}

	public AnnotatedSegment(TextInstance text, int startChar, int endChar, List<Token> tokens, String entityClass) {
		super(text, startChar, endChar, tokens);
		if (entityClass == null) {
			throw new IllegalArgumentException("Entity class cannot be null");
		}
		this.entityClass = entityClass;
		this.entities = null;
	}

	public String getEntityClass() {
		return entityClass;
	}

	public Set<Entity> getEntities() {
		return entities;
	}

	public void setEntities(Set<Entity> entities) {
		if (entities == null) {
			this.entities = null;
		} else {
			for (Entity entity : entities) {
				if (!entityClass.equals(entity.getType())) {
					throw new IllegalArgumentException("Type of all entities must match entity class of segment: " + entityClass + ", entity=" + entity.toString());
				}
			}
			// Make the entity set independent
			this.entities = new HashSet<Entity>(entities);
		}
	}

	public String visualizeEntitiesPrimaryIdentifiers() {
		List<String> entityIDs = new ArrayList<String>();
		for (Entity entity : entities) {
			entityIDs.add(entity.getPrimaryIdentifier());
		}
		return entityIDs.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((entities == null) ? 0 : entities.hashCode());
		result = prime * result + ((entityClass == null) ? 0 : entityClass.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		// FIXME
		// if (!super.equals(obj))
		// return false;
		if (getClass() != obj.getClass())
			return false;
		AnnotatedSegment other = (AnnotatedSegment) obj;
		// TODO These are the span checks
		if (startChar != other.startChar)
			return false;
		if (endChar != other.endChar)
			return false;
		if (!sourceText.equals(other.sourceText))
			return false;
		// TODO END These are the span checks
		if (!StaticUtilMethods.equalElements(entities, other.entities)) {
			return false;
		}
		if (!entityClass.equals(other.entityClass)) {
			return false;
		}
		return true;
	}

	public static String visualizeStates(List<AnnotatedSegment> stateSequence) {
		StringBuilder visualization = new StringBuilder();
		if (stateSequence == null) {
			visualization.append("null");
		} else {
			for (AnnotatedSegment segment : stateSequence) {
				visualization.append("(");
				visualization.append(segment.getText());
				visualization.append(")");
				visualization.append(segment.getEntityClass());
				if (!segment.getEntityClass().equals(T1Constants.NONENTITY_STATE)) {
					visualization.append("+");
					visualization.append(Entity.visualizePrimaryIdentifiers(segment.getEntities()));
				}
				visualization.append(" ");
			}
		}
		return visualization.toString().trim();
	}
}