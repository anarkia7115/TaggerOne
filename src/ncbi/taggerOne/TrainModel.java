package ncbi.taggerOne;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import ncbi.taggerOne.abbreviation.AbbreviationSource;
import ncbi.taggerOne.abbreviation.AbbreviationSourceProcessor;
import ncbi.taggerOne.dataset.Dataset;
import ncbi.taggerOne.lexicon.EntityFrequencyComparator;
import ncbi.taggerOne.lexicon.IDFTokenWeightCalculator;
import ncbi.taggerOne.lexicon.Index;
import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.lexicon.LexiconMappings;
import ncbi.taggerOne.lexicon.TokenWeightCalculator;
import ncbi.taggerOne.lexicon.loader.LexiconMappingsLoader;
import ncbi.taggerOne.model.normalization.AveragedNormalizationModel;
import ncbi.taggerOne.model.normalization.CachedNormalizationModel;
import ncbi.taggerOne.model.normalization.NormalizationModel;
import ncbi.taggerOne.model.normalization.NormalizationModelPredictor;
import ncbi.taggerOne.model.normalization.NormalizationModelUpdater;
import ncbi.taggerOne.model.optimization.MIRAUpdate;
import ncbi.taggerOne.model.optimization.OnlineOptimizer;
import ncbi.taggerOne.model.recognition.AveragedRecognitionModel;
import ncbi.taggerOne.model.recognition.RecognitionModel;
import ncbi.taggerOne.model.recognition.RecognitionModelPredictor;
import ncbi.taggerOne.processing.SentenceBreaker;
import ncbi.taggerOne.processing.TrainingProgressTracker;
import ncbi.taggerOne.processing.analysis.ErrorAnalyzer;
import ncbi.taggerOne.processing.evaluation.AnnotationLevelEvaluationProcessor;
import ncbi.taggerOne.processing.evaluation.AnnotationLevelEvaluationProcessor.Condition;
import ncbi.taggerOne.processing.evaluation.EvaluationProcessor;
import ncbi.taggerOne.processing.evaluation.InstanceLevelEvaluationProcessor;
import ncbi.taggerOne.processing.evaluation.PerfectNERInstanceLevelEvaluationProcessor;
import ncbi.taggerOne.processing.features.FeatureProcessor;
import ncbi.taggerOne.processing.features.segment.BiasFeatureProcessor;
import ncbi.taggerOne.processing.features.segment.SegmentLengthFeatureProcessor;
import ncbi.taggerOne.processing.features.segment.SegmentPatternFeatureProcessor;
import ncbi.taggerOne.processing.features.segment.StartEndClosedClassTokenFeatureProcessor;
import ncbi.taggerOne.processing.features.segment.StartEndTokenFeatureProcessor;
import ncbi.taggerOne.processing.features.segment.SurroundingCharactersFeatureProcessor;
import ncbi.taggerOne.processing.features.segment.SurroundingTokensFeatureProcessor;
import ncbi.taggerOne.processing.features.segment.UnbalancedParenFeatureProcessor;
import ncbi.taggerOne.processing.features.token.CharNGramFeatureProcessor;
import ncbi.taggerOne.processing.features.token.POSFeatureProcessor;
import ncbi.taggerOne.processing.features.token.POSFeatureProcessor.HepplePOSTaggerFactory;
import ncbi.taggerOne.processing.features.token.TokenFeatureProcessor;
import ncbi.taggerOne.processing.features.token.TokenPatternFeatureProcessor;
import ncbi.taggerOne.processing.mentionName.EntityNameTokenizer;
import ncbi.taggerOne.processing.mentionName.MentionNameProcessingPipeline;
import ncbi.taggerOne.processing.mentionName.MentionNameProcessor;
import ncbi.taggerOne.processing.mentionName.StringProcessNameApplicator;
import ncbi.taggerOne.processing.mentionName.StringProcessTokenApplicator;
import ncbi.taggerOne.processing.mentionName.TokenListToVectorConverter;
import ncbi.taggerOne.processing.mentionName.TokenListToWeightedVectorConverter;
import ncbi.taggerOne.processing.mentionName.VectorSpaceExtractor;
import ncbi.taggerOne.processing.stoppingCriteria.EvaluationProcessorStoppingCriteria;
import ncbi.taggerOne.processing.string.AcronymPreservingLowerCaseStringProcessor;
import ncbi.taggerOne.processing.string.CharacterClassStringProcessor;
import ncbi.taggerOne.processing.string.LowerCaseStringProcessor;
import ncbi.taggerOne.processing.string.NumberClassStringProcessor;
import ncbi.taggerOne.processing.string.PorterStemmer;
import ncbi.taggerOne.processing.string.PunctuationRemover;
import ncbi.taggerOne.processing.string.StopWordRemover;
import ncbi.taggerOne.processing.string.StringProcessingPipeline;
import ncbi.taggerOne.processing.string.StringProcessor;
import ncbi.taggerOne.processing.string.Trimmer;
import ncbi.taggerOne.processing.textInstance.AbbreviationResolverProcessor;
import ncbi.taggerOne.processing.textInstance.AnnotationModelTrainer;
import ncbi.taggerOne.processing.textInstance.AnnotationModelTrainingIteration;
import ncbi.taggerOne.processing.textInstance.AnnotationToStateConverter;
import ncbi.taggerOne.processing.textInstance.Annotator;
import ncbi.taggerOne.processing.textInstance.EntityClassStateExtractor;
import ncbi.taggerOne.processing.textInstance.FeatureInstantiator;
import ncbi.taggerOne.processing.textInstance.FeatureSetExtractor;
import ncbi.taggerOne.processing.textInstance.InstanceElementClearer;
import ncbi.taggerOne.processing.textInstance.InstanceElementClearer.InstanceElement;
import ncbi.taggerOne.processing.textInstance.MaxTargetAnnotationLength;
import ncbi.taggerOne.processing.textInstance.PredictedStatesToAnnotationConverter;
import ncbi.taggerOne.processing.textInstance.SegmentMentionProcessor;
import ncbi.taggerOne.processing.textInstance.Segmenter;
import ncbi.taggerOne.processing.textInstance.TextInstanceProcessingPipeline;
import ncbi.taggerOne.processing.textInstance.TextInstanceTokenizer;
import ncbi.taggerOne.types.TextInstance;
import ncbi.taggerOne.util.AbbreviationResolver;
import ncbi.taggerOne.util.Dictionary;
import ncbi.taggerOne.util.tokenization.Tokenizer;
import ncbi.taggerOne.util.vector.SparseVector;
import ncbi.util.Profiler;
import ncbi.util.ProgressReporter;

