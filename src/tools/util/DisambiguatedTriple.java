package tools.util;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Arrays;

public class DisambiguatedTriple {
	public static final int DOMAIN = 1;
	public static final int RANGE = 2;
	public static final int RELATION = 0;

	private int consistent_;
	private DisambiguatedTopic[] values_ = new DisambiguatedTopic[3];

	/**
	 * Constructor for a new DisambiguatedTriple as a clone of an existing
	 * {@link DisambiguatedTriple}.
	 *
	 * @param triple
	 *            The triple to clone.
	 */
	public DisambiguatedTriple(DisambiguatedTriple triple) {
		values_ = Arrays.copyOf(triple.values_, 3);
		consistent_ = triple.consistent_;
	}

	public DisambiguatedTriple(String relation, String domain, String range) {
		WMISocket wmi = ResourceAccess.requestWMISocket();
		OntologySocket ontology = ResourceAccess.requestOntologySocket();
		values_[RELATION] = DisambiguatedTopic.getCreateDisambiguatedTopic(
				relation, wmi, ontology);
		values_[DOMAIN] = DisambiguatedTopic.getCreateDisambiguatedTopic(
				domain, wmi, ontology);
		values_[RANGE] = DisambiguatedTopic.getCreateDisambiguatedTopic(range,
				wmi, ontology);
	}

	private DisambiguatedTopic getElement(int i) {
		return values_[i];
	}

	public DisambiguatedTopic getDomain() {
		return getElement(DOMAIN);
	}

	public DisambiguatedTopic getRange() {
		return getElement(RANGE);
	}

	public DisambiguatedTopic getRelation() {
		return getElement(RELATION);
	}

	public int isConsistent() {
		return consistent_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(values_);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DisambiguatedTriple other = (DisambiguatedTriple) obj;
		if (!Arrays.equals(values_, other.values_))
			return false;
		return true;
	}

	public void markConsistent(boolean b) {
		consistent_ = (b) ? 1 : -1;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean rawText) {
		return values_[DOMAIN].toString(rawText) + "#"
				+ values_[RELATION].toString(rawText) + "#"
				+ values_[RANGE].toString(rawText);
	}
}
