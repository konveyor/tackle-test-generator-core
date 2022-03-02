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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor.SequenceInfo;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor.SequenceResults;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;
import org.konveyor.tackle.testgen.util.TackleTestLogger;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.utils.ClassUtils;

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
	
	private final static ObjectMapper mapper = TackleTestJson.getObjectMapper();

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

	private void readResults(File resFile) throws ClassNotFoundException, JsonProcessingException, IOException {

		ObjectNode mainObject = (ObjectNode) mapper.readTree(resFile);
		
		mainObject.fieldNames().forEachRemaining(seqId -> {

			ObjectNode content = (ObjectNode) mainObject.get(seqId);

			ArrayNode originalIndices = (ArrayNode) content.get("original_sequence_indices");

			Set<Integer> indices = new HashSet<Integer>();

			for (int i = 0; i < originalIndices.size(); i++) {
				indices.add(originalIndices.get(i).asInt());
			}

			SequenceResults results;
			try {
				results = new SequenceResults(content, indices);
				id2Results.put(seqId, results);
			} catch (ClassNotFoundException e) {
				logger.warning("ClassNotFoundException: "+e.getMessage());
			}
		});

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
			
			if ( ! results.normalTermination[resCounter]) {
				
				logger.info("Creating fail assertion for statement "+statements[i]);
				
				addAssertInExceptionHandlingBlock(statements[i], codeWithAssertions, results);
				break STATEMENT_LOOP;
				
			} else {
				
				if (results.runtimeObjectType[resCounter] == null) {
					codeWithAssertions.append(statements[i]);
					codeWithAssertions.append(LINE_SEPARATOR);
				} else {
					
					/*
					 * If this runtime object name is not defined in the original sequence then it
					 * was added by Randoop and we should skip it
					 */

					while (!SequenceParser.resolve(results.runtimeObjectName[resCounter], block)) {
						resCounter++;
						if (results.runtimeObjectName[resCounter] == null) {
							if ( ! results.normalTermination[resCounter]) {
								logger.info("Creating fail assertion for statement "+statements[i]);
								addAssertInExceptionHandlingBlock(statements[i], codeWithAssertions, results);
								break STATEMENT_LOOP;
							} else {
								codeWithAssertions.append(statements[i]);
								codeWithAssertions.append(LINE_SEPARATOR);
								continue STATEMENT_LOOP;
							}
						}
					}
					
					logger.info("Creating value recording assertion for statement "+statements[i]);
					
					codeWithAssertions.append(statements[i]);
					codeWithAssertions.append(LINE_SEPARATOR);

					Class<?> theClass = results.runtimeObjectType[resCounter];

					String assertion = getAssertForSimpleType(theClass,
							(String) results.runtimePublicObjectState.get(resCounter)
									.get(results.runtimeObjectName[resCounter]),
							results.runtimeObjectName[resCounter],
							SequenceParser.isPrimitiveType(results.runtimeObjectName[resCounter], originalCode));

					if (assertion != null) {
						codeWithAssertions.append(assertion);
					} else {

						addAssertionsOnPublicFields(results.runtimePublicObjectState.get(resCounter),
								results.runtimeObjectName[resCounter], theClass, codeWithAssertions);
						addAssertionsOnPrivateFields(theClass, results.runtimePrivateObjectState.get(resCounter),
								results.runtimeObjectName[resCounter], theClass, codeWithAssertions);
					}
				}
			}
		}

		return codeWithAssertions.toString();
	}

	private void addAssertInExceptionHandlingBlock(String statement, StringBuilder codeWithAssertions, SequenceResults results) {
		
		codeWithAssertions.append("try {");
		codeWithAssertions.append(LINE_SEPARATOR);
		codeWithAssertions.append("\t"+statement);
		codeWithAssertions.append(LINE_SEPARATOR);
		codeWithAssertions.append("\torg.junit.Assert.fail(\"Expected exception of type "+results.getException()+"\");");
		codeWithAssertions.append(LINE_SEPARATOR);
		codeWithAssertions.append("} catch ("+results.getException()+" e) {");
		codeWithAssertions.append(LINE_SEPARATOR);
		codeWithAssertions.append("\t// Expected exception");
		codeWithAssertions.append(LINE_SEPARATOR);
		codeWithAssertions.append("}");
		
		assertCounter++;
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

	private void addAssertionsOnPrivateFields(Class<?> callingObjectType, Map<String, String> runtimeState, 
			String objName, Class<?> objClass, StringBuilder codeWithAssertions) {

		for (Map.Entry<String, String> entry : runtimeState.entrySet()) {

			String methodName = entry.getKey();

			Method theMethod;
			try {
				theMethod = objClass.getMethod(methodName);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			}

			String fieldVal = entry.getValue();
			
			// We need to cast the calling object because its formal type might be a superclass of its
			// recorded runtime type		
			
			String getterCall = "(("+callingObjectType.getName().replaceAll("\\$", ".")+") "+objName+")."+theMethod.getName()+"()";

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
			if (recordedVal.equals(SequenceExecutor.TKLTEST_NULL_STRING)) {
				return getNullAssert(actualVal);
			} else {
				return getAssert(getStringValueForAssert(recordedVal), actualVal);
			}
		} else if (ClassUtils.isPrimitiveOrWrapper(theType)) {
			String unboxingMethod = "";

			if ( ! isPrimitive && ! recordedVal.equals(SequenceExecutor.TKLTEST_NULL_STRING)) {
				unboxingMethod = "."+getUnboxingMethod(theType);
			}
			
			// Perform casting in case formal type is a superclass of recorded type, and unboxing
			// to avoid ambiguous assertion, i.e., to be done on the primitive types, not the object level

			if ( ! unboxingMethod.isEmpty()) {
				
				actualVal = "(("+theType.getName().replaceAll("\\$", ".")+") "+actualVal+")"+unboxingMethod;
			}
			
			if (recordedVal.equals(SequenceExecutor.TKLTEST_NULL_STRING)) {
				return getNullAssert(actualVal);
			}

			if (theType.getName().equals("java.lang.Float") ||
					theType.getName().equals("float") ||
					theType.getName().equals("java.lang.Double") ||
					theType.getName().equals("double")) {

				if (recordedVal.equals("NaN")) {
					recordedVal = "java.lang.Double.NaN";
				} else if (recordedVal.equals("Infinity")) {
					recordedVal = "java.lang.Double.POSITIVE_INFINITY";
				} else if (recordedVal.equals("-Infinity")) {
					recordedVal = "java.lang.Double.NEGATIVE_INFINITY";
				}

				return getAssert(recordedVal, "("+theType.getName()+") "+actualVal,  0.015);

			} else {
				
				if (theType.getName().equals("java.lang.Short") || theType.getName().equals("short")) {
					recordedVal = "(short) "+recordedVal;
				} else if (theType.getName().equals("java.lang.Byte") || theType.getName().equals("byte")) {
					recordedVal = "(byte) "+recordedVal;
				} else if (theType.getName().equals("java.lang.Long") || theType.getName().equals("long")) {
					recordedVal += "L";
				} else if (theType.getName().equals("java.lang.Character") || theType.getName().equals("char")) {
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
	
	private String getNullAssert(String arg) {
		
		assertCounter++;
		
		return "assertNull("+arg+");"+LINE_SEPARATOR;
	}

	private String getAssert(String arg1, String arg2, double delta) {
		
		assertCounter++;

		return "assertEquals("+arg1+", "+arg2+(delta == 0? "" : ", "+String.valueOf(delta))+");"+LINE_SEPARATOR;
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
			throws JsonGenerationException, JsonMappingException, IOException {
		
		ObjectNode sequencesObject = mapper.createObjectNode();

		for (Map.Entry<String, SequenceInfo> entry : id2AssertSequences.entrySet()) {

			String seqId = entry.getKey();

			ObjectNode sequenceObject = mapper.createObjectNode();

			sequenceObject.put("class_name", entry.getValue().className);
			sequenceObject.put("sequence", entry.getValue().sequence);

			ArrayNode importsArray = mapper.createArrayNode();

			for (String imp : entry.getValue().imports) {
				importsArray.add(imp);
			}

			sequenceObject.set("imports", importsArray);

			sequencesObject.set(seqId, sequenceObject);
		}
		
		mapper.writeValue(outputFile, sequencesObject);
	}

	private static String getStringValueForAssert(String runtimeVal) {

		if (runtimeVal.equals("\\")) {
			return "\""+"\\\\"+"\"";
		}

		return "\""+runtimeVal.replaceAll("\\\\","\\\\\\\\").replaceAll("\\\n", "\\\\n").
				replaceAll("\\\r", "\\\\r").replaceAll("\"", "\\\\\"")+"\"";
	}

	private static String getCharValueForAssert(String runtimeVal) {

		/* if integer is given leave it as is */

		try {
			Integer.parseInt(runtimeVal);
		} catch (NumberFormatException e) {
			String val = runtimeVal;
			if (val.equals("\\")) {
				val = "\\\\";
			} else if (val.equals("'")) {
				val = "\\'";
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
