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

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.utils.ClassUtils;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor.SequenceInfo;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor.SequenceResults;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.apache.commons.cli.*;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

/**
 * Add to a test sequence assertions capturing its runtime recorded objects and
 * results
 *
 * @author RACHELBRILL
 *
 */

public class DiffAssertionsGenerator {

	private static final Logger logger = TackleTestLogger.getLogger(DiffAssertionsGenerator.class);

	public static final String SER_UTIL_METHOD_NAME = "serializedToObject";
	public static final String FIELD_UTIL_METHOD_NAME = "getFieldValue";

	private static final String LINE_SEPARATOR = System.getProperty("line.separator");

	private Map<String, SequenceInfo> id2Sequences = new HashMap<String, SequenceInfo>();

	private Map<String, SequenceResults> id2Results = new HashMap<String, SequenceResults>();

	private final String applicationName;

	public DiffAssertionsGenerator(String appName, File seqFile, File resFile) throws ClassNotFoundException, IllegalArgumentException, SecurityException, IOException {

		this(appName);
		SequenceExecutor.readSequences(seqFile, id2Sequences);
		readResults(resFile);
		createAssertions(new File(applicationName+"_"+ Constants.DIFF_ASSERTIONS_OUTFILE_SUFFIX));
	}

	/**
	 *
	 * @param appName the name of the application under test. Will be used as part of output file names.
	 */

	public DiffAssertionsGenerator(String appName) {
		applicationName = appName;
	}

	private int assertCounter = 0;

	static final int ASSERT_NESTING_LEVEL = 2;

	private void readResults(File resFile) throws FileNotFoundException, ClassNotFoundException {

		JsonReader reader = null;
		JsonObject mainObject = null;

		try {

			InputStream fis = new FileInputStream(resFile);
			reader = Json.createReader(fis);
			mainObject = reader.readObject();
		} finally {
			if (reader != null) {
				reader.close();
			}
		}

		for (Map.Entry<String, JsonValue> entry : mainObject.entrySet()) {

			String seqId = entry.getKey();

			JsonObject content = (JsonObject) entry.getValue();



			JsonArray originalIndices = content.getJsonArray("original_sequence_indices");

			Set<Integer> indices = new HashSet<Integer>();

			for (int i = 0; i < originalIndices.size(); i++) {
				indices.add(originalIndices.getInt(i));
			}

			SequenceResults results = new SequenceResults(content, indices);

			id2Results.put(seqId, results);
		}

	}

    private void createAssertions(File outputFile) throws IOException, IllegalArgumentException, SecurityException {

		Map<String, SequenceInfo> id2AssertSequences = new HashMap<String, SequenceInfo>();

		for (Map.Entry<String, SequenceInfo> entry : id2Sequences.entrySet()) {

			String seqId = entry.getKey();

			SequenceResults results = id2Results.get(seqId);

			if (results == null) {
				logger.warning("Skipping sequence " + seqId + " with no runtime results");
			}

			if (!results.passed) {
				logger.warning("Skipping failing sequence " + seqId);
				continue;
			}

			SequenceInfo infoNoAssertions = entry.getValue();

			List<String> newImports = new ArrayList<String>();
			newImports.addAll(infoNoAssertions.imports);
			newImports.add("static org.junit.Assert.*");

			SequenceInfo infoWithAssertions = new SequenceInfo(infoNoAssertions.className,
					addAssertions(infoNoAssertions.sequence, results), newImports);

			id2AssertSequences.put(seqId, infoWithAssertions);
		}

		logger.info("Added " + assertCounter + " assertions");

		exportSequences(id2AssertSequences, outputFile);
	}

    /**
     * Adds runtime diff assertions to given code.
     * @param originalCode The sequence without the assertions
     * @param results The results of the sequence execution as given by {@link SequenceExecutor}.
     * @return The sequence with diff assertions embedded after every statement that creates objects.
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException
     * @throws SecurityException
     */

