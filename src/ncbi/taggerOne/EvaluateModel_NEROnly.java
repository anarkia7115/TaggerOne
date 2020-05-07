package ncbi.taggerOne;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import ncbi.taggerOne.abbreviation.AbbreviationSource;
import ncbi.taggerOne.abbreviation.AbbreviationSourceProcessor;
import ncbi.taggerOne.dataset.Dataset;
import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.model.normalization.AveragedNormalizationModel;
import ncbi.taggerOne.model.normalization.CachedNormalizationModel;
import ncbi.taggerOne.model.normalization.NormalizationModel;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.model.recognition.RecognitionModelPredictor;
import ncbi.taggerOne.processing.SentenceBreaker;
import ncbi.taggerOne.processing.analysis.HTMLAnalysisFile;
import ncbi.taggerOne.processing.analysis.InstanceCountAnalysisProcessor;
import ncbi.taggerOne.processing.analysis.MentionEntityCountAnalysisProcessor;
import ncbi.taggerOne.processing.analysis.MentionTextCountAnalysisProcessor;
import ncbi.taggerOne.processing.analysis.VisualizeStatesAnalysisProcessor;
import ncbi.taggerOne.processing.evaluation.AnnotationLevelEvaluationProcessor;
import ncbi.taggerOne.processing.evaluation.AnnotationLevelEvaluationProcessor.Condition;
import ncbi.taggerOne.processing.evaluation.EvaluationProcessor;
import ncbi.taggerOne.processing.postProcessing.ConsistencyPostProcessing;
import ncbi.taggerOne.processing.postProcessing.CoordinationPostProcessor;
import ncbi.taggerOne.processing.postProcessing.FilterByMentionText;
import ncbi.taggerOne.processing.postProcessing.FalseModifierRemover;
import ncbi.taggerOne.processing.textInstance.AbbreviationResolverProcessor;
import ncbi.taggerOne.processing.textInstance.AnnotationToStateConverter;
import ncbi.taggerOne.processing.textInstance.Annotator;
import ncbi.taggerOne.processing.textInstance.InstanceElementClearer;
import ncbi.taggerOne.processing.textInstance.InstanceElementClearer.InstanceElement;
import ncbi.taggerOne.processing.textInstance.SegmentMentionProcessor;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessingPipeline;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.AbbreviationResolver;
import ncbi.taggerOne.util.Dictionary;
import ncbi.util.Profiler;
import ncbi.util.ProgressReporter;

public class EvaluateModel_NEROnly {

	private static final Logger logger = LoggerFactory.getLogger(EvaluateModel_NEROnly.class);

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		OptionParser parser = new OptionParser();
		// Input data
		OptionSpec<String> evaluationDatasetConfig = parser.accepts("evaluationDatasetConfig").withRequiredArg().ofType(String.class).required();
		OptionSpec<String> modelInputFilename = parser.accepts("modelInputFilename").withRequiredArg().ofType(String.class).required();
		OptionSpec<Boolean> compileModel = parser.accepts("compileModel").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		OptionSpec<Boolean> useSentenceBreaker = parser.accepts("useSentenceBreaker").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		OptionSpec<String> coordinationPostProcessingArgs = parser.accepts("coordinationPostProcessingArgs").withRequiredArg().ofType(String.class);
		OptionSpec<Boolean> usefalseModifierRemoverPostProcessing = parser.accepts("usefalseModifierRemoverPostProcessing").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		OptionSpec<String> abbreviationSources = parser.accepts("abbreviationSource").withRequiredArg().ofType(String.class);
		OptionSpec<String> analysisFilename = parser.accepts("analysisFilename").withRequiredArg().ofType(String.class);
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
		AnnotationToStateConverter stateConverter = new AnnotationToStateConverter(abbreviationResolverProcessor.getAbbreviationResolver(), segmentMentionProcessor.getProcessor(), lexicon.getNonEntity());
		Dictionary<String> entityClassStates = originalAnnotator.getRecognitionModel().getEntityClassStates();
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

