package io.resources;

public enum DBPediaNamespace {
	DBPEDIA("dbp", "http://dbpedia.org/resource/"), DBPEDIAOWL("dbowl",
			"http://dbpedia.org/ontology/"), DBPEDIAPROP("dbpprop",
			"http://dbpedia.org/property/"), RDFS("rdfs",
			"http://www.w3.org/2000/01/rdf-schema#");

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
	
	public String format(String element) {
		return "<" + getURI() + element + ">";
	}
}
