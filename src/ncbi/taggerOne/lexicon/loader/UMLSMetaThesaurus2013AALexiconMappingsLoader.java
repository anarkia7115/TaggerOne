package ncbi.taggerOne.lexicon.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.lexicon.LexiconMappings;
import ncbi.util.Profiler;
import ncbi.util.StaticUtilMethods;

public class UMLSMetaThesaurus2013AALexiconMappingsLoader implements LexiconMappingsLoader {
	// FIXME Upgrade to 2015AA
	// Note that this class is designed to add term variants in the UMLS to a set of existing identifiers and mappings

	protected static final Logger logger = LoggerFactory.getLogger(UMLSMetaThesaurus2013AALexiconMappingsLoader.class);
	public static final String UMLS_METATHESAURUS_NAMESPACE = "UMLS_MT";

	private String conceptNamesFolder;
	private Set<String> allowedLang;
	private Set<String> allowedPref;
	private Set<String> allowedSupp;

	private Map<String, String> cuiToLexiconIdentifier;
	private Map<String, String> cuiToLexiconType;

	public UMLSMetaThesaurus2013AALexiconMappingsLoader() {
		// Empty
	}

	public void setArgs(String... args) {
		if (args.length != 5) {
			throw new IllegalArgumentException("UMLSMetaThesaurus2013AALexiconMappingsLoader must have four arguments: conceptNamesFolder and lexiconFilename");
		}
		this.conceptNamesFolder = args[1];
		this.allowedLang = StaticUtilMethods.getStringSet(args[2]);
		this.allowedPref = StaticUtilMethods.getStringSet(args[3]);
		this.allowedSupp = StaticUtilMethods.getStringSet(args[4]);
	}

	private void checkArgs() {
		if (conceptNamesFolder == null || allowedLang == null || allowedPref == null || allowedSupp == null) {
			throw new IllegalStateException("Cannot use CTDDiseaseLexiconMappingsLoader until args are set");
		}
	}

	// Format for MRCONSO.RRF:
	// 0 CUI|Unique identifier for concept||8|8.00|8|MRCONSO.RRF|char(8)|
	// 1 LAT|Language of Term(s)||3|3.00|3|MRCONSO.RRF|char(3)|
	// 2 TS|Term status||1|1.00|1|MRCONSO.RRF|char(1)|
	// 3 LUI|Unique identifier for term||8|8.14|9|MRCONSO.RRF|varchar(10)|
	// 4 STT|String type||2|2.01|3|MRCONSO.RRF|varchar(3)|
	// 5 SUI|Unique identifier for string||8|8.34|9|MRCONSO.RRF|varchar(10)|
	// 6 ISPREF|Indicates whether AUI is preferred||1|1.00|1|MRCONSO.RRF|char(1)|
	// 7 AUI|Unique identifier for atom||8|8.66|9|MRCONSO.RRF|varchar(9)|
	// 8 SAUI|Source asserted atom identifier||0|2.37|18|MRCONSO.RRF|varchar(50)|
	// 9 SCUI|Source asserted concept identifier||0|5.35|50|MRCONSO.RRF|varchar(50)|
	// 10 SDUI|Source asserted descriptor identifier||0|2.08|13|MRCONSO.RRF|varchar(50)|
	// 11 SAB|Source abbreviation||2|5.63|15|MRCONSO.RRF|varchar(40)|
	// 12 TTY|Term type in source||2|2.33|11|MRCONSO.RRF|varchar(20)|
	// 13 CODE|Unique Identifier or code for string in source||1|7.29|63|MRCONSO.RRF|varchar(100)|
	// 14 STR|String||1|36.95|2916|MRCONSO.RRF|varchar(3000)|
	// 15 SRL|Source Restriction Level||1|1.00|1|MRCONSO.RRF|integer|
	// 16 SUPPRESS|Suppressible flag||1|1.00|1|MRCONSO.RRF|char(1)|
	// 17 CVF|Content view flag||0|1.05|5|MRCONSO.RRF|varchar(50)|

	// Examples of MRCONSO.RRF:
	// C0026896|ENG|P|L0026896|PF|S0064394|N|A0088969||M0014279|D009157|MSH|MH|D009157|Myasthenia Gravis|0|N|1792|
	// C0751339|ENG|P|L1410159|PF|S1687407|Y|A7798239||M0335255|D009157|MSH|PEN|D009157|Myasthenia Gravis, Generalized|0|N|256|
	// C0751339|ENG|P|L1410159|VO|S1687406|Y|A1648395||M0335255|D009157|MSH|PM|D009157|Generalized Myasthenia Gravis|0|N||
	// C0751340|ENG|P|L1410361|PF|S1687722|Y|A7798265||M0335256|D009157|MSH|PEN|D009157|Myasthenia Gravis, Ocular|0|N|1792|
	// C0751340|ENG|P|L1410361|VO|S1687723|Y|A1648702||M0335256|D009157|MSH|PM|D009157|Ocular Myasthenia Gravis|0|N|1536|

