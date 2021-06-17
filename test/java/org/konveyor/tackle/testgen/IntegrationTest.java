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

package org.konveyor.tackle.testgen;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.konveyor.tackle.testgen.core.TestSequenceInitializer;
import org.konveyor.tackle.testgen.model.CTDTestPlanGenerator;
import org.konveyor.tackle.testgen.util.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class IntegrationTest {

    private static List<TestUtils.AppUnderTest> appsUnderTest;

    @BeforeClass
    public static void createAppsUnderTest() {
        appsUnderTest = new ArrayList<>();
        appsUnderTest.add(TestUtils.createIrsApp(null, null));
    }

    @Before
    /**
     * Delete existing output files
     */
    public void cleanUp() throws IOException {
        for (TestUtils.AppUnderTest testApp : appsUnderTest) {
            Files.deleteIfExists(Paths.get(testApp.testPlanFilename));
            String[] testSeqFilenames = testApp.testSeqFilename.split(",");
            for (String testSeqFile : testSeqFilenames) {
                Files.deleteIfExists(Paths.get(testSeqFile));
            }
        }
    }

    @Test
    public void testModelerInitializerExtender() throws Exception {

        // run test on apps
        for (TestUtils.AppUnderTest testApp: appsUnderTest) {

            // generate CTD test plan for app
            CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(testApp.appName,
                null, null,
                null, null, testApp.appPath,
                testApp.appClasspathFilename, true, 2, false, 2);
            analyzer.modelPartitions();

            // assert that output file for CTD modeling is  created
            assertTrue(new File(testApp.testPlanFilename).exists());

            // create building-block test sequences using combined test generator
            TestSequenceInitializer seqInitializer = new TestSequenceInitializer(testApp.appName,
                testApp.testPlanFilename, testApp.appPath, testApp.appClasspathFilename,
                Constants.COMBINED_TEST_GENERATOR_NAME, 5);
            seqInitializer.createInitialTests();

            // assert that test sequences files are created
            String[] testSeqFilenames = testApp.testSeqFilename.split(",");
            for (String testSeqFile : testSeqFilenames) {
                assertTrue(new File(testSeqFile).exists());
            }
        }
    }
}
