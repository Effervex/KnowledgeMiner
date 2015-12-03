package cyc;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.mapping.CycMapper;
import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;

/**
 * A partially complete concept requiring further mapping between the mapped
 * thing and an ontology concept. It may be the case that no concept is found,
 * hence this can represent a 'null' concept and should not be used for making
 * assertions.
 * 
 * @author Sam Sarjant
 */
public abstract class MappableConcept extends AssertionArgument {
	// TODO Implement this class as a factory - with caching of mapped results.

	private static final long serialVersionUID = 1L;

	/** The object that is mapped at runtime. */
	protected Object mappableThing_;

	/** The mappings found when applied to the ontology. */
	private transient WeightedSet<OntologyConcept> mappings_;

	public MappableConcept(Object mappableObject) {
		super();
		mappableThing_ = mappableObject;
	}

	public MappableConcept(MappableConcept existing) {
		super(existing);
		mappableThing_ = existing.mappableThing_;
		mappings_ = existing.mappings_;
	}

	/**
	 * Maps this object to an ontological concept
	 * 
	 * @param mapper
	 *            The mapper to use for mapping this.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return True if the thing was successfully (or already is) mapped.
	 */
	public final WeightedSet<OntologyConcept> mapThing(CycMapper mapper,
			WikipediaSocket wmi, OntologySocket ontology) {
		if (mappings_ == null) {
			mappings_ = mapThingInternal(mapper, wmi, ontology);
			if (mappings_ instanceof HierarchicalWeightedSet)
				((HierarchicalWeightedSet<OntologyConcept>) mappings_)
						.cleanEmptyParents();
		}
		return mappings_;
	}

	protected abstract WeightedSet<OntologyConcept> mapThingInternal(
			CycMapper mapper, WikipediaSocket wmi, OntologySocket ontology);

	@Override
	public String toString() {
		return "map(" + mappableThing_ + ")";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((mappableThing_ == null) ? 0 : mappableThing_.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		MappableConcept other = (MappableConcept) obj;
		if (mappableThing_ == null) {
			if (other.mappableThing_ != null)
				return false;
		} else if (!mappableThing_.equals(other.mappableThing_))
			return false;
		return true;
	}
}