	public String addAssertions(String originalCode, SequenceResults results) throws IllegalArgumentException, SecurityException {

		String[] statements = originalCode.split(LINE_SEPARATOR);

		BlockStmt block = null;

    	try {

    		block = StaticJavaParser.parseBlock("{"+originalCode+"}");
    	} catch (ParseProblemException e) {
    		throw new RuntimeException("Failed to parse sequence: "+e.getMessage());
    	}

		StringBuilder codeWithAssertions = new StringBuilder();

		int resCounter = 0;

		STATEMENT_LOOP: for (int i = 0; i < statements.length; i++, resCounter++) {

			codeWithAssertions.append(statements[i]);
			codeWithAssertions.append(LINE_SEPARATOR);

			if (results.runtimeObjectType[resCounter] != null) {

				/* If this runtime object name is not defined in the original sequence then
				 * it was added by Randoop and we should skip it
				 */

				while ( ! SequenceParser.resolve(results.runtimeObjectName[resCounter], block)) {
					resCounter++;
					if (results.runtimeObjectName[resCounter] == null) {
						// The object created by this statement could not be recorded - hence we skip it
						continue STATEMENT_LOOP;
					}
				}

				Class<?> theClass = results.runtimeObjectType[resCounter];

				String assertion = getAssertForSimpleType(theClass, (String) results.runtimePublicObjectState.get(resCounter).
							get(results.runtimeObjectName[resCounter]), results.runtimeObjectName[resCounter],
						SequenceParser.isPrimitiveType(results.runtimeObjectName[resCounter], originalCode));

				if (assertion != null) {
					codeWithAssertions.append(assertion);
				} else {

					addAssertionsOnPublicFields(results.runtimePublicObjectState.get(resCounter), results.runtimeObjectName[resCounter],
							theClass, codeWithAssertions);
					addAssertionsOnPrivateFields(results.runtimePrivateObjectState.get(resCounter), results.runtimeObjectName[resCounter],
							theClass, codeWithAssertions);
				}
			}
		}

		return codeWithAssertions.toString();
	}

	private void addAssertionsOnPublicFields(Map<String, String> runtimeState, String objName, Class<?> objClass, StringBuilder codeWithAssertions) {

		for (Map.Entry<String, String> entry : runtimeState.entrySet()) {

			String fieldName = entry.getKey();

			Field theField;
			try {
				theField = objClass.getField(fieldName);
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(e);
			}

			String fieldVal = entry.getValue().toString();

			String assertion = getAssertForSimpleType(theField.getType(), fieldVal, getFieldValueMethodCall(objName, theField),
					theField.getType().isPrimitive());

			if (assertion != null) {
				codeWithAssertions.append(assertion);
			}
		}
	}

	private void addAssertionsOnPrivateFields(Map<String, String> runtimeState, String objName, Class<?> objClass, StringBuilder codeWithAssertions) {

		for (Map.Entry<String, String> entry : runtimeState.entrySet()) {

			String methodName = entry.getKey();

			Method theMethod;
			try {
				theMethod = objClass.getMethod(methodName);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			}

			String fieldVal = entry.getValue();

			String getterCall = objName+"."+theMethod.getName()+"()";

			String assertion = getAssertForSimpleType(theMethod.getReturnType(), fieldVal, getterCall, theMethod.getReturnType().isPrimitive());

			if (assertion != null) {
				codeWithAssertions.append(assertion);
			}
		}
	}

	/*
	 * Creates assertions for Strings, primitives and wrappers. Returns null if another type is given.
	 * Is primitive is given as parameter rather than computed from the type class, because when we use
	 * Randoop runtime recording, all primitive recorded object types are automatically transfered to
	 * wrapper, hence we need code parsing to determine whether the type is a primitive or wrapper
	 */

