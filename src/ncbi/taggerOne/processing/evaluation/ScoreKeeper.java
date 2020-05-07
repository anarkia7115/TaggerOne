package ncbi.taggerOne.processing.evaluation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScoreKeeper implements Serializable {

	// TODO Refactor this into micro and macro versions, then use each evaluation processor to just call update

	private static final long serialVersionUID = 1L;

	private int tp;
	private int fp;
	private int fn;

	public ScoreKeeper() {
		tp = 0;
		fp = 0;
		fn = 0;
	}

	public int getTp() {
		return tp;
	}

	public int getFp() {
		return fp;
	}

	public int getFn() {
		return fn;
	}

	public void incrementTp() {
		tp++;
	}

	public void incrementFp() {
		fp++;
	}

	public void incrementFn() {
		fn++;
	}

	public <T> void update(Set<T> goldSet, Set<T> predictedSet) {
		List<T> goldValues = new ArrayList<T>(goldSet);
		List<T> predictedValues = new ArrayList<T>(predictedSet);
		int ntp1 = 0;
		int ntp2 = 0;
		int nfp = 0;
		int nfn = 0;
		for (int i = 0; i < goldValues.size(); i++) {
			T gold = goldValues.get(i);
			boolean found = false;
			for (int j = 0; j < predictedValues.size() && !found; j++) {
				T predicted = predictedValues.get(j);
				if (gold.equals(predicted)) {
					found = true;
				}
			}
			if (found) {
				ntp1++;
			} else {
				nfn++;
			}
		}
		for (int i = 0; i < predictedValues.size(); i++) {
			T predicted = predictedValues.get(i);
			boolean found = false;
			for (int j = 0; j < goldValues.size() && !found; j++) {
				T gold = goldValues.get(j);
				if (gold.equals(predicted)) {
					found = true;
				}
			}
			if (found) {
				ntp2++;
			} else {
				nfp++;
			}
		}
		if (ntp1 != ntp2) {
			throw new RuntimeException("tp1 (" + ntp1 + ") != tp2 (" + ntp2 + ")");
		}
		tp += ntp1;
		fp += nfp;
		fn += nfn;
	}

	public double getP() {
		double p = 1.0;
		if (tp + fp > 0) {
			p = ((double) tp) / (tp + fp);
		}
		return p;
	}

	public double getR() {
		double r = 1.0;
		if (tp + fn > 0) {
			r = ((double) tp) / (tp + fn);
		}
		return r;
	}

	public double getF() {
		double p = getP();
		double r = getR();
		double f = 0.0;
		if (p + r > 0.0) {
			f = 2.0 * p * r / (p + r);
		}
		return f;
	}

	public String scoreDetail() {
		double p = getP();
		double r = getR();
		double f = getF();
		return String.format("tp\t%d\tfp\t%d\tfn\t%d\tp\t%.6f\tr\t%.6f\tf\t%.6f", tp, fp, fn, p, r, f);
	}

}
