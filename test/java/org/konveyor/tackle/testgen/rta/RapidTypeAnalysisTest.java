package org.konveyor.tackle.testgen.rta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.junit.Test;

import org.konveyor.tackle.testgen.util.Utils;

public class RapidTypeAnalysisTest {

	@Test
	public void testRapidTypeAnalysis() throws Exception {

		Set<String> result = new RapidTypeAnalysis().
				performAnalysis("test/data/daytrader7/monolith/bin",
						Utils.getClasspathEntries(new File("test/data/daytrader7/DayTraderMonoClasspath.txt")));

		// read standard types and make sure they are the same as what we got

		InputStream fis = new FileInputStream("test/data/daytrader7/DayTrader_RTA_output.json");
		JsonReader reader = Json.createReader(fis);
		JsonArray typesArray = reader.readArray();
		reader.close();

		Set<String> standard = new HashSet<String>();

		for (JsonValue item : typesArray) {
			// remove extra double quotes
			standard.add(item.toString().substring(1, item.toString().length()-1));
		}
		assertEquals(standard.size(), result.size());
		assertTrue(result.equals(standard));
	}
}
