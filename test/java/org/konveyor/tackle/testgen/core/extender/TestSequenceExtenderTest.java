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
        appsUnderTest.add(ExtenderAppUnderTest.createAppForExtenderTest("daytrader7",
            "test"+File.separator+"data"+File.separator+"daytrader7"+
                File.separator+"DayTrader_ctd_models_new_format.json",
            "test"+File.separator+"data"+File.separator+
                "daytrader7"+File.separator+"DayTrader_EvoSuiteTestGenerator_bb_test_sequences.json"
        )); 
        appsUnderTest.add(ExtenderAppUnderTest.createAppForExtenderTest("irs",
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
        
        ObjectNode summaryInfo = (ObjectNode) TackleTestJson.getObjectMapper().readTree(testCovFile);	
        ObjectNode bbSeqInfo = (ObjectNode) summaryInfo.get("building_block_sequences_info");
        
		assertEquals(app.exp__bb_sequences, bbSeqInfo.get("base_sequences").asInt());
		assertEquals(app.exp__parsed_sequences_full, bbSeqInfo.get("parsed_base_sequences_full").asInt());
		assertEquals(app.exp__parsed_sequences_partial, bbSeqInfo.get("parsed_base_sequences_partial").asInt());
		assertEquals(app.exp__skipped_sequences, bbSeqInfo.get("skipped_base_sequences").asInt());
		assertEquals(app.exp__exception_sequences, bbSeqInfo.get("exception_base_sequences").asInt());
		assertEquals(app.exp__method_sequence_pool_keys, bbSeqInfo.get("method_sequence_pool_keys").asInt());
		assertEquals(app.exp__class_sequence_pool_keys, bbSeqInfo.get("class_sequence_pool_keys").asInt());

		ObjectNode extSeqInfo = (ObjectNode) summaryInfo.get("extended_sequences_info");
		assertEquals(app.exp__generated_sequences, extSeqInfo.get("generated_sequences").asInt());
		assertTrue(app.exp__executed_sequences <= extSeqInfo.get("executed_sequences").asInt());
		assertTrue(extSeqInfo.get("final_sequences").asInt() >= app.expmin_final_sequences);

		ObjectNode covInfo = (ObjectNode) summaryInfo.get("test_plan_coverage_info");
		assertEquals(app.exp__test_plan_rows, covInfo.get("test_plan_rows").asInt());
		assertEquals(app.exp__rows_covered_bb_sequences, covInfo.get("rows_covered_bb_sequences").asInt());

		ObjectNode uncovInfo = (ObjectNode) summaryInfo.get("uncovered_test_plan_rows_info");
		assertEquals(app.exp__no_bb_sequence_for_target_method, uncovInfo.get("no_bb_sequence_for_target_method").asInt());
		assertEquals(app.exp__non_instantiable_param_type, uncovInfo.get("non_instantiable_param_type").asInt());
		assertEquals(app.exp__excp_during_extension, uncovInfo.get("exception_during_extension").asInt());

		ArrayNode execExcpTypes = (ArrayNode) summaryInfo.get("execution_exception_types_other");
		assertEquals(app.exp__execution_exception_types_other, 
				TackleTestJson.getObjectMapper().convertValue(execExcpTypes, new TypeReference<List<String>>(){}));
				
		ArrayNode nonInstTypes = (ArrayNode) summaryInfo.get("non_instantiable_types");
		assertEquals(app.exp__non_instantiable_param_type, nonInstTypes.size());

		ArrayNode cnfTypes = (ArrayNode) summaryInfo.get("class_not_found_types");
		assertEquals(app.exp__class_not_found_types, cnfTypes.size());

		ObjectNode parseExcpTypes = (ObjectNode) summaryInfo.get("parse_exception_types");
		assertEquals(app.exp__parse_exception_types, new HashSet<String>(IteratorUtils.toList(parseExcpTypes.fieldNames())));
		assertEquals(app.exp__randoop_sequence_SequenceParseException,
				parseExcpTypes.get("randoop.sequence.SequenceParseException").asInt());
	}

    private void assertCoverageFile(ExtenderAppUnderTest app) throws JsonProcessingException, IOException {
        Path testCovFilePath = Paths.get(ExtenderAppUnderTest.getCoverageFileJsonName(app.appName));
        assertTrue(Files.exists(testCovFilePath));
        File testCovFile = new File(testCovFilePath.toString());

        // read coverage JSON file and assert over content
		ObjectNode summaryInfo = (ObjectNode) TackleTestJson.getObjectMapper().readTree(testCovFile);
		assertEquals(app.exp__partition_count, summaryInfo.size());
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
