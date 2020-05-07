package ncbi.taggerOne.model.normalization;

import java.io.Serializable;

import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.vector.Vector;

public interface NormalizationModelPredictor extends Serializable {

	public double getScoreBound(Vector<String> mentionVector);

	public void findBest(Vector<String> mentionVector, RankedList<Entity> bestEntities);

	public MentionName findBestName(Vector<String> mentionVector, Entity entity);

	public double scoreEntity(Vector<String> mentionVector, Entity entity);

	public double scoreNameVector(Vector<String> mentionVector, Vector<String> nameVector);

	// public void visualize();

	public void visualizeScore(Vector<String> mentionVector, Vector<String> nameVector);

	public NormalizationModelPredictor compile();

}
