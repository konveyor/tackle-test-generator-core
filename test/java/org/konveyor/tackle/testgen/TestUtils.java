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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.konveyor.tackle.testgen.core.DiffAssertionsGenerator;
import org.konveyor.tackle.testgen.core.EvoSuiteTestGenerator;
import org.konveyor.tackle.testgen.core.RandoopTestGenerator;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.core.extender.TestSequenceExtender;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.Utils;

public class TestUtils {

    private static final String COVERAGE_OUTDIR = "target"+File.separator+"jacoco-output";
    private static final String COVERAGE_FILE_PREFIX = "jacoco-";
    private static int COVERAGE_FILE_COUNTER = 1;

    public static String getJacocoAgentJarPath() {
        Optional<String> evosuiteJarPath = Arrays.stream(
            System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(elem -> elem.matches(".*org.jacoco.agent-.*-runtime.*"))
            .findFirst();
        if (evosuiteJarPath.isPresent()) {
            return evosuiteJarPath.get();
        }
        return "";
    }

    public static String getJacocoAgentArg(String testName) {
        String jacocoAgentJar = TestUtils.getJacocoAgentJarPath();
        if (jacocoAgentJar.isEmpty()) {
            return "";
        }
        String jacocoAgentArgs = "=destfile=" + COVERAGE_OUTDIR + File.separator+COVERAGE_FILE_PREFIX +
            testName + "-" + COVERAGE_FILE_COUNTER++ +".exec";
        System.out.println("jacoco agent args: "+jacocoAgentArgs);
        return "-javaagent:" + jacocoAgentJar + jacocoAgentArgs;
    }

    public static void launchProcess(String testClassName, String appName, String appPath,
                                     String appClasspathFileName, String seqFile, String testPlanFile,
                                     Boolean allResults, Boolean jeeSupport, String resultsFile)
        throws IOException, InterruptedException {
        String projectClasspath = "";

        File file = new File(appClasspathFileName);
        if (!file.isFile()) {
            throw new IOException(file.getAbsolutePath() + " is not a valid file");
        }
        projectClasspath += Utils.entriesToClasspath(Utils.getClasspathEntries(file));
        projectClasspath += (File.pathSeparator + appPath);

        // Adding evosuite runtime classes in case in what used to generate the tests
        projectClasspath += (File.pathSeparator + Utils.getEvoSuiteJarPath(Constants.EVOSUITE_MASTER_JAR_NAME));
        projectClasspath += (File.pathSeparator + Utils.getEvoSuiteJarPath(Constants.EVOSUITE_RUNTIME_JAR_NAME));

        // For SequenceExecutor class:
        projectClasspath += (File.pathSeparator+System.getProperty("java.class.path"));

        List<String> processArgs = new ArrayList<String>();
        processArgs.add("java");
        processArgs.add("-cp");
        processArgs.add("\""+projectClasspath+"\""); // add double quotes in case path contains spaces

        // add jacoco agent argument to collect coverage data for process
        String jacocoAgentArg = TestUtils.getJacocoAgentArg(testClassName+"Test");
        if (!jacocoAgentArg.isEmpty()) {
            processArgs.add(jacocoAgentArg);
        }
        if (testClassName.equals(SequenceExecutor.class.getSimpleName())) {
            processArgs.add(SequenceExecutor.class.getName());
            processArgs.add("-app");
            processArgs.add(appName);
            processArgs.add("-seq");
            processArgs.add(seqFile);
            processArgs.add("-all");
            processArgs.add(String.valueOf(allResults));
        }
        else if (testClassName.equals(DiffAssertionsGenerator.class.getSimpleName())) {
            processArgs.add(DiffAssertionsGenerator.class.getName());
            processArgs.add("-app");
            processArgs.add(appName);
            processArgs.add("-seq");
            processArgs.add(seqFile);
            processArgs.add("-seqr");
            processArgs.add(resultsFile);
        }
        else {
            processArgs.add(TestSequenceExtender.class.getName());
            processArgs.add("-app");
            processArgs.add(appName);
            processArgs.add("-tp");
            processArgs.add(testPlanFile);
            processArgs.add("-ts");
            processArgs.add(seqFile);
            if (jeeSupport) {
                processArgs.add("-jee");
            }
            processArgs.add("-da");
        }

        ProcessBuilder processExecutorPB = new ProcessBuilder(processArgs);;
//        processExecutorPB.inheritIO();
        processExecutorPB.redirectOutput(getDevNullFile());
        processExecutorPB.redirectError(getDevNullFile());
        long startTime = System.currentTimeMillis();
        Process processExecutorP = processExecutorPB.start();
        processExecutorP.waitFor();
        System.out.println("Execution took "+(System.currentTimeMillis()-startTime)+" milliseconds");
    }

    private static File getDevNullFile() {
        String osname = System.getProperty("os.name").toLowerCase();
        if (osname.startsWith("win")) {
            return new File("nul");
        }
        return new File("/dev/null");
    }

    /**
     * Class containing information about an application under test
     */
    public static class AppUnderTest {
        public String appName;
        public String appOutdir;
        public String appPath;
        public String appClasspathFilename;
        public String testPlanFilename;
        
        
        public AppUnderTest(String appName, String appClasspath, String appPath, String testPlanFilename) {
            this.appName = appName;
            this.appClasspathFilename = appClasspath;
            this.appPath = appPath;
            this.testPlanFilename = testPlanFilename;
            this.appOutdir = appName + "-" + Constants.AMPLIFIED_TEST_CLASSES_OUTDIR;
        }
    }

    
    public static class ExtenderAppUnderTest extends AppUnderTest {
        public String testSeqFilename;
        // expected values
        public int exp__bb_sequences;
        public int exp__parsed_sequences_full;
        public int exp__parsed_sequences_partial;
        public int exp__skipped_sequences;
        public int exp__exception_sequences;
        public int exp__method_sequence_pool_keys;
        public int exp__class_sequence_pool_keys;
        public int exp__generated_sequences;
        public int exp__executed_sequences;
        public int exp__test_plan_rows;
        public int exp__rows_covered_bb_sequences;
        public int expmin_final_sequences;
        public int exp__no_bb_sequence_for_target_method;
        public int exp__non_instantiable_param_type;
        public int exp__excp_during_extension;
        public List<String> exp__execution_exception_types_other;
        public int exp__class_not_found_types;
        public Set<String> exp__parse_exception_types;
        public int exp__randoop_sequence_SequenceParseException;
        public int exp__java_lang_Error;
        public int exp__partition_count;
        public Map<String, String> exp__target_method_coverage;
        public int exp__test_classes_count;

        public ExtenderAppUnderTest(String appName, String appClasspath, String appPath, String testPlanFilename, String testSeqFilename) {
            super(appName, appClasspath, appPath, testPlanFilename);
            this.testSeqFilename = testSeqFilename;

            if (this.testPlanFilename == null) {
                this.testPlanFilename = appName + "_" + Constants.CTD_OUTFILE_SUFFIX;
            }
            if (this.testSeqFilename == null) {
                this.testSeqFilename = appName + "_" + EvoSuiteTestGenerator.class.getSimpleName() + "_" +
                    Constants.INITIALIZER_OUTPUT_FILE_NAME_SUFFIX + "," +
                    appName + "_" + RandoopTestGenerator.class.getSimpleName() + "_" +
                    Constants.INITIALIZER_OUTPUT_FILE_NAME_SUFFIX;
            }
        }

        public static ExtenderAppUnderTest createIrsExtenderAppUnderTest(String testPlanFilename, String testSeqFilename){
            
            ExtenderAppUnderTest appUnderTest = new ExtenderAppUnderTest("irs",
                "test/data/irs/irsMonoClasspath.txt",
                "test/data/irs/monolith/target/classes",
                testPlanFilename,
                testSeqFilename);

            appUnderTest.exp__bb_sequences = 13;
            appUnderTest.exp__parsed_sequences_full = 12;
            appUnderTest.exp__parsed_sequences_partial = 0;
            appUnderTest.exp__skipped_sequences = 0;
            appUnderTest.exp__exception_sequences = 1;
            appUnderTest.exp__method_sequence_pool_keys = 11;
            appUnderTest.exp__class_sequence_pool_keys = 5;
            appUnderTest.exp__generated_sequences = 25;
            appUnderTest.exp__executed_sequences = 25;
            appUnderTest.exp__test_plan_rows = 25;
            appUnderTest.exp__rows_covered_bb_sequences = 11;
            appUnderTest.expmin_final_sequences = 23;
            appUnderTest.exp__no_bb_sequence_for_target_method = 0;
            appUnderTest.exp__non_instantiable_param_type = 0;
            appUnderTest.exp__excp_during_extension = 0;
            appUnderTest.exp__execution_exception_types_other = Arrays.asList();
            appUnderTest.exp__class_not_found_types = 0;
            appUnderTest.exp__parse_exception_types = Stream.of("randoop.sequence.SequenceParseException").
                    collect(Collectors.toSet());
            appUnderTest.exp__randoop_sequence_SequenceParseException = 1;
            appUnderTest.exp__java_lang_Error = 0;
            appUnderTest.exp__partition_count = 2;
            appUnderTest.exp__target_method_coverage =
                    Stream.of(new String[][]{
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
            appUnderTest.exp__test_classes_count = 5;
            
            return appUnderTest;
        }

        public static ExtenderAppUnderTest createDaytrader7ExtenderAppUnderTest(String testPlanFilename, String testSeqFilename) {
            
            ExtenderAppUnderTest appUnderTest = new ExtenderAppUnderTest("daytrader7",
                "test/data/daytrader7/daytrader7MonoClasspath.txt",
                "test/data/daytrader7/monolith/bin",
                testPlanFilename,
                testSeqFilename);

            appUnderTest.exp__bb_sequences = 159;
            appUnderTest.exp__parsed_sequences_full = 155;
            appUnderTest.exp__parsed_sequences_partial = 0;
            appUnderTest.exp__skipped_sequences = 3;
            appUnderTest.exp__exception_sequences = 1;
            appUnderTest.exp__method_sequence_pool_keys = 102;
            appUnderTest.exp__class_sequence_pool_keys = 39;
            appUnderTest.exp__generated_sequences = 1486;
            appUnderTest.exp__executed_sequences = 1471;
            appUnderTest.exp__test_plan_rows = 1486;
            appUnderTest.exp__rows_covered_bb_sequences = 282;
            appUnderTest.expmin_final_sequences = 1146;
            appUnderTest.exp__no_bb_sequence_for_target_method = 0;
            appUnderTest.exp__non_instantiable_param_type = 0;
            appUnderTest.exp__excp_during_extension = 15;
            appUnderTest.exp__execution_exception_types_other = Arrays.asList("java.lang.StringIndexOutOfBoundsException");
            appUnderTest.exp__class_not_found_types = 0;
            appUnderTest.exp__parse_exception_types = Stream.of("randoop.sequence.SequenceParseException").
                    collect(Collectors.toSet());
            appUnderTest.exp__randoop_sequence_SequenceParseException = 1;
            appUnderTest.exp__java_lang_Error = 17;
            appUnderTest.exp__partition_count = 4;
            appUnderTest.exp__target_method_coverage =
                    Stream.of(new String[][]{
                        {"DayTraderProcessor::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"},
                        {"DayTraderWeb::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"},
                        {"DayTraderUtil::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"}})
                        .collect(Collectors.toMap(value -> value[0], value -> value[1]));
            appUnderTest.exp__test_classes_count = 42;
                
            return appUnderTest;
        }

        public static String getSummaryFileJsonName(String appName) {
            return appName + Constants.EXTENDER_SUMMARY_FILE_JSON_SUFFIX;
        }

        public static String getCoverageFileJsonName(String appName) {
            return appName + Constants.COVERAGE_FILE_JSON_SUFFIX;
        }
    }
    

    public static class ModelerAppUnderTest extends AppUnderTest {
        
        public int maxNestDepth;
        public boolean addLocalRemote;
        public int level;

        public String targetClassListForClassListTest;
        public String excludedClassListForClassListTest;
        public String standardNodeFileForClassListTest;

        public String targetClassListForAllClassesTest;
        public String excludedClassListForAllClassesTest;
        public String standardNodeFileForAllClassesTest;

        public String targetClassListForAllClassesButExcludedTest;
        public String excludedClassListForAllClassesButExcludedTest;
        public String standardNodeFileForAllClassesButExcludedTest;

        public String targetClassListForAllClassesButExcludedClassAndPackageTest;
        public String excludedClassListForAllClassesButExcludedClassAndPackageTest;
        public String standardNodeFileForAllClassesButExcludedClassAndPackageTest;
        
        public String partitionsCPPrefix;
        public String partitionsCPSuffix;
        public String refactoringPrefix;
        public String partitionsPrefix;
        public String partitionsSuffix;
        public String partitionsSeparator;

        public ModelerAppUnderTest(String appName,
                                   String appClasspath,
                                   String appPath,
                                   String testPlanFilename,
                                   int maxNestDepth,
                                   boolean addLocalRemote,
                                   int level,
                                   String targetClassListForClassListTest,
                                   String excludedClassListForClassListTest,
                                   String standardNodeFileForClassListTest,
                                   String targetClassListForAllClassesTest,
                                   String excludedClassListForAllClassesTest,
                                   String standardNodeFileForAllClassesTest,
                                   String targetClassListForAllClassesButExcludedTest,
                                   String excludedClassListForAllClassesButExcludedTest,
                                   String standardNodeFileForAllClassesButExcludedTest,
                                   String targetClassListForAllClassesButExcludedClassAndPackageTest,
                                   String excludedClassListForAllClassesButExcludedClassAndPackageTest,
                                   String standardNodeFileForAllClassesButExcludedClassAndPackageTest) {
            
            super(appName, appClasspath, appPath, testPlanFilename);
            this.maxNestDepth = maxNestDepth;
            this.addLocalRemote = addLocalRemote;
            this.level = level;

            this.targetClassListForClassListTest = targetClassListForClassListTest;
            this.excludedClassListForClassListTest = excludedClassListForClassListTest;
            this.standardNodeFileForClassListTest = standardNodeFileForClassListTest;

            this.targetClassListForAllClassesTest = targetClassListForAllClassesTest;
            this.excludedClassListForAllClassesTest = excludedClassListForAllClassesTest;
            this.standardNodeFileForAllClassesTest = standardNodeFileForAllClassesTest;

            this.targetClassListForAllClassesButExcludedTest = targetClassListForAllClassesButExcludedTest;
            this.excludedClassListForAllClassesButExcludedTest = excludedClassListForAllClassesButExcludedTest;
            this.standardNodeFileForAllClassesButExcludedTest = standardNodeFileForAllClassesButExcludedTest;

            this.targetClassListForAllClassesButExcludedClassAndPackageTest = targetClassListForAllClassesButExcludedClassAndPackageTest;
            this.excludedClassListForAllClassesButExcludedClassAndPackageTest = excludedClassListForAllClassesButExcludedClassAndPackageTest;
            this.standardNodeFileForAllClassesButExcludedClassAndPackageTest = standardNodeFileForAllClassesButExcludedClassAndPackageTest;
            
            // for added partitions file support: change the input for relevant fields
            this.partitionsCPPrefix = null;
            this.partitionsCPSuffix = null;
            this.refactoringPrefix = null;
            this.partitionsPrefix = null;
            this.partitionsSuffix = null;
            this.partitionsSeparator = null;
        }

        public static ModelerAppUnderTest createDaytrader7ModelerAppUnderTest() {
            return new ModelerAppUnderTest("daytrader7",
                "test/data/daytrader7/daytrader7MonoClasspath.txt",
                "test/data/daytrader7/monolith/bin",
                null,
                2,
                false,
                1,
                "com.ibm.websphere.samples.daytrader.TradeAction::com.ibm.websphere.samples.daytrader.util.Log",
                null,
                "test/data/daytrader7/DayTrader_ctd_models_classlist.json",
                null,
                null,
                "test/data/daytrader7/DayTrader_ctd_models_all_classes.json",
                null,
                "com.ibm.websphere.samples.daytrader.TradeAction::com.ibm.websphere.samples.daytrader.util.Log",
                "test/data/daytrader7/DayTrader_ctd_models_all_classes_but_excluded.json",
                null,
                "com.ibm.websphere.samples.daytrader.TradeAction::com.ibm.websphere.samples.daytrader.web.websocket.*",
                "test/data/daytrader7/DayTrader_ctd_models_all_classes_but_excluded_package.json");
        }

        public static ModelerAppUnderTest create4_rifModelerAppUnderTest() {
            return new ModelerAppUnderTest("4_rif",
                "test/data/4_rif/4_rifMonoClasspath.txt",
                "test/data/4_rif/classes",
                null,
                2,
                false,
                1,
                "com.densebrain.rif.client.RIFManagerFactory::com.densebrain.rif.util.ObjectUtility",
                null,
                "test/data/4_rif/4_rif_ctd_models_classlist.json",
                null,
                null,
                "test/data/4_rif/4_rif_ctd_models_all_classes.json",
                null,
                "com.densebrain.rif.client.RIFManagerFactory::com.densebrain.rif.util.ObjectUtility",
                "test/data/4_rif/4_rif_ctd_models_all_classes_but_excluded.json",
                null,
                "com.densebrain.rif.client.service.*::com.densebrain.rif.server.transport.WebServiceDescriptor",
                "test/data/4_rif/4_rif_ctd_models_all_classes_but_excluded_package.json");
        }

        public static ModelerAppUnderTest create7_sfmisModelerAppUnderTest() {
            return new ModelerAppUnderTest("7_sfmis",
                "test/data/7_sfmis/7_sfmisMonoClasspath.txt",
                "test/data/7_sfmis/classes",
                null,
                2,
                false,
                1,
                "com.hf.sfm.crypt.Base64::com.hf.sfm.filter.setCharacterEncodingFilter::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo",
                null,
                "test/data/7_sfmis/7_sfmis_ctd_models_classlist.json",
                null,
                null,
                "test/data/7_sfmis/7_sfmis_ctd_models_all_classes.json",
                null,
                "com.hf.sfm.sfmis.personinfo.business.PersonInfoMgr::com.hf.sfm.system.business.MenuManage",
                "test/data/7_sfmis/7_sfmis_ctd_models_all_classes_but_excluded.json",
                null,
                "com.hf.sfm.system.pdo.*::com.hf.sfm.filter.setCharacterEncodingFilter::com.hf.sfm.util.DaoFactoryUtil::com.hf.sfm.util.DaoFactory",
                "test/data/7_sfmis/7_sfmis_ctd_models_all_classes_but_excluded_package.json");
        }

        public static ModelerAppUnderTest create40_glengineerModelerAppUnderTest() {
            return new ModelerAppUnderTest("40_glengineer",
                "test/data/40_glengineer/40_glengineerMonoClasspath.txt",
                "test/data/40_glengineer/classes",
                null,
                2,
                false,
                1,
                "glengineer.GroupLayoutEngineer::glengineer.agents.setters.FunctionsOnParallelGroup",
                null,
                "test/data/40_glengineer/40_glengineer_ctd_models_classlist.json",
                null,
                null,
                "test/data/40_glengineer/40_glengineer_ctd_models_all_classes.json",
                null,
                "glengineer.agents.setters.FunctionsOnTopSequentialGroup::glengineer.agents.settings.Settings",
                "test/data/40_glengineer/40_glengineer_ctd_models_all_classes_but_excluded.json",
                null,
                "glengineer.agents.settings.*::glengineer.agents.setters.*::glengineer.positions.HWordPosition",
                "test/data/40_glengineer/40_glengineer_ctd_models_all_classes_but_excluded_package.json");
        }

        public static ModelerAppUnderTest create53_shp2kmlModelerAppUnderTest() {
            return new ModelerAppUnderTest("53_shp2kml",
                "test/data/53_shp2kml/53_shp2kmlMonoClasspath.txt",
                "test/data/53_shp2kml/classes",
                null,
                2,
                false,
                1,
                "net.sourceforge.shp2kml.Shp2KMLGUI::net.sourceforge.shp2kml.KMLObject",
                null,
                "test/data/53_shp2kml/53_shp2kml_ctd_models_classlist.json",
                null,
                null,
                "test/data/53_shp2kml/53_shp2kml_ctd_models_all_classes.json",
                null,
                "net.sourceforge.shp2kml.GeomConverter",
                "test/data/53_shp2kml/53_shp2kml_ctd_models_all_classes_but_excluded.json",
                null,
                "net.sourceforge.*",
                "test/data/53_shp2kml/53_shp2kml_ctd_models_all_classes_but_excluded_package.json");
        }
        
        public static String getCtdOutfileName(String appName) {
            return appName + "_" + Constants.CTD_OUTFILE_SUFFIX;
        }

        public static String getCtdNonTargetedOutFileName(String appName) {
            return appName + "_" + Constants.CTD_NON_TARGETED_OUTFILE_SUFFIX;
        }
    }


    public static class SequenceInitializerAppUnderTest extends AppUnderTest {

        public String testGenName;
        public int timeLimit;
        public boolean targetMethods;
        public boolean baseAssertions;
        public Set<String> targetClasses;

        public SequenceInitializerAppUnderTest(String appName, String appClasspath,
                                               String appPath, String testPlanFilename,
                                               String testGenName, int timeLimit,
                                               boolean targetMethods, boolean baseAssertions,
                                               Set<String> targetClasses){
            super(appName, appClasspath, appPath, testPlanFilename);
            this.testGenName = testGenName;
            this.timeLimit = timeLimit;
            this.targetMethods = targetMethods;
            this.baseAssertions = baseAssertions;
            this.targetClasses = targetClasses;
        }
        
        public static SequenceInitializerAppUnderTest createDaytrader7SequenceInitializerAppUnderTest() {
            Set<String> targetClassesOfDaytrader7 = new HashSet<>();
            targetClassesOfDaytrader7.addAll(Arrays.asList(new String[] {
                "com.ibm.websphere.samples.daytrader.beans.MarketSummaryDataBean",
                "com.ibm.websphere.samples.daytrader.util.TradeConfig",
                "com.ibm.websphere.samples.daytrader.entities.AccountDataBean",
                "com.ibm.websphere.samples.daytrader.entities.QuoteDataBean",
                "com.ibm.websphere.samples.daytrader.entities.OrderDataBean" }));
            
            return new SequenceInitializerAppUnderTest("daytrader7",
                "test/data/daytrader7/daytrader7MonoClasspath.txt",
                "test/data/daytrader7/monolith/bin",
                "test/data/daytrader7/daytrader_ctd_models_shortened.json",
                "EvoSuiteTestGenerator",
                -1,
                false,
                false,
                targetClassesOfDaytrader7);
        }

        public static String getTargetDirName(String appName) {
            return appName + Constants.EVOSUITE_TARGET_DIR_NAME_SUFFIX;
        }

        public static String getOutputDirName(String appName) {
            return appName + Constants.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX;
        }

        public static String getOutputFileName(String appName) {
            return appName + "_" + EvoSuiteTestGenerator.class.getSimpleName()+"_"+
                Constants.INITIALIZER_OUTPUT_FILE_NAME_SUFFIX;
        }
    }
}
