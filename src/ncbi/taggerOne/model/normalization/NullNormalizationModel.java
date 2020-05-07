package ncbi.taggerOne.model.normalization;

import ncbi.taggerOne.lexicon.Index;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.matrix.Matrix;
import ncbi.taggerOne.util.vector.Vector;

public class NullNormalizationModel implements NormalizationModelPredictor, NormalizationModelUpdater {

	private static final long serialVersionUID = 1L;

	private Entity unknownEntity;

	public NullNormalizationModel(Index index) {
		unknownEntity = index.getUnknownEntity();
	}

	@Override
	public void update(double cosineSimWeight, Matrix<String, String> weights) {
		// Empty
	}

	@Override
	public double getScoreBound(Vector<String> mentionVector) {
		return 0.0;
	}

	@Override
	public double getWeight(int mentionIndex, int nameIndex) {
		return 0.0;
	}

	@Override
	public void findBest(Vector<String> mentionVector, RankedList<Entity> bestEntities) {
		bestEntities.add(0.0, unknownEntity);
	}

	@Override
	public MentionName findBestName(Vector<String> mentionVector, Entity entity) {
		return entity.getPrimaryName();
	}

	@Override
	public double scoreEntity(Vector<String> mentionVector, Entity entity) {
		return 0.0;
	}

	@Override
	public double scoreNameVector(Vector<String> mentionVector, Vector<String> nameVector) {
		return 0.0;
	}

	@Override
	public void visualizeScore(Vector<String> mentionVector, Vector<String> nameVector) {
		// Empty
	}

	@Override
	public NormalizationModelPredictor compile() {
		return this;
	}

	@Override
	public double getCosineSimWeight() {
		return 0.0;
	}

}
