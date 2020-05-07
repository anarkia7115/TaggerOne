package ncbi.taggerOne.lexicon;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.taggerOne.util.vector.Vector.VectorIterator;
import ncbi.taggerOne.util.vector.VectorFactory;
import ncbi.util.Profiler;

public class Index implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(Index.class);
	private static final long serialVersionUID = 1L;

	private Map<Vector<String>, Set<Entity>> vectorToEntityMap;
	private Entity unknownEntity;
	private Dictionary<Vector<String>> nameVectors;
	private Dictionary<String> mentionVectorSpace;
	private Dictionary<String> nameVectorSpace;
	protected int[] mentionIndexToNameIndex;
	private Comparator<Entity> entityComparator;
	private Dictionary<Entity> entityDictionary;

	public Index(Dictionary<String> mentionVectorSpace, Dictionary<String> nameVectorSpace, Set<Entity> entities, Entity unknownEntity) {
		if (mentionVectorSpace == null) {
			throw new IllegalArgumentException("mentionVectorSpace cannot be null");
		}
		if (nameVectorSpace == null) {
			throw new IllegalArgumentException("nameVectorSpace cannot be null");
		}
		if (entities == null) {
			throw new IllegalArgumentException("entities cannot be null");
		}
		if (unknownEntity == null) {
			throw new IllegalArgumentException("unknownEntity cannot be null");
		}
		this.mentionVectorSpace = mentionVectorSpace;
		this.nameVectorSpace = nameVectorSpace;
		// TODO Verify entities are all one type
		vectorToEntityMap = new HashMap<Vector<String>, Set<Entity>>();
		entityDictionary = new Dictionary<Entity>();
		for (Entity entity : entities) {
			entityDictionary.addElement(entity);
			for (MentionName name : entity.getNames()) {
				Vector<String> nameVector = name.getVector();
				if (nameVector != null) {
					Set<Entity> entitySet = vectorToEntityMap.get(nameVector);
					if (entitySet == null) {
						entitySet = new HashSet<Entity>();
						vectorToEntityMap.put(nameVector, entitySet);
					}
					entitySet.add(entity);
				}
			}
		}
		entityDictionary.freeze();
		List<Vector<String>> vectors = new ArrayList<Vector<String>>(vectorToEntityMap.keySet());
		nameVectors = new Dictionary<Vector<String>>();
		int ambiguous = 0;
		for (Vector<String> vector : vectors) {
			nameVectors.addElement(vector);
			if (vectorToEntityMap.get(vector).size() > 1) {
				ambiguous++;
			}
		}
		nameVectors.freeze();
		logger.info("Number of unique name vectors: " + nameVectors.size());
		logger.info("Number of ambiguous name vectors: " + ambiguous);
		this.unknownEntity = unknownEntity;
		entityComparator = null;
	}

	public Dictionary<String> getMentionVectorSpace() {
		return mentionVectorSpace;
	}

	public Dictionary<String> getNameVectorSpace() {
		return nameVectorSpace;
	}

	public Dictionary<Vector<String>> getNameVectorDictionary() {
		return nameVectors;
	}

	public Dictionary<Entity> getEntityDictionary() {
		return entityDictionary;
	}

	public void setEntityComparator(Comparator<Entity> entityComparator) {
		this.entityComparator = entityComparator;
	}

	public Set<Entity> getEntities(Vector<String> nameVector) {
		Set<Entity> entities = vectorToEntityMap.get(nameVector);
		if (entityComparator == null || entities == null) {
			return entities;
		}
		List<Entity> entityList = new ArrayList<Entity>(entities);
		Collections.sort(entityList, entityComparator);
		return new LinkedHashSet<Entity>(entityList);
	}

	public Entity getUnknownEntity() {
		return unknownEntity;
	}

	public Vector<String> convertMentionVectorToNameVectorEquivalent(Vector<String> mentionVector, VectorFactory factory) {
		if (mentionIndexToNameIndex == null) {
			// TODO Move this to constructor
			mentionIndexToNameIndex = new int[mentionVectorSpace.size()];
			Arrays.fill(mentionIndexToNameIndex, -1);
			Set<String> vectorSpaceElements = new HashSet<String>();
			vectorSpaceElements.addAll(mentionVectorSpace.getElements());
			vectorSpaceElements.addAll(nameVectorSpace.getElements());
			for (String element : vectorSpaceElements) {
				int mentionIndex = mentionVectorSpace.getIndex(element);
				int nameIndex = nameVectorSpace.getIndex(element);
				if (mentionIndex >= 0 && nameIndex >= 0) {
					mentionIndexToNameIndex[mentionIndex] = nameIndex;
				}
			}
		}
		Profiler.start("NormalizationModel.convertMentionVectorToNameVectorEquivalent()");
		Vector<String> nameVectorEquivalent = factory.create(nameVectorSpace);
		// Converts a mention vector to a name vector
		VectorIterator mentionIterator = mentionVector.getIterator();
		while (mentionIterator.next()) {
			int mentionIndex = mentionIterator.getIndex();
			double mentionValue = mentionIterator.getValue();
			int nameIndex = mentionIndexToNameIndex[mentionIndex];
			if (nameIndex >= 0) {
				nameVectorEquivalent.increment(nameIndex, mentionValue);
			}
		}
		Profiler.stop("NormalizationModel.convertMentionVectorToNameVectorEquivalent()");
		return nameVectorEquivalent;
	}

}
