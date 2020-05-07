package ncbi.taggerOne.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ncbi.taggerOne.types.MentionName;
import ncbi.util.Profiler;

public class AbbreviationResolver implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(AbbreviationResolver.class);

	private static final long serialVersionUID = 1L;

	private Map<String, Map<String, String>> abbreviations;

	public AbbreviationResolver() {
		abbreviations = new HashMap<String, Map<String, String>>();
	}

	public void addAbbreviations(String id, Map<String, String> abbreviation) {
		Map<String, String> current = abbreviations.get(id);
		if (current == null) {
			abbreviations.put(id, abbreviation);
		} else {
			current.putAll(abbreviation);
		}
	}

	public void clear() {
		abbreviations.clear();
	}

	public int size() {
		return abbreviations.size();
	}

	public String expandAbbreviations(String documentId, String lookupText) {
		Map<String, String> abbreviationMap = abbreviations.get(documentId);
		if (abbreviationMap == null) {
			return lookupText;
		}
		String result = lookupText;

		// 1. remove all abbreviations for words that has both abbr and replacement
		for (String abbreviation : abbreviationMap.keySet()) {
			if (result.contains(abbreviation)) {
				String replacement = abbreviationMap.get(abbreviation);
				if (result.contains(replacement)) {
					// Handles mentions like "von Hippel-Lindau (VHL) disease"
					// TODO PERFORMANCE Convert these to use Pattern
					result = result.replaceAll("\\(?\\b" + Pattern.quote(abbreviation) + "\\b\\)?", "");
				} 
			}
		}

		// 2. replace all abbr
		for (String abbreviation : abbreviationMap.keySet()) {
			if (result.contains(abbreviation)) {
				String replacement = abbreviationMap.get(abbreviation);
				// TODO PERFORMANCE Convert these to use Pattern
				result = result.replaceAll("\\(?\\b" + Pattern.quote(abbreviation) + "\\b\\)?", Matcher.quoteReplacement(replacement));
			}
		}

		return result;
	}

	public void expand(String documentId, MentionName mentionName) {
		if (mentionName.isLabel()) {
			return;
		}
		Profiler.start("AbbreviationResolver.expand()");
		String originalText = mentionName.getName();
		String modifiedText = expandAbbreviations(documentId, originalText);
		if (!modifiedText.equals(originalText)) {
			mentionName.setName(modifiedText);
		}
		Profiler.stop("AbbreviationResolver.expand()");
	}
}
