package ncbi.taggerOne.processing.string;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SynonymStringProcessor implements StringProcessor {

	private static final Logger logger = LoggerFactory.getLogger(SynonymStringProcessor.class);
	private static final long serialVersionUID = 1L;

	private Map<String, String> mappings;
	private boolean frozen;

	public SynonymStringProcessor() {
		this.mappings = new HashMap<String, String>();
		frozen = false;
	}

	public void addMapping(String fromStr, String toStr) {
		if (frozen) {
			throw new IllegalStateException("Cannot add mappings after freezing");
		}
		String toMapping = mappings.get(toStr);
		if (fromStr.equals(toMapping)) {
			logger.error("Synonym mapping from \"" + fromStr + "\" to \"" + toStr + "\" cannot be added because \"" + toStr + "\" is already mapped to \"" + fromStr + "\"");
		} else {
			if (toMapping != null) {
				logger.warn("Synonym mapping from \"" + fromStr + "\" to \"" + toStr + "\" likely in error because \"" + toStr + "\" is already mapped to \"" + toMapping + "\"");
			}
			String fromMapping = mappings.get(fromStr);
			if (fromMapping != null) {
				logger.warn("Synonym mapping from \"" + fromStr + "\" to \"" + toStr + "\" overwrites mapping to \"" + fromMapping + "\"");
			}
			mappings.put(fromStr, toStr);
		}
	}

	public void freeze() {
		frozen = true;
		for (String fromStr : mappings.keySet()) {
			String toStr = mappings.get(fromStr);
			String toMapping = mappings.get(toStr);
			if (toMapping != null) {
				logger.warn("Synonym mapping from \"" + fromStr + "\" to \"" + toStr + "\" likely in error because \"" + toStr + "\" is already mapped to \"" + toMapping + "\"");
			}
		}

	}

	@Override
	public String process(String fromStr) {
		if (!frozen) {
			throw new IllegalStateException("Cannot use until frozen");
		}
		String toStr = mappings.get(fromStr);
		if (toStr == null) {
			return fromStr;
		}
		return toStr;
	}

}