public class TrainModel {

	private static final Logger logger = LoggerFactory.getLogger(TrainModel.class);
	private static final String GREEK = "(alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega)";

	public static void main(String[] args) {

		// FIXME Setup command line help
		OptionParser parser = new OptionParser();
		// Expectations
		OptionSpec<String> entityTypes = parser.accepts("entityTypes").withRequiredArg().ofType(String.class).required();
		OptionSpec<String> lexiconNamespaces = parser.accepts("lexiconNamespaces").withRequiredArg().ofType(String.class);
		// Input data
		OptionSpec<String> trainingDatasetConfigs = parser.accepts("trainingDatasetConfig").withRequiredArg().ofType(String.class).required();
		OptionSpec<String> holdoutDatasetConfigs = parser.accepts("holdoutDatasetConfig").withRequiredArg().ofType(String.class);
		OptionSpec<Double> holdoutPercentage = parser.accepts("holdoutPercentage").withRequiredArg().ofType(Double.class).defaultsTo(0.1);
		OptionSpec<String> lexiconConfigs = parser.accepts("lexiconConfig").withRequiredArg().ofType(String.class);
		OptionSpec<String> abbreviationSources = parser.accepts("abbreviationSource").withRequiredArg().ofType(String.class);
		OptionSpec<Boolean> useSentenceBreaker = parser.accepts("useSentenceBreaker").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		OptionSpec<String> entityTokenizerClass = parser.accepts("entityTokenizerClass").withRequiredArg().ofType(String.class).required();
		OptionSpec<String> textInstanceTokenizerClass = parser.accepts("textInstanceTokenizerClass").withRequiredArg().ofType(String.class).required();
		OptionSpec<String> stemmerClass = parser.accepts("stemmerClass").withRequiredArg().ofType(String.class).required();
		// Training options
		OptionSpec<Double> regularization = parser.accepts("regularization").withRequiredArg().ofType(Double.class).required();
		OptionSpec<Double> maxStepSize = parser.accepts("maxStepSize").withRequiredArg().ofType(Double.class).required();
		OptionSpec<Long> solverTimeout = parser.accepts("solverTimeout").withRequiredArg().ofType(Long.class).defaultsTo(5000L);
		OptionSpec<Integer> topNLabelings = parser.accepts("topNLabelings").withRequiredArg().ofType(Integer.class).defaultsTo(1);
		OptionSpec<Integer> topNNormalization = parser.accepts("topNNormalization").withRequiredArg().ofType(Integer.class).defaultsTo(0);
		OptionSpec<Integer> maxTrainingIterations = parser.accepts("maxTrainingIterations").withRequiredArg().ofType(Integer.class).required();
		OptionSpec<Integer> normalizationCacheSize = parser.accepts("normalizationCacheSize").withRequiredArg().ofType(Integer.class).defaultsTo(1000);
		OptionSpec<Integer> iterationsPastLastImprovement = parser.accepts("iterationsPastLastImprovement").withRequiredArg().ofType(Integer.class).required();
		OptionSpec<Boolean> enforceNonNegativeDiagonal = parser.accepts("enforceNonNegativeDiagonal").withRequiredArg().ofType(Boolean.class).defaultsTo(false);
		OptionSpec<Boolean> deterministicOrdering = parser.accepts("deterministicOrdering").withRequiredArg().ofType(Boolean.class).defaultsTo(false);
		OptionSpec<Boolean> averageRecognitionModel = parser.accepts("averageRecognitionModel").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		OptionSpec<Boolean> averageNormalizationModels = parser.accepts("averageNormalizationModels").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
		// Output
		OptionSpec<String> modelOutputFilename = parser.accepts("modelOutputFilename").withRequiredArg().ofType(String.class);
		OptionSet options = parser.parse(args);
		// FIXME Validate
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

		logger.info("Loading lexicons");
		long start = System.currentTimeMillis();
		Set<String> entityTypeSet = new HashSet<String>(Arrays.asList(options.valueOf(entityTypes).split("\\|")));
		Set<String> lexiconNamespaceSet = new HashSet<String>();
		if (options.has(lexiconNamespaces)) {
			lexiconNamespaceSet.addAll(Arrays.asList(options.valueOf(lexiconNamespaces).split("\\|")));
		}
		StringProcessor nameNormalizer = new StringProcessor() {
			@Override
			public String process(String name) {
				// FIXME This should be converted to using the same string processing and tokenization as the name
				// vector space
				if (name == null) {
					return null;
				}
				String result = name.toLowerCase();
				result = result.replaceAll("\\p{Punct}", " ");
				Set<String> tokens = new TreeSet<String>(Arrays.asList(result.split("\\s+")));
				Set<String> stopWords = ncbi.taggerOne.processing.string.StopWordRemover.DEFAULT_STOP_WORDS;
				tokens.removeAll(stopWords);
				StringBuilder normalized = new StringBuilder();
				for (String token : tokens) {
					normalized.append(token);
					normalized.append(" ");
				}
				return normalized.toString().trim();
			}
		};
		LexiconMappings lexiconMappings = new LexiconMappings(lexiconNamespaceSet, entityTypeSet, nameNormalizer);
		List<LexiconMappingsLoader> loaders = new ArrayList<LexiconMappingsLoader>();
		try {
			for (String lexiconConfig : options.valuesOf(lexiconConfigs)) {
				String[] fields = lexiconConfig.split("\\|");
				LexiconMappingsLoader loader = (LexiconMappingsLoader) Class.forName(fields[0]).newInstance();
				loader.setArgs(fields);
				loaders.add(loader);
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		for (LexiconMappingsLoader lexiconLoader : loaders) {
			lexiconLoader.loadIdentifiers(lexiconMappings);
		}
		for (LexiconMappingsLoader lexiconLoader : loaders) {
			lexiconLoader.loadIdentifierEquivalencies(lexiconMappings);
		}
		for (LexiconMappingsLoader lexiconLoader : loaders) {
			lexiconLoader.loadNames(lexiconMappings);
		}
		Lexicon lexicon = lexiconMappings.createLexicon();
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Load the training datasets
		logger.info("Loading training and holdout instances");
		start = System.currentTimeMillis();
		List<TextInstance> trainingInstances = getInstances(options.valuesOf(trainingDatasetConfigs), lexicon);
		logger.info("Loaded " + trainingInstances.size() + " training instances");
		List<TextInstance> holdoutInstances = null;
		if (options.has(holdoutDatasetConfigs)) {
			logger.info("Using holdout sets defined by configuration");
			holdoutInstances = getInstances(options.valuesOf(holdoutDatasetConfigs), lexicon);
		} else {
			int holdoutSize = (int) Math.round(options.valueOf(holdoutPercentage) * trainingInstances.size());
			logger.info("Creating holdout set by randomly choosing instances from training set");
			holdoutInstances = new ArrayList<TextInstance>(trainingInstances.subList(0, holdoutSize));
			Collections.shuffle(holdoutInstances);
			holdoutInstances = holdoutInstances.subList(0, holdoutSize);
			trainingInstances.removeAll(holdoutInstances);
		}
		logger.info("Loaded " + holdoutInstances.size() + " holdout instances");
		if (options.valueOf(useSentenceBreaker)) {
			SentenceBreaker sentenceBreaker = new SentenceBreaker();
			trainingInstances = sentenceBreaker.breakSentences(trainingInstances);
			holdoutInstances = sentenceBreaker.breakSentences(holdoutInstances);
		}
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Tokenize and process entity names in lexicon, extract associated vector space
		logger.info("Extracting entity name vector space");
		start = System.currentTimeMillis();
		StringProcessNameApplicator nameApplicator = new StringProcessNameApplicator(new PunctuationRemover(" "), new Trimmer());
		Tokenizer entityTokenizer = null;
		try {
			entityTokenizer = (Tokenizer) Class.forName(options.valueOf(entityTokenizerClass)).newInstance();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		EntityNameTokenizer entityNameTokenizer = new EntityNameTokenizer(entityTokenizer);
		StringProcessor stemmer = null;
		try {
			stemmer = (StringProcessor) Class.forName(options.valueOf(stemmerClass)).newInstance();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		StringProcessTokenApplicator tokenApplicator = new StringProcessTokenApplicator(new AcronymPreservingLowerCaseStringProcessor(4), new StopWordRemover(StopWordRemover.DEFAULT_STOP_WORDS), stemmer);
		Map<String, Dictionary<String>> nameVectorSpaces = new HashMap<String, Dictionary<String>>();
		for (String entityType : entityTypeSet) {
			Dictionary<String> nameVectorSpace = new Dictionary<String>();
			nameVectorSpaces.put(entityType, nameVectorSpace);
			MentionNameProcessor extractorPipeline = new MentionNameProcessingPipeline(nameApplicator, entityNameTokenizer, tokenApplicator, new VectorSpaceExtractor(nameVectorSpace));
			extractorPipeline.process(entityType, lexicon);
			nameVectorSpace.freeze();
			logger.info("Name vector space extracted for type " + entityType + "; size = " + nameVectorSpace.size());
			for (int i = 0; i < nameVectorSpace.size(); i++) {
				String element = nameVectorSpace.getElement(i);
				Pattern pattern = Pattern.compile("\\s");
				Matcher matcher = pattern.matcher(element);
				if (matcher.find()) {
					logger.error("Name vector space element \"" + element + "\" contains whitespace");
				}
			}
		}
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Analyze target annotations
		logger.info("Analyzing target annotations");
		start = System.currentTimeMillis();
		Tokenizer textInstanceTokenizer = null;
		try {
			textInstanceTokenizer = (Tokenizer) Class.forName(options.valueOf(textInstanceTokenizerClass)).newInstance();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		TextInstanceTokenizer tokenizer = new TextInstanceTokenizer(textInstanceTokenizer);
		MaxTargetAnnotationLength maxAnnotationLength = new MaxTargetAnnotationLength();
		Dictionary<String> entityStates = new Dictionary<String>();
		EntityClassStateExtractor entityClassStateExtractor = new EntityClassStateExtractor(entityStates);
		TextInstanceProcessingPipeline targetAnalysisPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("TargetAnalysisPipeline", 1000), tokenizer, maxAnnotationLength, entityClassStateExtractor);
		targetAnalysisPipeline.processAll(trainingInstances);
		int maxSegmentLength = maxAnnotationLength.getMaxLength();
		entityStates.freeze();
		logger.info("Length of longest training annotation = " + maxSegmentLength);
		logger.info("Set of target states = " + entityStates.getElements());
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		logger.info("Loading abbreviations");
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
		AbbreviationResolver abbreviationResolver = new AbbreviationResolver();
		AbbreviationSourceProcessor abbreviationSourceProcessor = new AbbreviationSourceProcessor(abbreviationSourceList, abbreviationResolver);
		abbreviationSourceProcessor.processAll(trainingInstances);
		AbbreviationResolverProcessor abbreviationResolverProcessor = new AbbreviationResolverProcessor(abbreviationResolver);
		logger.info("Number of abbreviations = " + abbreviationResolver.size());
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Extract the feature set
		logger.info("Extracting NER feature set and mention vector space");
		start = System.currentTimeMillis();
		Segmenter segmenter = new Segmenter(maxSegmentLength);
		Dictionary<String> featureSet = new Dictionary<String>();
		List<FeatureProcessor> featureProcessors = getFeatureProcessors(maxSegmentLength);
		Dictionary<String> mentionVectorSpace = new Dictionary<String>();
		// Add all elements in name vector space to the mention vector space
		for (String entityType : entityTypeSet) {
			Dictionary<String> nameVectorSpace = nameVectorSpaces.get(entityType);
			for (String nameVectorSpaceElement : nameVectorSpace.getElements()) {
				mentionVectorSpace.addElement(nameVectorSpaceElement);
			}
		}
		TextInstanceProcessingPipeline extractionPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("ExtractionPipeline", 100), segmenter, new FeatureSetExtractor(featureSet, featureProcessors),
				abbreviationResolverProcessor, new SegmentMentionProcessor(new MentionNameProcessingPipeline(nameApplicator, entityNameTokenizer, tokenApplicator, new VectorSpaceExtractor(mentionVectorSpace))));
		extractionPipeline.processAll(trainingInstances);
		featureSet.freeze();
		mentionVectorSpace.addElement(T1Constants.UNKNOWN_TOKEN);
		mentionVectorSpace.freeze();
		logger.info("Feature set extracted; size = " + featureSet.size());
		logger.info("Mention vector space extracted; size = " + mentionVectorSpace.size());
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Calculate the token weights and convert entity names to vectors
		logger.info("Calculating token weights and converting entity names to vectors");
		start = System.currentTimeMillis();
		// FIXME simplify: have to convert to vectors to calculate the token weights, then reconvert to weighted vectors
		for (String entityType : entityTypeSet) {
			Dictionary<String> nameVectorSpace = nameVectorSpaces.get(entityType);
			TokenListToVectorConverter nameConverter1 = new TokenListToVectorConverter(SparseVector.factory, nameVectorSpace, true);
			nameConverter1.process(entityType, lexicon);
			TokenWeightCalculator tokenWeightCalculator = new TokenWeightCalculator(mentionVectorSpace, nameVectorSpace, lexicon.getEntities(entityType));
			TokenListToWeightedVectorConverter nameConverter = new TokenListToWeightedVectorConverter(SparseVector.factory, nameVectorSpace, tokenWeightCalculator.getNameWeights(), false, true);
			nameConverter.process(entityType, lexicon);
		}
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Instantiate the training set features
		logger.info("Instantiating training set features");
		start = System.currentTimeMillis();
		FeatureInstantiator instantiator = new FeatureInstantiator(SparseVector.factory, featureSet, featureProcessors);
		IDFTokenWeightCalculator tokenWeightCalculator = new IDFTokenWeightCalculator(mentionVectorSpace, nameVectorSpaces, lexicon);
		MentionNameProcessingPipeline mentionConverter = new MentionNameProcessingPipeline(nameApplicator, entityNameTokenizer, tokenApplicator,
				new TokenListToWeightedVectorConverter(SparseVector.factory, mentionVectorSpace, tokenWeightCalculator.getWeights(), false, false));
		AnnotationToStateConverter stateConverter = new AnnotationToStateConverter(abbreviationResolver, mentionConverter, lexicon.getNonEntity());
		TextInstanceProcessingPipeline instantiationPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("InstantiationPipeline", 100), instantiator, stateConverter, abbreviationResolverProcessor,
				new SegmentMentionProcessor(mentionConverter));
		instantiationPipeline.processAll(trainingInstances);
		logger.info("Training set instantiated.");
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Create indexes
		logger.info("Creating indexes");
		start = System.currentTimeMillis();
		lexicon.createIndexes(mentionVectorSpace, nameVectorSpaces);
		// Set index to use a simple form of disambiguation: the frequency of the entity in the training data
		EntityFrequencyComparator entityFrequencyComparator = new EntityFrequencyComparator();
		entityFrequencyComparator.updateFrequenciesFromTargetAnnotations(trainingInstances);
		for (String entityType : entityTypeSet) {
			Index entityIndex = lexicon.getIndex(entityType);
			entityIndex.setEntityComparator(entityFrequencyComparator);
		}
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		// Process the holdout dataset
		logger.info("Processing holdout instances");
		start = System.currentTimeMillis();
		// TODO Enable separate performance reporting for each holdout set
		abbreviationSourceProcessor.processAll(holdoutInstances);
		tokenizer.processAll(holdoutInstances);
		segmenter.processAll(holdoutInstances);
		instantiationPipeline.processAll(holdoutInstances);
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		logger.info("Creating NER and normalization models");
		start = System.currentTimeMillis();
		TrainingProgressTracker trainingProgressTracker = new TrainingProgressTracker();
		Map<String, NormalizationModelPredictor> entityModels = new HashMap<String, NormalizationModelPredictor>();
		Map<String, NormalizationModelPredictor> normalizationTrainingPredictionModels = new HashMap<String, NormalizationModelPredictor>();
		Map<String, NormalizationModelUpdater> normalizationUpdaterModels = new HashMap<String, NormalizationModelUpdater>();
		for (String entityType : entityTypeSet) {
			Index entityIndex = lexicon.getIndex(entityType);
			Dictionary<String> nameVectorSpace = nameVectorSpaces.get(entityType);
			NormalizationModel entityModel;
			NormalizationModelPredictor trainingNormalizationPredictor;
			if (options.valueOf(averageNormalizationModels)) {
				entityModel = new AveragedNormalizationModel(entityIndex, mentionVectorSpace, nameVectorSpace, trainingProgressTracker);
				trainingNormalizationPredictor = ((AveragedNormalizationModel) entityModel).getTrainingPredictor();
			} else {
				entityModel = new NormalizationModel(entityIndex, mentionVectorSpace, nameVectorSpace, trainingProgressTracker);
				trainingNormalizationPredictor = entityModel;
			}
			entityModels.put(entityType, entityModel);
			if (options.valueOf(normalizationCacheSize) > 0) {
				CachedNormalizationModel entityTrainingModel = new CachedNormalizationModel(trainingNormalizationPredictor, entityModel, options.valueOf(normalizationCacheSize));
				normalizationTrainingPredictionModels.put(entityType, entityTrainingModel);
				normalizationUpdaterModels.put(entityType, entityTrainingModel);
			} else {
				normalizationTrainingPredictionModels.put(entityType, trainingNormalizationPredictor);
				normalizationUpdaterModels.put(entityType, entityModel);
			}
		}

		RecognitionModel recognitionModel;
		RecognitionModelPredictor trainingRecognitionPredictor;
		if (options.valueOf(averageRecognitionModel)) {
			recognitionModel = new AveragedRecognitionModel(featureSet, entityStates, trainingProgressTracker);
			trainingRecognitionPredictor = ((AveragedRecognitionModel) recognitionModel).getTrainingPredictor();
		} else {
			recognitionModel = new RecognitionModel(featureSet, entityStates, trainingProgressTracker);
			trainingRecognitionPredictor = recognitionModel;
		}
		logger.info("Elapsed = " + (System.currentTimeMillis() - start));
		Profiler.print("");

		logger.info("Preparing evaluation setup");
		start = System.currentTimeMillis();
		Map<String, NormalizationModelPredictor> normalizationEvaluationPredictionModels = new HashMap<String, NormalizationModelPredictor>();
		for (String entityType : entityTypeSet) {
			NormalizationModelPredictor entityModel = entityModels.get(entityType);
			if (options.valueOf(normalizationCacheSize) > 0) {
				CachedNormalizationModel entityEvaluationModel = new CachedNormalizationModel(entityModel, options.valueOf(normalizationCacheSize));
				normalizationEvaluationPredictionModels.put(entityType, entityEvaluationModel);
			} else {
				normalizationEvaluationPredictionModels.put(entityType, entityModel);
			}
		}
		TextInstanceProcessingPipeline annotationPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("AnnotationPipeline", 10), tokenizer, segmenter,
				new FeatureInstantiator(SparseVector.factory, featureSet, featureProcessors), abbreviationResolverProcessor, new SegmentMentionProcessor(mentionConverter),
				new Annotator(lexicon, recognitionModel, normalizationEvaluationPredictionModels), new PredictedStatesToAnnotationConverter());
		TextInstanceProcessingPipeline preEvaluationPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("PreEvaluationPipeline", 10), new Annotator(lexicon, recognitionModel, normalizationEvaluationPredictionModels),
				new PredictedStatesToAnnotationConverter());
		TextInstanceProcessingPipeline postEvaluationPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("PostEvaluationPipeline", 10), new ErrorAnalyzer(normalizationEvaluationPredictionModels),
				new InstanceElementClearer(EnumSet.of(InstanceElement.PredictedStates, InstanceElement.PredictedAnnotations)));

