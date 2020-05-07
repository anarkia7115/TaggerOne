package ncbi.taggerOne.dataset;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.Entity;
import ncbi.taggerOne.types.TextInstance;
import ncbi.util.StaticUtilMethods;

public class PubtatorDataset implements Dataset {

	// TODO Cleanup and refactor
	private static final Logger logger = LoggerFactory.getLogger(PubtatorDataset.class);

	private Lexicon lexicon;
	private String corpusFilename;
	private String pmidsFilename;
	private String defaultNamespace;
	private Map<String, String> entityTypeMap;
	private Set<String> recognizeOnlyTypes;
	private Set<String> ignoreTypes;

	public PubtatorDataset() {
		// Empty
	}

	@Override
	public void setArgs(Lexicon lexicon, String... args) {
		if (args.length < 6) {
			throw new IllegalArgumentException("PubtatorDataset must have five arguments: corpusFilename, pmidsFilename, entityTypeMap, entityTypeUsageMap, defaultNamespace: " + Arrays.asList(args));
		}
		this.lexicon = lexicon;
		corpusFilename = args[1];
		pmidsFilename = args[2].trim();
		if (pmidsFilename.length() == 0) {
			pmidsFilename = null;
		}
		entityTypeMap = StaticUtilMethods.getStringMap(args[3]);

		// Verify that the targets for entityTypeUsageMap are one of {identify, recognize, ignore}
		Map<String, String> entityTypeUsageMap = StaticUtilMethods.getStringMap(args[4]);
		recognizeOnlyTypes = new HashSet<String>();
		ignoreTypes = new HashSet<String>();
		for (String entityType : entityTypeUsageMap.keySet()) {
			String usageStr = entityTypeUsageMap.get(entityType).toUpperCase(Locale.US);
			if (Dataset.Usage.RECOGNIZE.name().equals(usageStr)) {
				recognizeOnlyTypes.add(entityType);
			} else if (Dataset.Usage.IGNORE.name().equals(usageStr)) {
				ignoreTypes.add(entityType);
			} else if (!Dataset.Usage.IDENTIFY.name().equals(usageStr)) {
				throw new IllegalArgumentException("Unknown usage type for entity type " + entityType);
			}
		}

		// Verify that the values in entityTypeMap are in lexicon and present as a key in entityTypeUsageMap
		Set<String> entityTypes = lexicon.getTypes();
		for (String entityType : entityTypeMap.values()) {
			if (!entityTypeUsageMap.containsKey(entityType)) {
				throw new IllegalArgumentException("All values in entityTypeMap must be present as keys in entityTypeUsageMap: " + entityType);
			}
			if (!entityTypes.contains(entityType) && !ignoreTypes.contains(entityType)) {
				throw new IllegalArgumentException("All values in entityTypeMap must be present in lexicon or listed as Ignore: " + entityType);
			}
		}
		defaultNamespace = args[5];
	}

