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

package org.konveyor.tackle.testgen.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.evosuite.shaded.org.apache.commons.collections.IteratorUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.konveyor.tackle.testgen.TestUtils.ModelerAppUnderTest;
import org.konveyor.tackle.testgen.util.TackleTestJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class CTDModelingTest {
    
    private static List<ModelerAppUnderTest> appsUnderTest;
    
    @BeforeClass
    public static void createAppsUnderTest() {
        appsUnderTest = new ArrayList<>();
        appsUnderTest.add(ModelerAppUnderTest.createDaytrader7ModelerAppUnderTest());
        appsUnderTest.add(ModelerAppUnderTest.create4_rifModelerAppUnderTest());
        appsUnderTest.add(ModelerAppUnderTest.create7_sfmisModelerAppUnderTest());
        appsUnderTest.add(ModelerAppUnderTest.create40_glengineerModelerAppUnderTest());
        appsUnderTest.add(ModelerAppUnderTest.create53_shp2kmlModelerAppUnderTest());
    }
    
	@Before
	/**
	 * Delete existing modeling output file
	 */
	public void cleanUp() {
	    for (ModelerAppUnderTest appUnderTest : appsUnderTest) {
            FileUtils.deleteQuietly(new File(ModelerAppUnderTest.getCtdOutfileName(appUnderTest.appName)));
        }
	}

    @AfterClass
    public static void deleteGeneratedOutFiles() {
        for (ModelerAppUnderTest appUnderTest : appsUnderTest) {
            FileUtils.deleteQuietly(new File(ModelerAppUnderTest.getCtdOutfileName(appUnderTest.appName)));
            FileUtils.deleteQuietly(new File(ModelerAppUnderTest.getCtdNonTargetedOutFileName(appUnderTest.appName)));
        }
    }

	@Test
	public void testGenerateModelsAndTestPlansForClassList() throws Exception {
        for (ModelerAppUnderTest modelerAppUnderTest : appsUnderTest) {
            System.out.println("Current app under test in testGenerateModelsAndTestPlansForClassList: " + modelerAppUnderTest.appName);

            CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(
                modelerAppUnderTest.appName,
                modelerAppUnderTest.testPlanFilename,
                modelerAppUnderTest.targetClassListForClassListTest,
                modelerAppUnderTest.excludedClassListForClassListTest,
                modelerAppUnderTest.partitionsCPPrefix,
                modelerAppUnderTest.partitionsCPSuffix,
                modelerAppUnderTest.appPath,
                modelerAppUnderTest.appClasspathFilename,
                modelerAppUnderTest.maxNestDepth,
                modelerAppUnderTest.addLocalRemote,
                modelerAppUnderTest.level,
                modelerAppUnderTest.refactoringPrefix,
                modelerAppUnderTest.partitionsPrefix,
                modelerAppUnderTest.partitionsSuffix,
                modelerAppUnderTest.partitionsSeparator);

            analyzer.modelPartitions();

            executeModelingTest(modelerAppUnderTest.appName, modelerAppUnderTest.standardNodeFileForClassListTest);
        }
	}

    @Test
	public void testGenerateModelsAndTestPlansForAllClasses() throws Exception {
        for (ModelerAppUnderTest modelerAppUnderTest : appsUnderTest) {
            System.out.println("Current app under test in testGenerateModelsAndTestPlansForAllClasses: " + modelerAppUnderTest.appName);

            CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(
                modelerAppUnderTest.appName,
                modelerAppUnderTest.testPlanFilename,
                modelerAppUnderTest.targetClassListForAllClassesTest,
                modelerAppUnderTest.excludedClassListForAllClassesTest,
                modelerAppUnderTest.partitionsCPPrefix,
                modelerAppUnderTest.partitionsCPSuffix,
                modelerAppUnderTest.appPath,
                modelerAppUnderTest.appClasspathFilename,
                modelerAppUnderTest.maxNestDepth,
                modelerAppUnderTest.addLocalRemote,
                modelerAppUnderTest.level,
                modelerAppUnderTest.refactoringPrefix,
                modelerAppUnderTest.partitionsPrefix,
                modelerAppUnderTest.partitionsSuffix,
                modelerAppUnderTest.partitionsSeparator);

            analyzer.modelPartitions();

            executeModelingTest(modelerAppUnderTest.appName, modelerAppUnderTest.standardNodeFileForAllClassesTest);
        }
	}

    @Test
	public void testGenerateModelsAndTestPlansForAllClassesButExcluded() throws Exception {
        for (ModelerAppUnderTest modelerAppUnderTest : appsUnderTest) {
            System.out.println("Current app under test in testGenerateModelsAndTestPlansForAllClassesButExcluded: " + modelerAppUnderTest.appName);
            
            CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(
                modelerAppUnderTest.appName,
                modelerAppUnderTest.testPlanFilename,
                modelerAppUnderTest.targetClassListForAllClassesButExcludedTest,
                modelerAppUnderTest.excludedClassListForAllClassesButExcludedTest,
                modelerAppUnderTest.partitionsCPPrefix,
                modelerAppUnderTest.partitionsCPSuffix,
                modelerAppUnderTest.appPath,
                modelerAppUnderTest.appClasspathFilename,
                modelerAppUnderTest.maxNestDepth,
                modelerAppUnderTest.addLocalRemote,
                modelerAppUnderTest.level,
                modelerAppUnderTest.refactoringPrefix,
                modelerAppUnderTest.partitionsPrefix,
                modelerAppUnderTest.partitionsSuffix,
                modelerAppUnderTest.partitionsSeparator);

            analyzer.modelPartitions();

            executeModelingTest(modelerAppUnderTest.appName, modelerAppUnderTest.standardNodeFileForAllClassesButExcludedTest);
        }
    }
    
	@Test
	public void testGenerateModelsAndTestPlansForAllClassesButExcludedClassAndPackage() throws Exception {
        for (ModelerAppUnderTest modelerAppUnderTest : appsUnderTest) {
            System.out.println("Current app under test in testGenerateModelsAndTestPlansForAllClassesButExcludedClassAndPackage: " + modelerAppUnderTest.appName);
            
            CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(
                modelerAppUnderTest.appName,
                modelerAppUnderTest.testPlanFilename,
                modelerAppUnderTest.targetClassListForAllClassesButExcludedClassAndPackageTest,
                modelerAppUnderTest.excludedClassListForAllClassesButExcludedClassAndPackageTest,
                modelerAppUnderTest.partitionsCPPrefix,
                modelerAppUnderTest.partitionsCPSuffix,
                modelerAppUnderTest.appPath,
                modelerAppUnderTest.appClasspathFilename,
                modelerAppUnderTest.maxNestDepth,
                modelerAppUnderTest.addLocalRemote,
                modelerAppUnderTest.level,
                modelerAppUnderTest.refactoringPrefix,
                modelerAppUnderTest.partitionsPrefix,
                modelerAppUnderTest.partitionsSuffix,
                modelerAppUnderTest.partitionsSeparator);

            analyzer.modelPartitions();

            executeModelingTest(modelerAppUnderTest.appName, modelerAppUnderTest.standardNodeFileForAllClassesButExcludedClassAndPackageTest);
        }
	}

	/*
	 * We cannot compare the objects in a straightforward way because CTD result
	 * might differ between different JVMs. Hence we just compare the models to each
	 * other.
	 */

	@SuppressWarnings("unchecked")
    
    private void executeModelingTest(String appName, String standardNodeFile) throws Exception {
        // assert that output file for CTD modeling is created
        File outfile = new File(ModelerAppUnderTest.getCtdOutfileName(appName));
        assertTrue(appName, outfile.exists());

        JsonNode resultNode = TackleTestJson.getObjectMapper().readTree(outfile);
        JsonNode standardNode = TackleTestJson.getObjectMapper().readTree(new File(standardNodeFile));
        compareModels(appName, resultNode, standardNode);
    }
    
	private void compareModels(String appName, JsonNode resultObject, JsonNode standardObject) {
		ObjectNode resultObjects = (ObjectNode) resultObject.get("models_and_test_plans");
		ObjectNode standardObjects = (ObjectNode) standardObject.get("models_and_test_plans");

		assertEquals(appName, new HashSet<String>(IteratorUtils.toList(standardObjects.fieldNames())),
			new HashSet<String>(IteratorUtils.toList(resultObjects.fieldNames())));

		resultObjects.fieldNames().forEachRemaining(partition -> {

			ObjectNode classesObject = (ObjectNode) resultObjects.get(partition);

			ObjectNode standardClassesObject = (ObjectNode) standardObjects.get(partition);

			assert (standardClassesObject != null);
			
			assertEquals(appName, standardClassesObject.size(), classesObject.size());

			assertEquals(appName, new HashSet<String>(IteratorUtils.toList(standardClassesObject.fieldNames())),
					new HashSet<String>(IteratorUtils.toList(classesObject.fieldNames())));

			classesObject.fieldNames().forEachRemaining(className -> {

				ObjectNode methodsObject = (ObjectNode) classesObject.get(className);

				ObjectNode standardMethodsObject = (ObjectNode) standardClassesObject.get(className);

				assert (standardMethodsObject != null);
				
				assertEquals(appName, standardMethodsObject.size(), methodsObject.size());

				assertEquals(appName, new HashSet<String>(IteratorUtils.toList(standardMethodsObject.fieldNames())),
						new HashSet<String>(IteratorUtils.toList(methodsObject.fieldNames())));

				methodsObject.fieldNames().forEachRemaining(methodName -> {

					ObjectNode methodObject = (ObjectNode) methodsObject.get(methodName);

					ObjectNode standardMethodObject = (ObjectNode) standardMethodsObject.get(methodName);

					assert(standardMethodObject != null);

					assertEquals(appName, standardMethodObject.get("formatted_signature"),
							methodObject.get("formatted_signature"));

					ArrayNode attrsArray = (ArrayNode) methodObject.get("attributes");
					ArrayNode standardAttrsArray = (ArrayNode) standardMethodObject.get("attributes");

					assertEquals(appName, standardAttrsArray.size(), attrsArray.size());

					for (int k = 0; k < attrsArray.size(); k++) {
						ObjectNode attrObject = (ObjectNode) attrsArray.get(k);
						ArrayNode valuesArray = (ArrayNode) attrObject.get("values");
						ObjectNode standardAttrObject = (ObjectNode) standardAttrsArray.get(k);
						ArrayNode standardValuesArray = (ArrayNode) standardAttrObject.get("values");

						assertEquals(appName, standardValuesArray.size(), valuesArray.size());
					}

				});

			});
		});
	}
}