	// Format for MRSTY.RRF:
	// 0 CUI|Unique identifier for concept
	// 1 TUI|Unique identifier of semantic type
	// ... (other stuff we are ignoring)

	// Examples of MRSTY.RRF:
	// C0000005|T116|||||
	// C0000005|T121|||||
	// C0000005|T130|||||
	// C0000039|T119|||||
	// C0000039|T121|||||

	private void preload(LexiconMappings lexiconMappings) {
		try {
			Profiler.start("UMLSMetaThesaurus2013AALexiconMappingsLoader.preload()");
			cuiToLexiconIdentifier = new HashMap<String, String>();
			cuiToLexiconType = new HashMap<String, String>();
			MultiFileReader reader = new MultiFileReader(conceptNamesFolder, "MRCONSO.RRF");
			String line = reader.readLine();
			int lineNum = 0;
			LineFieldParser parser = new LineFieldParser();
			while (line != null) {
				parser.init(line);
				String CUI = parser.getField(0);
				boolean add = true;
				add &= (allowedLang == null) || (allowedLang.contains(parser.getField(1))); // Language
				add &= (allowedPref == null) || (allowedPref.contains(parser.getField(6))); // Preferred
				String SDUI = parser.getField(10); // "Source asserted descriptor identifier"
				String SAB = parser.getField(11); // "Source abbreviation"
				String CODE = parser.getField(13); // "Unique Identifier or code for string in source"
				add &= (allowedSupp == null) || (allowedSupp.contains(parser.getField(16))); // Suppressed
				if (add) {
					String identifier = getFullIdentifier(SAB, SDUI);
					if (identifier != null) {
						String entityType = lexiconMappings.getIdentifierType(identifier);
						if (entityType != null) {
							cuiToLexiconIdentifier.put(CUI, identifier);
							cuiToLexiconType.put(CUI, entityType);
						}
					}
					identifier = getFullIdentifier(SAB, CODE);
					if (identifier != null) {
						String entityType = lexiconMappings.getIdentifierType(identifier);
						if (entityType != null) {
							cuiToLexiconIdentifier.put(CUI, identifier);
							cuiToLexiconType.put(CUI, entityType);
						}
					}
				}
				line = reader.readLine();
				lineNum++;
				if (lineNum % 1000000 == 0) {
					logger.info("preload() Line: " + lineNum + " Entries: " + cuiToLexiconIdentifier.size()); // TODO Modify this output to look more like
				}
			}
			reader.close();
			Profiler.stop("UMLSMetaThesaurus2013AALexiconMappingsLoader.preload()");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void loadIdentifiers(LexiconMappings lexiconMappings) {
		checkArgs();
		preload(lexiconMappings);
		Profiler.start("UMLSMetaThesaurus2013AALexiconMappingsLoader.loadIdentifiers()");
		int lineNum = 0;
		for (String cui : cuiToLexiconIdentifier.keySet()) {
			String entityType = cuiToLexiconType.get(cui);
			lexiconMappings.addIdentifier(UMLS_METATHESAURUS_NAMESPACE + ":" + cui, entityType, false);
			lineNum++;
			if (lineNum % 100000 == 0) {
				logger.info("loadIdentifiers() Line: " + lineNum + " Entries: " + cuiToLexiconIdentifier.size()); // TODO Modify this output to look more like
			}
		}
		Profiler.stop("UMLSMetaThesaurus2013AALexiconMappingsLoader.loadIdentifiers()");
	}

	private static String getFullIdentifier(String namespace, String identifier) {
		if (namespace == null || identifier == null) {
			return null;
		}
		if (namespace.length() == 0 || identifier.length() == 0) {
			return null;
		}
		if (identifier.equals("NOCODE")) {
			return null;
		}
		String fullIdentifier = identifier;
		String localNamespace = namespace;
		if (localNamespace.equals("MSH")) {
			localNamespace = "MESH"; // FIXME Make this configurable
		}
		localNamespace = localNamespace + ":";
		if (!fullIdentifier.startsWith(localNamespace)) {
			fullIdentifier = localNamespace + fullIdentifier;
		}
		return fullIdentifier;
	}

	@Override
	public void loadIdentifierEquivalencies(LexiconMappings lexiconMappings) {
		checkArgs();
		Profiler.start("UMLSMetaThesaurus2013AALexiconMappingsLoader.loadIdentifierEquivalencies()");
		int lineNum = 0;
		for (String cui : cuiToLexiconIdentifier.keySet()) {
			String identifier = cuiToLexiconIdentifier.get(cui);
			lexiconMappings.addIdentifierEquivalence(UMLS_METATHESAURUS_NAMESPACE + ":" + cui, identifier);
			lineNum++;
			if (lineNum % 100000 == 0) {
				logger.info("loadIdentifierEquivalencies() Line: " + lineNum + " Entries: " + cuiToLexiconIdentifier.size()); // TODO Modify this output to look more like
			}
		}
		Profiler.stop("UMLSMetaThesaurus2013AALexiconMappingsLoader.loadIdentifierEquivalencies()");
	}

	@Override
	public void loadNames(LexiconMappings lexiconMappings) {
		checkArgs();
		try {
			Profiler.start("UMLSMetaThesaurus2013AALexiconMappingsLoader.loadNames()");
			MultiFileReader reader = new MultiFileReader(conceptNamesFolder, "MRCONSO.RRF");
			String line = reader.readLine();
			int lineNum = 0;
			LineFieldParser parser = new LineFieldParser();
			while (line != null) {
				parser.init(line);
				String CUI = parser.getField(0);
				boolean add = cuiToLexiconIdentifier.containsKey(CUI);
				add &= (allowedLang == null) || (allowedLang.contains(parser.getField(1))); // Language
				add &= (allowedPref == null) || (allowedPref.contains(parser.getField(6))); // Preferred
				String STR = parser.getField(14); // String (the actual term)
				add &= (allowedSupp == null) || (allowedSupp.contains(parser.getField(16))); // Suppressed
				if (add) {
					String primaryIdentifier = UMLS_METATHESAURUS_NAMESPACE + ":" + CUI;
					lexiconMappings.addTerm(primaryIdentifier, STR, false); // TODO How to handle preferences?
				}
				line = reader.readLine();
				lineNum++;
				if (lineNum % 100000 == 0) {
					logger.info("loadNames() Line: " + lineNum + " Entries: " + cuiToLexiconIdentifier.size()); // TODO Modify this output to look more like
				}
			}
			reader.close();
			Profiler.stop("UMLSMetaThesaurus2013AALexiconMappingsLoader.loadNames()");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private class MultiFileReader {
		private List<File> files;
		private BufferedReader currentReader;
		private int currentFile;
		private String currentLine;

		public MultiFileReader(String folder, String filename) throws IOException {
			final String filter = filename;
			File dir = new File(folder);
			File[] filesArray = dir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith(filter);
				}
			});
			files = Arrays.asList(filesArray);
			Collections.sort(files);
			currentFile = -1;
			incrementFile();
		}

		public String readLine() throws IOException {
			String nextLine = null;
			if (currentReader != null) {
				nextLine = currentReader.readLine();
				if (nextLine == null) {
					// Handle end of file
					// TODO This assumes that the last line in a file will always be concatenated with the first line of the next file
					String partialLine = currentLine;
					incrementFile();
					if (currentReader != null) {
						currentLine = partialLine + currentLine;
						nextLine = currentReader.readLine();
					} else {
						currentLine = partialLine + currentLine;
					}
				}
			}
			String returnLine = currentLine;
			currentLine = nextLine;
			return returnLine;
		}

		private void incrementFile() throws IOException {
			if (currentReader != null) {
				currentReader.close();
				currentReader = null;
			}
			currentFile++;
			if (currentFile < files.size()) {
				String filename = files.get(currentFile).getCanonicalPath();
				logger.info("Reading from file " + filename);
				if (filename.endsWith(".gz")) {
					currentReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)), T1Constants.UTF8_FORMAT));
				} else {
					currentReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), T1Constants.UTF8_FORMAT));
				}
				currentLine = currentReader.readLine();
			}
		}

		public void close() throws IOException {
			if (currentReader != null) {
				currentReader.close();
				currentReader = null;
			}
			files = null;
			currentLine = null;
		}
	}

	private static class LineFieldParser {
		// Using this class is faster (~20%) than using calls to String.split()
		private String line;
		private int currentField;
		private int beginIndex;
		private int endIndex;

		public LineFieldParser() {
			// Empty
		}

		public void init(String line) {
			this.line = line;
			currentField = 0;
			beginIndex = 0;
			endIndex = line.indexOf("|", beginIndex);
		}

		public String getField(int field) {
			if (field < currentField)
				throw new IllegalStateException("Cannot request a field lower than current field");
			while (currentField < field)
				advance();
			return line.substring(beginIndex, endIndex);
		}

		private void advance() {
			beginIndex = endIndex + 1;
			endIndex = line.indexOf("|", beginIndex);
			if (endIndex == -1)
				endIndex = line.length();
			currentField++;
		}
	}
}
