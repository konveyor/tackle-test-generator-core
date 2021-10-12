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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.evosuite.shaded.org.apache.commons.collections.IteratorUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.konveyor.tackle.testgen.TestUtils;
import org.konveyor.tackle.testgen.TestUtils.ExtenderAppUnderTest;
import org.konveyor.tackle.testgen.util.TackleTestJson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class TestSequenceExtenderTest {

    private static List<String> OUTDIRS;
    private static List<ExtenderAppUnderTest> appsUnderTest;

    @BeforeClass
    public static void createAppsUnderTest() throws IOException {
        appsUnderTest = new ArrayList<>();
        appsUnderTest.add(ExtenderAppUnderTest.createDaytrader7ExtenderAppUnderTest(
            "test/data/daytrader7/DayTrader_ctd_models_new_format.json",
            "test/data/daytrader7/DayTrader_EvoSuiteTestGenerator_bb_test_sequences.json"
        ));
        appsUnderTest.add(ExtenderAppUnderTest.createIrsExtenderAppUnderTest(
            "test/data/irs/irs_ctd_models_and_test_plans.json",
            "test/data/irs/irs_EvoSuiteTestGenerator_bb_test_sequences.json"
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

        for (ExtenderAppUnderTest app : appsUnderTest) {
        	Files.deleteIfExists(Paths.get(ExtenderAppUnderTest.getSummaryFileJsonName(app.appName)));
            Files.deleteIfExists(Paths.get(ExtenderAppUnderTest.getCoverageFileJsonName(app.appName)));
        }
    }

    @SuppressWarnings("unchecked")
	private void assertSummaryFile(ExtenderAppUnderTest app) throws JsonProcessingException, IOException {
        Path summaryFilePath = Paths.get(ExtenderAppUnderTest.getSummaryFileJsonName(app.appName));
        assertTrue(Files.exists(summaryFilePath));

        // read summary JSON file and assert over content
        File testCovFile = new File(summaryFilePath.toString());
        File stdCovFile = new File(app.summaryStandardFilename);
        
        ObjectNode summaryInfo = (ObjectNode) TackleTestJson.getObjectMapper().readTree(testCovFile);
        ObjectNode summaryInfoStd = (ObjectNode) TackleTestJson.getObjectMapper().readTree(stdCovFile);
        
        ObjectNode bbSeqInfo = (ObjectNode) summaryInfo.get("building_block_sequences_info");
        ObjectNode bbSeqInfoStd = (ObjectNode) summaryInfoStd.get("building_block_sequences_info");
        
		assertEquals(bbSeqInfoStd.get("base_sequences").asInt(), bbSeqInfo.get("base_sequences").asInt());
		assertEquals(bbSeqInfoStd.get("parsed_base_sequences_full").asInt(), bbSeqInfo.get("parsed_base_sequences_full").asInt());
		assertEquals(bbSeqInfoStd.get("parsed_base_sequences_partial").asInt(), bbSeqInfo.get("parsed_base_sequences_partial").asInt());
		assertEquals(bbSeqInfoStd.get("skipped_base_sequences").asInt(), bbSeqInfo.get("skipped_base_sequences").asInt());
		assertEquals(bbSeqInfoStd.get("exception_base_sequences").asInt(), bbSeqInfo.get("exception_base_sequences").asInt());
		assertEquals(bbSeqInfoStd.get("method_sequence_pool_keys").asInt(), bbSeqInfo.get("method_sequence_pool_keys").asInt());
		assertEquals(bbSeqInfoStd.get("class_sequence_pool_keys").asInt(), bbSeqInfo.get("class_sequence_pool_keys").asInt());

		ObjectNode extSeqInfo = (ObjectNode) summaryInfo.get("extended_sequences_info");
        ObjectNode extSeqInfoStd = (ObjectNode) summaryInfoStd.get("extended_sequences_info");

        assertEquals(extSeqInfoStd.get("generated_sequences").asInt(), extSeqInfo.get("generated_sequences").asInt());
		assertTrue(app.exp__executed_sequences <= extSeqInfo.get("executed_sequences").asInt());
		assertTrue(extSeqInfo.get("final_sequences").asInt() >= app.expmin_final_sequences);

		ObjectNode covInfo = (ObjectNode) summaryInfo.get("test_plan_coverage_info");
        ObjectNode covInfoStd = (ObjectNode) summaryInfoStd.get("test_plan_coverage_info");
		
		assertEquals(covInfoStd.get("test_plan_rows").asInt(), covInfo.get("test_plan_rows").asInt());
		assertEquals(covInfoStd.get("rows_covered_bb_sequences").asInt(), covInfo.get("rows_covered_bb_sequences").asInt());

		ObjectNode uncovInfo = (ObjectNode) summaryInfo.get("uncovered_test_plan_rows_info");
        ObjectNode uncovInfoStd = (ObjectNode) summaryInfoStd.get("uncovered_test_plan_rows_info");
        
		assertEquals(uncovInfoStd.get("no_bb_sequence_for_target_method").asInt(), uncovInfo.get("no_bb_sequence_for_target_method").asInt());
		assertEquals(uncovInfoStd.get("non_instantiable_param_type").asInt(), uncovInfo.get("non_instantiable_param_type").asInt());
		assertEquals(uncovInfoStd.get("exception_during_extension").asInt(), uncovInfo.get("exception_during_extension").asInt());

		ArrayNode execExcpTypes = (ArrayNode) summaryInfo.get("execution_exception_types_other");
        ArrayNode execExcpTypesStd = (ArrayNode) summaryInfoStd.get("execution_exception_types_other");
		
		assertEquals(TackleTestJson.getObjectMapper().convertValue(execExcpTypesStd, new TypeReference<List<String>>(){}),
            TackleTestJson.getObjectMapper().convertValue(execExcpTypes, new TypeReference<List<String>>(){}));
				
		ArrayNode nonInstTypes = (ArrayNode) summaryInfo.get("non_instantiable_types");
        ArrayNode nonInstTypesStd = (ArrayNode) summaryInfoStd.get("non_instantiable_types");
        
		assertEquals(nonInstTypesStd.size(), nonInstTypes.size());

		ArrayNode cnfTypes = (ArrayNode) summaryInfo.get("class_not_found_types");
        ArrayNode cnfTypesStd = (ArrayNode) summaryInfoStd.get("class_not_found_types");
        
		assertEquals(cnfTypesStd.size(), cnfTypes.size());

		ObjectNode parseExcpTypes = (ObjectNode) summaryInfo.get("parse_exception_types");
        ObjectNode parseExcpTypesStd = (ObjectNode) summaryInfoStd.get("parse_exception_types");
        
		assertEquals(new HashSet<String>(IteratorUtils.toList(parseExcpTypesStd.fieldNames())),
            new HashSet<String>(IteratorUtils.toList(parseExcpTypes.fieldNames())));
		assertEquals(parseExcpTypesStd.get("randoop.sequence.SequenceParseException").asInt(),
				parseExcpTypes.get("randoop.sequence.SequenceParseException").asInt());
	}

    private void assertCoverageFile(ExtenderAppUnderTest app) throws JsonProcessingException, IOException {
        Path testCovFilePath = Paths.get(ExtenderAppUnderTest.getCoverageFileJsonName(app.appName));
        assertTrue(Files.exists(testCovFilePath));
        File testCovFile = new File(testCovFilePath.toString());
        File testCovFileStd = new File(app.coverageStandardFilename);

        // read coverage JSON file and assert over content
		ObjectNode summaryInfo = (ObjectNode) TackleTestJson.getObjectMapper().readTree(testCovFile);
        ObjectNode summaryInfoStd = (ObjectNode) TackleTestJson.getObjectMapper().readTree(testCovFileStd);
        
		assertEquals(summaryInfoStd.size(), summaryInfo.size());
		for (String covKey : app.exp__target_method_coverage.keySet()) {
			String[] covKeyTokens = covKey.split("::");
			String actualCoverage = summaryInfo.get(covKeyTokens[0]).get(covKeyTokens[1])
					.get(covKeyTokens[2]).get(covKeyTokens[3]).asText();
			assertEquals(app.exp__target_method_coverage.get(covKey), actualCoverage);
		}
	}

    private void assertTestClassesDir(ExtenderAppUnderTest app) throws IOException {
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

        for (ExtenderAppUnderTest app : appsUnderTest) {

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
        for (ExtenderAppUnderTest app : appsUnderTest) {

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
