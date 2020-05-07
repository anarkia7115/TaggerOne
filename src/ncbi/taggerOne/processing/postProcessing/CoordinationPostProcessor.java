package ncbi.taggerOne.processing.postProcessing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.processing.mentionName.MentionNameProcessor;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.MentionName;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.RankedList;
import ncbi.taggerOne.util.vector.Vector;
import ncbi.util.ProcessRunner;

public class CoordinationPostProcessor extends TextInstanceProcessor {

	private static final Logger logger = LoggerFactory.getLogger(CoordinationPostProcessor.class);
	private static final long serialVersionUID = 1L;

	private File inputDir;
	private File outputDir;
	private String commandDir;
	private long timeout;
	private Map<String, NormalizationModelPredictor> normalizationTrainingPredictionModels;
	private MentionNameProcessor mentionNameProcessor;

	public CoordinationPostProcessor(Map<String, NormalizationModelPredictor> normalizationTrainingPredictionModels, MentionNameProcessor mentionNameProcessor) {
		this.normalizationTrainingPredictionModels = normalizationTrainingPredictionModels;
		this.mentionNameProcessor = mentionNameProcessor;
	}

	public void setArgs(String... args) {
		if (args.length < 4) {
			throw new IllegalArgumentException("CoordinationPostProcessor must have four arguments: inputDir, outputDir, commandDir, timeout: " + Arrays.asList(args));
		}
		this.inputDir = new File(args[0]);
		this.outputDir = new File(args[1]);
		this.commandDir = args[2];
		this.timeout = Long.parseLong(args[3]);
	}
	
	@Override
	public void processAll(List<TextInstance> instances) {
		Map<String, Set<String>> resolvedCoordinations = runSimConcept(instances);
		for (TextInstance instance : instances) {
			RankedList<List<AnnotatedSegment>> predictedAnnotationRankedList = instance.getPredictedAnnotations();
			int size = predictedAnnotationRankedList.size();
			RankedList<List<AnnotatedSegment>> filteredAnnotationRankedList = new RankedList<List<AnnotatedSegment>>(size);
			for (int i = 0; i < size; i++) {
				List<AnnotatedSegment> predictedAnnotation = predictedAnnotationRankedList.getObject(i);
				List<AnnotatedSegment> filteredAnnotation = new ArrayList<AnnotatedSegment>();
				for (AnnotatedSegment segment : predictedAnnotation) {
					String text = segment.getText().toLowerCase(Locale.US);
					// logger.info("TEXT\t" + segment.getText());
					Set<String> resolved = resolvedCoordinations.get(text);
					if (resolved == null) {
						filteredAnnotation.add(segment);
					} else {
						logger.info("COORDINATION: \"" + segment.getText() + "\" contains " + resolved.size() + " entities");
						String entityClass = segment.getEntityClass();
						NormalizationModelPredictor normalizationModelPredictor = normalizationTrainingPredictionModels.get(entityClass);
						Set<Entity> newEntities = new HashSet<Entity>();
						for (String mentionText : resolved) {
							MentionName name = new MentionName(mentionText);
							mentionNameProcessor.process(name);
							Vector<String> mentionVector = name.getVector();
							if (mentionVector != null) {
								RankedList<Entity> bestEntites = new RankedList<Entity>(1);
								normalizationModelPredictor.findBest(mentionVector, bestEntites);
								logger.info("COORDINATION: \"" + mentionVector.visualize() + "\" returned " + bestEntites.size() + " entities");
								for (int rank = 0; rank < bestEntites.size(); rank++) {
									Entity entity = bestEntites.getObject(rank);
									logger.info("COORDINATION: adding entity " + entity.getPrimaryName().getName());
									newEntities.add(entity);
								}
							}
						}
						AnnotatedSegment newSegment = new AnnotatedSegment(segment.getSourceText(), segment.getStartChar(), segment.getEndChar(), segment.getTokens(), newEntities);
						filteredAnnotation.add(newSegment);
					}
				}
				filteredAnnotationRankedList.add(predictedAnnotationRankedList.getValue(i), filteredAnnotation);
			}
			instance.setPredictedAnnotations(filteredAnnotationRankedList);
		}
	}

	@Override
	public void process(TextInstance input) {
		throw new RuntimeException("Not allowed");
	}

	private void addCoordination(Map<String, Set<String>> resolvedCoordinations, String coordinationText, List<String> resolvedText) {
		if (resolvedText.size() < 2) {
			throw new RuntimeException();
		}
		// Suppress all forms of "breast and ovarian cancer"
		if (coordinationText.contains("breast") && coordinationText.contains("ovarian") && coordinationText.contains("cancer")) {
			return;
		}
		Set<String> resolved = new HashSet<String>(resolvedText);
		resolvedCoordinations.put(coordinationText, resolved);
	}

	private Map<String, Set<String>> runSimConcept(List<TextInstance> instances) {
		Map<String, Set<String>> resolvedCoordinations = new HashMap<String, Set<String>>();
		try {
			// Get set of mentions to process
			Set<String> mentionTexts = new HashSet<String>();
			for (TextInstance instance : instances) {
				RankedList<List<AnnotatedSegment>> predictedAnnotationRankedList = instance.getPredictedAnnotations();
				for (int i = 0; i < predictedAnnotationRankedList.size(); i++) {
					for (AnnotatedSegment segment : predictedAnnotationRankedList.getObject(i)) {
						String text = segment.getText().toLowerCase(Locale.US);
						mentionTexts.add(text);
					}
				}
			}
			mentionTexts.removeAll(resolvedCoordinations.keySet());

			// Write text to a temp file
			File inputFile = File.createTempFile("coordinatedMentions", ".txt", inputDir);
			logger.debug("Writing to file: " + inputFile.getAbsolutePath());
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(inputFile), T1Constants.UTF8_FORMAT));
			for (String mentionText : mentionTexts) {
				writer.write(mentionText + "\n");
			}
			writer.close();

			// Process with SimConcept
			String command = "perl ./SimConcept.pl -i " + inputDir.getAbsolutePath() + " -o " + outputDir.getAbsolutePath();
			logger.debug("Executing: \"" + command + "\"");
			ProcessRunner pw = new ProcessRunner(command, commandDir);
			pw.await(timeout);
			String result = pw.getResult();
			String error = pw.getError();
			logger.debug("SimConcept result is: " + result);
			logger.debug("SimConcept error is: " + error);

			String outputFilename = outputDir.getAbsolutePath() + "/" + inputFile.getName() + ".SimConcept";

			// Delete input file
			inputFile.delete();

			// Read input
			logger.debug("Reading from file: " + outputFilename);
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(outputFilename), T1Constants.UTF8_FORMAT));
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0 && line.contains("\tCompositeMention:")) {
					line = line.replaceAll(":", "\t").replaceAll("\\|", "\t");
					List<String> fields = Arrays.asList(line.split("\\t"));
					int size = fields.size();
					List<String> resolvedText = fields.subList(2, size);
					logger.debug("Line \"" + line + "\" resolved to coordination " + fields.get(0) + " -> " + resolvedText);
					if (resolvedText.size() > 1) {
						addCoordination(resolvedCoordinations, fields.get(0), resolvedText);
					}
				}
				line = reader.readLine();
			}
			reader.close();

			// Delete output file
			(new File(outputFilename)).delete();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return resolvedCoordinations;
	}
}
