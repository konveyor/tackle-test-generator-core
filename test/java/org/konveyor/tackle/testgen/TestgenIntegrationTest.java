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
import org.konveyor.tackle.testgen.core.extender.TestSequenceExtender;
import org.konveyor.tackle.testgen.model.CTDTestPlanGenerator;
import org.konveyor.tackle.testgen.util.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestgenIntegrationTest {

    private static List<TestUtils.ExtenderAppUnderTest> appsUnderTest;
    private static List<String> OUTDIRS;

    @BeforeClass
    public static void createAppsUnderTest() {
        appsUnderTest = new ArrayList<>();
        appsUnderTest.add(TestUtils.createAppForExtenderTest("irs", null, null));
        OUTDIRS = appsUnderTest.stream()
            .map(app -> app.appOutdir)
            .collect(Collectors.toList());
    }

    @Before
    /**
     * Delete existing output files
     */
    public void cleanUp() throws IOException {
        for (TestUtils.ExtenderAppUnderTest testApp : appsUnderTest) {
            Files.deleteIfExists(Paths.get(testApp.testPlanFilename));
            String[] testSeqFilenames = testApp.testSeqFilename.split(",");
            for (String testSeqFile : testSeqFilenames) {
                Files.deleteIfExists(Paths.get(testSeqFile));
            }
        }
        for (String outdir: OUTDIRS) {
            if (Files.exists(Paths.get(outdir))) {
                Files.walk(Paths.get(outdir))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }
        for (TestUtils.ExtenderAppUnderTest app : appsUnderTest) {
        	Files.deleteIfExists(Paths.get(app.appName+Constants.EXTENDER_SUMMARY_FILE_JSON_SUFFIX));
        	Files.deleteIfExists(Paths.get(app.appName+Constants.COVERAGE_FILE_JSON_SUFFIX));
        }
    }

    @Test
    public void testModelerInitializerExtender() throws Exception {

        // run test on apps
        for (TestUtils.ExtenderAppUnderTest testApp: appsUnderTest) {

            // generate CTD test plan for app
            CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(testApp.appName,
                null, null, null, 
                null, null, testApp.appPath,
                testApp.appClasspathFilename, 2, false, 2, null, null, null, null);
            analyzer.modelPartitions();

            // assert that output file for CTD modeling is  created
            assertTrue(new File(testApp.testPlanFilename).exists());

            // create building-block test sequences using combined test generator
            TestSequenceInitializer seqInitializer = new TestSequenceInitializer(testApp.appName,
                testApp.testPlanFilename, testApp.appPath, testApp.appClasspathFilename,
                Constants.COMBINED_TEST_GENERATOR_NAME, 5, false, false);
            seqInitializer.createInitialTests();

            // assert that test sequences files are created
            String[] testSeqFilenames = testApp.testSeqFilename.split(",");
            for (String testSeqFile : testSeqFilenames) {
                assertTrue(new File(testSeqFile).exists());
            }

            // generate test cases via process launcher
            TestUtils.launchProcess(TestSequenceExtender.class.getSimpleName(),
                testApp.appName, testApp.appPath, testApp.appClasspathFilename, testApp.testSeqFilename,
                testApp.testPlanFilename, null, true,null);

            // assert that test directory and summary files are created
            assertTrue(Files.exists(Paths.get(testApp.appOutdir)));
            assertTrue(Files.exists(Paths.get(testApp.appName+Constants.EXTENDER_SUMMARY_FILE_JSON_SUFFIX)));
            assertTrue(Files.exists(Paths.get(testApp.appName+Constants.COVERAGE_FILE_JSON_SUFFIX)));

            // assert over the number of expected test classes
            assertEquals(testApp.exp__test_classes_count, Files
                .walk(Paths.get(testApp.appOutdir))
                .filter(p -> p.toFile().isFile())
                .count()
            );

        }

    }
}
