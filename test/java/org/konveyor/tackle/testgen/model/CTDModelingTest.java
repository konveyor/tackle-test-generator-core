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
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.konveyor.tackle.testgen.util.Constants;

public class CTDModelingTest {

	@Before
	/**
	 * Delete existing modeling output file
	 */
	public void cleanUp() {
		FileUtils.deleteQuietly(new File("DayTrader_"+ Constants.CTD_OUTFILE_SUFFIX));
	}

	@Test
	public void testGenerateModelsAndTestPlansForClassList() throws Exception {
		CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator("DayTrader",
				null, "com.ibm.websphere.samples.daytrader.TradeAction::com.ibm.websphere.samples.daytrader.util.Log",
				null, null, "test/data/daytrader7/monolith/bin",
				"test/data/daytrader7/DayTraderMonoClasspath.txt", true, 2, false, 1, null, null, null, null);
		analyzer.modelPartitions();

		// assert that output file for CTD modeling is  created

		String  outFilename = "DayTrader_"+Constants.CTD_OUTFILE_SUFFIX;

		assertTrue(new File(outFilename).exists());

		InputStream fis = new FileInputStream(outFilename);
		JsonReader reader = Json.createReader(fis);
		JsonObject resultObject = reader.readObject();
		reader.close();

		fis = new FileInputStream("test/data/daytrader7/DayTrader_ctd_models_classlist.json");
		reader = Json.createReader(fis);
		JsonObject standardObject = reader.readObject();
		reader.close();

		compareModels(resultObject, standardObject);
	}

	@Test
	public void testGenerateModelsAndTestPlansForAllClasses() throws Exception {
		CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator("DayTrader",
				null, null, null, null,
				"test/data/daytrader7/monolith/bin",
				"test/data/daytrader7/DayTraderMonoClasspath.txt", true, 2, false, 1, null, null, null, null);
		analyzer.modelPartitions();

		// assert that output file for CTD modeling is  created

		String  outFilename = "DayTrader_"+Constants.CTD_OUTFILE_SUFFIX;

		assertTrue(new File(outFilename).exists());

		InputStream fis = new FileInputStream(outFilename);
		JsonReader reader = Json.createReader(fis);
		JsonObject resultObject = reader.readObject();
		reader.close();

		fis = new FileInputStream("test/data/daytrader7/DayTrader_ctd_models_all_classes.json");
		reader = Json.createReader(fis);
		JsonObject standardObject = reader.readObject();
		reader.close();

//		compareModels(resultObject, standardObject);
	}

	/* We cannot compare the objects in a straightforward way because CTD result might differ
	 * between different JVMs. Hence we just compare the models to each other.
	 */

	private void compareModels(JsonObject resultObject, JsonObject standardObject) {
		JsonObject resultObjects = resultObject.getJsonObject("models_and_test_plans");
		JsonObject standardObjects = standardObject.getJsonObject("models_and_test_plans");

		assertEquals(standardObjects.keySet(), resultObjects.keySet());

		for (Map.Entry<String, JsonValue> entry : resultObjects.entrySet()) {
			String partition = entry.getKey();
			JsonObject classesObject = (JsonObject) entry.getValue();

			JsonObject standardClassesObject = (JsonObject) standardObjects.getJsonObject(partition);

			assert (standardClassesObject != null);

			assertEquals(standardClassesObject.keySet(), classesObject.keySet());

			for (Map.Entry<String, JsonValue> classEntry : classesObject.entrySet()) {

				String className = classEntry.getKey();
				JsonObject methodsObject = (JsonObject) classEntry.getValue();

				JsonObject standardMethodsObject = (JsonObject) standardClassesObject.getJsonObject(className);

				assert(standardMethodsObject != null);

				assertEquals(standardMethodsObject.keySet(), methodsObject.keySet());

				for (Map.Entry<String, JsonValue> methodEntry : methodsObject.entrySet()) {

					String methodName = methodEntry.getKey();

					JsonObject methodObject = (JsonObject) methodEntry.getValue();

					JsonObject standardMethodObject = (JsonObject) standardMethodsObject.getJsonObject(methodName);

					assert(standardMethodObject != null);

					assertEquals(standardMethodObject.get("formatted_signature"),
							methodObject.get("formatted_signature"));

					JsonArray attrsArray = methodObject.getJsonArray("attributes");
					JsonArray standardAttrsArray = standardMethodObject.getJsonArray("attributes");

					assertEquals(standardAttrsArray.size(), attrsArray.size());

					for (int k = 0; k < attrsArray.size(); k++) {
						JsonObject attrObject = attrsArray.getJsonObject(k);
						JsonArray valuesArray = attrObject.getJsonArray("values");
						JsonObject standardAttrObject = standardAttrsArray.getJsonObject(k);
						JsonArray standardValuesArray = standardAttrObject.getJsonArray("values");

						assertEquals(standardValuesArray.size(), valuesArray.size());
					}
				}
			}
		}
	}
}
