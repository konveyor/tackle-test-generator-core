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
import org.konveyor.tackle.testgen.TestUtils.SequenceInitializerAppUnderTest;
import org.konveyor.tackle.testgen.util.TackleTestJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestSequenceInitializerTest {
    
	@Before
	/**
	 * Delete existing EvoSuite input files and generated test cases
	 */
	public void cleanUp() {
        String appName = "daytrader7";
		FileUtils.deleteQuietly(new File(SequenceInitializerAppUnderTest.getTargetDirName(appName)));
		FileUtils.deleteQuietly(new File(SequenceInitializerAppUnderTest.getOutputDirName(appName)));
		FileUtils.deleteQuietly(new File(SequenceInitializerAppUnderTest.getOutputFileName(appName)));
	}

	@Test
	public void testGenerateInitialSequences() throws Exception {

        Set<String> targetClasses = new HashSet<String>();
        targetClasses
            .addAll(Arrays.asList(new String[] { "com.ibm.websphere.samples.daytrader.beans.MarketSummaryDataBean",
                "com.ibm.websphere.samples.daytrader.util.TradeConfig",
                "com.ibm.websphere.samples.daytrader.entities.AccountDataBean",
                "com.ibm.websphere.samples.daytrader.entities.QuoteDataBean",
                "com.ibm.websphere.samples.daytrader.entities.OrderDataBean" }));
        
        String appName = "daytrader7";
        SequenceInitializerAppUnderTest sequenceInitializerAppUnderTest = new SequenceInitializerAppUnderTest(appName,
            "test/data/daytrader7/daytrader_ctd_models_shortened.json", "EvoSuiteTestGenerator",
            -1, false, false, targetClasses);

        sequenceInitializerAppUnderTest.seqInitializer.createInitialTests();

		// assert that input/output files for evosuite are created
		assertTrue(new File(SequenceInitializerAppUnderTest.getTargetDirName(appName)).exists());
        assertTrue(new File(SequenceInitializerAppUnderTest.getOutputDirName(appName)).exists());
        
        File outputFile = new File(SequenceInitializerAppUnderTest.getOutputFileName(appName));
        assertTrue(outputFile.exists());

		JsonNode resultNode = TackleTestJson.getObjectMapper().readTree(outputFile);

		ObjectNode sequencesObject = (ObjectNode) resultNode.get("test_sequences");

		@SuppressWarnings("unchecked")
		Set<String> reachedClasses = new HashSet<String>(IteratorUtils.toList(sequencesObject.fieldNames()));

		assertTrue(reachedClasses.equals(targetClasses));
	}
}
