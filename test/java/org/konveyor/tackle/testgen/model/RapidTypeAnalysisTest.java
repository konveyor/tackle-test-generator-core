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
import java.util.Set;

import org.junit.Test;
import org.konveyor.tackle.testgen.util.TackleTestJson;
import org.konveyor.tackle.testgen.util.Utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

public class RapidTypeAnalysisTest {

	@Test
	public void testRapidTypeAnalysis() throws Exception {

		Set<String> result = new RapidTypeAnalysis().
				performAnalysis("test/data/daytrader7/monolith/bin",
						Utils.getClasspathEntries(new File("test/data/daytrader7/DayTraderMonoClasspath.txt")));

		// read standard types and make sure they are the same as what we got
		
		JsonNode standardNode = TackleTestJson.getObjectMapper().readTree(new File("test/data/daytrader7/DayTrader_RTA_output.json"));

		Set<String> standard = TackleTestJson.getObjectMapper().convertValue(standardNode, new TypeReference<Set<String>>() {});
		
		assertEquals(standard.size(), result.size());
		assertTrue(result.equals(standard));
	}
}
