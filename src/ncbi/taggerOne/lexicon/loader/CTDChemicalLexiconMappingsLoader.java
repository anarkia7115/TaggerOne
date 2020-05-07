package ncbi.taggerOne.lexicon.loader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import ncbi.taggerOne.T1Constants;
import ncbi.taggerOne.lexicon.LexiconMappings;

public class CTDChemicalLexiconMappingsLoader implements LexiconMappingsLoader {

	private String entityType;
	private String lexiconFilename;

	public CTDChemicalLexiconMappingsLoader() {
		// Empty
	}

	@Override
	public void setArgs(String... args) {
		if (args.length != 3) {
			throw new IllegalArgumentException("CTDChemicalLexiconMappingsLoaderFactory must have two arguments: entityType and lexiconFilename");
		}
		this.entityType = args[1];
		this.lexiconFilename = args[2];
	}

	private void checkArgs() {
		if (entityType == null || lexiconFilename == null) {
			throw new IllegalStateException("Cannot use CTDDiseaseLexiconMappingsLoader until args are set");
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
					String id = split[1];
					lexiconMappings.addIdentifier(id, entityType, true);
					// TODO Might consider loading CasRN or Drug bank IDs identifiers
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
		// TODO Might consider loading CasRN or Drug bank IDs identifiers
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
					lexiconMappings.addTerm(identifier, split[0], true); // Primary name
					if (split.length > 7) {
						for (String name : split[7].split("\\|")) {
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