		// Load and process the test data
		logger.info("Loading dataset");
		start = System.currentTimeMillis();
		String[] evaluationDatasetFields = options.valueOf(evaluationDatasetConfig).split("\\|");
		List<TextInstance> evaluationInstances = null;
		try {
			Dataset evaluationDataset = (Dataset) Class.forName(evaluationDatasetFields[0]).newInstance();
			evaluationDataset.setArgs(lexicon, evaluationDatasetFields);
			evaluationInstances = evaluationDataset.getInstances();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		// FIXME Refactor so the sentence breaker is part of the processing pipeline
		if (options.valueOf(useSentenceBreaker)) {
			SentenceBreaker sentenceBreaker = new SentenceBreaker();
			evaluationInstances = sentenceBreaker.breakSentences(evaluationInstances);
		}
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Set up post-processing filters
		ProcessingTimer processingTimerPipeline = new ProcessingTimer("AnnotationPipeline", annotationPipeline);
		List<TextInstanceProcessor> postProcessors = new ArrayList<TextInstanceProcessor>();
		postProcessors.add(processingTimerPipeline);
		if (options.valueOf(usefalseModifierRemoverPostProcessing)) {
			Set<String> falseModifiers = new HashSet<String>();
			falseModifiers.add("absence of");
			falseModifiers.add("absence of any");
			FalseModifierRemover falseModifierRemover = new FalseModifierRemover(falseModifiers);
			postProcessors.add(falseModifierRemover);
		}
		postProcessors.add(new FilterByMentionText("death", "psychiatric", "TNF", "CIN", "CPO", "LV", "l-sotalol", "P9605", "ZnSO", "GEM-P", "PKCa KO", "SHR", "SPH", "CC", "Gemfibrozil-lovastatin", "gemfibrozil-lovastatin",
				"sterile leukocyturia", "LF", "AgSD cream", "FDA", "PTRA", "Lactoferrin", "lactoferrin"));
		TextInstanceProcessingPipeline postProcessingPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("PostProcessingPipeline", 10), postProcessors);
		postProcessingPipeline.processAll(evaluationInstances);

		if (options.has(coordinationPostProcessingArgs)) {
			CoordinationPostProcessor coordinationPostProcessor = new CoordinationPostProcessor(normalizationPredictorModels, segmentMentionProcessor.getProcessor());
			coordinationPostProcessor.setArgs(options.valueOf(coordinationPostProcessingArgs).split("\\|"));
			coordinationPostProcessor.processAll(evaluationInstances);
		}

		ConsistencyPostProcessing consistencyPostProcessing = new ConsistencyPostProcessing(lexicon, entityClassStates, 5, 6);
		consistencyPostProcessing.processAll(evaluationInstances);

		// Set up evaluation
		EvaluationProcessor evaluationProcessor1 = new AnnotationLevelEvaluationProcessor("PERFORMANCE", Condition.EXACT_BOUNDARY, Condition.ENTITY_CLASS);
		EvaluationProcessor evaluationProcessor8 = new AnnotationLevelEvaluationProcessor("PERFORMANCE", Condition.OVERLAP_BOUNDARY, Condition.ENTITY_CLASS);
		MentionTextCountAnalysisProcessor evaluationProcessor6 = new MentionTextCountAnalysisProcessor();
		MentionEntityCountAnalysisProcessor evaluationProcessor7 = new MentionEntityCountAnalysisProcessor();
		HTMLAnalysisFile analysisFile = null;
		String analysisFilenameStr = options.valueOf(analysisFilename);
		if (analysisFilenameStr != null) {
			analysisFile = new HTMLAnalysisFile(analysisFilenameStr, lexicon, normalizationPredictorModels);
		}
		InstanceCountAnalysisProcessor evaluationProcessor10 = new InstanceCountAnalysisProcessor();
		List<TextInstanceProcessor> evaluationProcessors = new ArrayList<TextInstanceProcessor>();
		evaluationProcessors.add(stateConverter);
		evaluationProcessors.add(evaluationProcessor1);
		evaluationProcessors.add(evaluationProcessor8);
		evaluationProcessors.add(evaluationProcessor6);
		evaluationProcessors.add(evaluationProcessor7);
		evaluationProcessors.add(evaluationProcessor10);
		if (analysisFile != null) {
			evaluationProcessors.add(analysisFile);
		}
		evaluationProcessors.add(new VisualizeStatesAnalysisProcessor(normalizationPredictorModels));
		evaluationProcessors.add(new InstanceElementClearer(EnumSet.allOf(InstanceElement.class)));
		TextInstanceProcessingPipeline evaluationPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("EvaluationPipeline", 10), evaluationProcessors);
		evaluationPipeline.processAll(evaluationInstances);
		logger.info(evaluationProcessor1.scoreDetail());
		logger.info(evaluationProcessor8.scoreDetail());
		evaluationProcessor6.visualize();
		evaluationProcessor7.visualize();
		evaluationProcessor10.visualize();
		if (analysisFile != null) {
			analysisFile.close();
		}
		Profiler.print("\t");
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
