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

package org.konveyor.tackle.testgen.core.executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.evosuite.shaded.org.apache.commons.collections.IteratorUtils;
import org.junit.Before;
import org.junit.Test;
import org.konveyor.tackle.testgen.TestUtils;
import org.konveyor.tackle.testgen.util.Constants;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SequenceExecutorTest {

	private final File outputFile = new File("DayTrader_"+ Constants.EXECUTOR_OUTFILE_SUFFIX);


	@Before
	/**
	 * Delete existing output files
	 */
	public void cleanUp() {

		FileUtils.deleteQuietly(outputFile);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExecuteSequences() throws Exception {

		TestUtils.launchProcess(SequenceExecutor.class.getSimpleName(),
            "DayTrader",
            "test/data/daytrader7/monolith/bin",
            "test/data/daytrader7/DayTraderMonoClasspath.txt",
            "test/data/daytrader7/DayTrader_extended_sequences.json",
            null,
            true,
            null,
            null);

		assertTrue(outputFile.exists());

		ObjectNode mainObject = (ObjectNode) SequenceExecutor.mapper.readTree(outputFile);
		ObjectNode standardObject = (ObjectNode) SequenceExecutor.mapper.readTree(new File("test/data/daytrader7/DayTrader_extended_sequences_results.json"));

		Set<String> seqKeys = new HashSet<String>(IteratorUtils.toList(mainObject.fieldNames()));
		Set<String> seqStandardKeys = new HashSet<String>(IteratorUtils.toList(standardObject.fieldNames()));
		assertEquals(seqKeys, seqStandardKeys);
		
		
		mainObject.fieldNames().forEachRemaining(key -> {

			ObjectNode currentObject = (ObjectNode) mainObject.get(key);
			ObjectNode currentStandardObject = (ObjectNode) standardObject.get(key);

			assertEquals(currentObject.get("normal_termination").asBoolean(), currentStandardObject.get("normal_termination").asBoolean());
			assertEquals(currentObject.get("original_sequence_indices"), currentStandardObject.get("original_sequence_indices"));

			ArrayNode currentArray = (ArrayNode) currentObject.get("per_statement_results");
			ArrayNode standardArray = (ArrayNode) currentStandardObject.get("per_statement_results");

			assertEquals(currentArray.size(), standardArray.size());

			for (int i=0; i<currentArray.size();i++) {
				boolean normalTermination = currentArray.get(i).get("statement_normal_termination").asBoolean();
				assertEquals(standardArray.get(i).get("statement_normal_termination").asBoolean(), normalTermination);

				if (normalTermination) {

					if (IteratorUtils.toList(currentArray.get(i).fieldNames()).contains("runtime_object_name")) {
						assertEquals(standardArray.get(i).get("runtime_object_name").asText(), currentArray.get(i).get("runtime_object_name").asText());
						assertEquals(standardArray.get(i).get("runtime_object_type").asText(), currentArray.get(i).get("runtime_object_type").asText());
					} else {
						assertTrue( ! IteratorUtils.toList(standardArray.get(i).fieldNames()).contains("runtime_object_name"));
					}
				} else {
					assertEquals(standardArray.get(i).get("exception").asText(), currentArray.get(i).get("exception").asText());
				}

			}

		});
	}
}
