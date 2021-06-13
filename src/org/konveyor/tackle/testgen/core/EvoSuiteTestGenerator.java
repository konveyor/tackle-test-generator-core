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

package org.konveyor.tackle.testgen.core;

import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.apache.commons.io.FileUtils;
import org.konveyor.tackle.testgen.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/***
 * EvoSuite runner with proxy methods as target
 * @author RACHELBRILL
 *
 */

public class EvoSuiteTestGenerator extends AbstractTestGenerator {

	public EvoSuiteTestGenerator(List<String> targetPath, String appName) {
		super(targetPath);
		this.appName = appName;
		evosuiteOutputDir = new File(appName+EVOSUITE_OUTPUT_DIR_NAME_SUFFIX);
	}

	private final String appName;
	private boolean generateAssertions = false;
	static final String EVOSUITE_OUTPUT_DIR_NAME_SUFFIX = "-evosuite-tests";
	private File evosuiteOutputDir;
	static final String EVOSUITE_TARGET_DIR_NAME_SUFFIX = "-evosuite-targets";
	static final String EVOSUITE_APP_COPY_DIR_NAME_SUFFIX = "-evosuite-app-copy";
	private int timeLimit = 0;
	private CoverageCriterion criterion = null;

	private static final Logger logger = TackleTestLogger.getLogger(EvoSuiteTestGenerator.class);

	enum CoverageCriterion {LINE, BRANCH, EXCEPTION, WEAKMUTATION, OUTPUT, METHOD, METHODNOEXCEPTION, CBRANCH, ALL;}

	enum Options {SEARCH_BUDGET, CRITERION, BASE_DIR, ASSERTIONS;}

	String listFiles(File dir) throws IOException {
		String output = "";
		for (File fileOrDir : dir.listFiles()) {
			if (fileOrDir.isDirectory()) {
				output += listFiles(fileOrDir);
			} else {
				output +=  new String(Files.readAllBytes(Paths.get(fileOrDir.getAbsolutePath())));
			}
		}
		return output;
	}

	public File getOutputDir() {
		return evosuiteOutputDir;
	}

	public void generateTests() throws IOException, InterruptedException {

		StringBuilder methodTargets= new StringBuilder();
		String methodTargetList = null;
		Set<String> targetMethods = new HashSet<String>();
		for (Map.Entry<String, Set<String>> entry : targets.entrySet()) {
			for (String sig : entry.getValue()) {
				if ( ! targetMethods.contains(sig)) {
					targetMethods.add(sig);
					methodTargets.append(sig);
					methodTargets.append(':');
				}
			}
		}

		if (methodTargets.length() > 0) {
			methodTargetList = methodTargets.substring(0, methodTargets.length()-1);
			logger.fine("Evosuite method targets list: "+methodTargetList);
		}

        List<String> args = new ArrayList<String>();
		args.add("java");
		args.add("-jar");
		args.add(Utils.getEvoSuiteJarPath(Constants.EVOSUITE_MASTER_JAR_NAME));
		if (methodTargetList != null) {
			args.add("-Dtarget_method_list="+methodTargetList);
		}
		args.add("-Dassertions");
		args.add(Boolean.toString(generateAssertions));
		if (timeLimit > 0) {
			args.add("-Dsearch_budget");
			args.add(Integer.toString(timeLimit));
		}
		if (criterion != null && criterion != CoverageCriterion.ALL) {
			args.add("-criterion");
			args.add(criterion.name());
		}

		if (targetClassesPath == null) {
			throw new RuntimeException("Target classes path needs to be set");
		}

		// A really ugly part of evosuite where we need to copy app classes and target classes from their original
		// path into a new location - otherwise all constructors of other classes in that same path will
		// be targeted as well

		File destinationDir = new File(appName+EVOSUITE_TARGET_DIR_NAME_SUFFIX);
		destinationDir.mkdir();

		if ( ! destinationDir.isDirectory()) {
			throw new IOException("Could not create directory "+destinationDir.getAbsolutePath());
		}

		File copyDir = new File(appName+EVOSUITE_APP_COPY_DIR_NAME_SUFFIX);
		copyDir.mkdir();

		if ( ! copyDir.isDirectory()) {
			throw new IOException("Could not create directory "+copyDir.getAbsolutePath());
		}

		copyAppAndTargetClasses(targetClassesPath, copyDir, destinationDir);

		args.add("-target");
		args.add(destinationDir.getAbsolutePath());

		args.add("-Dtest_dir");
		args.add(evosuiteOutputDir.getAbsolutePath());

		args.add("-projectCP");
		// Using the copy path instead of the original monolith path which contains also the target classes
		String cp = copyDir.getAbsolutePath();
		if ( ! projectClasspath.isEmpty()) {
			cp+=File.pathSeparator+projectClasspath;
		}
		args.add(cp);

		ProcessBuilder evosuitePB = new ProcessBuilder(args);
		evosuitePB.inheritIO();
		long startTime = System.currentTimeMillis();
		Process evosuiteP = evosuitePB.start();
		evosuiteP.waitFor();
		logger.fine("test generation took "+(System.currentTimeMillis()-startTime)+" milliseconds");

		if ( ! evosuiteOutputDir.isDirectory()) {
			logger.severe("Unit tests output directory not created");
		}
	}

