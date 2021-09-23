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
import java.util.HashSet;

import org.apache.commons.io.FileUtils;
import org.evosuite.shaded.org.apache.commons.collections.IteratorUtils;
import org.junit.Before;
import org.junit.Test;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CTDModelingTest {

	@Before
	/**
	 * Delete existing modeling output file
	 */
	public void cleanUp() {
		FileUtils.deleteQuietly(new File("DayTrader_" + Constants.CTD_OUTFILE_SUFFIX));
	}

	@Test
	public void testGenerateModelsAndTestPlansForClassList() throws Exception {
		CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator("DayTrader", null,
				"com.ibm.websphere.samples.daytrader.TradeAction::com.ibm.websphere.samples.daytrader.util.Log", null,
				null, null, "test/data/daytrader7/monolith/bin", "test/data/daytrader7/daytrader7MonoClasspath.txt", 2,
				false, 1, null, null, null, null);
		analyzer.modelPartitions();

		// assert that output file for CTD modeling is created

		String outFilename = "DayTrader_" + Constants.CTD_OUTFILE_SUFFIX;

		assertTrue(new File(outFilename).exists());

		JsonNode resultNode = TackleTestJson.getObjectMapper().readTree(new File(outFilename));

		JsonNode standardNode = TackleTestJson.getObjectMapper().
				readTree(new File("test/data/daytrader7/DayTrader_ctd_models_classlist.json"));

		compareModels(resultNode, standardNode);
	}

	@Test
	public void testGenerateModelsAndTestPlansForAllClasses() throws Exception {
		CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator("DayTrader", null, null, null, null, null,
				"test/data/daytrader7/monolith/bin", "test/data/daytrader7/daytrader7MonoClasspath.txt", 2, false, 1,
				null, null, null, null);
		analyzer.modelPartitions();

		// assert that output file for CTD modeling is created

		String outFilename = "DayTrader_" + Constants.CTD_OUTFILE_SUFFIX;

		assertTrue(new File(outFilename).exists());

		JsonNode resultNode = TackleTestJson.getObjectMapper().readTree(new File(outFilename));

		JsonNode standardNode = TackleTestJson.getObjectMapper().
					readTree(new File("test/data/daytrader7/DayTrader_ctd_models_all_classes.json"));

		compareModels(resultNode, standardNode);
	}

	@Test
	public void testGenerateModelsAndTestPlansForAllClassesButExcluded() throws Exception {
		CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator("DayTrader", null, null,
				"com.ibm.websphere.samples.daytrader.TradeAction::com.ibm.websphere.samples.daytrader.util.Log", null,
				null, "test/data/daytrader7/monolith/bin", "test/data/daytrader7/daytrader7MonoClasspath.txt", 2, false,
				1, null, null, null, null);
		analyzer.modelPartitions();

		// assert that output file for CTD modeling is created

		String outFilename = "DayTrader_" + Constants.CTD_OUTFILE_SUFFIX;

		assertTrue(new File(outFilename).exists());

		JsonNode resultNode = TackleTestJson.getObjectMapper().readTree(new File(outFilename));

		JsonNode standardNode = TackleTestJson.getObjectMapper().
					readTree(new File("test/data/daytrader7/DayTrader_ctd_models_all_classes_but_excluded.json"));

		compareModels(resultNode, standardNode);
	}

	@Test
	public void testGenerateModelsAndTestPlansForAllClassesButExcludedClassAndPackage() throws Exception {
		CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator("DayTrader", null, null,
				"com.ibm.websphere.samples.daytrader.TradeAction::com.ibm.websphere.samples.daytrader.web.websocket.*",
				null, null, "test/data/daytrader7/monolith/bin", "test/data/daytrader7/daytrader7MonoClasspath.txt", 2,
				false, 1, null, null, null, null);
		analyzer.modelPartitions();

		// assert that output file for CTD modeling is created

		String outFilename = "DayTrader_" + Constants.CTD_OUTFILE_SUFFIX;

		assertTrue(new File(outFilename).exists());

		JsonNode resultNode = TackleTestJson.getObjectMapper().readTree(new File(outFilename));

		JsonNode standardNode = TackleTestJson.getObjectMapper().
					readTree(new File("test/data/daytrader7/DayTrader_ctd_models_all_classes_but_excluded_package.json"));

		compareModels(resultNode, standardNode);
	}

	/*
	 * We cannot compare the objects in a straightforward way because CTD result
	 * might differ between different JVMs. Hence we just compare the models to each
	 * other.
	 */

	@SuppressWarnings("unchecked")
	private void compareModels(JsonNode resultObject, JsonNode standardObject) {
		ObjectNode resultObjects = (ObjectNode) resultObject.get("models_and_test_plans");
		ObjectNode standardObjects = (ObjectNode) standardObject.get("models_and_test_plans");

		assertEquals(new HashSet<String>(IteratorUtils.toList(standardObjects.fieldNames())),
				new HashSet<String>(IteratorUtils.toList(resultObjects.fieldNames())));

		resultObjects.fieldNames().forEachRemaining(partition -> {

			ObjectNode classesObject = (ObjectNode) resultObjects.get(partition);

			ObjectNode standardClassesObject = (ObjectNode) standardObjects.get(partition);

			assert (standardClassesObject != null);
			
			assertEquals(standardClassesObject.size(), classesObject.size());

			assertEquals(new HashSet<String>(IteratorUtils.toList(standardClassesObject.fieldNames())),
					new HashSet<String>(IteratorUtils.toList(classesObject.fieldNames())));

			classesObject.fieldNames().forEachRemaining(className -> {

				ObjectNode methodsObject = (ObjectNode) classesObject.get(className);

				ObjectNode standardMethodsObject = (ObjectNode) standardClassesObject.get(className);

				assert (standardMethodsObject != null);
				
				assertEquals(standardMethodsObject.size(), methodsObject.size());

				assertEquals(new HashSet<String>(IteratorUtils.toList(standardMethodsObject.fieldNames())),
						new HashSet<String>(IteratorUtils.toList(methodsObject.fieldNames())));

				methodsObject.fieldNames().forEachRemaining(methodName -> {

					ObjectNode methodObject = (ObjectNode) methodsObject.get(methodName);

					ObjectNode standardMethodObject = (ObjectNode) standardMethodsObject.get(methodName);

					assert(standardMethodObject != null);

					assertEquals(standardMethodObject.get("formatted_signature"),
							methodObject.get("formatted_signature"));

					ArrayNode attrsArray = (ArrayNode) methodObject.get("attributes");
					ArrayNode standardAttrsArray = (ArrayNode) standardMethodObject.get("attributes");

					assertEquals(standardAttrsArray.size(), attrsArray.size());

					for (int k = 0; k < attrsArray.size(); k++) {
						ObjectNode attrObject = (ObjectNode) attrsArray.get(k);
						ArrayNode valuesArray = (ArrayNode) attrObject.get("values");
						ObjectNode standardAttrObject = (ObjectNode) standardAttrsArray.get(k);
						ArrayNode standardValuesArray = (ArrayNode) standardAttrObject.get("values");

						assertEquals(standardValuesArray.size(), valuesArray.size());
					}

				});

			});
		});
	}
}
