package ncbi.taggerOne.processing.textInstance;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.processing.TrainingProgressTracker;
import ncbi.taggerOne.processing.stoppingCriteria.StoppingCriteria;
import ncbi.taggerOne.types.TextInstance;
import ncbi.util.Profiler;
import ncbi.util.SimpleComparator;

public class AnnotationModelTrainer extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(AnnotationModelTrainer.class);
	public static final int[] DEFAULT_HASH_SEEDS = { 1758616143, -2030729035, 1541601825, -459744459, 1856439628, 1300393201, -1256964498, -1352518603, 1521303043, 1428983660, 363908407, 1578659438,
			-432498204, 1039836170, -198760098, 1011801700, 29212369, 1537831493, 1323612324, 261725082 };

	private static final long serialVersionUID = 1L;

	private TextInstanceProcessor trainingProcessor;
	private StoppingCriteria stoppingCriteria;
	private TrainingProgressTracker callback;
	private Comparator<TextInstance> instanceOrdering;

	public AnnotationModelTrainer(TextInstanceProcessor trainingProcessor, StoppingCriteria stoppingCriteria, TrainingProgressTracker callback) {
		this(trainingProcessor, stoppingCriteria, callback, null);
	}

	public AnnotationModelTrainer(TextInstanceProcessor trainingProcessor, StoppingCriteria stoppingCriteria, TrainingProgressTracker callback, Comparator<TextInstance> instanceOrdering) {
		this.trainingProcessor = trainingProcessor;
		this.stoppingCriteria = stoppingCriteria;
		this.callback = callback;
		this.instanceOrdering = instanceOrdering;
	}

	@Override
	public void process(TextInstance input) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public void processAll(List<TextInstance> input) {
		List<TextInstance> input2 = new ArrayList<TextInstance>(input);
		boolean stop = stoppingCriteria.stop();
		while (!stop) {
			callback.resetUpdates();
			if (instanceOrdering == null) {
				Collections.shuffle(input2);
			} else {
				Collections.sort(input2, instanceOrdering);
			}
			Profiler.start("AnnotationModelTrainer.processAll():training");
			trainingProcessor.processAll(input2);
			Profiler.stop("AnnotationModelTrainer.processAll():training");

			callback.incrementIteration();
			int iteration = callback.getIteration();
			// TODO Would reporting the training set hinge loss also be informative?
			logger.info("Iteration " + iteration + ", updates = " + callback.getUpdates() + "/" + input2.size());
			Profiler.print("\t");
			Profiler.start("AnnotationModelTrainer.processAll():stop");
			stop = stoppingCriteria.stop();
			Profiler.stop("AnnotationModelTrainer.processAll():stop");
		}
	}

	public static class DeterministicShuffler extends SimpleComparator<TextInstance> {

		private static final long serialVersionUID = 1L;

		private MessageDigest messageDigest;
		private TrainingProgressTracker callback;
		private int[] hashSeeds;

		public DeterministicShuffler(TrainingProgressTracker callback, int[] hashSeeds) {
			try {
				messageDigest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
			this.callback = callback;
			this.hashSeeds = hashSeeds;
		}

		@Override
		public int compare(TextInstance input1, TextInstance input2) {
			int hashSeedIndex = callback.getIteration() % hashSeeds.length;
			int hashSeed = hashSeeds[hashSeedIndex];
			String str1 = (new StringBuilder(input1.getInstanceId())).reverse().toString();
			String str2 = (new StringBuilder(input2.getInstanceId())).reverse().toString();
			long hash1 = getMD5Hash(str1) % hashSeed;
			long hash2 = getMD5Hash(str2) % hashSeed;
			if (hash1 != hash2) {
				return Long.compare(hash1, hash2);
			}
			hash1 = getMD5Hash(input1.getText()) ^ hashSeed;
			hash2 = getMD5Hash(input2.getText()) ^ hashSeed;
			if (hash1 != hash2) {
				return Long.compare(hash1, hash2);
			}
			return input1.getText().compareTo(input2.getText());
		}

		private long getMD5Hash(String str) {
			try {
				messageDigest.reset();
				messageDigest.update(str.getBytes(T1Constants.UTF8_FORMAT));
				byte[] digest = messageDigest.digest();
				BigInteger bigInt = new BigInteger(1, digest);
				return Math.abs(bigInt.longValue());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
