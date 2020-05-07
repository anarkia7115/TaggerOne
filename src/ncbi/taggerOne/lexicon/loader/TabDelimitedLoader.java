package ncbi.taggerOne.lexicon.loader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.lexicon.LexiconMappings;

public class TabDelimitedLoader implements LexiconMappingsLoader {

	private String defaultNamespace;
	private String entityType;
	private String lexiconFilename;

	/*
	 * File format is: Identifier \t Name | Name ... Identifiers may appear on more than one line The first name listed for the identifier will be marked preferred
	 */

	public TabDelimitedLoader() {
		// Empty
	}

	public void setArgs(String... args) {
		if (args.length != 4) {
			throw new IllegalArgumentException("TabDelimitedLoader must have three arguments: default namespace, entityType and lexiconFilename");
		}
		this.defaultNamespace = args[1];
		this.entityType = args[2];
		this.lexiconFilename = args[3];
	}

	private void checkArgs() {
		if (entityType == null || lexiconFilename == null) {
			throw new IllegalStateException("Cannot use TabDelimitedLoader until args are set");
		}
	}

	@Override
	public void loadIdentifiers(LexiconMappings lexiconMappings) {
		checkArgs();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(lexiconFilename), T1Constants.UTF8_FORMAT));
			String line = reader.readLine();

			while (line != null) {
				line = line.trim();
				if (!line.startsWith("#")) {
					// logger.info("LINE1: " + line);
					String[] fields = line.split("\t");

					String id = fields[0]; // ID
					// Add default namespace if no namespace defined
					if (!id.contains(":")) {
						id = defaultNamespace + ":" + id;
					}

					// Adds the identifier as the specified type
					// Readding as same type is ok
					// Throws error if adding as a different type
					lexiconMappings.addIdentifier(id, entityType, true);
				}
				line = reader.readLine();
			}
			reader.close();
		} catch (IOException e) {
			// TODO Improve error handling
			throw new RuntimeException(e);
		}
	}

	@Override
	public void loadIdentifierEquivalencies(LexiconMappings lexiconMappings) {
		// Empty - this format does not define any identifier equivalencies
	}

	@Override
	public void loadNames(LexiconMappings lexiconMappings) {
		checkArgs();
		try {
			Set<String> hasPrimaryName = new HashSet<String>();
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(lexiconFilename), T1Constants.UTF8_FORMAT));
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (!line.startsWith("#")) {
					// logger.info("LINE2: " + line);
					String[] fields = line.split("\t");

					String id = fields[0]; // ID
					// Add default namespace if no namespace defined
					if (!id.contains(":")) {
						id = defaultNamespace + ":" + id;
					}

					// Get names
					String[] names = fields[1].split("\\|");
					for (int nameIndex = 0; nameIndex < names.length; nameIndex++) {
						boolean added = hasPrimaryName.add(id);
						lexiconMappings.addTerm(id, names[nameIndex], added);
					}
				}
				line = reader.readLine();
			}
			reader.close();
		} catch (IOException e) {
			// TODO Improve error handling
			throw new RuntimeException(e);
		}
	}
}
