package ncbi.taggerOne.types;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Entity implements Serializable {

	private static final long serialVersionUID = 1L;

	private String type;
	private String primaryIdentifier;
	private Set<String> identifiers;
	private MentionName primaryName;
	private Set<MentionName> names;

	public Entity(String type, String primaryIdentifier, MentionName primaryName) {
		if (type == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		this.type = type;
		if (primaryIdentifier == null) {
			throw new IllegalArgumentException("primaryIdentifier cannot be null");
		}
		this.primaryIdentifier = primaryIdentifier;
		identifiers = new HashSet<String>();
		identifiers.add(primaryIdentifier);
		if (primaryName == null) {
			throw new IllegalArgumentException("primaryName cannot be null");
		}
		this.primaryName = primaryName;
		names = new HashSet<MentionName>();
		names.add(primaryName);
	}

	public void addIdentifiers(Set<String> identifiers) {
		this.identifiers.addAll(identifiers);
	}

	public void addNames(Set<MentionName> names) {
		this.names.addAll(names);
	}

	public String getType() {
		return type;
	}

	public String getPrimaryIdentifier() {
		return primaryIdentifier;
	}

	public Set<String> getIdentifiers() {
		return identifiers;
	}

	public MentionName getPrimaryName() {
		return primaryName;
	}

	public Set<MentionName> getNames() {
		return names;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + type.hashCode();
		result = prime * result + identifiers.hashCode();
		result = prime * result + names.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Entity other = (Entity) obj;
		if (!type.equals(other.type))
			return false;
		if (!identifiers.equals(other.identifiers))
			return false;
		if (!names.equals(other.names))
			return false;
		return true;
	}

	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(getType());
		str.append('-');
		str.append(getPrimaryIdentifier());
		str.append('-');
		str.append(getPrimaryName().getName());
		return str.toString();
	}

	public String visualize() {
		StringBuilder str = new StringBuilder();
		str.append(getType());
		str.append('\t');
		str.append(getPrimaryIdentifier());
		str.append('\t');
		str.append(getPrimaryName().getName());
		str.append('\t');
		List<String> identifiers = new ArrayList<String>(getIdentifiers());
		Collections.sort(identifiers);
		str.append(identifiers);
		str.append('\t');
		List<String> names = new ArrayList<String>();
		for (MentionName name : getNames()) {
			names.add(name.getName());
		}
		Collections.sort(names);
		str.append(names);
		return str.toString();
	}

	public static String visualizePrimaryIdentifiers(Set<Entity> entities) {
		if (entities == null) {
			return "";
		}
		List<String> entityIDs = new ArrayList<String>();
		for (Entity entity : entities) {
			if (entity != null) {
				entityIDs.add(entity.getPrimaryIdentifier());
			}
		}
		Collections.sort(entityIDs);
		StringBuilder identifiers = new StringBuilder();
		identifiers.append(entityIDs.get(0));
		for (int i = 1; i < entityIDs.size(); i++) {
			identifiers.append("|");
			identifiers.append(entityIDs.get(i));
		}
		return identifiers.toString();
	}
}
