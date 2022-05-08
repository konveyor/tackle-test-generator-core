/*
 * Copyright IBM Corporation 2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.konveyor.tackle.testgen.core;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.konveyor.tackle.testgen.TestUtils.SequenceInitializerAppUnderTest;
import org.konveyor.tackle.testgen.util.TackleTestJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class TestSequenceInitializerTest {
    
    private static List<SequenceInitializerAppUnderTest> appsUnderTest;
    
    @BeforeClass
    public static void createAppsUnderTest() {
        appsUnderTest = new ArrayList<>();
        appsUnderTest.add(SequenceInitializerAppUnderTest.createDaytrader7SequenceInitializerAppUnderTest());
    }

    @Before
	/**
	 * Delete existing EvoSuite input files and generated test cases
	 */
	public void cleanUp() {
        for (SequenceInitializerAppUnderTest appUnderTest : appsUnderTest) {
            FileUtils.deleteQuietly(new File(SequenceInitializerAppUnderTest.getTargetDirName(appUnderTest.appName)));
            FileUtils.deleteQuietly(new File(SequenceInitializerAppUnderTest.getOutputDirName(appUnderTest.appName)));
            FileUtils.deleteQuietly(new File(SequenceInitializerAppUnderTest.getOutputFileName(appUnderTest.appName)));
        }
	}

	@Test
	public void testGenerateInitialSequences() throws Exception {
        for (SequenceInitializerAppUnderTest sequenceInitializerAppUnderTest : appsUnderTest) {
            TestSequenceInitializer seqInitializer = new TestSequenceInitializer(
                sequenceInitializerAppUnderTest.appName,
                sequenceInitializerAppUnderTest.testPlanFilename,
                sequenceInitializerAppUnderTest.appPath,
                sequenceInitializerAppUnderTest.appClasspathFilename,
                sequenceInitializerAppUnderTest.testGenName,
                sequenceInitializerAppUnderTest.timeLimit,
                sequenceInitializerAppUnderTest.targetMethods,
                sequenceInitializerAppUnderTest.baseAssertions, "");

            seqInitializer.createInitialTests();

            // assert that input/output files for evosuite are created
            assertTrue(new File(SequenceInitializerAppUnderTest.getTargetDirName(sequenceInitializerAppUnderTest.appName)).exists());
            assertTrue(new File(SequenceInitializerAppUnderTest.getOutputDirName(sequenceInitializerAppUnderTest.appName)).exists());

            File outputFile = new File(SequenceInitializerAppUnderTest.getOutputFileName(sequenceInitializerAppUnderTest.appName));
            assertTrue(outputFile.exists());

            JsonNode resultNode = TackleTestJson.getObjectMapper().readTree(outputFile);

            ObjectNode sequencesObject = (ObjectNode) resultNode.get("test_sequences");

            Set<String> reachedClasses = StreamSupport.stream(
    				Spliterators.spliteratorUnknownSize(sequencesObject.fieldNames(), 
    						Spliterator.ORDERED), false)
    		  .collect(Collectors.toSet());

            assertTrue(reachedClasses.equals(sequenceInitializerAppUnderTest.targetClasses));
        }
	}
}
