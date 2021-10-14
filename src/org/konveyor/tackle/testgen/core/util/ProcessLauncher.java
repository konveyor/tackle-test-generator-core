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

package org.konveyor.tackle.testgen.core.util;

import org.konveyor.tackle.testgen.core.DiffAssertionsGenerator;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Launches sequence executor in a separate process after setting the classpath
 *
 * @author RACHELBRILL
 *
 */

public class ProcessLauncher {

	private static final Logger logger = TackleTestLogger.getLogger(ProcessLauncher.class);

	public ProcessLauncher(String className, String appName, String appPath, String appClasspathFileName, String seqFile, Boolean allResults, String resultsFile)
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
		if (className.equals(SequenceExecutor.class.getSimpleName())) {

			processArgs.add(SequenceExecutor.class.getName());
			processArgs.add("-app");
			processArgs.add(appName);
			processArgs.add("-seq");
			processArgs.add(seqFile);
			processArgs.add("-all");
			processArgs.add(String.valueOf(allResults));
		} else {
			processArgs.add(DiffAssertionsGenerator.class.getName());
			processArgs.add("-app");
			processArgs.add(appName);
			processArgs.add("-seq");
			processArgs.add(seqFile);
			processArgs.add("-seqr");
			processArgs.add(resultsFile);
		}

		ProcessBuilder processExecutorPB = new ProcessBuilder(processArgs);
		processExecutorPB.inheritIO();
		long startTime = System.currentTimeMillis();
		Process processExecutorP = processExecutorPB.start();
		processExecutorP.waitFor();
		// TODO: just for debugging purposes - invoke directly instead of as a separate process
		//new SequenceExecutor(seqFile, Boolean.valueOf(allResults));
		logger.fine("Execution took "+(System.currentTimeMillis()-startTime)+" milliseconds");
	}

	private static CommandLine parseCommandLineOptions(String[] args) {
        Options options = new Options();

      // option for class name to invoke
        options.addOption(Option.builder("cl")
                .longOpt("class-name")
                .hasArg()
                .desc("Name of class to invoke")
                .type(String.class)
                .build()
        );

        // option for application name
        options.addOption(Option.builder("app")
                .longOpt("application-name")
                .hasArg()
                .desc("Name of the application under test")
                .type(String.class)
                .build()
        );

      // option for application path
        options.addOption(Option.builder("pt")
            .longOpt("application-path")
            .hasArg()
            .desc("\"Path to the application under test")
            .type(String.class)
            .build()
        );

        // option for application classpath file name
        options.addOption(Option.builder("clpt")
            .longOpt("application-classpath")
            .hasArg()
            .desc("\"Name of file containing application classpath entries")
            .type(String.class)
            .build()
        );

        // option for sequences file
        options.addOption(Option.builder("seq")
                .longOpt("sequences")
                .hasArg()
                .desc("Name of JSON file containing the extended sequences")
                .type(String.class)
                .build()
        );

        // option for sequences results file
        options.addOption(Option.builder("all")
            .longOpt("record-all")
            .hasArg()
            .desc("\"Whether to collect also created runtime object results. If set to false,"
            		+ " only pass/fail results are recorded. Must be specified when invoking "+SequenceExecutor.class.getSimpleName()+".")
            .type(Boolean.class)
            .build()
        );

        // option for sequences results file
        options.addOption(Option.builder("seqr")
            .longOpt("sequences-results")
            .hasArg()
            .desc("\"Name of JSON file containing the extended sequences results. Must be specified when invoking "+DiffAssertionsGenerator.class.getSimpleName()+".")
            .type(String.class)
            .build()
        );

        // help option
        options.addOption(Option.builder("h")
            .longOpt("help")
            .desc("Print this help message")
            .build()
        );

        HelpFormatter formatter = new HelpFormatter();

        // parse command line options
        CommandLineParser argParser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = argParser.parse(options, args);
            // if help option specified, print help message and return null
            if (cmd.hasOption("h")) {
                formatter.printHelp(ProcessLauncher.class.getName(), options, true);
                return null;
            }
        }
        catch (ParseException e) {
            logger.warning(e.getMessage());
            formatter.printHelp(ProcessLauncher.class.getName(), options, true);
        }

        // check whether required options are specified
        if (!cmd.hasOption("cl") || !cmd.hasOption("app") || !cmd.hasOption("pt") || !cmd.hasOption("clpt") || !cmd.hasOption("seq")) {
            formatter.printHelp(ProcessLauncher.class.getName(), options, true);
            return null;
        }

        String className = cmd.getOptionValue("cl");

        if (className.equals(SequenceExecutor.class.getSimpleName()) && !cmd.hasOption("all")) {
        	formatter.printHelp(ProcessLauncher.class.getName(), options, true);
            return null;
        }

        if (className.equals(DiffAssertionsGenerator.class.getSimpleName()) && !cmd.hasOption("seqr")) {
        	formatter.printHelp(ProcessLauncher.class.getName(), options, true);
            return null;
        }

        return cmd;
    }

	public static void main(String args[]) throws FileNotFoundException, IOException, SecurityException, IllegalArgumentException, InterruptedException {

		 // parse command-line options
        CommandLine cmd = parseCommandLineOptions(args);

        // if parser command-line is empty (which occurs if the help option is specified or a
        // parse exception occurs, exit
        if (cmd == null) {
            System.exit(0);
        }

        String className = cmd.getOptionValue("cl");
        String appName = cmd.getOptionValue("app");
        String appPath = cmd.getOptionValue("pt");
        String classpathFilename = cmd.getOptionValue("clpt");
        String seqFile = cmd.getOptionValue("seq");
        logger.info("Invoked class name: "+className);
        logger.info("Application name: "+appName);
        logger.info("Application path: "+appPath);
        logger.info("Application classpath file name: "+classpathFilename);
        logger.info("Sequences file name: "+seqFile);

        Boolean allResults = null;
        if (cmd.hasOption("all")) {
            allResults = Boolean.parseBoolean(cmd.getOptionValue("all"));
        }

        String resultsFile = null;
        if (cmd.hasOption("seqr")) {
        	resultsFile = cmd.getOptionValue("seqr");
        }

		new ProcessLauncher(className, appName, appPath, classpathFilename, seqFile, allResults, resultsFile);
	}
}
