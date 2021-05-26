package org.konveyor.tackle.testgen.core.extender;

import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.Utils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.json.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSequenceExtenderTest {

    private static final String JEE_SUPPORT_OPTION = "JEE_SUPPORT";
    private static List<String> OUTDIRS;
    private static List<AppUnderTest> appsUnderTest;

    /**
     * Class containing information about an application under test
     */
    static class AppUnderTest {
        String appName;
        String appOutdir;
        String classpath;
        String testPlanFilename;
        String testSeqFilename;

        // expected values
        int exp__bb_sequences;
        int exp__parsed_sequences;
        int exp__method_sequence_pool_keys;
        int exp__class_sequence_pool_keys;
        int exp__generated_sequences;
        int exp__executed_sequences;
        int exp__test_plan_rows;
        int exp__rows_covered_bb_sequences;
        int expmin_final_sequences;
        int exp__no_bb_sequence_for_target_method;
        int exp__non_instantiable_param_type;
        int exp__excp_during_extension;
        List<String> exp__execution_exception_types_other;
        int exp__class_not_found_types;
        Set<String> exp__parse_exception_types;
        int exp__randoop_sequence_SequenceParseException;
        int exp__java_lang_Error;
        int exp__partition_count;
        Map<String, String> exp__tatget_method_coverage;
        int exp__test_classes_count;

        AppUnderTest(String appName, String classpath, String testPlanFilename, String testSeqFilename) {
            this.appName = appName;
            this.classpath = classpath;
            this.appOutdir = appName+"-"+ Constants.AMPLIFIED_TEST_CLASSES_OUTDIR;
            this.testPlanFilename = testPlanFilename;
            this.testSeqFilename = testSeqFilename;
        }
    }

    private static AppUnderTest createDaytraderApp() throws IOException {
        // create classpath string
        String classpathFilename = "test"+File.separator+"data"+File.separator+
            "daytrader7"+File.separator+"DayTraderMonoClasspath.txt";
        String projectClasspath = "";
        File file = new File(classpathFilename);
        if (!file.isFile()) {
            throw new IOException(file.getAbsolutePath() + " is not a valid file");
        }
        projectClasspath += Utils.entriesToClasspath(Utils.getClasspathEntries(file));
        projectClasspath += (File.pathSeparator + "test"+File.separator+"data"+File.separator+"daytrader7"+
            File.separator+"monolith"+File.separator+"bin");
        projectClasspath += (File.pathSeparator + "lib" + File.separator + "evosuite-master-1.0.7-SNAPSHOT.jar");
        projectClasspath += (File.pathSeparator + "lib" + File.separator
            + "evosuite-standalone-runtime-1.0.7-SNAPSHOT.jar");
        projectClasspath += (File.pathSeparator + System.getProperty("java.class.path"));

        String testPlanFilename = "test"+File.separator+"data"+File.separator+
            "daytrader7"+File.separator+"DayTrader_ctd_models_new_format.json";
        String testSeqFilename = "test"+File.separator+"data"+File.separator+
            "daytrader7"+File.separator+"DayTrader_EvoSuiteTestGenerator_bb_test_sequences.json";

        // construct app object
        AppUnderTest app = new AppUnderTest("DayTrader", projectClasspath, testPlanFilename,
            testSeqFilename);

        // set expected values
        app.exp__bb_sequences = 159;
        app.exp__parsed_sequences = 141;
        app.exp__method_sequence_pool_keys = 102;
        app.exp__class_sequence_pool_keys = 39;
        app.exp__generated_sequences = 1486;
        app.exp__executed_sequences = 1471;
        app.exp__test_plan_rows = 1486;
        app.exp__rows_covered_bb_sequences = 282;
        app.expmin_final_sequences = 1146;
        app.exp__no_bb_sequence_for_target_method = 0;
        app.exp__non_instantiable_param_type = 0;
        app.exp__excp_during_extension = 15;
        app.exp__execution_exception_types_other = Arrays.asList("java.lang.StringIndexOutOfBoundsException");
        app.exp__class_not_found_types = 0;
        app.exp__parse_exception_types = Stream.of("java.lang.Error", "randoop.sequence.SequenceParseException").
            collect(Collectors.toSet());
        app.exp__randoop_sequence_SequenceParseException = 1;
        app.exp__java_lang_Error = 17;
        app. exp__partition_count = 4;
        app.exp__tatget_method_coverage =
            Stream.of(new String[][] {
                {"DayTraderProcessor::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"},
                {"DayTraderWeb::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"},
                {"DayTraderUtil::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"}})
                .collect(Collectors.toMap(value -> value[0], value -> value[1]));
        app.exp__test_classes_count = 42;

        return app;
    }

    private static AppUnderTest createIrsApp() throws IOException {
        // create classpath string
        String irsRootDir = "test"+File.separator+"data"+File.separator+"irs";
        String classpathFilename = irsRootDir+File.separator+"irsMonoClasspath.txt";
        String projectClasspath = "";
        File file = new File(classpathFilename);
        if (!file.isFile()) {
            throw new IOException(file.getAbsolutePath() + " is not a valid file");
        }
        projectClasspath += Utils.entriesToClasspath(Utils.getClasspathEntries(file));
        projectClasspath += (File.pathSeparator + irsRootDir + File.separator + "monolith" +
            File.separator + "target"+File.separator + "classes");
        projectClasspath += (File.pathSeparator + "lib" + File.separator + "evosuite-master-1.0.7-SNAPSHOT.jar");
        projectClasspath += (File.pathSeparator + "lib" + File.separator
            + "evosuite-standalone-runtime-1.0.7-SNAPSHOT.jar");
        projectClasspath += (File.pathSeparator + System.getProperty("java.class.path"));

        String testPlanFilename = irsRootDir+File.separator+"irs_ctd_models_and_test_plans.json";
        String testSeqFilename = irsRootDir+File.separator+"irs_EvoSuiteTestGenerator_bb_test_sequences.json";

        // construct app object
        AppUnderTest app = new AppUnderTest("irs", projectClasspath, testPlanFilename,
            testSeqFilename);

        // set expected values
        app.exp__bb_sequences = 13;
        app.exp__parsed_sequences = 12;
        app.exp__method_sequence_pool_keys = 11;
        app.exp__class_sequence_pool_keys = 5;
        app.exp__generated_sequences = 25;
        app.exp__executed_sequences = 25;
        app.exp__test_plan_rows = 25;
        app.exp__rows_covered_bb_sequences = 11;
        app.expmin_final_sequences = 23;
        app.exp__no_bb_sequence_for_target_method = 0;
        app.exp__non_instantiable_param_type = 0;
        app.exp__excp_during_extension = 0;
        app.exp__execution_exception_types_other = Arrays.asList();
        app.exp__class_not_found_types = 0;
        app.exp__parse_exception_types = Stream.of("randoop.sequence.SequenceParseException").
            collect(Collectors.toSet());
        app.exp__randoop_sequence_SequenceParseException = 1;
        app.exp__java_lang_Error = 0;
        app.exp__partition_count = 2;
        app.exp__tatget_method_coverage =
            Stream.of(new String[][] {
                {"app-partition_1::irs.Employer::setEmployees(java.util.List)::test_plan_row_1", "COVERED"},
                {"app-partition_1::irs.Employer::addEmployees(irs.Employee[])::test_plan_row_1", "COVERED"},
                {"app-partition_1::irs.IRS::setAllSalarySets(java.util.Map)::test_plan_row_1", "COVERED"},
                {"app-partition_1::irs.IRS::setAllSalaryMaps(java.util.Map)::test_plan_row_1", "COVERED"},
                {"app-partition_1::irs.IRS::setEmployerSalaryListMap(java.util.List)::test_plan_row_1", "COVERED"},
                {"app-partition_1::irs.IRS::setEmployerSalaryListSet(java.util.List)::test_plan_row_1", "COVERED"},
                {"app-partition_1::irs.IRS::setEmployerArrayList(java.util.List[])::test_plan_row_1", "COVERED"},
                {"app-partition_1::irs.IRS::setEmployerArrayMap(java.util.Map[])::test_plan_row_1", "COVERED"},
                {"app-partition_2::irs.BusinessProcess::main(java.lang.String[])::test_plan_row_1", "COVERED"}})
                .collect(Collectors.toMap(value -> value[0], value -> value[1]));
        app.exp__test_classes_count = 5;

        return app;
    }

    @BeforeClass
    public static void createAppsUnderTest() throws IOException {
        appsUnderTest = new ArrayList<>();
        appsUnderTest.add(createDaytraderApp());
        appsUnderTest.add(createIrsApp());
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
        Files.deleteIfExists(Paths.get(Constants.COVERAGE_FILE_JSON));
        Files.deleteIfExists(Paths.get(Constants.EXTENDER_SUMMARY_FILE_JSON));
    }

    private List<String> getCommandLine(String classpath)  {
        List<String> args = new ArrayList<String>();
        args.add("java");
        args.add("-cp");
        args.add("\"" + classpath + "\""); // add double quotes in case path contains spaces
        return args;
    }

    private File getDevNullFile() {
        String osname = System.getProperty("os.name").toLowerCase();
        if (osname.startsWith("win")) {
            return new File("nul");
        }
        return new File("/dev/null");
    }

    private void assertSummaryFile(AppUnderTest app) throws FileNotFoundException {
        Path summaryFilePath = Paths.get(Constants.EXTENDER_SUMMARY_FILE_JSON);
        assertTrue(Files.exists(summaryFilePath));

        // read summary JSON file and assert over content
        File testCovFile = new File(summaryFilePath.toString());
        InputStream fis = new FileInputStream(testCovFile);
        try (JsonReader reader = Json.createReader(fis)) {
            JsonObject summaryInfo = reader.readObject();
            JsonObject bbSeqInfo = summaryInfo.getJsonObject("building_block_sequences_info");
            assertEquals(app.exp__bb_sequences, bbSeqInfo.getInt("bb_sequences"));
            assertEquals(app.exp__parsed_sequences, bbSeqInfo.getInt("parsed_sequences"));
            assertEquals(app.exp__method_sequence_pool_keys, bbSeqInfo.getInt("method_sequence_pool_keys"));
            assertEquals(app.exp__class_sequence_pool_keys, bbSeqInfo.getInt("class_sequence_pool_keys"));

            JsonObject extSeqInfo = summaryInfo.getJsonObject("extended_sequences_info");
            assertEquals(app.exp__generated_sequences, extSeqInfo.getInt("generated_sequences"));
            assertEquals(app.exp__executed_sequences, extSeqInfo.getInt("executed_sequences"));
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

    private void assertCoverageFile(AppUnderTest app) throws FileNotFoundException {
        Path testCovFilePath = Paths.get(Constants.COVERAGE_FILE_JSON);
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

    private void assertTestClassesDir(AppUnderTest app) throws IOException {
        Path testClassesDir = Paths.get(app.appOutdir);
        assertTrue(Files.exists(testClassesDir));

        // expected values
        assertEquals(app.exp__test_classes_count, Files
            .walk(testClassesDir)
            .filter(p -> p.toFile().isFile())
            .count()
        );
    }

    @Test
    public void testGenerateTestsWithJEESupport() throws Exception {

        for (AppUnderTest app : appsUnderTest) {

            // skip irs app for execution with JEE support
            if (app.appName.equals("irs")) {
                 continue;
            }

            // build command line to be executed via process builder
            List<String> args = getCommandLine(app.classpath);
            args.add(SequenceExtenderRunner.class.getName());
            args.add(app.appName);
            args.add(app.testPlanFilename);
            args.add(app.testSeqFilename);
            args.add(JEE_SUPPORT_OPTION);

            // execute test case via process builder
            ProcessBuilder sequenceParserPB = new ProcessBuilder(args);
//        sequenceParserPB.inheritIO();
            sequenceParserPB.redirectOutput(getDevNullFile());
//        sequenceParserPB.redirectError(ProcessBuilder.Redirect.INHERIT);
            sequenceParserPB.redirectError(getDevNullFile());
            Process sequenceExecutorP = sequenceParserPB.start();
            sequenceExecutorP.waitFor();
            assertEquals(0, sequenceExecutorP.exitValue());

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
        for (AppUnderTest app : appsUnderTest) {

//            if (app.appName.equals("DayTrader")) {
//                continue;
//            }

            // build command line to be executed via process builder
            List<String> args = getCommandLine(app.classpath);
            args.add(SequenceExtenderRunner.class.getName());
            args.add(app.appName);
            args.add(app.testPlanFilename);
            args.add(app.testSeqFilename);

            // execute test case via process builder
            ProcessBuilder sequenceParserPB = new ProcessBuilder(args);
            sequenceParserPB.redirectOutput(getDevNullFile());
            sequenceParserPB.redirectError(getDevNullFile());
            Process sequenceExecutorP = sequenceParserPB.start();
            sequenceExecutorP.waitFor();
            assertEquals(0, sequenceExecutorP.exitValue());

            // assert over summary file
            assertSummaryFile(app);

            // assert over coverage file
            assertCoverageFile(app);

            // assert over generated test classes dir
            assertTestClassesDir(app);
        }
    }


    static class SequenceExtenderRunner {

        public static void main(String[] args) throws IOException {

            // get app name from command-line args
            String appName = args[0];
            String testPlanFilename = args[1];
            String testSeqFilename = args[2];

            // set jee support option
            boolean jeeSupportOpt = false;
            if (args.length == 4 && args[3].equals(JEE_SUPPORT_OPTION)) {
                jeeSupportOpt = true;
            }

            // compute extended sequences with diff assertions for test plan and sequence file name
            TestSequenceExtender testSeqExt = new TestSequenceExtender(appName,
                testPlanFilename, testSeqFilename, true, null, jeeSupportOpt,
                Constants.NUM_SEQUENCE_EXECUTION, true
            );
            testSeqExt.createExtendedSequences();

            // write test classes
            testSeqExt.writeTestClasses(true);

            // write coverage information to JSON file and assert over file content
            testSeqExt.writeTestCoverageFile(null);
        }

    }

}
