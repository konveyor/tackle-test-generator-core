package org.konveyor.tackle.testgen.core.executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.konveyor.tackle.testgen.core.util.ProcessLauncher;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import org.konveyor.tackle.testgen.util.Constants;

public class SequenceExecutorTest {

	private final File outputFile = new File("DayTrader_"+ Constants.EXECUTOR_OUTFILE_SUFFIX);


	@Before
	/**
	 * Delete existing output files
	 */
	public void cleanUp() {

		FileUtils.deleteQuietly(outputFile);
	}

	@Test
	public void testExecuteSequences() throws Exception {

		new ProcessLauncher("SequenceExecutor", "DayTrader", "test/data/daytrader7/monolith/bin",
				"test/data/daytrader7/DayTraderMonoClasspath.txt", "test/data/daytrader7/DayTrader_extended_sequences.json", true, null);

		assertTrue(outputFile.exists());

		JsonObject mainObject = getMainObject(outputFile);
		JsonObject standardObject = getMainObject(new File("test/data/daytrader7/DayTrader_extended_sequences_results.json"));

		Set<String> seqKeys = mainObject.keySet();
		Set<String> seqStandardKeys = standardObject.keySet();
		assertEquals(seqKeys, seqStandardKeys);

		for (String key : mainObject.keySet()) {

			JsonObject currentObject = mainObject.getJsonObject(key);
			JsonObject currentStandardObject = standardObject.getJsonObject(key);

			assertEquals(currentObject.getBoolean("normal_termination"), currentStandardObject.getBoolean("normal_termination"));
			assertEquals(currentObject.getJsonArray("original_sequence_indices"), currentStandardObject.getJsonArray("original_sequence_indices"));

			JsonArray currentArray = currentObject.getJsonArray("per_statement_results");
			JsonArray standardArray = currentStandardObject.getJsonArray("per_statement_results");

			assertEquals(currentArray.size(), standardArray.size());

			for (int i=0; i<currentArray.size();i++) {
				boolean normalTermination = currentArray.getJsonObject(i).getBoolean("statement_normal_termination");
				assertEquals(standardArray.getJsonObject(i).getBoolean("statement_normal_termination"), normalTermination);

				if (normalTermination) {

					if (currentArray.getJsonObject(i).containsKey("runtime_object_name")) {
						assertEquals(standardArray.getJsonObject(i).getString("runtime_object_name"), currentArray.getJsonObject(i).getString("runtime_object_name"));
						assertEquals(standardArray.getJsonObject(i).getString("runtime_object_type"), currentArray.getJsonObject(i).getString("runtime_object_type"));
					} else {
						assertTrue( ! standardArray.getJsonObject(i).containsKey("runtime_object_name"));
					}
				} else {
					assertEquals(standardArray.getJsonObject(i).getString("exception"), currentArray.getJsonObject(i).getString("exception"));
				}

			}

		}
	}

	private JsonObject getMainObject(File jsonFile) throws FileNotFoundException {
		InputStream fis = new FileInputStream(jsonFile);
		JsonReader reader = Json.createReader(fis);
		JsonObject mainObject = reader.readObject();
		reader.close();
		return mainObject;
	}

}
