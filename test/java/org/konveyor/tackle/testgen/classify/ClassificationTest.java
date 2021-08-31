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

package org.konveyor.tackle.testgen.classify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.konveyor.tackle.testgen.util.TackleTestJson;

import com.fasterxml.jackson.databind.node.ArrayNode;

public class ClassificationTest {

    private static final String REPORTS_PATH = "test/data/junit-reports";
    private static final String ERROR_PATTERNS_PATH = "test/data/junit-reports/errorPatterns.json";
    private static final String TEST_DIR = "daytrader-ctd-amplified-tests";
    private static final String OUTPUT_PATH = TEST_DIR + "_" + ClassifyErrors.OUTPUT_FILE;
    private static final String COMPARE_OUTPUT_PATH = REPORTS_PATH + File.separator + "standardErrors.json";
    @Before
    /**
     * Delete existing modeling output file
     */
    public void cleanUp() {
        FileUtils.deleteQuietly(new File(OUTPUT_PATH));
    }

    @Test
    public void testClassification() throws Exception {
        String[] reportPath = new String[4];
        reportPath[0] = "test" + File.separator + "data" + File.separator + "junit-reports" + File.separator + "daytrader7-partition1";
        reportPath[1] = "test" + File.separator + "data" + File.separator + "junit-reports" + File.separator + "daytrader7-partition2";
        reportPath[2] = "test" + File.separator + "data" + File.separator + "junit-reports" + File.separator + "daytrader7-partition3";
        reportPath[3] = "test" + File.separator + "data" + File.separator + "junit-reports" + File.separator + "daytrader7-web";

        String[] appPackages = new String[1];
        appPackages[0] = "com.ibm.websphere.samples.daytrader.*";
        new ClassifyErrors(reportPath, ERROR_PATTERNS_PATH, appPackages, TEST_DIR);

        // assert that output file for classification is created
        assertTrue(new File(OUTPUT_PATH).exists());
        
        ArrayNode resultArray = (ArrayNode) TackleTestJson.getObjectMapper().readTree(new File(OUTPUT_PATH)); 
        ArrayNode standardArray = (ArrayNode) TackleTestJson.getObjectMapper().readTree(new File(COMPARE_OUTPUT_PATH)); 

        assertEquals(standardArray.size(), resultArray.size());
        for (int objectInd=0; objectInd<resultArray.size(); objectInd++) {
            assertEquals(standardArray.get(objectInd).get(ClassifyErrors.OUTPUT_ERROR_TYPE_TAG).asText(),
            		resultArray.get(objectInd).get(ClassifyErrors.OUTPUT_ERROR_TYPE_TAG).asText());
            ArrayNode typeResult = (ArrayNode) resultArray.get(objectInd).get(ClassifyErrors.OUTPUT_ERRORS_TAG);
            ArrayNode typeStandard = (ArrayNode) standardArray.get(objectInd).get(ClassifyErrors.OUTPUT_ERRORS_TAG);
            assertEquals(typeStandard.size(), typeResult.size());
            for (int typeInd=0; typeInd<typeResult.size(); typeInd++) {
                assertEquals(typeStandard.get(typeInd).get(ClassifyErrors.INPUT_PATTERN_TAG).asText(),
                    typeResult.get(typeInd).get(ClassifyErrors.INPUT_PATTERN_TAG).asText());
                assertEquals(typeStandard.get(typeInd).get(ClassifyErrors.INPUT_TYPE_TAG).asText(),
                    typeResult.get(typeInd).get(ClassifyErrors.INPUT_TYPE_TAG).asText());
                assertEquals(typeStandard.get(typeInd).get(ClassifyErrors.INPUT_SEMANTIC_TAG).asText(),
                    typeResult.get(typeInd).get(ClassifyErrors.INPUT_SEMANTIC_TAG).asText());
                ArrayNode messageResult = (ArrayNode) typeResult.get(typeInd).get(ClassifyErrors.INPUT_MESSAGE_TAG);
                ArrayNode messageStandard = (ArrayNode) typeStandard.get(typeInd).get(ClassifyErrors.INPUT_MESSAGE_TAG);
                assertEquals(messageStandard.size(), messageResult.size());
                for (int messageInd = 0; messageInd < messageResult.size(); messageInd++) {
                    assertEquals(messageStandard.get(messageInd), messageResult.get(messageInd));
                }

                ArrayNode partitionResult = (ArrayNode) typeResult.get(typeInd).get(ClassifyErrors.OUTPUT_PARTITIONS_TAG);
                ArrayNode partitionStandard = (ArrayNode) typeStandard.get(typeInd).get(ClassifyErrors.OUTPUT_PARTITIONS_TAG);
                assertEquals(partitionStandard.size(), partitionResult.size());
                for (int partitionInd = 0; partitionInd < partitionResult.size(); partitionInd++) {
                    assertEquals(partitionStandard.get(partitionInd).get(ClassifyErrors.OUTPUT_PARTITION_TAG).asText(),
                        partitionResult.get(partitionInd).get(ClassifyErrors.OUTPUT_PARTITION_TAG).asText());
                    ArrayNode testFilesResult = (ArrayNode) partitionResult.get(partitionInd).get(ClassifyErrors.OUTPUT_TEST_CLASSES_TAG);
                    ArrayNode testFilesStandard = (ArrayNode) partitionStandard.get(partitionInd).get(ClassifyErrors.OUTPUT_TEST_CLASSES_TAG);
                    assertEquals(testFilesStandard.size(), testFilesResult.size());
                    for (int testFileInd = 0; testFileInd < testFilesResult.size(); testFileInd++) {
                        assertEquals(testFilesStandard.get(testFileInd).get(ClassifyErrors.OUTPUT_TEST_CLASS_TAG).asText(),
                            testFilesResult.get(testFileInd).get(ClassifyErrors.OUTPUT_TEST_CLASS_TAG).asText());
                        ArrayNode testIdsResult = (ArrayNode) testFilesResult.get(testFileInd).get(ClassifyErrors.OUTPUT_TEST_METHODS_TAG);
                        ArrayNode testIdsStandard = (ArrayNode) testFilesStandard.get(testFileInd).get(ClassifyErrors.OUTPUT_TEST_METHODS_TAG);
                        assertEquals(testIdsStandard.size(), testIdsResult.size());
                        for (int testIdInd = 0; testIdInd < testIdsResult.size(); testIdInd++) {
                            assertEquals(testIdsStandard.get(testIdInd), testIdsResult.get(testIdInd));
                        }
                    }
                }
            }
        }
    }
}
