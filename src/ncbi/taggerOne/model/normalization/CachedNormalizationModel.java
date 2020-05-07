package ncbi.taggerOne.model.normalization;

import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.util.LRUCache;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.matrix.Matrix;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.util.Profiler;

public class CachedNormalizationModel implements NormalizationModelPredictor, NormalizationModelUpdater {

	private static final long serialVersionUID = 1L;

	private NormalizationModelPredictor wrappedPredictor;
	private NormalizationModelUpdater wrappedUpdater;
	private LRUCache<Vector<String>, RankedList<Entity>> cache;

	// TODO PERFORMANCE Cache the score bound

	public CachedNormalizationModel(NormalizationModelPredictor wrappedPredictor, int maxCacheSize) {
		this(wrappedPredictor, null, maxCacheSize);
	}

	public CachedNormalizationModel(NormalizationModelPredictor wrappedPredictor, NormalizationModelUpdater wrappedUpdater, int maxCacheSize) {
		if (wrappedUpdater != null && wrappedPredictor instanceof AveragedNormalizationModel) {
			throw new IllegalArgumentException("wrappedUpdater should be null if not training, otherwise wrappedPredictor should be the raw version (not averaged)");
		}
		this.wrappedPredictor = wrappedPredictor;
		this.wrappedUpdater = wrappedUpdater;
		cache = new LRUCache<Vector<String>, RankedList<Entity>>(LRUCache.DEFAULT_CAPACITY, LRUCache.DEFAULT_LOAD_FACTOR, maxCacheSize);
	}

	@Override
	public NormalizationModelPredictor compile() {
		cache.clear();
		wrappedPredictor = wrappedPredictor.compile();
		return this;
	}

	public NormalizationModelPredictor getWrappedPredictor() {
		return wrappedPredictor;
	}

	public NormalizationModelUpdater getWrappedUpdater() {
		return wrappedUpdater;
	}

	public int getMaxCacheSize() {
		return cache.getMaxSize();
	}

	@Override
	public void update(double cosineSimWeight, Matrix<String, String> weights) {
		cache.clear();
		wrappedUpdater.update(cosineSimWeight, weights);
	}

	@Override
	public double getScoreBound(Vector<String> mentionVector) {
		// If mentionVector is present in cache, use it as the highest score
		double highest = Double.NEGATIVE_INFINITY;
		RankedList<Entity> cachedEntities = cache.get(mentionVector);
		if (cachedEntities == null) {
			// TODO PERFORMANCE Add a cache for the scoreBound times
			Profiler.start("CachedNormalizationModel.getScoreBound()@wrapped");
			highest = wrappedPredictor.getScoreBound(mentionVector);
			Profiler.stop("CachedNormalizationModel.getScoreBound()@wrapped");
		} else {
			Profiler.start("CachedNormalizationModel.getScoreBound()@cache");
			highest = cachedEntities.getValue(0);
			Profiler.stop("CachedNormalizationModel.getScoreBound()@cache");
		}
		return highest;
	}

	@Override
	public double getCosineSimWeight() {
		return wrappedUpdater.getCosineSimWeight();
	}

	@Override
	public double getWeight(int mentionIndex, int nameIndex) {
		return wrappedUpdater.getWeight(mentionIndex, nameIndex);
	}

	public void clearCache() {
		cache.clear();
	}

	@Override
	public void findBest(Vector<String> mentionVector, RankedList<Entity> bestEntities) {
		RankedList<Entity> cachedEntities = cache.get(mentionVector);
		if (cachedEntities == null) {
			Profiler.start("CachedNormalizationModel.findBest()@wrapped");
			wrappedPredictor.findBest(mentionVector, bestEntities);
			cache.put(mentionVector, bestEntities);
			Profiler.stop("CachedNormalizationModel.findBest()@wrapped");
		} else if (cachedEntities.maxSize() < bestEntities.maxSize()) {
			Profiler.start("CachedNormalizationModel.findBest()@expand");
			wrappedPredictor.findBest(mentionVector, bestEntities);
			cache.put(mentionVector, bestEntities);
			Profiler.stop("CachedNormalizationModel.findBest()@expand");
		} else {
			Profiler.start("CachedNormalizationModel.findBest()@cache");
			for (int i = 0; i < cachedEntities.size(); i++) {
				bestEntities.add(cachedEntities.getValue(i), cachedEntities.getObject(i));
			}
			Profiler.stop("CachedNormalizationModel.findBest()@cache");
		}
	}

	@Override
	public MentionName findBestName(Vector<String> mentionVector, Entity entity) {
		return wrappedPredictor.findBestName(mentionVector, entity);
	}

	@Override
	public double scoreEntity(Vector<String> mentionVector, Entity entity) {
		return wrappedPredictor.scoreEntity(mentionVector, entity);
	}

	@Override
	public double scoreNameVector(Vector<String> mentionVector, Vector<String> nameVector) {
		return wrappedPredictor.scoreNameVector(mentionVector, nameVector);
	}

	@Override
	public void visualizeScore(Vector<String> mentionVector, Vector<String> nameVector) {
		wrappedPredictor.visualizeScore(mentionVector, nameVector);
	}
}
