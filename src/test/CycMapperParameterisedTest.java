/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Arrays;
import java.util.Collection;

import knowledgeMiner.mapping.CycMapper;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * A test class for CycMapper.
 */
@RunWith(Parameterized.class)
public class CycMapperParameterisedTest {
	/** The mapper under test. */
	private static CycMapper mapper_;

	private static WMISocket wmi_;
	private static OntologySocket cyc_;

	private String cycTerm_;
	private String pageTitle_;

	public CycMapperParameterisedTest(String cycTerm, String pageTitle) {
		super();
		cycTerm_ = cycTerm;
		pageTitle_ = pageTitle;
	}

	@SuppressWarnings("rawtypes")
	@Parameters
	public static Collection mappingValues() {
		return Arrays.asList(new String[][] {
				/* 00 */{ "Dog", "Dog" },
				/* 01 */{ "PlanetEarth", "Earth" },
				/* 02 */{ "OzzieSmith-BaseballPlayer", "Ozzie Smith" },
				/* 03 */{ "JaguarTheCompany", "Jaguar Cars" },
				/* 04 */{ "Nawoiy-StateUzbekistan", "Navoiy Province" },
				/* 05 */{ "M4-TheProgram", "M4 (computer language)" },
				/* 06 */{ "AmericanBeauty-TheAlbum",
						"American Beauty (soundtrack)" }, // Fail
				/* 07 */{ "Common-Region", "Common land" }, // Fail
				/* 08 */{ "Casino-TheMovie", "Casino (film)" }, // Fail
				/* 09 */{ "Summit", "Summit (topography)" },
				/* 10 */{ "UniversityOfCalifornia-Irvine",
						"University of California, Irvine" },
				/* 11 */{ "Bordeaux-Wine", "Bordeaux wine" },
				/* 12 */{ "CopperOre", "Copper" }, // Fail
				/* 13 */{ "ToyotaCoronaCar", "Toyota Corona" },
				/* 14 */{ "AugustanaCollegeOfSouthDakota",
						"Augustana College (South Dakota)" }, // Fail
				/* 15 */{ "JeepCJ7Car", "Jeep CJ" }, // Fail
				/* 16 */{ "Babying", null }, // Fail
				/* 17 */{ "Unit10", null },
				/* 18 */{ "HaveYouSeenMeLately-TheAlbum",
						"Have You Seen Me Lately" },
				/* 19 */{ "JackVance", "Jack Vance" },
				/* 20 */{ "RoseColor", "Rose (color)" },
				/* 21 */{ "(FocalFieldOfStudyFn Urologist)", "Urology" },
				/* 22 */{ "PlatinumColor", "Platinum (color)" } });
	}

	/**
	 * Sets up the mapper beforehand.
	 * 
	 * @throws Exception
	 *             If something goes awry.
	 */
	@BeforeClass
	public static void setUp() throws Exception {
		cyc_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		mapper_ = new CycMapper();
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}
}