	@Override
	public List<TextInstance> getInstances() {
		List<Abstract> abstracts = new ArrayList<Abstract>();
		try {
			Set<String> pmids = null;
			if (pmidsFilename != null) {
				pmids = getPMIDs(pmidsFilename);
			}
			PubtatorParser parser = new PubtatorParser(entityTypeMap, corpusFilename);
			Abstract a = parser.getAbstract();
			while (a != null) {
				if (pmids == null || pmids.contains(a.getId()))
					abstracts.add(a);
				a = parser.getAbstract();
			}
			parser.close();
			if (pmids != null && pmids.size() != abstracts.size()) {
				logger.warn("pmids.size() = " + pmids.size() + ", abstracts.size() = " + abstracts.size());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		logger.info("abstracts.size()=" + abstracts.size());

		List<TextInstance> instances = new ArrayList<TextInstance>();
		for (Abstract a : abstracts) {
			List<Tag> tags = a.getTags();
			String id = a.getId();
			String text = a.getText();
			TextInstance textInstance = new TextInstance(id, id, text, 0);
			List<AnnotatedSegment> annotations = new ArrayList<AnnotatedSegment>();
			for (Tag tag : new ArrayList<Tag>(tags)) {
				// logger.info("id=" + tag.id + " type=" + tag.type);
				Set<Entity> entities = null;
				if (!ignoreTypes.contains(tag.type)) {
					if (recognizeOnlyTypes.contains(tag.type)) {
						entities = Collections.singleton(lexicon.getUnknownEntity(tag.type));
					} else {
						entities = getEntities(tag.id, tag.type);
					}
					AnnotatedSegment mention = new AnnotatedSegment(textInstance, tag.start, tag.end, null, entities);
					annotations.add(mention);
				}
			}
			textInstance.setTargetAnnotation(annotations);
			instances.add(textInstance);
		}
		return instances;
	}

	private Set<Entity> getEntities(String idSetStr, String type) {
		if (idSetStr == null) {
			return Collections.singleton(lexicon.getUnknownEntity(type));
		}
		Set<Entity> entities = new HashSet<Entity>();
		String[] idFields = idSetStr.split("\\||,");
		for (String id : idFields) {
			String entityId = id;
			if (entityId != null) {
				if (entityId.equals("-1")) {
					entityId = null;
				}
			}
			if (entityId != null) {
				if (entityId.contains("|") || entityId.contains("+") || entityId.equals("")) {
					logger.warn("Ignoring identifier \"" + entityId + "\"");
					// FIXME Handle composite mentions
					entityId = null;
				} else if (!entityId.contains(":")) {
					entityId = defaultNamespace + ":" + entityId;
				}
			}
			Entity entity = lexicon.getUnknownEntity(type);
			if (entityId != null) {
				entity = lexicon.getEntity(entityId);
			}
			// TODO Check entity against type
			if (entity == null) {
				logger.error("Lexicon does not contain entity " + entityId + " of type " + type);
				entity = lexicon.getUnknownEntity(type);
			}
			entities.add(entity);
		}
		return entities;
	}

	private static Set<String> getPMIDs(String filename) throws IOException {
		Set<String> pmids = new HashSet<String>();
		BufferedReader dataFile = new BufferedReader(new FileReader(filename));
		String line = dataFile.readLine();
		while (line != null) {
			line = line.trim();
			if (line.length() > 0)
				pmids.add(line);
			line = dataFile.readLine();
		}
		dataFile.close();
		return pmids;
	}

	private static class PubtatorParser {

		private Map<String, String> entityTypeMap;
		private BufferedReader reader;
		private String currentLine;

		public PubtatorParser(Map<String, String> entityTypeMap, String dataFilename) throws IOException {
			this.entityTypeMap = entityTypeMap;
			reader = new BufferedReader(new FileReader(dataFilename));
			nextLine();
		}

		public void close() throws IOException {
			reader.close();
		}

		public Abstract getAbstract() throws IOException {
			if (currentLine == null)
				return null;
			Abstract a = new Abstract();
			getTitleText(a);
			getAbstractText(a);
			Tag t = getTag(a);
			while (t != null) {
				a.addTag(t);
				t = getTag(a);
			}
			return a;
		}

		private void getTitleText(Abstract a) throws IOException {
			String[] split = currentLine.split("\\|");
			if (split.length != 3)
				throw new IllegalArgumentException("Invalid title text=\"" + currentLine + "\"");
			a.setId(split[0]);
			if (!split[1].equals("t"))
				throw new IllegalArgumentException("Invalid title text=\"" + currentLine + "\"");
			a.setTitleText(split[2]);
			nextLine();
		}

		private void getAbstractText(Abstract a) throws IOException {
			String[] split = currentLine.split("\\|");
			if (split.length != 3)
				throw new IllegalArgumentException("Invalid abstract text=\"" + currentLine + "\"");
			if (!split[0].equals(a.getId()))
				throw new IllegalArgumentException("Invalid abstract text=\"" + currentLine + "\"");
			if (!split[1].equals("a"))
				throw new IllegalArgumentException("Invalid abstract text=\"" + currentLine + "\"");
			a.setAbstractText(split[2]);
			nextLine();
		}

		private Tag getTag(Abstract a) throws IOException {
			String line = currentLine;
			if (line == null) {
				return null;
			}
			if (!line.startsWith(a.getId())) {
				// This indicates that it found a new abstract
				return null;
			}
			String[] split = line.split("\t");
			if (split.length < 5 || split.length > 7) {
				throw new IllegalArgumentException("Annotations should have between 5 and 7 values separated by tabs in abstract " + a.getId() + " text=\"" + line + "\"");
			}
			if (!split[0].equals(a.getId()))
				throw new IllegalArgumentException();
			int start = Integer.parseInt(split[1]);
			int end = Integer.parseInt(split[2]);
			String text = a.getSubText(start, end);
			if (!split[3].equals(text)) {
				throw new IllegalArgumentException("Text from mention definition (\"" + split[3] + "\") does not match text specified by mention boundaries (\"" + text + "\") in abstract "
						+ a.getId());
			}
			if (!text.equals(text.trim())) {
				throw new IllegalArgumentException("Mention text cannot begin or end with whitespace (\"" + text + "\") in abstract " + a.getId());
			}
			String typeText = mapEntityType(split[4]);
			String id = null;
			if (split.length > 5) {
				id = split[5].trim();
			}
			Tag t = new Tag(id, typeText, start, end);
			nextLine();
			return t;
		}

		private String mapEntityType(String entityType) {
			if (!entityTypeMap.containsKey(entityType)) {
				throw new RuntimeException("Entity type map does not contain key \"" + entityType + "\"");
			}
			return entityTypeMap.get(entityType);
		}

		private String nextLine() throws IOException {
			do {
				currentLine = reader.readLine();
				if (currentLine != null)
					currentLine = currentLine.trim();
			} while (currentLine != null && currentLine.length() == 0);
			return currentLine;
		}
	}

	private static class Abstract {
		private String id;
		private List<Tag> tags;
		private String text;

		public Abstract() {
			tags = new ArrayList<Tag>();
			text = "";
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public void setTitleText(String titleText) {
			text += titleText + " ";
		}

		public void setAbstractText(String abstractText) {
			text += abstractText;
		}

		public List<Tag> getTags() {
			return tags;
		}

		public void addTag(Tag tag) {
			tags.add(tag);
		}

		public String getSubText(int start, int end) {
			return text.substring(start, end);
		}

		public String getText() {
			return text;
		}
	}

	private static class Tag {
		public String id;
		public String type;
		public int start;
		public int end;

		public Tag(String id, String type, int start, int end) {
			this.id = id;
			this.type = type;
			this.start = start;
			this.end = end;
		}
	}
}
