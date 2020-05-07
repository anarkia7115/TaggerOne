package ncbi.taggerOne.processing.evaluation;

import ncbi.taggerOne.processing.textInstance.TextInstanceProcessor;

public abstract class EvaluationProcessor extends TextInstanceProcessor {

	private static final long serialVersionUID = 1L;

	protected String scoreDetailPrefix;

	public EvaluationProcessor(String scoreDetailPrefix) {
		this.scoreDetailPrefix = scoreDetailPrefix;
	}

	// TODO Refactor to a getScore() method that returns a ScoreKeeper
	public abstract double score();

	public abstract String scoreDetail();

	// TODO Create an n-best recall evaluator

}
