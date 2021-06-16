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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.konveyor.tackle.testgen.TestUtils;
import org.konveyor.tackle.testgen.core.util.ProcessLauncher;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import org.konveyor.tackle.testgen.util.Constants;

public class DiffAssertionGeneratorTest {

	private final File outputFile = new File("DayTrader_"+ Constants.DIFF_ASSERTIONS_OUTFILE_SUFFIX);


	@Before
	/**
	 * Delete existing output files
	 */
	public void cleanUp() {

		FileUtils.deleteQuietly(outputFile);
	}

	@Test
	public void testDiffAssertionGenerator() throws Exception {

		TestUtils.launchProcess(DiffAssertionsGenerator.class.getSimpleName(),
            "DayTrader",
            "test/data/daytrader7/monolith/bin",
            "test/data/daytrader7/DayTraderMonoClasspath.txt",
            "test/data/daytrader7/DayTrader_extended_sequences.json",
            null,
            null,
            null,
            "test/data/daytrader7/DayTrader_extended_sequences_results.json");

		assertTrue(outputFile.exists());

		JsonObject mainObject = getMainObject(outputFile);
		JsonObject standardObject = getMainObject(new File("test/data/daytrader7/DayTrader_extended_sequences_with_assertions.json"));

		Set<String> seqKeys = mainObject.keySet();
		Set<String> seqStandardKeys = standardObject.keySet();
		assertEquals(seqStandardKeys, seqKeys);

		for (String key : mainObject.keySet()) {

			JsonObject currentObject = mainObject.getJsonObject(key);
			JsonObject currentStandardObject = standardObject.getJsonObject(key);

			assertEquals(currentStandardObject.getString("class_name"), currentObject.getString("class_name"));
			assertEquals(currentStandardObject.getJsonArray("imports"), currentObject.getJsonArray("imports"));

			String currentSeqCode = currentObject.getString("sequence");
			String standardSeqCode = currentStandardObject.getString("sequence");

			int assertCount = countMatches(currentSeqCode, "assertEquals(");
			int standardAssertCount = countMatches(standardSeqCode, "assertEquals(");

			assertEquals(standardAssertCount, assertCount);

		}
	}

	private JsonObject getMainObject(File jsonFile) throws FileNotFoundException {
		InputStream fis = new FileInputStream(jsonFile);
		JsonReader reader = Json.createReader(fis);
		JsonObject mainObject = reader.readObject();
		reader.close();
		return mainObject;
	}

	private int countMatches(String str, String findStr) {
		int count = 0;
		int lastIndex = 0;

		while (lastIndex != -1) {

		    lastIndex = str.indexOf(findStr, lastIndex);

		    if (lastIndex != -1) {
		        count++;
		        lastIndex += findStr.length();
		    }
		}
		return count;
	}

}
