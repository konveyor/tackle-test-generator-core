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

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.konveyor.tackle.testgen.util.Constants;

public class EvoSuiteTestGeneratorTest {

	@Before
	/**
	 * Delete existing EvoSuite input files and generated test cases
	 */
	public void cleanUp() {

		FileUtils.deleteQuietly(new File("DayTrader"+ Constants.EVOSUITE_TARGET_DIR_NAME_SUFFIX));
		FileUtils.deleteQuietly(new File("DayTrader"+ Constants.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX));
		FileUtils.deleteQuietly(new File("irs_JDK11"+ Constants.EVOSUITE_TARGET_DIR_NAME_SUFFIX));
		FileUtils.deleteQuietly(new File("irs_JDK11"+ Constants.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX));
	}

	@Test
	public void testGenerateTests() throws Exception {
		String classpath = "test/data/daytrader7/externalJars/javax.json-api-1.0.jar"+File.pathSeparator+
				"test/data/daytrader7/externalJars/javax.jms-api-2.0.1.jar";
		EvoSuiteTestGenerator evosuoiteTestgen = new EvoSuiteTestGenerator(Collections.singletonList("test/data/daytrader7/monolith/bin"), "DayTrader",
												System.getProperty("java.home"));
		evosuoiteTestgen.setProjectClasspath(classpath);

		// set configuration options
		Map<String, String> config = new HashMap<>();
		config.put(EvoSuiteTestGenerator.Options.SEARCH_BUDGET.name(), "30");
		config.put(EvoSuiteTestGenerator.Options.ASSERTIONS.name(), "false");
		config.put(EvoSuiteTestGenerator.Options.CRITERION.name(), "BRANCH");
		evosuoiteTestgen.configure(config);
		evosuoiteTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.TradeAction");
		evosuoiteTestgen.generateTests();

		// assert that input/output files for evosuite are created
		assertTrue(new File("DayTrader"+ Constants.EVOSUITE_TARGET_DIR_NAME_SUFFIX).exists());
		assertTrue(new File("DayTrader"+ Constants.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX).exists());
	}
	
	@Test
	public void testGenerateTestsJDK11() throws Exception {
		
		String jdkPath = System.getenv("JDK11_HOME");
		
		StringBuilder classpath = new StringBuilder();
		
		BufferedReader br = new BufferedReader(new FileReader("test/data/irs_JDK11/irsMonoClasspath.txt"));

		String line;
		while ((line = br.readLine()) != null) {
			classpath.append(line);
			classpath.append(File.pathSeparator);
		}
		
		br.close();
		
		classpath.deleteCharAt(classpath.length()-1); // remove last path separator
		
		EvoSuiteTestGenerator evosuoiteTestgen = new EvoSuiteTestGenerator(Collections.singletonList("test/data/irs_JDK11/monolith/target/classes"), 
				"irs_JDK11", jdkPath);
		evosuoiteTestgen.setProjectClasspath(classpath.toString());

		// set configuration options
		Map<String, String> config = new HashMap<>();
		config.put(EvoSuiteTestGenerator.Options.SEARCH_BUDGET.name(), "30");
		config.put(EvoSuiteTestGenerator.Options.ASSERTIONS.name(), "false");
		config.put(EvoSuiteTestGenerator.Options.CRITERION.name(), "BRANCH");
		evosuoiteTestgen.configure(config);
		evosuoiteTestgen.addCoverageTarget("irs.Employer");
		evosuoiteTestgen.generateTests();

		// assert that input/output files for evosuite are created
		assertTrue(new File("irs_JDK11"+ Constants.EVOSUITE_TARGET_DIR_NAME_SUFFIX).exists());
		assertTrue(new File("irs_JDK11"+ Constants.EVOSUITE_OUTPUT_DIR_NAME_SUFFIX).exists());
	}
}
