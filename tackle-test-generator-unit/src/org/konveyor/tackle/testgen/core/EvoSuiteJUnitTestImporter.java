/*
 * Copyright IBM Corporation 2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.konveyor.tackle.testgen.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;

import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.Utils;

class EvoSuiteJUnitTestImporter extends AbstractJUnitTestImporter {

	EvoSuiteJUnitTestImporter(File dir) {
		super(dir);
	}

	@Override
	public String getClassName(File testFile) {

		return Utils.fileToClass(testFile.getAbsolutePath().replace("_ESTest", ""), rootDir.getAbsolutePath(), ".java", File.separator);
	}

	public static void main(String args[]) throws IllegalArgumentException {

		if (args.length != 1) {
	         throw new IllegalArgumentException("Usage: EvoSuiteJUnitTestImporter [JUnit tests root directory]");
		}

		new EvoSuiteJUnitTestImporter(new File(args[0]));
	}

	@Override
	public boolean isJUnitFile(File testFile) {
		return testFile.getName().endsWith(Constants.JAVA_FILE_SUFFIX) && ! testFile.getName().endsWith(Constants.EVOSUITE_HELP_FILE_SUFFIX);
	}

	@Override
	protected void getTests(File dir) {

		for (File file : dir.listFiles()) {

			if (file.isDirectory()) {
				getTests(file);
			} else if (isJUnitFile(file)) {
				junitFiles.add(file);
			}
		}

	}

	@Override
	protected String filterSequence(String seq) {
		return seq;
	}

	@Override
	protected List<String> beforeAfterCodeSegments(String className) throws IOException {

		File scaffoldingFile = new File(rootDir, getScaffoldingClassName(className));

		if ( ! scaffoldingFile.isFile()) {
			throw new FileNotFoundException("Cannot find file: "+scaffoldingFile);
		}

		String contents = FileUtils.readFileToString(scaffoldingFile, "UTF-8");

		List<String> codeSegs = new ArrayList<String>();

		if (contents.contains("public static void "+Constants.EVOSUITE_BEFORE_CLASS_METHOD+"()")) {
			codeSegs.add(Constants.EVOSUITE_BEFORE_CLASS_METHOD);
		}
		if (contents.contains("public static void "+Constants.EVOSUITE_AFTER_CLASS_METHOD+"()")) {
			codeSegs.add(Constants.EVOSUITE_AFTER_CLASS_METHOD);
		}
		if (contents.contains("public void "+Constants.EVOSUITE_BEFORE_TEST_METHOD+"()")) {
			codeSegs.add(Constants.EVOSUITE_BEFORE_TEST_METHOD);
		}
		if (contents.contains("public void "+Constants.EVOSUITE_AFTER_TEST_METHOD+"()")) {
			codeSegs.add(Constants.EVOSUITE_AFTER_TEST_METHOD);
		}

		return codeSegs;
	}

	@Override
	protected void compileBeforeAfterCode(List<String> classesToCompile, String classpath) {

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

		List<String> sources = classesToCompile.stream()
				.map(cls -> rootDir + File.separator + getScaffoldingClassName(cls))
				.collect(Collectors.toList());

		List<String> optionList = new ArrayList<>();
		optionList.addAll(Arrays.asList("-classpath", classpath));
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjectsFromStrings(sources);
		JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, optionList, null, fileObjects);
		Boolean result = task.call();
		if (result == null || ! result) {
			throw new RuntimeException("Compiling EvoSuite Scaffolding classes failed" + fileObjects);
		}
	}

	private static String getScaffoldingClassName(String className) {

		String separator = File.separator;

		if (separator.equals("\\")) {
			separator = separator + "\\";
		}

		return className.replaceAll("\\.", separator) + Constants.EVOSUITE_HELP_FILE_SUFFIX;
	}

}


