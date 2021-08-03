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

package org.konveyor.tackle.testgen.core.extender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.konveyor.tackle.testgen.TestUtils;
import org.konveyor.tackle.testgen.util.Constants;

public class TestSequenceExtenderTest {

    private static List<String> OUTDIRS;
    private static List<TestUtils.AppUnderTest> appsUnderTest;

    @BeforeClass
    public static void createAppsUnderTest() throws IOException {
        appsUnderTest = new ArrayList<>();
        appsUnderTest.add(TestUtils.createDaytraderApp(
            "test"+File.separator+"data"+File.separator+"daytrader7"+
                File.separator+"DayTrader_ctd_models_new_format.json",
            "test"+File.separator+"data"+File.separator+
                "daytrader7"+File.separator+"DayTrader_EvoSuiteTestGenerator_bb_test_sequences.json"
        ));
        appsUnderTest.add(TestUtils.createIrsApp(
            "test"+File.separator+"data"+File.separator+"irs"+
                File.separator+"irs_ctd_models_and_test_plans.json",
            "test"+File.separator+"data"+File.separator+"irs"+
                File.separator+"irs_EvoSuiteTestGenerator_bb_test_sequences.json"
        ));
        OUTDIRS = appsUnderTest.stream()
            .map(app -> app.appOutdir)
            .collect(Collectors.toList());
    }

