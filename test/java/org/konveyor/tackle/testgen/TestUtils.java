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
        projectClasspath += (File.pathSeparator + Utils.getJarPath(Constants.EVOSUITE_MASTER_JAR_NAME));
        projectClasspath += (File.pathSeparator + Utils.getJarPath(Constants.EVOSUITE_RUNTIME_JAR_NAME));

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
        public String summaryStandardFilename;
        public String coverageStandardFilename;
        
        // expected values
        /*
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
        */
        
        public int exp__executed_sequences;
        public int expmin_final_sequences;
        public Map<String, String> exp__target_method_coverage;
        public int exp__test_classes_count;
        
        public ExtenderAppUnderTest(String appName,
                                    String appClasspath,
                                    String appPath,
                                    String testPlanFilename,
                                    String testSeqFilename,
                                    String summaryStandardFilename,
                                    String coverageStandardFilename) {
            super(appName, appClasspath, appPath, testPlanFilename);
            this.testSeqFilename = testSeqFilename;
            this.summaryStandardFilename = summaryStandardFilename;
            this.coverageStandardFilename = coverageStandardFilename;

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
                testSeqFilename,
                "test/data/irs/irs_test_generation_summary_standard_woJEE.json",
                "test/data/irs/irs_coverage_report_standard_woJEE.json");

            appUnderTest.exp__executed_sequences = 25;
            appUnderTest.expmin_final_sequences = 23;
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
                testSeqFilename,
                "test/data/daytrader7/daytrader7_test_generation_summary_standard_wJEE.json",
                "test/data/daytrader7/daytrader7_coverage_report_standard_wJEE.json");
            
            appUnderTest.exp__executed_sequences = 1471;
            appUnderTest.expmin_final_sequences = 1146;
            appUnderTest.exp__target_method_coverage =
                Stream.of(new String[][]{
                    {"DayTraderProcessor::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"},
                    {"DayTraderWeb::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"},
                    {"DayTraderUtil::com.ibm.websphere.samples.daytrader.entities.AccountDataBean::login(java.lang.String)::test_plan_row_1", "COVERED"}})
                    .collect(Collectors.toMap(value -> value[0], value -> value[1]));
            appUnderTest.exp__test_classes_count = 42;
            return appUnderTest;
        }
        
        public static ExtenderAppUnderTest create4_rifExtenderAppUnderTest(String testPlanFilename, String testSeqFilename) {

            ExtenderAppUnderTest appUnderTest = new ExtenderAppUnderTest("4_rif",
                "test/data/4_rif/4_rifMonoClasspath.txt",
                "test/data/4_rif/classes",
                testPlanFilename,
                testSeqFilename,
                "test/data/4_rif/4_rif_test_generation_summary_standard_wJEE.json",
                "test/data/4_rif/4_rif_coverage_report_standard_wJEE.json");
            
            appUnderTest.exp__executed_sequences = 51;
            appUnderTest.expmin_final_sequences = 36;
            appUnderTest.exp__target_method_coverage = Stream.of(new String[][]{
                {"monolithic::com.densebrain.rif.server.transport.WebServiceContainer::newInstance(java.lang.String,int,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceContainer::getEPRForService(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceContainer::newInstance(org.apache.axis2.context.ConfigurationContext)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceContainer::com.densebrain.rif.server.transport.WebServiceContainer(org.apache.axis2.context.ConfigurationContext)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceContainer::com.densebrain.rif.server.transport.WebServiceContainer(org.apache.axis2.context.ConfigurationContext,java.lang.String,java.lang.String,int)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.client.service.types.InvokeResponse::getPullParser(javax.xml.namespace.QName)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.client.service.types.InvokeResponse::set_return(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::setTypesNamespace(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_13", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_6", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_12", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_7", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_11", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_8", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_10", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_2", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_3", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_4", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_5", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::equals(java.lang.Object)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.server.transport.WebServiceDescriptor::setTargetNamespace(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.client.service.RIFServiceStub::com.densebrain.rif.client.service.RIFServiceStub(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.client.service.RIFServiceStub::com.densebrain.rif.client.service.RIFServiceStub(org.apache.axis2.context.ConfigurationContext,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.client.service.types.Invoke::getPullParser(javax.xml.namespace.QName)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.client.service.types.Invoke::setMethodName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.client.service.types.Invoke::setClassName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.client.service.types.Invoke::setSerializedParams(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.server.RIFServer::com.densebrain.rif.server.RIFServer(int)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.util.ObjectUtility::serializeObject(java.lang.Object)::test_plan_row_7", "COVERED"},
                {"monolithic::com.densebrain.rif.util.ObjectUtility::serializeObject(java.lang.Object)::test_plan_row_11", "COVERED"},
                {"monolithic::com.densebrain.rif.util.ObjectUtility::serializeObject(java.lang.Object)::test_plan_row_8", "COVERED"},
                {"monolithic::com.densebrain.rif.util.ObjectUtility::serializeObject(java.lang.Object)::test_plan_row_10", "COVERED"},
                {"monolithic::com.densebrain.rif.util.ObjectUtility::serializeObject(java.lang.Object)::test_plan_row_5", "COVERED"},
                {"monolithic::com.densebrain.rif.util.ObjectUtility::decodeString(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.util.ObjectUtility::deserializeObject(byte[])::test_plan_row_1", "COVERED"},
                {"monolithic::com.densebrain.rif.util.ObjectUtility::encodeBytes(byte[])::test_plan_row_1", "COVERED"}
            }).collect(Collectors.toMap(value -> value[0], value -> value[1]));
            
            appUnderTest.exp__test_classes_count = 7;
            return appUnderTest;
        }

        public static ExtenderAppUnderTest create7_sfmisExtenderAppUnderTest(String testPlanFilename, String testSeqFilename) {

            ExtenderAppUnderTest appUnderTest = new ExtenderAppUnderTest("7_sfmis",
                "test/data/7_sfmis/7_sfmisMonoClasspath.txt",
                "test/data/7_sfmis/classes",
                testPlanFilename,
                testSeqFilename,
                "test/data/7_sfmis/7_sfmis_test_generation_summary_standard_woJEE.json",
                "test/data/7_sfmis/7_sfmis_coverage_report_standard_woJEE.json");

            appUnderTest.exp__executed_sequences = 108;
            appUnderTest.expmin_final_sequences = 70;
            appUnderTest.exp__target_method_coverage = Stream.of(new String[][]{
                {"monolithic::com.hf.sfm.util.BasePara::setOrdersql(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setSort(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setQueryparams(java.lang.String[])::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setSql(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setPaging(boolean)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setQueryValue(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setDir(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setSqlpath(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setStart(int)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setQuerySql(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setGroupsql(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.BasePara::setLimit(int)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AWorker::setFlatid(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AWorker::setPassword(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AWorker::setIdno(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AWorker::setGroupid(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AWorker::setAccount(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AWorker::com.hf.sfm.system.pdo.AWorker(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AWorker::setState(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AWorker::setPersonid(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::setIdno(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::setUrl(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::com.hf.sfm.system.pdo.Menu(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::setStatus(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::setImg(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::setOper(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::setParentid(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::setName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.Menu::setSort(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.crypt.Base64::byteArrayToAltBase64(byte[])::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.crypt.Base64::base64ToByteArray(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.crypt.Base64::byteArrayToBase64(byte[])::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.crypt.Base64::altBase64ToByteArray(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.crypt.Base64::main(java.lang.String[])::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.DataSource::getSession(javax.servlet.http.HttpSession,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setSex(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setPersontype(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setMobile(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setIndate(java.util.Date)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setBirthday(java.util.Date)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setPersonid(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setWbm(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo(java.lang.String,java.lang.String,java.util.Date,java.lang.String,java.util.Date,java.lang.String,java.util.Date,java.lang.String,java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setReason(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setOutdate(java.util.Date)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setPym(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.personinfo.pdo.APersonInfo::setDepartment(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AGroup::setIdno(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AGroup::setMark(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AGroup::setName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.system.pdo.AGroup::com.hf.sfm.system.pdo.AGroup(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.department.pdo.ADepartment::setIdno(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.department.pdo.ADepartment::setMark(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.department.pdo.ADepartment::setName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.sfmis.department.pdo.ADepartment::com.hf.sfm.sfmis.department.pdo.ADepartment(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.DaoFactory::encrypt(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.ListRange::setData(java.util.ArrayList)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.ListRange::setTotalSize(int)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.Loader::setRs(java.util.List)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.Loader::collectToMap(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.Loader::setColNames(java.lang.String[])::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.Loader::setRange(com.hf.sfm.util.ListRange)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.Loader::setTotalCount(int)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.OddParamsOfArrayInLoader::com.hf.sfm.util.OddParamsOfArrayInLoader(java.lang.String,java.lang.Throwable)::test_plan_row_2", "COVERED"},
                {"monolithic::com.hf.sfm.util.OddParamsOfArrayInLoader::com.hf.sfm.util.OddParamsOfArrayInLoader(java.lang.String,java.lang.Throwable)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.OddParamsOfArrayInLoader::com.hf.sfm.util.OddParamsOfArrayInLoader(java.lang.Throwable)::test_plan_row_2", "COVERED"},
                {"monolithic::com.hf.sfm.util.OddParamsOfArrayInLoader::com.hf.sfm.util.OddParamsOfArrayInLoader(java.lang.Throwable)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.util.OddParamsOfArrayInLoader::com.hf.sfm.util.OddParamsOfArrayInLoader(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::com.hf.sfm.filter.setCharacterEncodingFilter::init(javax.servlet.FilterConfig)::test_plan_row_1", "COVERED"}
                
            }).collect(Collectors.toMap(value -> value[0], value -> value[1]));

            appUnderTest.exp__test_classes_count = 13;
            return appUnderTest;
        }

        public static ExtenderAppUnderTest create40_glengineerExtenderAppUnderTest(String testPlanFilename, String testSeqFilename) {

            ExtenderAppUnderTest appUnderTest = new ExtenderAppUnderTest("40_glengineer",
                "test/data/40_glengineer/40_glengineerMonoClasspath.txt",
                "test/data/40_glengineer/classes",
                testPlanFilename,
                testSeqFilename,
                "test/data/40_glengineer/40_glengineer_test_generation_summary_standard_woJEE.json",
                "test/data/40_glengineer/40_glengineer_coverage_report_standard_woJEE.json");

            appUnderTest.exp__executed_sequences = 232;
            appUnderTest.expmin_final_sequences = 70;
            appUnderTest.exp__target_method_coverage = Stream.of(new String[][]{
                {"monolithic::glengineer.agents.settings.ContainerGapSettings::glengineer.agents.settings.ContainerGapSettings(int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.ContainerGapSettings::glengineer.agents.settings.ContainerGapSettings(glengineer.agents.settings.SpecialGapSizes)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.SequentialGroupAgent::addPreferredGapAfter(glengineer.agents.PreferredGapAgent,glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.SequentialGroupAgent::addPreferredGapAfter(glengineer.agents.PreferredGapAgent,glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.SequentialGroupAgent::addPreferredGapAfter(glengineer.agents.PreferredGapAgent,glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.SequentialGroupAgent::addPreferredGapAfter(glengineer.agents.PreferredGapAgent,glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.SequentialGroupAgent::addPreferredGapBefore(glengineer.agents.PreferredGapAgent,glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.SequentialGroupAgent::addPreferredGapBefore(glengineer.agents.PreferredGapAgent,glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.SequentialGroupAgent::addPreferredGapBefore(glengineer.agents.PreferredGapAgent,glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.SequentialGroupAgent::addPreferredGapBefore(glengineer.agents.PreferredGapAgent,glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.WordPosition::contains(glengineer.positions.CharPosition)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.positions.WordPosition::contains(glengineer.positions.CharPosition)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.positions.WordPosition::contains(glengineer.positions.CharPosition)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::compareTo(glengineer.positions.VWordPosition)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::glengineer.positions.VWordPosition(glengineer.positions.CharPosition)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::glengineer.positions.VWordPosition(glengineer.positions.CharPosition)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::glengineer.positions.VWordPosition(glengineer.positions.CharPosition)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::contains(int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::glengineer.positions.VWordPosition(int,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::glengineer.positions.VWordPosition(glengineer.positions.CharPosition,int)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::equals(glengineer.positions.WordPosition)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::equals(glengineer.positions.WordPosition)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.VWordPosition::glengineer.positions.VWordPosition(int,glengineer.positions.CharPosition)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.positions.CharPosition::glengineer.positions.CharPosition(int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.CharPosition::equals(glengineer.positions.CharPosition)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.positions.CharPosition::equals(glengineer.positions.CharPosition)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.positions.CharPosition::equals(glengineer.positions.CharPosition)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.Sizes::glengineer.agents.settings.Sizes(int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.Sizes::glengineer.agents.settings.Sizes(int,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.blocks.CharTable::isLetter(char)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.blocks.CharTable::isSplitter(char)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.blocks.CharTable::isWordChar(char)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ContainerGapAgent::glengineer.agents.ContainerGapAgent(int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation::addFollowingGap(int,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation(glengineer.agents.GroupAgent,glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation(glengineer.agents.GroupAgent,glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation(glengineer.agents.GroupAgent,glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation::glengineer.agents.GroupAgent$FunctionsOnGroupAndElementImplementation(glengineer.agents.GroupAgent,glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.Axis::valueOf(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::isComponent(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::findDependingSequentialGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::findDependingComponentByName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::glengineer.agents.TemporaryGapAgent(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::findDependingGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::equals(glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::equals(glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::equals(glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::equals(glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::isGroup(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.TemporaryGapAgent::findDependingParallelGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::findDependingSequentialGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::findDependingGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::getComponent(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addAgent(glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addAgent(glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addAgent(glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addAgent(glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addGapBefore(glengineer.agents.GapAgent,glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addGapBefore(glengineer.agents.GapAgent,glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addGapBefore(glengineer.agents.GapAgent,glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addGapBefore(glengineer.agents.GapAgent,glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::findDependingParallelGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::isComponent(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::findDependingComponentByName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addGapAfter(glengineer.agents.GapAgent,glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addGapAfter(glengineer.agents.GapAgent,glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addGapAfter(glengineer.agents.GapAgent,glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.GroupAgent::addGapAfter(glengineer.agents.GapAgent,glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.SpecialGapSizes::glengineer.agents.settings.SpecialGapSizes(int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::glengineer.agents.PreferredGapAgent(javax.swing.LayoutStyle$ComponentPlacement,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::isComponent(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::glengineer.agents.PreferredGapAgent(javax.swing.LayoutStyle$ComponentPlacement)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::findDependingSequentialGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::setSettings(javax.swing.LayoutStyle$ComponentPlacement,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::findDependingComponentByName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::findDependingGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::equals(glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::equals(glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::equals(glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::equals(glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::isGroup(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.PreferredGapAgent::findDependingParallelGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::glengineer.positions.HWordPosition(glengineer.positions.CharPosition)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::glengineer.positions.HWordPosition(glengineer.positions.CharPosition)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::glengineer.positions.HWordPosition(glengineer.positions.CharPosition)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::contains(int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::glengineer.positions.HWordPosition(glengineer.positions.CharPosition,int)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::glengineer.positions.HWordPosition(int,glengineer.positions.CharPosition)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::glengineer.positions.HWordPosition(int,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::equals(glengineer.positions.WordPosition)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.positions.HWordPosition::equals(glengineer.positions.WordPosition)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::isComponent(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::findDependingSequentialGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::findDependingComponentByName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::glengineer.agents.ComponentAgent(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::findDependingGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::equals(glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::equals(glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::equals(glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::equals(glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::isGroup(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.ComponentAgent::findDependingParallelGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::isComponent(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::findDependingSequentialGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::glengineer.agents.GapAgent(glengineer.agents.settings.Sizes)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::glengineer.agents.GapAgent(int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::findDependingComponentByName(java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::findDependingGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::equals(glengineer.agents.Agent)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::equals(glengineer.agents.Agent)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::equals(glengineer.agents.Agent)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::equals(glengineer.agents.Agent)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::glengineer.agents.GapAgent(int,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::setSizes(int,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::findDependingParallelGroupByNames(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.GapAgent::isGroup(java.lang.String,java.lang.String)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.Println::glengineer.Println(java.lang.Object)::test_plan_row_6", "COVERED"},
                {"monolithic::glengineer.Println::glengineer.Println(java.lang.Object)::test_plan_row_8", "COVERED"},
                {"monolithic::glengineer.Println::glengineer.Println(java.lang.Object)::test_plan_row_9", "COVERED"},
                {"monolithic::glengineer.Println::glengineer.Println(java.lang.Object)::test_plan_row_2", "COVERED"},
                {"monolithic::glengineer.Println::glengineer.Println(java.lang.Object)::test_plan_row_3", "COVERED"},
                {"monolithic::glengineer.Println::glengineer.Println(java.lang.Object)::test_plan_row_4", "COVERED"},
                {"monolithic::glengineer.Println::glengineer.Println(java.lang.Object)::test_plan_row_5", "COVERED"},
                {"monolithic::glengineer.Println::glengineer.Println(java.lang.Object)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.CharPosition2::glengineer.positions.CharPosition2(int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.positions.CharPosition1::glengineer.positions.CharPosition1(int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.GapSettings::glengineer.agents.settings.GapSettings(int,int,int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.GapSettings::glengineer.agents.settings.GapSettings(int)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.GapSettings::glengineer.agents.settings.GapSettings(glengineer.agents.settings.Sizes)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.PreferredGapSettings::glengineer.agents.settings.PreferredGapSettings(javax.swing.LayoutStyle$ComponentPlacement)::test_plan_row_1", "COVERED"},
                {"monolithic::glengineer.agents.settings.PreferredGapSettings::glengineer.agents.settings.PreferredGapSettings(javax.swing.LayoutStyle$ComponentPlacement,int,int)::test_plan_row_1", "COVERED"}
            }).collect(Collectors.toMap(value -> value[0], value -> value[1]));

            appUnderTest.exp__test_classes_count = 22;
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