	private String getAssertForSimpleType(Class<?> theType, String recordedVal, String actualVal, boolean isPrimitive) {

		if (theType.getName().equals("java.lang.String")) {
			return getAssert(getStringValueForAssert(recordedVal), actualVal);
		} else if (ClassUtils.isPrimitiveOrWrapper(theType)) {
			String unboxingMethod = "";

			if ( ! isPrimitive && ! recordedVal.equals(SequenceExecutor.TKLTEST_NULL_STRING)) {
				unboxingMethod = "."+getUnboxingMethod(theType);
			}

			actualVal += unboxingMethod;

			if (theType.getName().equals("java.lang.Float") ||
					theType.getName().equals("float") ||
					theType.getName().equals("java.lang.Double") ||
					theType.getName().equals("double")) {

				if (recordedVal.equals("NaN")) {
					recordedVal = "java.lang.Double.NaN";
				}

				if (recordedVal.equals("Infinity")) {
					recordedVal = "java.lang.Double.POSITIVE_INFINITY";
				}

				return getAssert(recordedVal, "("+theType.getName()+") "+actualVal,  0.0001);

			} else {

				if (theType.getName().equals("java.lang.Character") || theType.getName().equals("char")) {
					recordedVal = getCharValueForAssert(recordedVal);
				}
				return getAssert(recordedVal, actualVal);
			}
		}

		return null;
	}

	private String getAssert(String arg1, String arg2) {
		return getAssert(arg1, arg2, 0);
	}

	private String getAssert(String arg1, String arg2, double delta) {
		assertCounter++;

		String arg1_fixed = arg1;

		if (arg1.equals(SequenceExecutor.TKLTEST_NULL_STRING)) {
			arg1_fixed = "null";
		}

		// Check long case - shouldn't pass as int but passes as long
		// TODO: This will work also if the argument is an actual string that contains
		// a long, because we add double quotes to all strings. It will not work if
		// the argument is an actual string that contains a double-quoted long.
		// In the future we should handle this in the sequence parser. Randoop
		// generates long values with "L" but Randoop Sequence class can't parse
		// them, hence we removed the "L" in the sequence parser.

		try {
			Integer.parseInt(arg1);
		} catch (NumberFormatException e) {
			try {
				Long.parseLong(arg1);
				arg1_fixed += "L";
			} catch (NumberFormatException e1) {
				// do nothing
			}
		}

		return "assertEquals("+arg1_fixed+", "+arg2+(delta == 0? "" : ", "+String.valueOf(delta))+");"+LINE_SEPARATOR;
	}

	private static String getFieldValueMethodCall(String objName, Field field) {
		return FIELD_UTIL_METHOD_NAME+"(" + objName+", \""+ field.getName() + "\")";
	}

	private String getUnboxingMethod(Class<?> theClass) {

		if (theClass.equals(Boolean.class)) {
			return "booleanValue()";
		} else if (theClass.equals(Byte.class)) {
			return "byteValue()";
		} else if (theClass.equals(Character.class)) {
			return "charValue()";
		} else if (theClass.equals(Short.class)) {
			return "shortValue()";
		} else if (theClass.equals(Integer.class)) {
			return "intValue()";
		} else if (theClass.equals(Long.class)) {
			return "longValue()";
		} else if (theClass.equals(Double.class)) {
				return "doubleValue()";
		} else if (theClass.equals(Float.class)) {
			return "floatValue()";
		} else {
			throw new IllegalArgumentException("Unsupported wrapper class: "+theClass.getName());
		}
	}

