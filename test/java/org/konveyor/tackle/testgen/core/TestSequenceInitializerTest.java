/*
Copyright IBM Corporation 2021

Licensed under the Eclipse Public License 2.0, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.konveyor.tackle.testgen.core;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.evosuite.shaded.org.apache.commons.collections.IteratorUtils;
import org.junit.Before;
import org.junit.Test;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
				"test/data/daytrader7/DayTraderMonoClasspath.txt", "EvoSuiteTestGenerator", -1, false, false);

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
		
		JsonNode resultNode = TackleTestJson.getObjectMapper().readTree(outputFile);

		ObjectNode sequencesObject = (ObjectNode) resultNode.get("test_sequences");

		@SuppressWarnings("unchecked")
		Set<String> reachedClasses = new HashSet<String>(IteratorUtils.toList(sequencesObject.fieldNames()));

		assertTrue(reachedClasses.equals(targetClasses));
	}

}
