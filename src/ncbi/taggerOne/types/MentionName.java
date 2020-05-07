package ncbi.taggerOne.types;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import ncbi.taggerOne.util.vector.Vector;

public class MentionName implements Serializable {

	private static final long serialVersionUID = 1L;

	private boolean isLabel;
	private String name;
	private List<String> tokens;
	private Vector<String> vector;

	public MentionName(String name) {
		this(false, name);
	}

	public MentionName(boolean isLabel, String name) {
		if (name == null) {
			throw new IllegalArgumentException("Name cannot be null");
		}
		this.name = name;
		this.isLabel = isLabel;
		if (isLabel) {
			tokens = Collections.singletonList(name);
		}
		this.vector = null;
	}

	public boolean isLabel() {
		return isLabel;
	}

	public List<String> getTokens() {
		return tokens;
	}

	public void setTokens(List<String> tokens) {
		if (isLabel) {
			throw new IllegalArgumentException("MentionNames marked as labels cannot be modified");
		}
		this.tokens = tokens;
	}

	public Vector<String> getVector() {
		return vector;
	}

	public void setVector(Vector<String> vector) {
		this.vector = vector;
	}

	public void setName(String name) {
		if (isLabel) {
			throw new IllegalArgumentException("MentionNames marked as labels cannot be modified");
		}
		this.name = name;
	}

	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (isLabel ? 1231 : 1237);
		result = prime * result + name.hashCode();
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
		MentionName other = (MentionName) obj;
		if (isLabel != other.isLabel)
			return false;
		if (!name.equals(other.name))
			return false;
		return true;
	}

}
