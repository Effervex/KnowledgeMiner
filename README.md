KnowledgeMiner is a project developed at The University of Waikato, Hamilton, New Zealand. The aim of this project is to extract ontological information from Wikipedia (and eventually, other text resources) and store this into the OpenCyc ontology (or rather, a reimplementation of it - see CycDAG).

The general process of the algorithm is to iterate through every Wikipedia article and map each article to an existing concept in the ontology, or create a new one. Upon mapping, information is extracted from the article and asserted to the concept. The process is a little more involved than this, but that's the general gist of it.

==================
== Requirements ==
==================

* Java 1.8
* Access to WikipediaMiner wrapped by the WPM console wrapper. This will be a significant limitation to those not at Waikato.
* Access to the CycDAG ontology (a reimplementation of OpenCyc). This can be found at https://github.com/Effervex/CycDAG

==============
== Starting ==
==============

Assuming you have access to the requirements (or have re-engineered the requirements), KnowledgeMiner can be started through two alternative main methods:
knowledgeMiner.KnowledgeMiner
OR knowledgeMiner.ConceptMiningTask (a Runnable class)
Note that WikipediaMiner should be accessible through port 2424 and CycDAG through 2425 (this can be modified with the '-p XXX' command-line argument).

The former is the full process, running through every article/concept in Wikipedia/Ontology by creating individual ConceptMiningTasks and running them. The latter provides a bit more control on what is being mapped and is mainly used for testing.

=========================
== General Methodology ==
=========================

The general process followed by an individual ConceptMiningTask is to iterate through the following steps:
* Map article to concept
* Map concept to article
* Mine article
* Identify maximal consistent mined information
* Assert information to concept
(Technically mining is the first step, but it's easier to explain in this order).

This entire process is guided by a priority queue of potential mappings between concepts and article, each in a different state of completion. Each potential mapping incorporates a numerical value between 0 and 1 representing the confidence of the mapping - this is used to sort the priority queue. Initially, a potential mapping (i.e. a concept or article) has weight=1. Mapping and consistency have the potential to reduce this weight.

For the mapping step, the value returned for each discovered mapping is proportional to the likelihood of the mapping. Note that mapping is bi-directional - a concept has to map to an article and back again for it to be considered useful. Also note that there is the chance that a new concept is created (because one doesn't exist). This is initialised with weight=0.5 (this is arbitrary and needs refinement).

For the mining step, the value is not changed. However, during the consistency step, the value is modified proportionally to the number of valid assertions retained during this step.

Finally, if a potential mapping between concept and article has been mapped bidirectionally, and a consistent chunk of information has been identified, then the information is asserted to the concept.

Throughout this process, multiple mappings can be identified. During the mapping steps, often more than one possible mapping will be returned. Then, during the reversed mapping step, more mappings to potential things can be identified. For example, 'Basketball (ball)' the article can map to Basketball-Game (concept), which then maps back to 'Basketball (game)' which maps back to Basketball-Game. This mapping does not involve the original term 'Basketball (ball)', but it acts as a process of elimination, as now 'Basketball (ball)' cannot be mapped to Basketball-Game.

=======================
== Important Classes ==
=======================

There are some classes that deserve some more description:
cyc.CycConstants: These are concepts that both already exist in OpenCyc and new ones that are created for KnowledgeMiner specifically. A convenience class.
cyc.MappableConcept: Mining does not immediately disambiguate mined information into concepts. They are instead stored as incomplete mappings which are grounded during consistency check. This allows mined information to retain its value even as the ontology changes.
io.IOManager: The class in charge of most File IO.
io.ResourceAccess: The class in charge of resource IO. That is, ontology access and WMI access should be received via this class - it creates sockets where necessary and operates with ThreadLocal access to sockets.
knowledgeMiner.AssertionGrid & knowledgeMiner.DisjointnessDisambiguator: The core classes behind the 'Disjointness Disambiguation' process (identification of maximal consistent information).
knowledgeMiner.WeightedHeuristics: The abstract class underpinning all of the heuristics. Doesn't contribute much alone, but forms the top of the heuristic structure.
knowledgeMiner.mapping.CycMapper: The core class containing all of the Mapping Heuristics. CycToWiki, WikiToCyc, TextToCyc, etc. Mapping algorithm access should go through this class.
knowledgeMiner.mining.CycMiner: The core class containing all of the Mining Heuristics. These currently only concern Wikipedia, but could be extended.
knowledgeMiner.mining.MinedAssertion: The main form of representing assertions (well, an abstract parent of the two forms, but you get the idea).
knowledgeMiner.mining.MinedInformation: The primary output of mining heuristics. MinedInformation contains any and all types of mined information that can be produced by a mining heuristic.
knowledgeMiner.mining.SentenceParserHeuristic: A fairly major heuristic that isn't with the Wikipedia heuristics only because it can go beyond them. This heuristic employs a parser to produce assertions from natural text (currently only taxonomic assertions).
knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor: A class for preprocessing mined information for articles such that runtime is much quicker. Can also preprocess mapped info, but this unfortunately grows stale quickly due to the changing ontology, so is of little use.
util.wikipedia.WikiParser: A convenience class for doing all sorts of text processing stuff (primarily for Wikipedia).

================
== Disclaimer ==
================

All source code is copyright The University of Waikato, New Zealand (2014)

Most source code is produced by Dr Sam Sarjant at The University of Waikato. Contact him directly with questions at: Sam Sarjant sarjant@waikato.ac.nz