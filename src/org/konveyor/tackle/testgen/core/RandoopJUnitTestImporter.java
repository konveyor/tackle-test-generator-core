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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RandoopJUnitTestImporter extends AbstractJUnitTestImporter {


	//TODO: this pattern will not work if the original class name ends with a number.
	// It is unclear how Randoop will be able to map test classes to their original classes
	// in such a case.
	private final Pattern JUNIT_NAME_PATTERN = Pattern.compile("(.*)[0-9]+\\.java");

	private static final Logger logger = TackleTestLogger.getLogger(RandoopJUnitTestImporter.class);

	RandoopJUnitTestImporter(File dir, List<String> appClassPaths) throws IOException {
		super(dir);
		TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
		TypeSolver classLoaderTypeSolver;

		if (appClassPaths.isEmpty()) {
			classLoaderTypeSolver = new ClassLoaderTypeSolver(ClassLoader.getSystemClassLoader());
		} else {
			classLoaderTypeSolver = new ClassLoaderTypeSolver(new URLClassLoader(Utils.entriesToURL(appClassPaths), ClassLoader.getSystemClassLoader()));
		}
		CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
		combinedSolver.add(reflectionTypeSolver);
		combinedSolver.add(classLoaderTypeSolver);
		JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);
		StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
	}

	@Override
	public String getClassName(File testFile) throws FileNotFoundException {

		Matcher m = JUNIT_NAME_PATTERN.matcher(testFile.getName());

		if ( ! m.matches()) {
			throw new RuntimeException("Unexpected junit file name: "+testFile.getName());
		}

		return m.group(1);
	}

	@Override
	public boolean isJUnitFile(File testFile) {
		Matcher m = JUNIT_NAME_PATTERN.matcher(testFile.getName());
		return m.matches();
	}

	/* Randoop generates declarations with fully qualified class names that cannot be parsed.
	 * Hence we replace the dots with underscores */

	@Override
	protected void getTests(File dir) throws IOException {
		for (File file : dir.listFiles()) {

			if (file.isDirectory()) {
				getTests(file);
			} else if (isJUnitFile(file)) {

				junitFiles.add(file);
			}
		}
	}

	//TODO: will not work for classes containing underscores in their original name
	@Override
	public Set<String> getSequences(String className) {
		return super.getSequences(className.replaceAll("\\.", "_"));
	}

	//TODO: will not work for classes containing underscores in their original name
	@Override
	public Set<String> getImports(String className) {
		return super.getImports(className.replaceAll("\\.", "_"));
	}

	@Override
	protected String filterSequence(String seq) {

		String[] lines = seq.split(System.lineSeparator());

		StringBuilder filteredSeq = new StringBuilder(lines[0]);

		// Skip first two lines if they contain a debug print

		if ( ! lines[1].trim().equals("if (debug)")) {
			filteredSeq.append(lines[1]+System.lineSeparator());
			filteredSeq.append(lines[2]+System.lineSeparator());
		}

		for (int i=3;i<lines.length;i++) {
			filteredSeq.append(lines[i]+System.lineSeparator());
		}

		return filteredSeq.toString();
	}

	@Override
	protected List<String> beforeAfterCodeSegments(String className) {
		return Collections.emptyList();
	}

	@Override
	protected void compileBeforeAfterCode(List<String> classes, String classpath) {
		throw new UnsupportedOperationException();
	}
}
