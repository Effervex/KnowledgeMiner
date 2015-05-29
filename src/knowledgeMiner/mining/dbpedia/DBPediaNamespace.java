package knowledgeMiner.mining.dbpedia;

public enum DBPediaNamespace {
	DBPEDIA("dbp", "http://dbpedia.org/resource/"), DBPEDIAOWL("dbowl",
			"http://dbpedia.org/ontology/"), DBPEDIAPROP("dbpprop",
			"http://dbpedia.org/property/");

	private String uri_;
	private String shortName_;

	private DBPediaNamespace(String shortname, String uri) {
		shortName_ = shortname;
		uri_ = uri;
	}

	public String getURI() {
		return uri_;
	}

	public String getShort() {
		return shortName_;
	}

	/**
	 * Formats the element and namespace appropriately for queries.
	 *
	 * @param namespace
	 *            The namespace to prefix the element with.
	 * @param element
	 *            The element to format.
	 * @return A URI representing the namespace prefixed element.
	 */
	public static String format(DBPediaNamespace namespace, String element) {
		return "<" + namespace.getURI() + element + ">";
	}
}