	private void copyAppAndTargetClasses(List<String> sourceDirs, File appCopyDir, File targetDir) throws IOException {

		// First copy all app classes to new location

		FileUtils.cleanDirectory(appCopyDir);
		FileUtils.cleanDirectory(targetDir);

		for (String sourceDirOrJarName : sourceDirs) {

			File sourceDirOrJar = new File(sourceDirOrJarName);

			if (sourceDirOrJar.isDirectory()) {
				FileUtils.copyDirectory(sourceDirOrJar, appCopyDir, new java.io.FileFilter() {
					public boolean accept(File pathname) {
						return pathname.isDirectory() || pathname.getName().endsWith(".class");
					}
				});
			} else {
				throw new IllegalArgumentException("Do not support non-directory monolith paths: "+sourceDirOrJarName);
			}


		}

		// Now move all target classes from the copy location to EvoSuite target location

		for (String targetClass : targets.keySet()) {

			String path = targetClass.replace(".", File.separator)+".class";

			File classFile = new File(appCopyDir, path);

			if ( ! classFile.isFile()) {
				// target is probably not an application class
				continue;
			}

			if (path.contains(File.separator)) {
				String targetClassDirName = path.substring(0, path.lastIndexOf(File.separator));
				File targetClassDir = new File(targetDir, targetClassDirName);
				targetClassDir.mkdirs();
				if ( ! targetClassDir.isDirectory()) {
					throw new IOException("Could not create directory "+targetClassDir.getAbsolutePath());
				}
			}

			File destFile = new File(targetDir, path);

			classFile.renameTo(destFile);
		}
	}


	public void configure(Map<String, String> settings) {

		for (Map.Entry<String, String> entry : settings.entrySet()) {
			if (entry.getKey().equals(Options.SEARCH_BUDGET.name())) {
				timeLimit = Integer.valueOf(entry.getValue());
			} else if (entry.getKey().equals(Options.CRITERION.name())) {
				criterion = CoverageCriterion.valueOf(entry.getValue());
			} else if (entry.getKey().equals(Options.BASE_DIR.name())) {
				evosuiteOutputDir = new File(entry.getValue(), appName+EVOSUITE_OUTPUT_DIR_NAME_SUFFIX);
			} else  if (entry.getKey().equals(Options.ASSERTIONS.name())) {
				generateAssertions = Boolean.valueOf(entry.getValue());
			} else {
				throw new IllegalArgumentException("Unknown evosuite setting: "+entry.getKey());
			}
		}
	}

	@Override
	String getName() {
		return EvoSuiteTestGenerator.class.getSimpleName();
	}

	@Override
	AbstractJUnitTestImporter getJUnitTestImporter(File outputDir) throws IOException {
		return new EvoSuiteJUnitTestImporter(outputDir);
	}
}
