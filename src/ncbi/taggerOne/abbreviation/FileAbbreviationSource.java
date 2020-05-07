package ncbi.taggerOne.abbreviation;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.T1Constants;

public class FileAbbreviationSource implements AbbreviationSource {

	private static final Logger logger = LoggerFactory.getLogger(FileAbbreviationSource.class);

	private static final long serialVersionUID = 1L;

	private Map<String, Map<String, String>> abbreviations;

	public FileAbbreviationSource() {
		abbreviations = new HashMap<String, Map<String, String>>();
	}

	@Override
	public void setArgs(String... args) {
		if (args.length < 2) {
			throw new IllegalArgumentException("FileAbbreviationSource must have at least one argument: filename" + Arrays.asList(args));
		}
		for (int i = 1; i < args.length; i++) {
			String filename = args[i];
			loadAbbreviations(filename);
		}
	}

	public void loadAbbreviations(String filename) {
		try {
			int count = 0;
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), T1Constants.UTF8_FORMAT));
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.length() > 0) {
					String[] split = line.split("\\t");
					createAbbreviation(split[0], split[1], split[2]);
					count++;
				}
				line = reader.readLine();
			}
			reader.close();
			logger.info("Loaded " + count + " abbreviations from file " + filename);
		} catch (IOException e) {
			// TODO Improve exception handling
			throw new RuntimeException(e);
		}
	}

	private void createAbbreviation(String pmid, String shortForm, String longForm) {
		Map<String, String> abbreviation = abbreviations.get(pmid);
		if (abbreviation == null) {
			abbreviation = new HashMap<String, String>();
			abbreviations.put(pmid, abbreviation);
		}
		Pattern pattern = Pattern.compile("\\b" + Pattern.quote(shortForm) + "\\b");
		Matcher matcher = pattern.matcher(longForm);
		if (matcher.find()) {
			logger.warn("Ignoring abbreviation \"" + shortForm + "\" -> \"" + longForm + "\" because long form contains short form");
		} else if (abbreviation.containsKey(shortForm)) {
			String previousLongForm = abbreviation.get(shortForm);
			if (!previousLongForm.equals(longForm)) {
				logger.warn("Abbreviation \"" + shortForm + "\" -> \"" + longForm + "\" is already defined as \"" + previousLongForm + "\"");
			}
		} else {
			abbreviation.put(shortForm, longForm);
		}
	}

	@Override
	public Map<String, String> getAbbreviations(String id, String text) {
		Map<String, String> abbr = abbreviations.get(id);
		if (abbr == null) {
			return new HashMap<String, String>();
		}
		return abbr;
	}

}