		List<EvaluationProcessor> evaluationProcessors = new ArrayList<EvaluationProcessor>();
		evaluationProcessors.add(new AnnotationLevelEvaluationProcessor("PERFORMANCE", Condition.EXACT_BOUNDARY, Condition.ENTITY_CLASS));
		evaluationProcessors.add(new InstanceLevelEvaluationProcessor("PERFORMANCE"));
		List<EvaluationProcessor> additionalProcessors = new ArrayList<EvaluationProcessor>();
		additionalProcessors.add(new AnnotationLevelEvaluationProcessor("PERFORMANCE", Condition.EXACT_BOUNDARY, Condition.ENTITY_ID));
		additionalProcessors.add(new PerfectNERInstanceLevelEvaluationProcessor("PERFORMANCE", normalizationEvaluationPredictionModels));
		EvaluationProcessorStoppingCriteria stoppingCriteria = new EvaluationProcessorStoppingCriteria(options.valueOf(maxTrainingIterations), options.valueOf(iterationsPastLastImprovement), trainingProgressTracker, holdoutInstances,
				preEvaluationPipeline, postEvaluationPipeline, annotationPipeline, options.valueOf(modelOutputFilename), evaluationProcessors, additionalProcessors);

		logger.info("Elapsed = " + (System.currentTimeMillis() - start));

