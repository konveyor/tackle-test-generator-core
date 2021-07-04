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

package org.konveyor.tackle.testgen.core.executor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;
import org.konveyor.tackle.testgen.core.JUnitTestExporter;
import org.konveyor.tackle.testgen.core.extender.ExtenderSummary;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;

import randoop.sequence.Sequence;

public class JUnitExecutor {

    private static final Logger logger = TackleTestLogger.getLogger(JUnitExecutor.class);

    // imports for the initial test sequence classes
    private HashMap<String, List<String>> classImports;

    // test class setup and teardown methods
    private Map<String, Set<String>> classBeforeAfterMethods;

    private final String applicationName;

    private final String outputDir;

    public JUnitExecutor(String appName, String outDir, HashMap<String, List<String>> classImports,
                         Map<String, Set<String>> classBeforeAfterMethods) {
        this.applicationName = appName;
        this.outputDir = outDir;
        this.classImports = classImports;
        this.classBeforeAfterMethods = classBeforeAfterMethods;
    }

    public void runFailedwithJEESupport(List<String> failedSeqIds, String partition, String clsName,
                                        Map<String, Boolean> seqIdToPartial,
                                        Map<String, String> seqIdToRowId,
                                        Map<String, Map<String, Constants.TestPlanRowCoverage>> seqIdToCovInfo,
                                        HashMap<String, Sequence> seqIdMap,
                                        ExtenderSummary extSummary) {
        Set<String> passedIDs;
        if ( ! failedSeqIds.isEmpty()) {

            try {

                List<String> seqList = new ArrayList<String>(failedSeqIds.size());

                for (String seqId : failedSeqIds) {
                    seqList.add(seqIdMap.get(seqId).toCodeString().replaceAll("<Capture\\d+>", ""));
                }

                passedIDs = runFailedSequencesAsJUnit(partition, clsName, seqList, failedSeqIds);
                for (String seqId : passedIDs) {
                    String testPlanRowId = seqIdToRowId.get(seqId);
                    Map<String, Constants.TestPlanRowCoverage> methodCovInfo = seqIdToCovInfo.get(seqId);
                    if (seqIdToPartial.get(seqId).equals(Boolean.TRUE)) {
                        methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.PARTIAL_JEE);
                        extSummary.covTestPlanRows__partial_jee++;
                    } else {
                        methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.COVERED_JEE);
                        extSummary.covTestPlanRows__full_jee++;
                    }
                }
            } catch (IOException e) {
                logger.warning(
                    "Failed to write JEE unit test for partition: " + partition + ", class: " + clsName);
                // Basically ignore, we will not try EvoSuite JEE support in this case
            }
        }
    }

    /**
     * Compiles and runs the originally failed sequences as junit tests with EvoRunner to provide
     * JEE support. Returns the seq ids of sequences that passed
     * @param partition
     * @param clsName
     * @param testSequences
     * @param seqId
     * @return
     * @throws IOException
     */
    private Set<String> runFailedSequencesAsJUnit(String partition, String clsName, List<String> testSequences,
                                                  List<String> seqId)
        throws IOException {

        if (this.classBeforeAfterMethods.get(clsName) == null ||
            this.classBeforeAfterMethods.isEmpty()) {
            return Collections.emptySet();
        }

        String outDirName = this.outputDir;
        if (outDirName == null) {
            // if output dir not specified, use default output dir name
            outDirName = applicationName + "-" + Constants.AMPLIFIED_TEST_CLASSES_OUTDIR;
        }
        File junitFile = new JUnitTestExporter(new File(outDirName + File.separator + partition), false).
            writeJEEUnitTest(clsName, testSequences, this.classImports.get(clsName),
                this.classBeforeAfterMethods.get(clsName));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        List<String> optionList = new ArrayList<>();
        optionList.addAll(Arrays.asList("-classpath", System.getProperty("java.class.path")));
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjects(junitFile);
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, optionList, null, fileObjects);
        File junitClassFile = new File(junitFile.getParentFile(), junitFile.getName().replace(".java", ".class"));
        Boolean result = task.call();
        if (result == null || ! result) {
            logger.warning("Failed to compile JEE unit test "+junitFile.getAbsolutePath());
            FileUtils.deleteQuietly(junitFile);
            FileUtils.deleteQuietly(junitClassFile);
            return Collections.emptySet();
        }

        List<String> args = new ArrayList<String>();
        args.add("java");
        args.add("-classpath");
        args.add(System.getProperty("java.class.path")+File.pathSeparator+junitFile.getParent());
        args.add("org.junit.runner.JUnitCore");
        args.add(junitFile.getName().substring(0, junitFile.getName().indexOf(".java")));
        ProcessBuilder junitPB = new ProcessBuilder(args);
        //File JEEOutputFile = new File(junitFile.getParentFile(), junitFile.getName().replace(".java", ".junit.out")); // for debugging
        //File JEEErrorFile = new File(junitFile.getParentFile(), junitFile.getName().replace(".java", ".junit.err")); // for debugging
        //junitPB.redirectOutput(JEEOutputFile);
        //junitPB.redirectError(JEEErrorFile);
        junitPB.redirectOutput(ProcessBuilder.Redirect.PIPE);
        junitPB.redirectErrorStream(true);
        Process junitP = junitPB.start();
        BufferedReader outReader = new BufferedReader(new InputStreamReader(junitP.getInputStream(), StandardCharsets.UTF_8));
        Set<String> passedIds = new HashSet<String>();
        try {
            List<String> outputLines = new ArrayList<String>();
            String line;
            while ((line = outReader.readLine()) != null) {
                outputLines.add(line);
            }
            junitP.waitFor();
            String[] output = new String[outputLines.size()];
            Set<Integer> passedTestsIndices = new JUnitOutputParser(outputLines.toArray(output), testSequences.size(), junitFile).parseJUnitOutput();
            for (int ind : passedTestsIndices) {
                passedIds.add(seqId.get(ind));
            }
        } catch (InterruptedException e) {
            logger.warning("Failed to run JEE unit test "+junitFile.getAbsolutePath());
            FileUtils.deleteQuietly(junitFile);
            return Collections.emptySet();
        } finally {
            FileUtils.deleteQuietly(junitClassFile);
            outReader.close();
        }

        return passedIds;
    }

    /**
     * Parses junit output to extract from test case names the ids of tests that have failed.
     * Also deletes the junit test in case all tests failed, otherwise changes the failing tests annotations
     * from '@Test' to '@EvoSuiteExclude' so they are skipped in the final junit tests
     * @author RACHELBRILL
     *
     */
    private static class JUnitOutputParser {

        private final File junitTestFile;
        private int totalNumTests;
        String[] lines;

        private JUnitOutputParser(String[] lines, int numTests, File file) {
            this.junitTestFile = file;
            totalNumTests = numTests;
            //this.lines = Files.readLines(junitOutputFile, StandardCharsets.UTF_8);
            this.lines = lines;
        }

        private Set<Integer> parseJUnitOutput() {

            Set<Integer> passedTests = new HashSet<Integer>();
            for (int j = 0; j < totalNumTests; j++) {
                passedTests.add(j);
            }

            Pattern failurePattern = Pattern.compile("Tests run: " + totalNumTests + ",  Failures: (\\d+)");

            for (int i = lines.length - 1; i >= 0; i--) {
                if (lines[i].equals(String.format("OK (%d test"+(totalNumTests==1? "" : "s")+")", totalNumTests))) {
                    return passedTests;
                } else {
                    Matcher failureMatcher = failurePattern.matcher(lines[i]);

                    if (failureMatcher.matches()) {
                        int numFailures = Integer.parseInt(failureMatcher.group(1));

                        if (numFailures == totalNumTests) {
                            FileUtils.deleteQuietly(junitTestFile);
                            return Collections.emptySet(); // return empty list - all tests failed
                        } else {
                            try {
                                Set<Integer> failedTests = getFailingTests(numFailures);
                                passedTests.removeAll(failedTests);
                                try {
                                    annotateFailingTests(failedTests);
                                } catch (IOException e) {
                                    logger.warning("Failed to annotate failing tests in "+junitTestFile.getAbsolutePath()+" : "+e.getMessage());
                                }
                                return passedTests;
                            } catch (IllegalArgumentException e) {
                                logger.warning(e.getMessage());
                                FileUtils.deleteQuietly(junitTestFile);
                                return Collections.emptySet();
                            }
                        }
                    }
                }
            }

            logger.warning(
                "No indication of failure of success was found in junit test " + junitTestFile.getAbsolutePath());
            FileUtils.deleteQuietly(junitTestFile);
            return Collections.emptySet(); // returns empty list when no indications of failure not passing were found
        }

        private Set<Integer> getFailingTests(int numFailures) {

            Set<Integer> failingTests = new HashSet<Integer>();

            for (int i=0; i<lines.length; i++) {
                if (lines[i].equals("There "+(numFailures ==1? "was " : "were ") + numFailures + " failure"+(numFailures == 1? "" : "s")+":")) {

                    int locatedTests=0;
                    i++;
                    while (locatedTests < numFailures && i<lines.length) {
                        String line = lines[i];
                        if (line.startsWith((locatedTests+1)+") test")) {

                            Pattern failingTestPattern = Pattern.compile((locatedTests+1)+"\\) test(\\d+)\\("+
                                junitTestFile.getName().substring(0,junitTestFile.getName().indexOf(".java"))+"\\)");

                            Matcher matcher = failingTestPattern.matcher(line);

                            if (matcher.matches()) {
                                locatedTests++;
                                failingTests.add(Integer.parseInt(matcher.group(1)));
                            }
                        }
                        i++;
                    }

                    if (locatedTests == numFailures) {
                        return failingTests;
                    } else {
                        throw new IllegalArgumentException("failed to locate failing tests for "+junitTestFile.getAbsolutePath());
                    }
                }
            }

            throw new IllegalArgumentException("failed to locate failing tests for "+junitTestFile.getAbsolutePath());
        }

        private void annotateFailingTests(Set<Integer> failingTests) throws IOException {

            List<String> lines = FileUtils.readLines(junitTestFile, StandardCharsets.UTF_8);
            List<String> newLines = new ArrayList<String>(lines.size());

            Pattern testPattern = Pattern.compile("\tpublic void test(\\d+)\\(\\) throws Throwable \\{");

            for (String line : lines) {
                Matcher testMatcher = testPattern.matcher(line);

                if (testMatcher.matches()) {
                    Integer testId = Integer.parseInt(testMatcher.group(1));
                    if (failingTests.contains(testId)) {
                        newLines.set(newLines.size() - 1, "\t@EvoSuiteExclude");
                    }
                }
                newLines.add(line);
            }

            FileUtils.writeLines(junitTestFile, newLines);
        }
    }

}
