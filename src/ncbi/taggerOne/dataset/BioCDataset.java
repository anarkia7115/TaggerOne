package ncbi.taggerOne.dataset;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import bioc.BioCDocument;
import bioc.BioCPassage;
import bioc.io.woodstox.ConnectorWoodstox;
import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.lexicon.Lexicon;
import ncbi.taggerOne.types.AnnotatedSegment;
import ncbi.taggerOne.types.TextInstance;
import ncbi.util.StaticUtilMethods;

public class BioCDataset implements Dataset {

	private Lexicon lexicon;
	private String filename;
	private String defaultNamespace;
	private Map<String, String> entityTypeMap;
	private Set<String> recognizeOnlyTypes;
	private Set<String> ignoreTypes;

	public BioCDataset() {
		// Empty
	}

	@Override
	public void setArgs(Lexicon lexicon, String... args) {
		if (args.length < 5) {
			throw new IllegalArgumentException("BioCDataset must have four arguments: filename, entityTypeMap, entityTypeUsageMap, defaultNamespace: " + Arrays.asList(args));
		}
		this.lexicon = lexicon;
		this.filename = args[1];
		this.entityTypeMap = StaticUtilMethods.getStringMap(args[2]);

		// Verify that the targets for entityTypeUsageMap are one of {identify, recognize, ignore}
		Map<String, String> entityTypeUsageMap = StaticUtilMethods.getStringMap(args[3]);
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
		List<TextInstance> instances = new ArrayList<TextInstance>();
		ConnectorWoodstox connector = new ConnectorWoodstox();
		try {
			connector.startRead(new InputStreamReader(new FileInputStream(filename), T1Constants.UTF8_FORMAT));
			while (connector.hasNext()) {
				BioCDocument document = connector.next();
				String documentId = document.getID();
				int counter = 0;
				for (BioCPassage passage : document.getPassages()) {
					int offset = passage.getOffset();
					String counterText = Integer.toString(counter);
					while (counterText.length() < 2) {
						counterText = "0" + counterText;
					}
					TextInstance instance = new TextInstance(documentId, documentId + "-" + counter, passage.getText(), offset);
					// FIXME Add annotations
					instance.setTargetAnnotation(new ArrayList<AnnotatedSegment>());
					instances.add(instance);
				}
			}
		} catch (IOException e) {
			// TODO Improve error handling
			throw new RuntimeException(e);
		}
		return instances;
	}
}