	private void exportSequences(Map<String, SequenceInfo> id2AssertSequences, File outputFile)
			throws FileNotFoundException {

		JsonObjectBuilder sequencesObject = Json.createObjectBuilder();

		for (Map.Entry<String, SequenceInfo> entry : id2AssertSequences.entrySet()) {

			String seqId = entry.getKey();

			JsonObjectBuilder sequenceObject = Json.createObjectBuilder();

			sequenceObject.add("class_name", entry.getValue().className);
			sequenceObject.add("sequence", entry.getValue().sequence);

			JsonArrayBuilder importsBuilder = Json.createArrayBuilder();

			for (String imp : entry.getValue().imports) {
				importsBuilder.add(imp);
			}

			sequenceObject.add("imports", importsBuilder.build());

			sequencesObject.add(seqId, sequenceObject.build());
		}

		JsonWriter writer = null;

		try {

			JsonWriterFactory writerFactory = Json
					.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
			writer = writerFactory.createWriter(new FileOutputStream(outputFile));
			writer.writeObject(sequencesObject.build());
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	private static String getStringValueForAssert(String runtimeVal) {
		if (runtimeVal.equals(SequenceExecutor.TKLTEST_NULL_STRING)) {
			return runtimeVal;
		}

		if (runtimeVal.equals("\\")) {
			return "\""+"\\\\"+"\"";
		}

		return "\""+runtimeVal.replaceAll("\\\\","\\\\\\\\").replaceAll("\\\n", "\\\\n").
				replaceAll("\"", "\\\\\"")+"\"";
	}

	private static String getCharValueForAssert(String runtimeVal) {

		/* if integer is given leave it as is */

		try {
			Integer.parseInt(runtimeVal);
		} catch (NumberFormatException e) {
			String val = runtimeVal;
			if (val.equals("\\")) {
				val = "\\\\";
			}
			return "'"+val+"'";
		}

		return runtimeVal;
	}

	private static CommandLine parseCommandLineOptions(String[] args) {
        Options options = new Options();

        // option for sequences file
        options.addOption(Option.builder("app")
                .longOpt("application-name")
                .hasArg()
                .desc("Name of the application under test")
                .type(String.class)
                .build()
        );

        // option for sequences file
        options.addOption(Option.builder("seq")
                .longOpt("sequences")
                .hasArg()
                .desc("Name of JSON file containing the extended sequences")
                .type(String.class)
                .build()
        );

     // option for sequences results file
        options.addOption(Option.builder("seqr")
            .longOpt("sequences-results")
            .hasArg()
            .desc("Name of JSON file containing the extended sequences results")
            .type(String.class)
            .build()
        );

        // help option
        options.addOption(Option.builder("h")
            .longOpt("help")
            .desc("Print this help message")
            .build()
        );

        HelpFormatter formatter = new HelpFormatter();

        // parse command line options
        CommandLineParser argParser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = argParser.parse(options, args);
            // if help option specified, print help message and return null
            if (cmd.hasOption("h")) {
                formatter.printHelp(DiffAssertionsGenerator.class.getName(), options, true);
                return null;
            }
        }
        catch (ParseException e) {
            logger.warning(e.getMessage());
            formatter.printHelp(DiffAssertionsGenerator.class.getName(), options, true);
        }

        // check whether required options are specified
        if (!cmd.hasOption("app") || !cmd.hasOption("seq") || !cmd.hasOption("seqr")) {
            formatter.printHelp(DiffAssertionsGenerator.class.getName(), options, true);
            return null;
        }
        return cmd;
    }

	public int getAssertCount() {
		return assertCounter;
	}

	public static void main(String args[]) throws FileNotFoundException, IOException, SecurityException,
			IllegalArgumentException, ClassNotFoundException {

		 // parse command-line options
        CommandLine cmd = parseCommandLineOptions(args);

        // if parser command-line is empty (which occurs if the help option is specified or a
        // parse exception occurs, exit
        if (cmd == null) {
            System.exit(0);
        }

        String appName = cmd.getOptionValue("app");
        String seqFilename = cmd.getOptionValue("seq");
        String seqResultsFilename = cmd.getOptionValue("seqr");
        logger.info("Application name: "+appName);
        logger.info("Sequences file: "+seqFilename);
        logger.info("Sequences results file: "+seqResultsFilename);

		new DiffAssertionsGenerator(appName, new File(seqFilename), new File(seqResultsFilename));
	}

}