    @Before
    /**
     * Delete existing output files
     */
    public void cleanUp() throws IOException {
        for (String outdir: OUTDIRS) {
            if (Files.exists(Paths.get(outdir))) {
                Files.walk(Paths.get(outdir))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }

        for (TestUtils.AppUnderTest app : appsUnderTest) {
        	Files.deleteIfExists(Paths.get(app.appName+Constants.EXTENDER_SUMMARY_FILE_JSON_SUFFIX));
        	Files.deleteIfExists(Paths.get(app.appName+Constants.COVERAGE_FILE_JSON_SUFFIX));
        }
    }

    private void assertSummaryFile(TestUtils.AppUnderTest app) throws FileNotFoundException {
        Path summaryFilePath = Paths.get(app.appName+Constants.EXTENDER_SUMMARY_FILE_JSON_SUFFIX);
        assertTrue(Files.exists(summaryFilePath));

        // read summary JSON file and assert over content
        File testCovFile = new File(summaryFilePath.toString());
        InputStream fis = new FileInputStream(testCovFile);
        try (JsonReader reader = Json.createReader(fis)) {
            JsonObject summaryInfo = reader.readObject();
            JsonObject bbSeqInfo = summaryInfo.getJsonObject("building_block_sequences_info");
            assertEquals(app.exp__bb_sequences, bbSeqInfo.getInt("base_sequences"));
            assertEquals(app.exp__parsed_sequences_full, bbSeqInfo.getInt("parsed_base_sequences_full"));
            assertEquals(app.exp__parsed_sequences_partial, bbSeqInfo.getInt("parsed_base_sequences_partial"));
            assertEquals(app.exp__skipped_sequences, bbSeqInfo.getInt("skipped_base_sequences"));
            assertEquals(app.exp__exception_sequences, bbSeqInfo.getInt("exception_base_sequences"));
            assertEquals(app.exp__method_sequence_pool_keys, bbSeqInfo.getInt("method_sequence_pool_keys"));
            assertEquals(app.exp__class_sequence_pool_keys, bbSeqInfo.getInt("class_sequence_pool_keys"));

            JsonObject extSeqInfo = summaryInfo.getJsonObject("extended_sequences_info");
            assertEquals(app.exp__generated_sequences, extSeqInfo.getInt("generated_sequences"));
            assertTrue(app.exp__executed_sequences <= extSeqInfo.getInt("executed_sequences"));
            assertTrue(extSeqInfo.getInt("final_sequences") >= app.expmin_final_sequences);

            JsonObject covInfo = summaryInfo.getJsonObject("test_plan_coverage_info");
            assertEquals(app.exp__test_plan_rows, covInfo.getInt("test_plan_rows"));
            assertEquals(app.exp__rows_covered_bb_sequences, covInfo.getInt("rows_covered_bb_sequences"));

            JsonObject uncovInfo = summaryInfo.getJsonObject("uncovered_test_plan_rows_info");
            assertEquals(app.exp__no_bb_sequence_for_target_method, uncovInfo.getInt("no_bb_sequence_for_target_method"));
            assertEquals(app.exp__non_instantiable_param_type, uncovInfo.getInt("non_instantiable_param_type"));
            assertEquals(app.exp__excp_during_extension, uncovInfo.getInt("exception_during_extension"));

            JsonArray execExcpTypes = summaryInfo.getJsonArray("execution_exception_types_other");
            assertEquals(
                app.exp__execution_exception_types_other,
                execExcpTypes.getValuesAs(JsonString.class)
                    .stream()
                    .map(JsonString::getString)
                    .collect(Collectors.toList())
            );

            JsonArray nonInstTypes = summaryInfo.getJsonArray("non_instantiable_types");
            assertEquals(app.exp__non_instantiable_param_type, nonInstTypes.size());

            JsonArray cnfTypes = summaryInfo.getJsonArray("class_not_found_types");
            assertEquals(app.exp__class_not_found_types, cnfTypes.size());

            JsonObject parseExcpTypes = summaryInfo.getJsonObject("parse_exception_types");
            assertEquals(app.exp__parse_exception_types, parseExcpTypes.keySet());
            assertEquals(app.exp__randoop_sequence_SequenceParseException, parseExcpTypes.getInt("randoop.sequence.SequenceParseException"));
        }
    }

    private void assertCoverageFile(TestUtils.AppUnderTest app) throws FileNotFoundException {
        Path testCovFilePath = Paths.get(app.appName+Constants.COVERAGE_FILE_JSON_SUFFIX);
        assertTrue(Files.exists(testCovFilePath));
        File testCovFile = new File(testCovFilePath.toString());
        InputStream fis = new FileInputStream(testCovFile);

        // read coverage JSON file and assert over content
        try (JsonReader reader = Json.createReader(fis)) {
            JsonObject summaryInfo = reader.readObject();
            assertEquals(app.exp__partition_count, summaryInfo.keySet().size());
            for (String covKey : app.exp__tatget_method_coverage.keySet()) {
                String[] covKeyTokens = covKey.split("::");
                String actualCoverage = summaryInfo.getJsonObject(covKeyTokens[0])
                    .getJsonObject(covKeyTokens[1])
                    .getJsonObject(covKeyTokens[2])
                    .getString(covKeyTokens[3]);
                assertEquals(app.exp__tatget_method_coverage.get(covKey), actualCoverage);
            }
        }
    }

    private void assertTestClassesDir(TestUtils.AppUnderTest app) throws IOException {
        Path testClassesDir = Paths.get(app.appOutdir);
        assertTrue(Files.exists(testClassesDir));

        // expected values
        assertTrue(app.exp__test_classes_count <= Files
            .walk(testClassesDir)
            .filter(p -> p.toFile().isFile())
            .count()
        );
    }

    @Test
    public void testGenerateTestsWithJEESupport() throws Exception {

        for (TestUtils.AppUnderTest app : appsUnderTest) {

            // skip irs app for execution with JEE support
            if (app.appName.equals("irs")) {
                 continue;
            }

            // generate test cases via process launcher
            TestUtils.launchProcess(TestSequenceExtender.class.getSimpleName(),
                app.appName, app.appPath, app.appClasspathFilename, app.testSeqFilename,
                app.testPlanFilename, null, true,null);

            // assert over summary file
            assertSummaryFile(app);

            // assert over coverage file
            assertCoverageFile(app);

            // assert over generated test classes dir
            assertTestClassesDir(app);
        }
    }

    @Test
    public void testGenerateTestsWithoutJEESupport() throws Exception {
        for (TestUtils.AppUnderTest app : appsUnderTest) {

//            if (app.appName.equals("DayTrader")) {
//                continue;
//            }

            // execute test cases via process launcher
            TestUtils.launchProcess(TestSequenceExtender.class.getSimpleName(),
                app.appName, app.appPath, app.appClasspathFilename, app.testSeqFilename,
                app.testPlanFilename, null, false,null);

            // assert over summary file
            assertSummaryFile(app);

            // assert over coverage file
            assertCoverageFile(app);

            // assert over generated test classes dir
            assertTestClassesDir(app);
        }
    }

}
