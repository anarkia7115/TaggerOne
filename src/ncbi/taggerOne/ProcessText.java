package ncbi.taggerOne;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLStreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bioc.BioCAnnotation;
import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.BioCPassage;
import bioc.io.BioCDocumentWriter;
import bioc.io.BioCFactory;
import bioc.io.woodstox.ConnectorWoodstox;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import ncbi.taggerOne.abbreviation.AbbreviationSource;
import ncbi.taggerOne.abbreviation.AbbreviationSourceProcessor;
import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.model.normalization.AveragedNormalizationModel;
import ncbi.taggerOne.model.normalization.CachedNormalizationModel;
import ncbi.taggerOne.model.normalization.NormalizationModel;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.model.recognition.RecognitionModelPredictor;
import ncbi.taggerOne.processing.SentenceBreaker;
import ncbi.taggerOne.processing.analysis.OutputAnalysisProcessor;
import ncbi.taggerOne.processing.postProcessing.AbbreviationPostProcessing;
import ncbi.taggerOne.processing.postProcessing.ConsistencyPostProcessing;
import ncbi.taggerOne.processing.postProcessing.CoordinationPostProcessor;
import ncbi.taggerOne.processing.postProcessing.FilterByMentionText;
import ncbi.taggerOne.processing.postProcessing.FalseModifierRemover;
import ncbi.taggerOne.processing.textInstance.AbbreviationResolverProcessor;
import ncbi.taggerOne.processing.textInstance.Annotator;
import ncbi.taggerOne.processing.textInstance.PredictedStatesToAnnotationConverter;
import ncbi.taggerOne.processing.textInstance.SegmentMentionProcessor;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessingPipeline;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.AbbreviationResolver;
import ncbi.taggerOne.util.Dictionary;
import ncbi.util.Profiler;

public class ProcessText {

	private static final Logger logger = LoggerFactory.getLogger(ProcessText.class);

