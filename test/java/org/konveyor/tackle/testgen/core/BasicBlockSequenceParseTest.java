package org.konveyor.tackle.testgen.core;

import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;
import org.junit.Test;

import javax.json.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class BasicBlockSequenceParseTest {

	private static final Logger logger = TackleTestLogger.getLogger(BasicBlockSequenceParseTest.class);

	@Test
	public void parseRunner() throws Throwable {

		String projectClasspath = "";

		File file = new File("test/data/daytrader7/DayTraderMonoClasspath.txt");
		if (!file.isFile()) {
			throw new IOException(file.getAbsolutePath() + " is not a valid file");
		}
		projectClasspath += Utils.entriesToClasspath(Utils.getClasspathEntries(file));
		projectClasspath += (File.pathSeparator + "test/data/daytrader7/monolith/bin");

		projectClasspath += (File.pathSeparator + "lib" + File.separator + "evosuite-master-1.0.7-SNAPSHOT.jar");
		projectClasspath += (File.pathSeparator + "lib" + File.separator
				+ "evosuite-standalone-runtime-1.0.7-SNAPSHOT.jar");
		// For SequenceExecutor class:
		projectClasspath += (File.pathSeparator + System.getProperty("java.class.path"));

		List<String> args = new ArrayList<String>();
		args.add("java");
		args.add("-cp");
		args.add("\"" + projectClasspath + "\""); // add double quotes in case path contains spaces
		args.add(ParseRunner.class.getName());

		ProcessBuilder seuqenceParserPB = new ProcessBuilder(args);
		seuqenceParserPB.inheritIO();
		Process sequenceExecutorP = seuqenceParserPB.start();
		sequenceExecutorP.waitFor();
	}

	static class ParseRunner {

		private int totalInitSequences = 0;
		private int parsedInitSequences = 0;

		public void parseSequences() throws Throwable {

			JsonObject initialTestSeq = null;

			InputStream fis = new FileInputStream(new File("test/data/daytrader7/bb_sequences_test.json"));
			//InputStream fis = new FileInputStream(new File("test/data/bb_sequences_test_TradeConfig.json"));
			//InputStream fis = new FileInputStream(new File("bb_sequences_short.json"));
			JsonReader reader = null;
			try {
				reader = Json.createReader(fis);
				initialTestSeq = reader.readObject().getJsonObject("test_sequences");
			} finally {

				if (reader != null) {
					reader.close();
				}
			}

			for (String cls : initialTestSeq.keySet()) {
				JsonObject clsInfo = (JsonObject) initialTestSeq.getJsonObject(cls);
				JsonArray sequences = clsInfo.getJsonArray("sequences");
				JsonArray imports = clsInfo.getJsonArray("imports");
				List<String> importList = imports.getValuesAs(JsonString.class).stream().map(JsonString::getString)
						.collect(Collectors.toList());

				totalInitSequences += sequences.size();
				logger.info("Initial sequences for " + cls + ": " + sequences.size());
				logger.info("Imports: " + importList);

				// iterate over each string sequence for class and parse it into a randoop
				// sequence object
				for (JsonString seq : sequences.getValuesAs(JsonString.class)) {
					String testSeq = seq.getString();
					// logger.fine("- " + testSeq);
					try {
						// create randoop sequence object
						SequenceParser.codeToSequence(testSeq, importList, cls, true, new ArrayList<Integer>());
						// logger.fine("Randoop test sequence: " + randoopSeq);
						parsedInitSequences++;

					} catch (Throwable e) {
						// if exception occurs in creating randoop sequence, record exception
						// information
						// for debugging
						logger.warning("Error parsing sequence for class " + cls + ":\n" + testSeq);
						e.printStackTrace();
						//throw e;
					}
				}
			}
			logger.info("=======> total_seq=" + totalInitSequences + "; parsed_seq="
					+ parsedInitSequences);

			assertEquals(totalInitSequences, parsedInitSequences);
		}

		public static void main(String[] args) throws Throwable {
			(new ParseRunner()).parseSequences();
		}

	}

}
