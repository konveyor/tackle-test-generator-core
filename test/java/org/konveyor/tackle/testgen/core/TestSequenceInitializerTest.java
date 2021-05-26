package org.konveyor.tackle.testgen.core;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import org.konveyor.tackle.testgen.util.Constants;

public class TestSequenceInitializerTest {


	private final File outputFile = new File("DayTrader_"+EvoSuiteTestGenerator.class.getSimpleName()+"_"+
	Constants.INITIALIZER_OUTPUT_FILE_NAME_SUFFIX);

	@Before
	/**
	 * Delete existing EvoSuite input files and generated test cases
	 */
	public void cleanUp() {

		File targetDir = new File("DayTrader"+EvoSuiteTestGenerator.EVOSUITE_TARGET_DIR_NAME_SUFFIX);
		FileUtils.deleteQuietly(targetDir);
		FileUtils.deleteQuietly(new File("DayTrader"+EvoSuiteTestGenerator.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX));
		FileUtils.deleteQuietly(outputFile);
	}

	@Test
	public void testGenerateInitialSequences() throws Exception {

		TestSequenceInitializer seqInitializer = new TestSequenceInitializer("DayTrader",
				"test/data/daytrader7/daytrader_ctd_models_shortened.json", "test/data/daytrader7/monolith/bin",
				"test/data/daytrader7/DayTraderMonoClasspath.txt", "EvoSuiteTestGenerator", -1);

		seqInitializer.createInitialTests();

		// assert that input/output files for evosuite are created
		assertTrue(new File("DayTrader"+EvoSuiteTestGenerator.EVOSUITE_TARGET_DIR_NAME_SUFFIX).exists());
		assertTrue(new File("DayTrader"+EvoSuiteTestGenerator.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX).exists());
		assertTrue(outputFile.exists());

		Set<String> targetClasses = new HashSet<String>();

		targetClasses
				.addAll(Arrays.asList(new String[] { "com.ibm.websphere.samples.daytrader.beans.MarketSummaryDataBean",
						"com.ibm.websphere.samples.daytrader.util.TradeConfig",
						"com.ibm.websphere.samples.daytrader.entities.AccountDataBean",
						"com.ibm.websphere.samples.daytrader.entities.QuoteDataBean",
						"com.ibm.websphere.samples.daytrader.entities.OrderDataBean" }));

		InputStream fis = new FileInputStream(outputFile);
		JsonReader reader = Json.createReader(fis);
		JsonObject mainObject = reader.readObject();
		reader.close();

		JsonObject sequencesObject = mainObject.getJsonObject("test_sequences");

		Set<String> reachedClasses = sequencesObject.keySet();

		assertTrue(reachedClasses.equals(targetClasses));
	}

}
