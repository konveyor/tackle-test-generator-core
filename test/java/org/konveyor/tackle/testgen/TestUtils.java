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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.konveyor.tackle.testgen.core.DiffAssertionsGenerator;
import org.konveyor.tackle.testgen.core.EvoSuiteTestGenerator;
import org.konveyor.tackle.testgen.core.RandoopTestGenerator;
import org.konveyor.tackle.testgen.core.TestSequenceInitializer;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.core.extender.TestSequenceExtender;
import org.konveyor.tackle.testgen.model.CTDTestPlanGenerator;
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
        // todo: add public String testPlanFilename;
        
        
        public AppUnderTest(String appName) {
            String appRootDir = "test" + File.separator + "data" + File.separator + appName;
            this.appName = appName;
            
            // for new apps: classpath file should be in this format:
            this.appClasspathFilename = appRootDir + File.separator + appName + "MonoClasspath.txt";
            this.appOutdir = appName + "-" + Constants.AMPLIFIED_TEST_CLASSES_OUTDIR;
            
            // for new apps: add appPath here
            if (appName.equals("irs")) {
                this.appPath = appRootDir + File.separator + "monolith" +
                    File.separator + "target" + File.separator + "classes";
            }
            else if (appName.equals("daytrader7")) {
                this.appPath = appRootDir + File.separator + "monolith" +
                    File.separator + "bin";
            }
            else {
                System.out.println("App name is not recognized: " + appName);
            }
        }
    }

    public static class ExtenderAppUnderTest extends AppUnderTest {
        public String testPlanFilename;
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

        public ExtenderAppUnderTest(String appName, String testPlanFilename, String testSeqFilename) {
            super(appName);
            this.testPlanFilename = testPlanFilename;
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

            // set expected values
            // for new apps: add expected values here
            if (appName.equals("irs") && ((testPlanFilename == null && testSeqFilename == null) ||
                                     (testPlanFilename.equals("test"+ File.separator+"data"+File.separator+"irs"+File.separator+"irs_ctd_models_and_test_plans.json") &&
                                         testSeqFilename.equals("test"+File.separator+"data"+File.separator+"irs"+File.separator+"irs_EvoSuiteTestGenerator_bb_test_sequences.json")))) {
                this.exp__bb_sequences = 13;
                this.exp__parsed_sequences_full = 12;
                this.exp__parsed_sequences_partial = 0;
                this.exp__skipped_sequences = 0;
                this.exp__exception_sequences = 1;
                this.exp__method_sequence_pool_keys = 11;
                this.exp__class_sequence_pool_keys = 5;
                this.exp__generated_sequences = 25;
                this.exp__executed_sequences = 25;
                this.exp__test_plan_rows = 25;
                this.exp__rows_covered_bb_sequences = 11;
                this.expmin_final_sequences = 23;
                this.exp__no_bb_sequence_for_target_method = 0;
                this.exp__non_instantiable_param_type = 0;
                this.exp__excp_during_extension = 0;
                this.exp__execution_exception_types_other = Arrays.asList();
                this.exp__class_not_found_types = 0;
                this.exp__parse_exception_types = Stream.of("randoop.sequence.SequenceParseException").
                    collect(Collectors.toSet());
                this.exp__randoop_sequence_SequenceParseException = 1;
                this.exp__java_lang_Error = 0;
                this.exp__partition_count = 2;
                this.exp__target_method_coverage =
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
                this.exp__test_classes_count = 5;
                
            } else if (appName.equals("daytrader7") &&
                (testPlanFilename.equals("test"+File.separator+"data"+File.separator+"daytrader7"+File.separator+"DayTrader_ctd_models_new_format.json") &&
                    testSeqFilename.equals("test"+File.separator+"data"+File.separator+"daytrader7"+File.separator+"DayTrader_EvoSuiteTestGenerator_bb_test_sequences.json"))) {
                this.exp__bb_sequences = 159;
                this.exp__parsed_sequences_full = 155;
                this.exp__parsed_sequences_partial = 0;
                this.exp__skipped_sequences = 3;
                this.exp__exception_sequences = 1;
                this.exp__method_sequence_pool_keys = 102;
                this.exp__class_sequence_pool_keys = 39;
                this.exp__generated_sequences = 1486;
                this.exp__executed_sequences = 1471;
                this.exp__test_plan_rows = 1486;
                this.exp__rows_covered_bb_sequences = 282;
                this.expmin_final_sequences = 1146;
                this.exp__no_bb_sequence_for_target_method = 0;
                this.exp__non_instantiable_param_type = 0;
                this.exp__excp_during_extension = 15;
                this.exp__execution_exception_types_other = Arrays.asList("java.lang.StringIndexOutOfBoundsException");
                this.exp__class_not_found_types = 0;
                this.exp__parse_exception_types = Stream.of("randoop.sequence.SequenceParseException").
                    collect(Collectors.toSet());
                this.exp__randoop_sequence_SequenceParseException = 1;
                this.exp__java_lang_Error = 17;
                this.exp__partition_count = 4;
                this.exp__target_method_coverage =
                    Stream.of(new String[][]{
                        {"DayTraderProcessor::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"},
                        {"DayTraderWeb::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"},
                        {"DayTraderUtil::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"}})
                        .collect(Collectors.toMap(value -> value[0], value -> value[1]));
                this.exp__test_classes_count = 42;
            } else {
                System.out.println("Combination of (appName, testPlanFilename, testSeqFilename) is not recognized: (" + appName + ", " + testPlanFilename + ", " + testSeqFilename + ")");
            }
        }

        public static String getSummaryFileJsonName(String appName) {
            return appName + Constants.EXTENDER_SUMMARY_FILE_JSON_SUFFIX;
        }

        public static String getCoverageFileJsonName(String appName) {
            return appName + Constants.COVERAGE_FILE_JSON_SUFFIX;
        }
    }

    
    public static class IntegrationAppUnderTest extends ExtenderAppUnderTest {
        public IntegrationAppUnderTest(String appName) {
            super(appName, null, null);
        }
    }
    

    public static class ModelerAppUnderTest extends AppUnderTest {

        public String fileName;
        public String targetClassList;
        public String excludedClassList;
        public int maxNestDepth;
        public boolean addLocalRemote;
        public int level;
        
        public String standardNodeFile;
        public String partitionsCPPrefix;
        public String partitionsCPSuffix;
        public String refactoringPrefix;
        public String partitionsPrefix;
        public String partitionsSuffix;
        public String partitionsSeparator;

        public ModelerAppUnderTest(String appName, String fileName, String targetClassList,
                                   String excludedClassList, int maxNestDepth, boolean addLocalRemote,
                                   int level, String standardNodeFile) {
            super(appName);
            this.standardNodeFile = standardNodeFile;
            this.fileName = fileName;
            this.targetClassList = targetClassList;
            this.excludedClassList = excludedClassList;
            this.maxNestDepth = maxNestDepth;
            this.addLocalRemote = addLocalRemote;
            this.level = level;
            
            // for added partitions file support: change the input for relevant fields based on appName
            this.partitionsCPPrefix = null;
            this.partitionsCPSuffix = null;
            this.refactoringPrefix = null;
            this.partitionsPrefix = null;
            this.partitionsSuffix = null;
            this.partitionsSeparator = null;
        }
        
        public static String getCtdOutfileName(String appName) {
            return appName + "_" + Constants.CTD_OUTFILE_SUFFIX;
        }
    }


    public static class SequenceInitializerAppUnderTest extends AppUnderTest {

        public String ctdModelsFileName;
        public String testGenName;
        public int timeLimit;
        public boolean targetMethods;
        public boolean baseAssertions;
        public Set<String> targetClasses;

        public SequenceInitializerAppUnderTest(String appName, String ctdModelsFileName,
                                               String testGenName, int timeLimit,
                                               boolean targetMethods, boolean baseAssertions,
                                               Set<String> targetClasses){
            super(appName);
            this.ctdModelsFileName = ctdModelsFileName;
            this.testGenName = testGenName;
            this.timeLimit = timeLimit;
            this.targetMethods = targetMethods;
            this.baseAssertions = baseAssertions;
            this.targetClasses = targetClasses;
        }

        public static String getTargetDirName(String appName) {
            return appName + EvoSuiteTestGenerator.EVOSUITE_TARGET_DIR_NAME_SUFFIX;
        }

        public static String getOutputDirName(String appName) {
            return appName + EvoSuiteTestGenerator.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX;
        }

        public static String getOutputFileName(String appName) {
            return appName + "_" + EvoSuiteTestGenerator.class.getSimpleName()+"_"+
                Constants.INITIALIZER_OUTPUT_FILE_NAME_SUFFIX;
        }
    }
}