		logger.info("Training");
		start = System.currentTimeMillis();
		OnlineOptimizer optimizer = new MIRAUpdate(lexicon, featureSet, trainingRecognitionPredictor, recognitionModel, normalizationTrainingPredictionModels, normalizationUpdaterModels, options.valueOf(regularization),
				options.valueOf(maxStepSize), options.valueOf(solverTimeout), options.valueOf(topNLabelings), options.valueOf(topNNormalization), options.valueOf(enforceNonNegativeDiagonal));
		Annotator annotator = new Annotator(lexicon, trainingRecognitionPredictor, normalizationTrainingPredictionModels);
		AnnotationModelTrainingIteration trainingIteration = new AnnotationModelTrainingIteration(annotator, normalizationTrainingPredictionModels, optimizer, trainingProgressTracker);
		int trainingPipelineReportingIncrement = options.valueOf(useSentenceBreaker) ? 100 : 10;
		TextInstanceProcessingPipeline trainingPipeline = new TextInstanceProcessingPipeline(new ProgressReporter("TrainingPipeline", trainingPipelineReportingIncrement), trainingIteration,
				new InstanceElementClearer(EnumSet.of(InstanceElement.PredictedStates, InstanceElement.PredictedAnnotations)));
		Comparator<TextInstance> shuffler = null;
		if (options.valueOf(deterministicOrdering)) {
			shuffler = new AnnotationModelTrainer.DeterministicShuffler(trainingProgressTracker, AnnotationModelTrainer.DEFAULT_HASH_SEEDS);
		}
		AnnotationModelTrainer trainer = new AnnotationModelTrainer(trainingPipeline, stoppingCriteria, trainingProgressTracker, shuffler);
		trainer.processAll(trainingInstances);
		logger.info("Training complete.");
		logger.info("Highest evaluation score= " + stoppingCriteria.getHighestScore());
		logger.info("Total elapsed time= " + (System.currentTimeMillis() - start));
		Profiler.print("");
		// TODO How to provide exact replication of processing pipeline that is still configurable (eg topN)?
		logger.info("Done.");
	}

	public static List<TextInstance> getInstances(List<String> datasetConfigs, Lexicon lexicon) {
		List<TextInstance> instances = new ArrayList<TextInstance>();
		for (String datasetConfig : datasetConfigs) {
			String[] datasetFields = datasetConfig.split("\\|");
			try {
				Dataset dataset = (Dataset) Class.forName(datasetFields[0]).newInstance();
				dataset.setArgs(lexicon, datasetFields);
				instances.addAll(dataset.getInstances());
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		}
		return instances;
	}

	public static List<FeatureProcessor> getFeatureProcessors(int maxSegmentLength) {
		List<FeatureProcessor> processors = new ArrayList<FeatureProcessor>();

		// Token level features
		processors.add(new TokenFeatureProcessor("W", new LowerCaseStringProcessor(), true));
		processors.add(new TokenFeatureProcessor("STEM", new StringProcessingPipeline(new LowerCaseStringProcessor(), new PorterStemmer()), true));
		processors.add(new TokenFeatureProcessor("WC", new CharacterClassStringProcessor(false), true));
		processors.add(new TokenFeatureProcessor("BWC", new CharacterClassStringProcessor(true), true));
		processors.add(new TokenFeatureProcessor("NC", new NumberClassStringProcessor(false), false));
		processors.add(new TokenFeatureProcessor("BNC", new NumberClassStringProcessor(true), false));
		processors.add(new CharNGramFeatureProcessor("2GM", 2));
		processors.add(new CharNGramFeatureProcessor("3GM", 3));
		processors.add(new CharNGramFeatureProcessor("4GM", 4));
		HepplePOSTaggerFactory taggerFactory = new HepplePOSTaggerFactory("nlpdata/tagger");
		processors.add(new POSFeatureProcessor("POS", taggerFactory)); // TODO Configure

		// Segment level features
		processors.add(new BiasFeatureProcessor("B"));
		processors.add(new SurroundingCharactersFeatureProcessor("SC", new CharacterClassStringProcessor(true)));
		processors.add(new UnbalancedParenFeatureProcessor("UP"));
		processors.add(new SegmentLengthFeatureProcessor("LEN", maxSegmentLength));
		processors.add(new SegmentPatternFeatureProcessor("ALPHA1", Pattern.compile("[A-Za-z]+")));
		processors.add(new SegmentPatternFeatureProcessor("ALPHA2", Pattern.compile("[A-Za-z ]+")));
		processors.add(new SegmentPatternFeatureProcessor("ALLCAPS1", Pattern.compile("[A-Z]+")));
		processors.add(new SegmentPatternFeatureProcessor("ALLCAPS2", Pattern.compile("[A-Z ]+")));
		processors.add(new SegmentPatternFeatureProcessor("ALLLOWER1", Pattern.compile("[a-z]+")));
		processors.add(new SegmentPatternFeatureProcessor("ALLLOWER2", Pattern.compile("[a-z ]+")));
		processors.add(new SegmentPatternFeatureProcessor("INITCAP1", Pattern.compile("[A-Z][a-z]*")));
		processors.add(new SegmentPatternFeatureProcessor("INITCAP2", Pattern.compile("[A-Z][a-z]*( [A-Z][a-z ]*)*")));
		processors.add(new SegmentPatternFeatureProcessor("INTEGER", Pattern.compile("[0-9]+")));
		processors.add(new SegmentPatternFeatureProcessor("CAPSDIGITS", Pattern.compile("[A-Z]+[0-9]+")));
		processors.add(new SegmentPatternFeatureProcessor("REALNUMBER_S", Pattern.compile("[0-9]+\\.[0-9]+")));
		processors.add(new StartEndTokenFeatureProcessor("T", new LowerCaseStringProcessor()));
		processors.add(new SurroundingTokensFeatureProcessor("SW", 3, new LowerCaseStringProcessor()));
		// processors.add(new SurroundingPOSFeatureProcessor("POS_S", 2, taggerFactory));// TODO Configure

		// Features equivalent to BANNER
		processors.add(new TokenPatternFeatureProcessor("ALPHA", Pattern.compile("[A-Za-z]+")));
		processors.add(new TokenPatternFeatureProcessor("INITCAPS", Pattern.compile("[A-Z].*")));
		processors.add(new TokenPatternFeatureProcessor("UPPER-LOWER", Pattern.compile("[A-Z][a-z].*")));
		processors.add(new TokenPatternFeatureProcessor("LOWER-UPPER", Pattern.compile("[a-z]+[A-Z]+.*")));
		processors.add(new TokenPatternFeatureProcessor("ALLCAPS", Pattern.compile("[A-Z]+")));
		processors.add(new TokenPatternFeatureProcessor("MIXEDCAPS", Pattern.compile("[A-Z][a-z]+[A-Z][A-Za-z]*")));
		processors.add(new TokenPatternFeatureProcessor("SINGLECHAR", Pattern.compile("[A-Za-z]")));
		processors.add(new TokenPatternFeatureProcessor("SINGLEDIGIT", Pattern.compile("[0-9]")));
		processors.add(new TokenPatternFeatureProcessor("DOUBLEDIGIT", Pattern.compile("[0-9][0-9]")));
		processors.add(new TokenPatternFeatureProcessor("NUMBER", Pattern.compile("[0-9,]+")));
		processors.add(new TokenPatternFeatureProcessor("HASDIGIT", Pattern.compile(".*[0-9].*")));
		processors.add(new TokenPatternFeatureProcessor("ALPHANUMERIC", Pattern.compile(".*[0-9].*[A-Za-z].*")));
		processors.add(new TokenPatternFeatureProcessor("ALPHANUMERIC", Pattern.compile(".*[A-Za-z].*[0-9].*")));
		processors.add(new TokenPatternFeatureProcessor("NUMBERS_LETTERS", Pattern.compile("[0-9]+[A-Za-z]+")));
		processors.add(new TokenPatternFeatureProcessor("LETTERS_NUMBERS", Pattern.compile("[A-Za-z]+[0-9]+")));
		processors.add(new TokenPatternFeatureProcessor("REALNUMBER", Pattern.compile("(-|\\+)?[0-9,]+(\\.[0-9]*)?%?")));
		processors.add(new TokenPatternFeatureProcessor("REALNUMBER", Pattern.compile("(-|\\+)?[0-9,]*(\\.[0-9]+)?%?")));
		processors.add(new TokenPatternFeatureProcessor("ROMAN", Pattern.compile("[IVXDLCM]+", Pattern.CASE_INSENSITIVE)));
		processors.add(new TokenPatternFeatureProcessor("GREEK", Pattern.compile(GREEK, Pattern.CASE_INSENSITIVE)));
		processors.add(new TokenPatternFeatureProcessor("ISPUNCT", Pattern.compile("[`~!@#$%^&*()-=_+\\[\\]\\\\{}|;\':\\\",./<>?]+")));

		String ElementAbbrs = "(H|He|Li|Be|B|C|N|O|F|Ne|Na|Mg|Al|Si|P|S|Cl|Ar|K|Ca|Sc|Ti|V|Cr|Mn|Fe|Co|Ni|Cu|Zn|Ga|Ge|As|Se|Br|Kr|Rb|Sr|Y|Zr|Nb|Mo|Tc|Ru|Rh|Pd|Ag|Cd|In|Sn|SbTe|I|Xe|Cs|Ba|La|Ce|Pr|Nd|Pm|Sm|Eu|Gd|Tb|Dy|Ho|Er|Tm|Yb|Lu|Hf|Ta|W|Re|Os|Ir|Pt|Au|Hg|Tl|Pb|Bi|Po|At|Rn|Fr|Ra|Ac|Th|Pa|U|Np|Pu|Am|Cm|Bk|Cf)";
		String Elements = "(Hydrogen|Helium|Lithium|Beryllium|Boron|Carbon|Nitrogen|Oxygen|Fluorine|Neon|Sodium|Magnesium|Aluminum|Aluminium|Silicon|Phosphorus|Sulfur|Chlorine|Argon|Potassium|Calcium|Scandium|Titanium|Vanadium|Chromium|Manganese|Iron|Cobalt|Nickel|Copper|Zinc|Gallium|Germanium|Arsenic|Selenium|Bromine|Krypton|Rubidium|Strontium|Yttrium|Zirconium|Niobium|Molybdenum|Technetium|Ruthenium|Rhodium|Palladium|Silver|Cadmium|Indium|Tin|Antimony|Tellurium|Iodine|Xenon|Cesium|Barium|Lanthanum|Cerium|Praseodymium|Neodymium|Promethium|Samarium|Europium|Gadolinium|Terbium|Dysprosium|Holmium|Erbium|Thulium|Ytterbium|Lutetium|Hafnium|Tantalum|Tungsten|Rhenium|Osmium|Iridium|Platinum|Gold|Mercury|Thallium|Lead|Bismuth|Polonium|Astatine|Radon|Francium|Radium|Actinium|Thorium|Protactinium|Uranium|Neptunium|Plutonium|Americium|Curium|Berkelium|Californium)";
		String AminoAcidLong = "(Alanine|Arginine|Asparagine|Aspartic|Cysteine|Glutamine|Glutamic|Glycine|Histidine|Isoleucine|Leucine|Lysine|Methionine|Phenylalanine|Proline|Serine|Threonine|Tryptophan|Tyrosine|Valine)";
		String AminoAcidMed = "(Ala|Arg|Asn|Asp|Cys|Gln|Glu|Gly|His|Ile|Leu|Lys|Met|Phe|Pro|Ser|Thr|Trp|Tyr|Val|Asx|Glx)";
		String AminoAcidShort = "(A|R|N|D|C|Q|E|G|H|I|L|K|M|F|P|S|T|W|Y|V|B|Z)";

		// Case sensitivity has been considered in the below
		processors.add(new TokenPatternFeatureProcessor("ELEMENT_ABBR", Pattern.compile(ElementAbbrs)));
		processors.add(new TokenPatternFeatureProcessor("FORMULA_PART", Pattern.compile(ElementAbbrs + "+"))); // Full
																												// formulas
																												// are
																												// multi-token
																												// with
																												// the
																												// fine
																												// tokenization
		processors.add(new TokenPatternFeatureProcessor("ELEMENT_NAME", Pattern.compile(Elements)));
		processors.add(new TokenPatternFeatureProcessor("AMINO_LONG", Pattern.compile(AminoAcidLong)));
		processors.add(new TokenPatternFeatureProcessor("AMINO_MED", Pattern.compile(AminoAcidMed, Pattern.CASE_INSENSITIVE)));
		processors.add(new TokenPatternFeatureProcessor("AMINO_SHORT", Pattern.compile(AminoAcidShort)));
		processors.add(new SegmentPatternFeatureProcessor("AMINO_STRING", Pattern.compile(AminoAcidMed + "-(" + AminoAcidMed + "-)*" + AminoAcidMed)));
		processors.add(new SegmentPatternFeatureProcessor("FORMULA_LIKE", Pattern.compile("(" + ElementAbbrs + "|\\(|\\)|\\[|\\]|\\+|-|\\s|[0-9])+")));

		// TODO Percentage of each character class
		// TODO Words surrounding other instances of this segment in the text
		// TODO Distributional features
		// TODO Local dictionary: was this segment tagged earlier in the sequence?
		// TODO Add anatomy tags as input
		// TODO Add gene/protein tags as input
		// TODO Any way to handle coordination ellipsis?
		processors.add(new StartEndClosedClassTokenFeatureProcessor("CC", new HashSet<String>(Arrays.asList(StartEndClosedClassTokenFeatureProcessor.DEFAULT_CLOSED_CLASS))));
		return processors;
	}
}
