package ncbi.taggerOne.lexicon.loader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.lexicon.LexiconMappings;

public class ElementsLoader implements LexiconMappingsLoader {

	private String entityType;
	private String lexiconFilename;

	/*
	 * File format is: Atomic number \t MeSH identifier \t Standard name \t Symbol \t Alternate spellings
	 */

	public ElementsLoader() {
		// Empty
	}

	@Override
	public void setArgs(String... args) {
		if (args.length != 3) {
			throw new IllegalArgumentException("ElementsLoader must have two arguments: entityType and lexiconFilename");
		}
		this.entityType = args[1];
		this.lexiconFilename = args[2];
	}

	private void checkArgs() {
		if (entityType == null || lexiconFilename == null) {
			throw new IllegalStateException("Cannot use ElementsLoader until args are set");
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
					String[] split = line.split("\t");

					// Get identifiers
					String id = split[1]; // Primary ID
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
		// Empty - this file does not define any identifier equivalencies
	}

	@Override
	public void loadNames(LexiconMappings lexiconMappings) {
		checkArgs();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(lexiconFilename), T1Constants.UTF8_FORMAT));
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (!line.startsWith("#")) {
					String[] split = line.split("\t");

					// Get identifier
					String identifier = split[1];

					// Get names
					lexiconMappings.addTerm(identifier, split[2], true); // Primary name
					lexiconMappings.addTerm(identifier, split[3], false); // Abbreviation
					if (split.length > 4) {
						for (String name : split[4].split("\\|")) {
							name = name.trim();
							if (name.length() > 0) {
								lexiconMappings.addTerm(identifier, name, false); // Alternate names
							}
						}
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
