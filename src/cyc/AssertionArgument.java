package cyc;

import java.io.Serializable;

public abstract class AssertionArgument implements Serializable {
	private static final long serialVersionUID = 1L;
	/** The temporal context for any assertions using this argument. */
	private OntologyConcept temporalContext_;
	
	public AssertionArgument(AssertionArgument existing) {
		temporalContext_ = existing.temporalContext_;
	}
	
	public AssertionArgument() {
	}

	public OntologyConcept getTemporalContext() {
		return temporalContext_;
	}

	@Override
	public abstract AssertionArgument clone();

	public void setTemporalContext(OntologyConcept context) {
		temporalContext_ = context;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+ ((temporalContext_ == null) ? 0 : temporalContext_.hashCode());
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
		AssertionArgument other = (AssertionArgument) obj;
		if (temporalContext_ == null) {
			if (other.temporalContext_ != null)
				return false;
		} else if (!temporalContext_.equals(other.temporalContext_))
			return false;
		return true;
	}

	public abstract String toPrettyString();

	public abstract String getIdentifier();
}
