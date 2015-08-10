package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.ResourceAccess;
import io.ontology.DAGSocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import tools.PairwiseDisjointExperimenter;
import tools.WEKAAsserter;
import tools.WEKAAsserter.GeneraliseTask;
import util.Pair;

public class WEKAAsserterTest {
	public static File DISJOINTS_250 = new File(
			"C:/Users/Sam Sarjant/Documents/Dropbox/PostDoc "
					+ "Progress/KnowledgeMiner Outputs/DisjointnessDiscovery"
					+ "/20150325MINISAIndvPullUp/randomClassifiedDisjoints.txt");
	private static WEKAAsserter sut_;
	private static DAGSocket ontology_;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		File classifierFile = new File(
				"C:/Users/Sam Sarjant/Documents/Dropbox/PostDoc Progress/KnowledgeMiner Outputs/DisjointnessDiscovery/20150320IndvPullUp/PrecisionSubsampled.model");
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
		sut_ = new WEKAAsserter(classifierFile, ontology_);
	}

	@After
	public void tearDown() throws Exception {
//		ontology_.command("map", "removeedge $1 |\nsearchprop E \"MT\"\n"
//				+ PairwiseDisjointExperimenter.EXPERIMENT_MICROTHEORY
//				+ "\n\\|(\\d+)", false);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		ontology_.close();
	}

//	@Test
	public void testRecursiveGeneralise() throws Exception {
		sut_.generalising_ = true;
		Map<Pair<String, String>, Boolean> examined = new HashMap<>();

		// Planet and Crater (1.0 disjoint)
		String asWeka = ontology_.command("asweka", "Planet Crater Antonym",
				false);
		String[] split = asWeka.split("\\|");
		sut_.getAssertions().clear();
		GeneraliseTask task = sut_.new GeneraliseTask(split[1], 1);
		task.run();
		Collection<String> assertions = sut_.getAssertions();
		assertEquals(assertions.size(), 2);
		assertTrue(assertions
				.contains("(disjointWith AstronomicalBody Crater)"));
		assertTrue(assertions
				.contains("(disjointWith Depression-Topographical Planet)"));
		examined.clear();

		// Fish and Supper (1.0 disjoint)
		asWeka = ontology_.command("asweka", "Fish Supper RelatedTo", false);
		split = asWeka.split("\\|");
		sut_.getAssertions().clear();
		task = sut_.new GeneraliseTask(split[1], 1);
		task.run();
		assertions = sut_.getAssertions();
		assertEquals(assertions.size(), 1);
		assertTrue(assertions
				.contains("(disjointWith Food-ReadyToEat (CollectionUnionFn (TheSet Person Animal)))"));
		examined.clear();

		// Brass and BrassInstrument (<1.0 disjoint)
		asWeka = ontology_.command("asweka",
				"Brass BrassInstrument DerivedFrom", false);
		split = asWeka.split("\\|");
		sut_.getAssertions().clear();
		task = sut_.new GeneraliseTask(split[1], 1);
		task.run();
		assertions = sut_.getAssertions();
		assertEquals(assertions.size(), 0);
		examined.clear();

		// USCity and Village (1.0 conjoint)
		asWeka = ontology_.command("asweka", "USCity Village Antonym", false);
		split = asWeka.split("\\|");
		sut_.getAssertions().clear();
		task = sut_.new GeneraliseTask(split[1], 1);
		task.run();
		assertions = sut_.getAssertions();
		assertEquals(assertions.size(), 0);
		examined.clear();

		// Fish and Starfish (multiple <1.0 conjoints)
		asWeka = ontology_.command("asweka", "Fish Starfish RelatedTo", false);
		split = asWeka.split("\\|");
		sut_.getAssertions().clear();
		task = sut_.new GeneraliseTask(split[1], 1);
		task.run();
		assertions = sut_.getAssertions();
		assertEquals(assertions.size(), 0);
		examined.clear();

		// Possible problem case (conjoint)
		asWeka = ontology_.command("asweka",
				"CourseOfAMeal Crustacean RelatedTo", false);
		split = asWeka.split("\\|");
		sut_.getAssertions().clear();
		task = sut_.new GeneraliseTask(split[1], 1);
		task.run();
		assertions = sut_.getAssertions();
		assertEquals(assertions.size(), 0);
		examined.clear();
	}

	@Test
	public void test250DisjointsNonGeneralised() throws Exception {
		sut_.generalising_ = false;
		// Test the disjoints as standard classifications wihtout generalisation
		Map<Pair<String, String>, Boolean> examined = new HashMap<>();

		if (!DISJOINTS_250.exists())
			fail("Could not find " + DISJOINTS_250);

		// Before
		BufferedReader in = new BufferedReader(new FileReader(DISJOINTS_250));
		String input = null;
		while ((input = in.readLine()) != null) {
			String asWeka = ontology_.command("asweka", input, false);
			String[] split = asWeka.split("\\|");
			GeneraliseTask task = sut_.new GeneraliseTask(split[1], 1);
			task.run();
		}
		in.close();

		Collection<String> assertions = sut_.getAssertions();
		System.out.println("Before (" + assertions.size() + ")");
		System.out.println(assertions);
	}

	@Test
	public void test250DisjointsGeneralised() throws Exception {
		sut_.generalising_ = true;
		// Test the disjoints as standard classifications wihtout generalisation
		Map<Pair<String, String>, Boolean> examined = new HashMap<>();

		if (!DISJOINTS_250.exists())
			fail("Could not find " + DISJOINTS_250);

		// Before
		BufferedReader in = new BufferedReader(new FileReader(DISJOINTS_250));
		String input = null;
		while ((input = in.readLine()) != null) {
			String asWeka = ontology_.command("asweka", input, false);
			String[] split = asWeka.split("\\|");
			GeneraliseTask task = sut_.new GeneraliseTask(split[1], 1);
			task.run();
		}
		in.close();

		Collection<String> assertions = sut_.getAssertions();
		System.out.println("After (" + assertions.size() + ")");
		System.out.println(assertions);
	}
}
