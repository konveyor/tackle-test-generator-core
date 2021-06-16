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

import org.konveyor.tackle.testgen.core.DiffAssertionsGenerator;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.core.extender.TestSequenceExtender;
import org.konveyor.tackle.testgen.core.extender.TestSequenceExtenderTest;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TestUtils {

    private static final String COVERAGE_OUTDIR = "target"+File.separator+"jacoco-output";
    private static final String COVERAGE_FILE_PREFIX = "jacoco-";
    private static int COVERAGE_FILE_COUNTER = 1;

    public static boolean containsFiles(File dir) {

		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				return true;
			} else {
				boolean hasFiles = containsFiles(file);
				if (hasFiles) {
					return true;
				}
			}
		}

		return false;
	}

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

}
