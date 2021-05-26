package org.konveyor.tackle.testgen.core;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class EvoSuiteTestGeneratorTest {

	@Before
	/**
	 * Delete existing EvoSuite input files and generated test cases
	 */
	public void cleanUp() {

		File targetDir = new File("DayTrader"+EvoSuiteTestGenerator.EVOSUITE_TARGET_DIR_NAME_SUFFIX);
		FileUtils.deleteQuietly(targetDir);
		FileUtils.deleteQuietly(new File("DayTrader"+EvoSuiteTestGenerator.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX));
	}

	@Test
	public void testGenerateTests() throws Exception {
		String classpath = "test/data/daytrader7/externalJars/javax.json-api-1.0.jar"+File.pathSeparator+
				"test/data/daytrader7/externalJars/javax.jms-api-2.0.1.jar";
		EvoSuiteTestGenerator evosuoiteTestgen = new EvoSuiteTestGenerator(Collections.singletonList("test/data/daytrader7/monolith/bin"), "DayTrader");
		evosuoiteTestgen.setProjectClasspath(classpath);

		// set configuration options
		Map<String, String> config = new HashMap<>();
		config.put(EvoSuiteTestGenerator.Options.SEARCH_BUDGET.name(), "30");
		config.put(EvoSuiteTestGenerator.Options.ASSERTIONS.name(), "false");
		config.put(EvoSuiteTestGenerator.Options.CRITERION.name(), "METHOD");
		evosuoiteTestgen.configure(config);
		evosuoiteTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.TradeAction");
		evosuoiteTestgen.generateTests();

		// assert that input/output files for evosuite are created
		assertTrue(new File("DayTrader"+EvoSuiteTestGenerator.EVOSUITE_TARGET_DIR_NAME_SUFFIX).exists());
		assertTrue(new File("DayTrader"+EvoSuiteTestGenerator.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX).exists());
	}
}
