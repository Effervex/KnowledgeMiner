package tools.util;

public enum TripleMetrics {
	TEXT_TRIPLE, // The text triple (for unique ID)
	RELATION, // The relation of the triple.
	DOMAIN_TEXT, // The domain of the triple (text)
	RANGE_TEXT, // The range of the triple (text)
	DOMAIN_ARTICLE, // The mapped domain article
	DOMAIN_CYC_MAPPED, // The mapped domain concept from article
	RANGE_ARTICLE, // The mapped range article
	RANGE_CYC_MAPPED, // The mapped range concept from article
	DOMAIN_WEIGHT_FAMILY_CONTEXT, // The weight of the domain art to IA
	DOMAIN_WEIGHT_FREQ_CONTEXT, // The weighted art freq of the domain art
	RANGE_WEIGHT_FAMILY_CONTEXT, // The weight of the range art to IA
	RANGE_WEIGHT_FREQ_CONTEXT, // The weighted art freq of the range art
	DOMAIN_RANGE_RELATEDNESS, // The relatedness between domain and range
	DOMAIN_RANGE_RELATEDNESS_DOM_CONTEXT, // The relatedness between domain and range
										// with family context
	DOMAIN_RANGE_RELATEDNESS_RAN_CONTEXT, // The relatedness between domain and
											// range with reverse family context
	VALID_CYC; // If the assertion is a valid (isa) assertion
}
