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

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import javax.json.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

        InputStream fis = new FileInputStream(OUTPUT_PATH);
        JsonReader reader = Json.createReader(fis);
        JsonArray resultObject = reader.readArray();
        reader.close();

        fis = new FileInputStream(COMPARE_OUTPUT_PATH);
        reader = Json.createReader(fis);
        JsonArray standardObject = reader.readArray();
        reader.close();

        assertEquals(standardObject.size(), resultObject.size());
        for (int objectInd=0; objectInd<resultObject.size(); objectInd++) {
            assertEquals(standardObject.getJsonObject(objectInd).getString(ClassifyErrors.OUTPUT_ERROR_TYPE_TAG),
                resultObject.getJsonObject(objectInd).getString(ClassifyErrors.OUTPUT_ERROR_TYPE_TAG));
            JsonArray typeResult = resultObject.getJsonObject(objectInd).getJsonArray(ClassifyErrors.OUTPUT_ERRORS_TAG);
            JsonArray typeStandard = standardObject.getJsonObject(objectInd).getJsonArray(ClassifyErrors.OUTPUT_ERRORS_TAG);
            assertEquals(typeStandard.size(), typeResult.size());
            for (int typeInd=0; typeInd<typeResult.size(); typeInd++) {
                assertEquals(typeStandard.getJsonObject(typeInd).getString(ClassifyErrors.INPUT_PATTERN_TAG),
                    typeResult.getJsonObject(typeInd).getString(ClassifyErrors.INPUT_PATTERN_TAG));
                assertEquals(typeStandard.getJsonObject(typeInd).getString(ClassifyErrors.INPUT_TYPE_TAG),
                    typeResult.getJsonObject(typeInd).getString(ClassifyErrors.INPUT_TYPE_TAG));
                assertEquals(typeStandard.getJsonObject(typeInd).getString(ClassifyErrors.INPUT_SEMANTIC_TAG),
                    typeResult.getJsonObject(typeInd).getString(ClassifyErrors.INPUT_SEMANTIC_TAG));
                JsonArray messageResult = typeResult.getJsonObject(typeInd).getJsonArray(ClassifyErrors.INPUT_MESSAGE_TAG);
                JsonArray messageStandard = typeStandard.getJsonObject(typeInd).getJsonArray(ClassifyErrors.INPUT_MESSAGE_TAG);
                assertEquals(messageStandard.size(), messageResult.size());
                for (int messageInd = 0; messageInd < messageResult.size(); messageInd++) {
                    assertEquals(messageStandard.get(messageInd), messageResult.get(messageInd));
                }

                JsonArray partitionResult = typeResult.getJsonObject(typeInd).getJsonArray(ClassifyErrors.OUTPUT_PARTITIONS_TAG);
                JsonArray partitionStandard = typeStandard.getJsonObject(typeInd).getJsonArray(ClassifyErrors.OUTPUT_PARTITIONS_TAG);
                assertEquals(partitionStandard.size(), partitionResult.size());
                for (int partitionInd = 0; partitionInd < partitionResult.size(); partitionInd++) {
                    assertEquals(partitionStandard.getJsonObject(partitionInd).getString(ClassifyErrors.OUTPUT_PARTITION_TAG),
                        partitionResult.getJsonObject(partitionInd).getString(ClassifyErrors.OUTPUT_PARTITION_TAG));
                    JsonArray testFilesResult = partitionResult.getJsonObject(partitionInd).getJsonArray(ClassifyErrors.OUTPUT_TEST_CLASSES_TAG);
                    JsonArray testFilesStandard = partitionStandard.getJsonObject(partitionInd).getJsonArray(ClassifyErrors.OUTPUT_TEST_CLASSES_TAG);
                    assertEquals(testFilesStandard.size(), testFilesResult.size());
                    for (int testFileInd = 0; testFileInd < testFilesResult.size(); testFileInd++) {
                        assertEquals(testFilesStandard.getJsonObject(testFileInd).getString(ClassifyErrors.OUTPUT_TEST_CLASS_TAG),
                            testFilesResult.getJsonObject(testFileInd).getString(ClassifyErrors.OUTPUT_TEST_CLASS_TAG));
                        JsonArray testIdsResult = testFilesResult.getJsonObject(testFileInd).getJsonArray(ClassifyErrors.OUTPUT_TEST_METHODS_TAG);
                        JsonArray testIdsStandard = testFilesStandard.getJsonObject(testFileInd).getJsonArray(ClassifyErrors.OUTPUT_TEST_METHODS_TAG);
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