	public static void main(String[] args) throws IOException, ClassNotFoundException, XMLStreamException {
		OptionParser parser = new OptionParser();
		// Input data
		OptionSpec<String> fileFormat = parser.accepts("fileFormat").withRequiredArg().ofType(String.class).required();
		OptionSpec<String> inputFilename = parser.accepts("inputFilename").withRequiredArg().ofType(String.class).required();
		OptionSpec<String> outputFilename = parser.accepts("outputFilename").withRequiredArg().ofType(String.class).required();
		OptionSpec<String> modelInputFilename = parser.accepts("modelInputFilename").withRequiredArg().ofType(String.class).required();
		OptionSpec<Boolean> compileModel = parser.accepts("compileModel").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		OptionSpec<Boolean> useSentenceBreaker = parser.accepts("useSentenceBreaker").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		OptionSpec<String> coordinationPostProcessingArgs = parser.accepts("coordinationPostProcessingArgs").withRequiredArg().ofType(String.class);
		OptionSpec<String> consistencyPostProcessingArgs = parser.accepts("consistencyPostProcessingArgs").withRequiredArg().ofType(String.class);
		OptionSpec<String> abbreviationPostProcessingArgs = parser.accepts("abbreviationPostProcessingArgs").withRequiredArg().ofType(String.class);
		OptionSpec<Boolean> usefalseModifierRemoverPostProcessing = parser.accepts("usefalseModifierRemoverPostProcessing").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		OptionSpec<String> abbreviationSources = parser.accepts("abbreviationSource").withRequiredArg().ofType(String.class);
		// TODO Add options for post-processing
		OptionSet options = parser.parse(args);
		// TODO Validate
		logger.info("Command line options:");
		for (OptionSpec<?> spec : options.specs()) {
			StringBuilder str = new StringBuilder();
			List<String> optionNames = spec.options();
			if (optionNames.size() == 1) {
				str.append(optionNames.get(0));
			} else {
				str.append(optionNames.toString());
			}
			str.append(" = ");
			List<?> values = spec.values(options);
			if (values.size() == 1) {
				str.append(values.get(0).toString());
			} else {
				str.append(values.toString());
			}
			logger.info("\t" + str.toString());
		}

		// Load the annotation pipeline
		logger.info("Loading model");
		long start = System.currentTimeMillis();
		ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(options.valueOf(modelInputFilename))));
		TextInstanceProcessingPipeline originalAnnotationPipeline = (TextInstanceProcessingPipeline) ois.readObject();
		ois.close();
		List<TextInstanceProcessor> originalProcessors = originalAnnotationPipeline.getProcessors();
		AbbreviationResolverProcessor abbreviationResolverProcessor = (AbbreviationResolverProcessor) originalProcessors.get(3);
		AbbreviationResolver abbreviationResolver = abbreviationResolverProcessor.getAbbreviationResolver();
		SegmentMentionProcessor segmentMentionProcessor = (SegmentMentionProcessor) originalProcessors.get(4);
		Annotator originalAnnotator = (Annotator) originalProcessors.get(5);
		Lexicon lexicon = originalAnnotator.getLexicon();
		Map<String, NormalizationModelPredictor> originalNormalizationPredictorModels = originalAnnotator.getNormalizationModels();
		TextInstanceProcessingPipeline annotationPipeline = originalAnnotationPipeline;
		Map<String, NormalizationModelPredictor> normalizationPredictorModels = originalNormalizationPredictorModels;
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Prepare abbreviations source
		logger.info("Loading abbreviation source");
		start = System.currentTimeMillis();
		List<AbbreviationSource> abbreviationSourceList = new ArrayList<AbbreviationSource>();
		try {
			for (String abbreviationSourceConfig : options.valuesOf(abbreviationSources)) {
				String[] fields = abbreviationSourceConfig.split("\\|");
				AbbreviationSource source = (AbbreviationSource) Class.forName(fields[0]).newInstance();
				source.setArgs(fields);
				abbreviationSourceList.add(source);
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		List<TextInstanceProcessor> processors = new ArrayList<TextInstanceProcessor>();
		processors.add(new AbbreviationSourceProcessor(abbreviationSourceList, abbreviationResolver));
		processors.addAll(originalProcessors);
		logger.info("Number of abbreviations = " + abbreviationResolver.size());
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Compile model
		if (options.valueOf(compileModel)) {
			logger.info("Compiling model");
			RecognitionModelPredictor recognitionModel = originalAnnotator.getRecognitionModel().compile();
			normalizationPredictorModels = new HashMap<String, NormalizationModelPredictor>();
			for (String entityType : originalNormalizationPredictorModels.keySet()) {
				NormalizationModelPredictor originalPredictor = originalNormalizationPredictorModels.get(entityType);
				int maxCacheSize = ((CachedNormalizationModel) originalPredictor).getMaxCacheSize();
				NormalizationModelPredictor wrappedPredictor = ((CachedNormalizationModel) originalPredictor).getWrappedPredictor();
				if (wrappedPredictor instanceof AveragedNormalizationModel) {
					CachedNormalizationModel newPredictor = new CachedNormalizationModel(wrappedPredictor.compile(), maxCacheSize);
					normalizationPredictorModels.put(entityType, newPredictor);
				} else if (wrappedPredictor instanceof NormalizationModel) {
					CachedNormalizationModel newPredictor = new CachedNormalizationModel(wrappedPredictor.compile(), maxCacheSize);
					normalizationPredictorModels.put(entityType, newPredictor);
				} else {
					throw new RuntimeException("Not implemented");
				}
			}
			Annotator annotator = new Annotator(lexicon, recognitionModel, normalizationPredictorModels);
			processors.set(6, annotator);
			annotationPipeline = new TextInstanceProcessingPipeline(processors);
			logger.info("Elapsed = " + (System.currentTimeMillis() - start));
		}

		// Set up post-processing filters
		ProcessingTimer processingTimerPipeline = new ProcessingTimer("AnnotationPipeline", annotationPipeline);
		List<TextInstanceProcessor> evaluationProcessors = new ArrayList<TextInstanceProcessor>();
		evaluationProcessors.add(processingTimerPipeline);
		if (options.valueOf(usefalseModifierRemoverPostProcessing)) {
			Set<String> falseModifiers = new HashSet<String>();
			falseModifiers.add("absence of");
			falseModifiers.add("absence of any");
			FalseModifierRemover falseModifierRemover = new FalseModifierRemover(falseModifiers);
			evaluationProcessors.add(falseModifierRemover);
		}
		evaluationProcessors.add(new FilterByMentionText("death", "psychiatric", "TNF", "CIN", "CPO"));
		evaluationProcessors.add(new OutputAnalysisProcessor());
		evaluationProcessors.add(new PredictedStatesToAnnotationConverter());
		TextInstanceProcessingPipeline evaluationPipeline = new TextInstanceProcessingPipeline(evaluationProcessors);
		
		CoordinationPostProcessor coordinationPostProcessor = null;
		if (options.has(coordinationPostProcessingArgs)) {
			coordinationPostProcessor = new CoordinationPostProcessor(normalizationPredictorModels, segmentMentionProcessor.getProcessor());
			coordinationPostProcessor.setArgs(options.valueOf(coordinationPostProcessingArgs).split("\\|"));
		}

		AbbreviationPostProcessing abbreviationPostProcessing = null;
		if (options.has(abbreviationPostProcessingArgs)) {
			String[] ppArgs = options.valueOf(abbreviationPostProcessingArgs).split("\\|");
			int changeThreshold = Integer.parseInt(ppArgs[0]);
			int addThreshold = Integer.parseInt(ppArgs[1]);
			boolean dropIfNoExpandedPrediction = Boolean.parseBoolean(ppArgs[2]);
			abbreviationPostProcessing = new AbbreviationPostProcessing(abbreviationResolver, changeThreshold, addThreshold, dropIfNoExpandedPrediction);
		}

		ConsistencyPostProcessing consistencyPostProcessing = null;
		if (options.has(consistencyPostProcessingArgs)) {
			String[] ppArgs = options.valueOf(consistencyPostProcessingArgs).split("\\|");
			int changeThreshold = Integer.parseInt(ppArgs[0]);
			int addThreshold = Integer.parseInt(ppArgs[1]);
			Dictionary<String> entityClassStates = originalAnnotator.getRecognitionModel().getEntityClassStates();
			consistencyPostProcessing = new ConsistencyPostProcessing(lexicon, entityClassStates, changeThreshold, addThreshold);
		}


		if (options.valueOf(fileFormat).toLowerCase(Locale.US).equals("pubtator")) {
			processPubtator(options.valueOf(inputFilename), options.valueOf(outputFilename), options.valueOf(useSentenceBreaker), evaluationPipeline, coordinationPostProcessor, abbreviationPostProcessing, consistencyPostProcessing);
		} else if (options.valueOf(fileFormat).toLowerCase(Locale.US).equals("bioc")) {
			processBioC(options.valueOf(inputFilename), options.valueOf(outputFilename), options.valueOf(useSentenceBreaker), evaluationPipeline, coordinationPostProcessor, abbreviationPostProcessing, consistencyPostProcessing);
		} else {
			throw new RuntimeException("File format must be BioC or Pubtator = " + options.valueOf(fileFormat));
		}
		Profiler.print("\t");
	}

	private static void processBioC(String inputFilename, String outputFilename, boolean useSentenceBreaker, TextInstanceProcessingPipeline evaluationPipeline, CoordinationPostProcessor coordinationPostProcessor, AbbreviationPostProcessing abbreviationPostProcessing, ConsistencyPostProcessing consistencyPostProcessing)
			throws XMLStreamException, IOException {
		// Open BioC files for input & output
		ConnectorWoodstox connector = new ConnectorWoodstox();
		BioCCollection collection = connector.startRead(new InputStreamReader(new FileInputStream(inputFilename), T1Constants.UTF8_FORMAT));
		String bioCParser = BioCFactory.WOODSTOX;
		BioCFactory factory = BioCFactory.newFactory(bioCParser);
		BioCDocumentWriter writer = factory.createBioCDocumentWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), T1Constants.UTF8_FORMAT));
		writer.writeCollectionInfo(collection);
		while (connector.hasNext()) {
			BioCDocument document = connector.next();
			String documentId = document.getID();
			logger.info("ID=" + documentId);
			for (BioCPassage passage : document.getPassages()) {
				// Get instance
				List<TextInstance> instances = new ArrayList<TextInstance>();
				int offset = passage.getOffset();
				TextInstance instance = new TextInstance(documentId, documentId, passage.getText(), offset);
				instance.setTargetAnnotation(new ArrayList<AnnotatedSegment>());
				instances.add(instance);
				// Break into sentences
				if (useSentenceBreaker) {
					SentenceBreaker sentenceBreaker = new SentenceBreaker();
					instances = sentenceBreaker.breakSentences(instances);
				}
				// Process
				evaluationPipeline.processAll(instances);
				if (coordinationPostProcessor != null) {
					coordinationPostProcessor.processAll(instances);
				}
				if (abbreviationPostProcessing != null) {
					abbreviationPostProcessing.processAll(instances);
				}
				if (consistencyPostProcessing != null) {
					consistencyPostProcessing.processAll(instances);
				}
				int counter = 0;
				for (TextInstance instance2 : instances) {
					List<AnnotatedSegment> predictedAnnotation = instance2.getPredictedAnnotations().getObject(0);
					for (AnnotatedSegment segment : predictedAnnotation) {
						BioCAnnotation annotation = new BioCAnnotation();
						annotation.setID(Integer.toString(counter));
						Map<String, String> infons = new HashMap<String, String>();
						infons.put("type", segment.getEntityClass());
						String identifiers = visualizeIdentifiers(segment.getEntities());
						if (identifiers != null) {
							infons.put("identifier", identifiers);
						}
						annotation.setInfons(infons);
						annotation.setLocation(instance2.getOffset() + segment.getStartChar(), segment.getEndChar() - segment.getStartChar());
						annotation.setText(segment.getText());
						counter++;
						passage.addAnnotation(annotation);
					}
				}
			}
			writer.writeDocument(document);
		}
		writer.close();
	}

	private static String visualizeIdentifiers(Set<Entity> entities) {
		List<String> entityIDs = new ArrayList<String>();
		for (Entity entity : entities) {
			if (entity != null) {
				String primaryIdentifier = entity.getPrimaryIdentifier();
				if (!primaryIdentifier.startsWith(T1Constants.UNKNOWN_ENTITY_ID_PREFIX) && !primaryIdentifier.equals(T1Constants.NONENTITY_STATE)) {
					entityIDs.add(primaryIdentifier);
				}
			}
		}
		if (entityIDs.size() == 0) {
			return null;
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

	private static void processPubtator(String inputFilename, String outputFilename, boolean useSentenceBreaker, TextInstanceProcessingPipeline evaluationPipeline, CoordinationPostProcessor coordinationPostProcessor, AbbreviationPostProcessing abbreviationPostProcessing, ConsistencyPostProcessing consistencyPostProcessing) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFilename), T1Constants.UTF8_FORMAT));
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), T1Constants.UTF8_FORMAT));
		String line = reader.readLine();
		Map<String, String> titles = new HashMap<String, String>();
		while (line != null) {
			line = line.trim();
			String[] fields = line.split("\\|");
			if (fields.length == 3) {
				String id = fields[0];
				String type = fields[1];
				String text = fields[2];

				if (type.equals("t")) {
					writer.write(id + "|t|" + text + "\n");
					// Store title
					titles.put(id, text);
				} else if (type.equals("a")) {
					writer.write(id + "|a|" + text + "\n");
					// Process abstract
					String title = titles.get(id);
					List<TextInstance> instances = new ArrayList<TextInstance>();
					TextInstance instance = new TextInstance(id, id, title + " " + text, 0);
					instance.setTargetAnnotation(new ArrayList<AnnotatedSegment>());
					instances.add(instance);
					// Break into sentences
					if (useSentenceBreaker) {
						SentenceBreaker sentenceBreaker = new SentenceBreaker();
						instances = sentenceBreaker.breakSentences(instances);
					}
					// Process
					evaluationPipeline.processAll(instances);
					if (coordinationPostProcessor != null) {
						coordinationPostProcessor.processAll(instances);
					}
					if (abbreviationPostProcessing != null) {
						abbreviationPostProcessing.processAll(instances);
					}
					if (consistencyPostProcessing != null) {
						consistencyPostProcessing.processAll(instances);
					}
					for (TextInstance instance2 : instances) {
						List<AnnotatedSegment> predictedAnnotation = instance2.getPredictedAnnotations().getObject(0);
						for (AnnotatedSegment segment : predictedAnnotation) {
							int start = instance2.getOffset() + segment.getStartChar();
							int end = instance2.getOffset() + segment.getEndChar();
							writer.write(id + "\t" + start + "\t" + end + "\t" + segment.getText() + "\t");
							String identifiers = visualizeIdentifiers(segment.getEntities());
							if (identifiers == null) {
								writer.write(segment.getEntityClass() + "\n");
							} else {
								writer.write(segment.getEntityClass() + "\t" + Entity.visualizePrimaryIdentifiers(segment.getEntities()) + "\n");
							}
						}
					}
					writer.write("\n");
				}
			}
			line = reader.readLine();
		}
		reader.close();
		writer.close();
	}

	private static class ProcessingTimer extends TextInstanceProcessor {

		private static final long serialVersionUID = 1L;

		private String timerName;
		private TextInstanceProcessor wrappedProcessor;

		public ProcessingTimer(String timerName, TextInstanceProcessor wrappedProcessor) {
			this.timerName = timerName;
			this.wrappedProcessor = wrappedProcessor;
		}

		@Override
		public void process(TextInstance input) {
			Profiler.start(timerName + ".process()");
			wrappedProcessor.process(input);
			Profiler.stop(timerName + ".process()");
		}

		@Override
		public void processAll(List<TextInstance> input) {
			Profiler.start(timerName + ".processAll()");
			wrappedProcessor.processAll(input);
			Profiler.stop(timerName + ".processAll()");
		}

	}

}
