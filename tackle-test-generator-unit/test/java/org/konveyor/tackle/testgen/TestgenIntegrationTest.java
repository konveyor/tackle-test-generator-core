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

package org.konveyor.tackle.testgen;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.konveyor.tackle.testgen.core.TestSequenceInitializer;
import org.konveyor.tackle.testgen.core.extender.TestSequenceExtender;
import org.konveyor.tackle.testgen.model.CTDTestPlanGenerator;
import org.konveyor.tackle.testgen.TestUtils.IntegrationAppUnderTest;
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
import static org.konveyor.tackle.testgen.TestUtils.assertMinimum;


public class TestgenIntegrationTest {

    private static List<IntegrationAppUnderTest> appsUnderTestWithSeqInit;
    private static List<IntegrationAppUnderTest> appsUnderTest;
    private static List<String> OUTDIRS;

    @BeforeClass
    public static void createAppsUnderTest() {
        appsUnderTestWithSeqInit = new ArrayList<>();
        appsUnderTestWithSeqInit.add(IntegrationAppUnderTest.createIrsIntegrationAppUnderTest());

        appsUnderTest = new ArrayList<>();
        appsUnderTest.add(IntegrationAppUnderTest.createDaytrader7IntegrationAppUnderTest());
        appsUnderTest.add(IntegrationAppUnderTest.create4_rifIntegrationAppUnderTest());
        appsUnderTest.add(IntegrationAppUnderTest.create7_sfmisIntegrationAppUnderTest());
        appsUnderTest.add(IntegrationAppUnderTest.create40_glengineerIntegrationAppUnderTest()); 
        appsUnderTest.add(IntegrationAppUnderTest.create53_shp2kmlIntegrationAppUnderTest());
        
        OUTDIRS = appsUnderTest.stream()
            .map(app -> app.appOutdir)
            .collect(Collectors.toList());
        OUTDIRS.addAll(appsUnderTestWithSeqInit.stream()
            .map(app -> app.appOutdir)
            .collect(Collectors.toList()));
    }

    @Before
    /**
     * Delete existing output files
     */
    public void cleanUp() throws IOException {
        for (IntegrationAppUnderTest testApp : appsUnderTestWithSeqInit) {
            Files.deleteIfExists(Paths.get(testApp.testPlanFilename));
            String[] testSeqFilenames = testApp.testSeqFilename.split(",");
            for (String testSeqFile : testSeqFilenames) {
                Files.deleteIfExists(Paths.get(testSeqFile));
            }
            Files.deleteIfExists(Paths.get(TestUtils.ExtenderAppUnderTest.getSummaryFileJsonName(testApp.appName)));
            Files.deleteIfExists(Paths.get(TestUtils.ExtenderAppUnderTest.getCoverageFileJsonName(testApp.appName)));
        }

        for (IntegrationAppUnderTest testApp : appsUnderTest) {
            Files.deleteIfExists(Paths.get(testApp.testPlanFilename));
            Files.deleteIfExists(Paths.get(TestUtils.ExtenderAppUnderTest.getSummaryFileJsonName(testApp.appName)));
            Files.deleteIfExists(Paths.get(TestUtils.ExtenderAppUnderTest.getCoverageFileJsonName(testApp.appName)));
        }
        
        for (String outdir : OUTDIRS) {
            if (Files.exists(Paths.get(outdir))) {
                Files.walk(Paths.get(outdir))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }
    }

    @Test
    public void testModelerInitializerExtender() throws Exception {

        // run test on apps
        for (IntegrationAppUnderTest testApp: appsUnderTestWithSeqInit) {
            System.out.println("Current app under test in testModelerInitializerExtender: " + testApp.appName);

            // generate CTD test plan for app
            CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(testApp.appName, null,
                testApp.targetClassList, null, null, null,
                testApp.appPath, testApp.appClasspathFilename, 2, false,
                2, null, null, null, null);
            analyzer.modelPartitions();

            // assert that output file for CTD modeling is created
            assertTrue(testApp.appName, new File(testApp.testPlanFilename).exists());

            // create building-block test sequences using combined test generator
            TestSequenceInitializer seqInitializer = new TestSequenceInitializer(testApp.appName,
                testApp.testPlanFilename, testApp.appPath, testApp.appClasspathFilename,
                Constants.COMBINED_TEST_GENERATOR_NAME, 5, false, false, "");
            seqInitializer.createInitialTests();

            // assert that test sequences files are created
            String[] testSeqFilenames = testApp.testSeqFilename.split(",");
            for (String testSeqFile : testSeqFilenames) {
                assertTrue(testApp.appName + ": Missing testSeqFile: " + testSeqFile, new File(testSeqFile).exists());
            }

            // generate test cases via process launcher
            TestUtils.launchProcess(TestSequenceExtender.class.getSimpleName(),
                testApp.appName, testApp.appPath, testApp.appClasspathFilename, testApp.testSeqFilename,
                testApp.testPlanFilename, null, true, false, null);

            // assert that test directory and summary files are created
            assertTrue(testApp.appName, Files.exists(Paths.get(testApp.appOutdir)));
            assertTrue(testApp.appName, Files.exists(Paths.get(TestUtils.ExtenderAppUnderTest.getSummaryFileJsonName(testApp.appName))));
            assertTrue(testApp.appName, Files.exists(Paths.get(TestUtils.ExtenderAppUnderTest.getCoverageFileJsonName(testApp.appName))));

            // assert over the number of expected test classes
            assertEquals(testApp.appName, testApp.exp__test_classes_count, Files
                .walk(Paths.get(testApp.appOutdir))
                .filter(p -> p.toFile().isFile())
                .count()
            );
        }
    }

    @Test
    public void testModelerExtenderReusingBB() throws Exception {

        // run test on apps
        for (IntegrationAppUnderTest testApp: appsUnderTest) {
            System.out.println("Current app under test in testModelerExtenderReusingBB: " + testApp.appName);

            // generate CTD test plan for app
            CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(testApp.appName, null,
                testApp.targetClassList, null, null, null,
                testApp.appPath, testApp.appClasspathFilename, 2, false,
                2, null, null, null, null);
            analyzer.modelPartitions();

            // assert that output file for CTD modeling is created
            assertTrue(testApp.appName, new File(testApp.testPlanFilename).exists());

            // generate test cases via process launcher
            TestUtils.launchProcess(TestSequenceExtender.class.getSimpleName(),
                testApp.appName, testApp.appPath, testApp.appClasspathFilename, testApp.testSeqFilename,
                testApp.testPlanFilename, null, true, false, null);

            // assert that test directory and summary files are created
            assertTrue(testApp.appName, Files.exists(Paths.get(testApp.appOutdir)));
            assertTrue(testApp.appName, Files.exists(Paths.get(TestUtils.ExtenderAppUnderTest.getSummaryFileJsonName(testApp.appName))));
            assertTrue(testApp.appName, Files.exists(Paths.get(TestUtils.ExtenderAppUnderTest.getCoverageFileJsonName(testApp.appName))));

            long numOfTestFiles = Files
                .walk(Paths.get(testApp.appOutdir))
                .filter(p -> p.toFile().isFile())
                .count();
            assertMinimum(testApp.appName, testApp.exp__test_classes_count, Math.toIntExact(numOfTestFiles));
        }
    }
}
