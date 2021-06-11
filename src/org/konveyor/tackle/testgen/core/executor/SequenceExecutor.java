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

import com.github.javaparser.utils.ClassUtils;
import org.konveyor.tackle.testgen.core.EvoSuiteTestGenerator;
import org.konveyor.tackle.testgen.core.SequenceParser;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.apache.commons.cli.*;
import randoop.ExceptionalExecution;
import randoop.ExecutionOutcome;
import randoop.ExecutionVisitor;
import randoop.NormalExecution;
import randoop.org.checkerframework.checker.signature.qual.SignatureBottom;
import randoop.sequence.ExecutableSequence;
import randoop.sequence.Sequence;
import randoop.sequence.SequenceParseException;
import randoop.test.TestChecks;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

/**
 * Creates Randoop sequences from junit test sequences, executes them in memory and collect
 * results.
 * @author RACHELBRILL
 *
 */

public class SequenceExecutor {

	private Map<String, SequenceInfo> id2Sequences = new HashMap<String, SequenceInfo>();

	private Map<String, SequenceResults> id2ExecutionResults = new HashMap<String, SequenceResults>();

	// Maps sequence id to its original indices (without Randoop-added statements)
	private Map<String, List<Integer>> id2Indices = new HashMap<String, List<Integer>>();

	boolean allResults = false; // when set to true, runtime object state is also returned

	private static boolean VERBOSE = true;

	public static final String TKLTEST_NULL_STRING = "__tkltest_null";

	private static final Logger logger = TackleTestLogger.getLogger(SequenceExecutor.class);

    public static class SequenceInfo {
		public final String className;
		public final String sequence;
		public final List<String> imports;

		public SequenceInfo(String name, String seq, List<String> imps) {
			className = name;
			sequence = seq;
			imports = imps;
		}
	}

	public static class SequenceResults {
		public String[] runtimeObjectName;
		public Class<?>[] runtimeObjectType;
		public List<Map<String, String>> runtimePublicObjectState;
		public List<Map<String, String>> runtimePrivateObjectState;
		Boolean[] normalTermination;
		String[] output;
        public String[] exception;
        public String[] exceptionMessage;
        public String[] cause;
        public String[] causeMessage;
		public boolean passed;

		public SequenceResults(int size) {
			normalTermination = new Boolean[size];
			runtimeObjectName = new String[size];
			runtimeObjectType = new Class<?>[size];
			runtimePublicObjectState = new ArrayList<Map<String, String>>(size);
			for (int i=0;i<size;i++) {
				runtimePublicObjectState.add(null);
			}
			runtimePrivateObjectState = new ArrayList<Map<String, String>>(size);
			for (int i=0;i<size;i++) {
				runtimePrivateObjectState.add(null);
			}
			output = new String[size];
			exception = new String[size];
			exceptionMessage = new String[size];
			cause = new String[size];
			causeMessage = new String[size];
		}

		public SequenceResults(SequenceResults other) {
			runtimeObjectName = Arrays.copyOf(other.runtimeObjectName, other.runtimeObjectName.length);
			runtimeObjectType = Arrays.copyOf(other.runtimeObjectType, other.runtimeObjectType.length);
			runtimePublicObjectState = new ArrayList<Map<String, String>>(other.runtimePublicObjectState);
			runtimePrivateObjectState = new ArrayList<Map<String, String>>(other.runtimePrivateObjectState);
			normalTermination = Arrays.copyOf(other.normalTermination, other.normalTermination.length);
			output = Arrays.copyOf(other.output, other.output.length);
			exception = Arrays.copyOf(other.exception, other.exception.length);
			exceptionMessage = Arrays.copyOf(other.exceptionMessage, other.exceptionMessage.length);
			cause = Arrays.copyOf(other.cause, other.cause.length);
			causeMessage = Arrays.copyOf(other.causeMessage, other.causeMessage.length);
			passed = other.passed;
		}

		public SequenceResults(JsonObject content, Set<Integer> indices) throws ClassNotFoundException {

			this(indices.size());
			passed = content.getBoolean("normal_termination");

			JsonArray statementResults = content.getJsonArray("per_statement_results");

			int k = 0;
			for (int i = 0; i < statementResults.size(); i++) {
				if (indices.contains(i)) {
					JsonObject statementInfo = statementResults.getJsonObject(i);
					normalTermination[k] = statementInfo.getBoolean("statement_normal_termination");
					if (normalTermination[k]) {
						if (statementInfo.keySet().contains("runtime_object_name")) {
							runtimeObjectName[k] = statementInfo.getString("runtime_object_name");
							runtimeObjectType[k] = Class.forName(statementInfo.getString("runtime_object_type"));
							runtimePublicObjectState.set(k, jsonToObjState(statementInfo.getJsonObject("runtime_object_state")));
							runtimePrivateObjectState.set(k, jsonToObjState(statementInfo.getJsonObject("runtime_private_object_state")));
						}
					}
					k++;
				}
			}
		}

		private Map<String, String> jsonToObjState(JsonObject objState) {
			Map<String, String> state = new HashMap<String, String>();

			for (Map.Entry<String, JsonValue> entry : objState.entrySet()) {
				state.put(entry.getKey(), entry.getValue().toString());
			}

			return state;
		}

		public int size() {
			return normalTermination.length;
		}

		public JsonObject toJson(List<Integer> origSeqIndices) {

			JsonArrayBuilder sequenceArray = Json.createArrayBuilder();

			JsonObjectBuilder sequenceObject = Json.createObjectBuilder();

			sequenceObject.add("normal_termination", passed);

			sequenceObject.add("original_sequence_indices", toJsonArray(origSeqIndices));

			for (int i=0; i<normalTermination.length && normalTermination[i] != null; i++) {
				JsonObjectBuilder statementObject = Json.createObjectBuilder();

				statementObject.add("statement_normal_termination", normalTermination[i]);
				if (output[i] != null) {
					statementObject.add("output", output[i]);
				}
				if (runtimeObjectName[i] != null) {
					statementObject.add("runtime_object_name", runtimeObjectName[i]);
					statementObject.add("runtime_object_type", runtimeObjectType[i].getName());
					statementObject.add("runtime_object_state", objectStateToJson(runtimePublicObjectState.get(i)));
					statementObject.add("runtime_private_object_state", objectStateToJson(runtimePrivateObjectState.get(i)));
				}

				if ( ! normalTermination[i]) {

					statementObject.add("exception", exception[i]);
					statementObject.add("exception_message", exceptionMessage[i]);

					if (cause[i] != null) {
						statementObject.add("cause", cause[i]);
						statementObject.add("cause_message",  causeMessage[i]);
					}
				}
				sequenceArray.add(statementObject.build());
			}

			sequenceObject.add("per_statement_results", sequenceArray.build());

			return sequenceObject.build();
		}

		private JsonObject objectStateToJson(Map<String, String> objState) {

			JsonObjectBuilder objBuilder = Json.createObjectBuilder();

			for (Map.Entry<String, String> entry : objState.entrySet()) {

				objBuilder.add(entry.getKey(), entry.getValue());
			}

			return objBuilder.build();
		}

		/* Retain in results only recorded values that agree with given results */

		private void retain(SequenceResults other) {

			for (int i=0; i< runtimeObjectName.length; i++) {


				if (runtimeObjectName[i] != null) {

					runtimePublicObjectState.get(i).entrySet().retainAll(other.runtimePublicObjectState.get(i).entrySet());
					runtimePrivateObjectState.get(i).entrySet().retainAll(other.runtimePrivateObjectState.get(i).entrySet());

					if (runtimePublicObjectState.get(i).isEmpty() && runtimePrivateObjectState.get(i).isEmpty()) {

						logger.info("Removing recorded state for "+runtimeObjectName[i]+" because it is suspected to be random");

						runtimeObjectName[i] = null;
						runtimeObjectType[i] = null;
					}
				}
			}

		}
	}


	public SequenceExecutor(String appName, String seqFile, boolean allResults) throws IOException, SequenceParseException {

		this(allResults);

		boolean addPackageDeclaration = readSequences(new File(seqFile), id2Sequences);

		executeSequences(addPackageDeclaration);

		toJson(appName);
	}

	/**
	 *
	 * @param allResults When set to true, records all results (including runtime generated objects).
	 * Otherwise, records only fail/pass results.
	 */

	public SequenceExecutor(boolean allResults) {
		this.allResults = allResults;
	}

	/**
	 *
	 * @param sequencesFile
	 * @param id2Seq
	 * @return whether the sequences were generated by EvoSuite
	 * @throws FileNotFoundException
	 */

	public static boolean readSequences(File sequencesFile, Map<String, SequenceInfo> id2Seq) throws FileNotFoundException {

		JsonReader reader = null;
		JsonObject mainObject = null;

		try {

			InputStream fis = new FileInputStream(sequencesFile);
			reader = Json.createReader(fis);
			mainObject = reader.readObject();
		} finally {
			if (reader != null) {
				reader.close();
			}
		}

		JsonObject seqObject = mainObject.getJsonObject("test_sequences");

		for (Map.Entry<String, JsonValue> entry : seqObject.entrySet()) {

			String seqId = entry.getKey();

			JsonObject content = (JsonObject) entry.getValue();

			JsonArray importsArray = content.getJsonArray("imports");

			List<String> imports = new ArrayList<String>();

			for (int i = 0; i < importsArray.size(); i++) {
				imports.add(importsArray.getString(i));
			}

			id2Seq.put(seqId,
					new SequenceInfo(content.getString("class_name"), content.getString("sequence"), imports));
		}

		String toolName = mainObject.getString("test_generation_tool");

		return toolName.equals(EvoSuiteTestGenerator.class.getSimpleName());
	}

	private void executeSequences(boolean addPackageDeclaration) throws SequenceParseException {

		for (Map.Entry<String, SequenceInfo> entry : id2Sequences.entrySet()) {

			String id = entry.getKey();

			SequenceInfo info = entry.getValue();

			List<Integer> originalSeqIndices = new ArrayList<Integer>();

			Sequence randoopSequence = SequenceParser.codeToSequence(info.sequence, info.imports, info.className, addPackageDeclaration, originalSeqIndices);

			id2Indices.put(id,  originalSeqIndices);
			String[] origStatements = id2Sequences.get(id).sequence.split(System.lineSeparator());

			ExecutableSequence es = new ExecutableSequence(randoopSequence);
			es.execute(new SequenceExecutionVisitor(id, randoopSequence.toParsableString().split(System.lineSeparator()),
					origStatements, new HashSet<Integer>(originalSeqIndices)), new SequenceTestCheckGenerator());
		}
	}

	/**
	 * Executes a given sequence and return results
	 * @param seqId The id of the sequence
	 * @param randoopSequence the sequence
	 * @param numExecutions of executions to perform. Recorded values that are different between different executions, hence suspected to be random, will be erased.
	 * @return the results of the sequence - global pass/fail and per statement results
	 * @throws IOException
	 */

	public SequenceResults executeSequence(String seqId, Sequence randoopSequence, int numExecutions) {

		String[] statements = randoopSequence.toParsableString().split(System.lineSeparator());

		ExecutableSequence es = new ExecutableSequence(randoopSequence);
		es.execute(new SequenceExecutionVisitor(seqId, statements, null, null), new SequenceTestCheckGenerator());

		SequenceResults results = id2ExecutionResults.get(seqId);

		if (numExecutions == 1 || ! results.passed) {
			return results;
		}

		SequenceResults updatedResults = new SequenceResults(results);

		for (int i=1; i<numExecutions;i++) {

			es = new ExecutableSequence(randoopSequence);
			es.execute(new SequenceExecutionVisitor(seqId, statements, null, null), new SequenceTestCheckGenerator());
			results = id2ExecutionResults.get(seqId);

			if ( ! results.passed) {
				return results;
			}

			updatedResults.retain(results);
		}

		return updatedResults;
	}

	private class SequenceExecutionVisitor implements ExecutionVisitor {

		private SequenceResults results;

		private final String seqID;
		private String[] executedStatements;
		private String[] origStatements;
		private Set<Integer> origSeqIndices;
		private int origSeqCounter = -1;

		SequenceExecutionVisitor(String id, String[] execStmts, String[] origStmts, Set<Integer> origIndices) {
			seqID = id;
			executedStatements = execStmts;
			origStatements = origStmts;
			origSeqIndices = origIndices;
		}

		@Override
		public void initialize(ExecutableSequence es) {

			results = new SequenceResults(es.size());
		}

		@Override
		public void visitAfterSequence(ExecutableSequence es) {

			// Check if execution terminated normally - if not need to record
			// last executed statement

			results.passed = (Boolean.TRUE.equals(results.normalTermination[es.size() - 1]));

			if (results.normalTermination[es.size() - 1] == null) {
				// find first statement that didn't terminate normally
				for (int i = 0; i < es.size(); i++) {
					if (results.normalTermination[i] == null) {
						recordResults(es, i);
						break;
					}
				}
			}

			id2ExecutionResults.put(seqID, results);
		}

		@Override
		public void visitAfterStatement(ExecutableSequence es, int index) {

			recordResults(es, index);
		}

		private void recordResults(ExecutableSequence es, int index) {

			ExecutionOutcome result = es.getResult(index);

			results.normalTermination[index] = (result instanceof NormalExecution);

			if (result instanceof ExceptionalExecution) {
				Throwable exception =  ((ExceptionalExecution) result).getException();
				String fullMessage = exception.toString();
				if (fullMessage.contains(":")) {
					results.exception[index] = fullMessage.substring(0, fullMessage.indexOf(':'));
				} else {
					results.exception[index] = fullMessage;
				}

				results.exceptionMessage[index] =  exception.getMessage() == null? "" : exception.getMessage();

				Throwable cause = exception.getCause();

				if (cause != null) {
					results.cause[index] = cause.toString().substring(0, cause.toString().indexOf(':'));
					results.causeMessage[index] =   cause.getMessage();
				}
			}

			boolean isOrigStatement = origSeqIndices == null || origSeqIndices.contains(index);

			if (isOrigStatement) {
				origSeqCounter++;
			}


			if (allResults) {

				Object runtimeObject = result instanceof NormalExecution
						? ((NormalExecution) result).getRuntimeValue()
						: null;

				if (runtimeObject != null) {
					String receiverName;

					if (isOrigStatement && origStatements != null) {

						String statement = origStatements[origSeqCounter].trim();

						int spaceIndex = statement.indexOf(' ');
						int equalsIndex = statement.indexOf(" = ");

						if (equalsIndex == -1) {
							throw new RuntimeException("cannot locate receive name in statement "+statement);
						}

						if (spaceIndex > -1) {
							if (spaceIndex != equalsIndex) {
								receiverName = statement.substring(spaceIndex+1, equalsIndex);
							} else {
								receiverName = statement.substring(0, spaceIndex);
							}
						} else {
							receiverName = statement.substring(0, equalsIndex);
						}

					} else {
						receiverName = executedStatements[index].substring(0, executedStatements[index].indexOf(" = "));
					}

					Map<String, String> objPublicState = new HashMap<String, String>();
					Map<String, String> objPrivateState = new HashMap<String, String>();

					getObjectState(runtimeObject, receiverName, objPublicState, objPrivateState);

					if ( ! objPublicState.isEmpty() || ! objPrivateState.isEmpty()) {

						results.runtimeObjectName[index] = receiverName;
						results.runtimeObjectType[index] = runtimeObject.getClass();
						results.runtimePublicObjectState.set(index, objPublicState);
						results.runtimePrivateObjectState.set(index, objPrivateState);

					} else {
						// otherwise we cannot record the state of this object so we skip it (contains only non-public fields)
						logger.warning("Skipping recording of object "+receiverName+" in sequence "+origSeqCounter+ " because it has no public fields");
					}
				}
			}

			results.output[index] = result.get_output();

			if (VERBOSE) {

				StringBuilder resultsStr = new StringBuilder();

				resultsStr.append(es.sequence.getStatement(index).toString());
				resultsStr.append(" Normal termination: " + results.normalTermination[index]);
				if (results.output[index] != null) {
					resultsStr.append(" Output: " + results.output[index]);
				}
				if (results.runtimePublicObjectState.get(index) != null) {
					for (Map.Entry<String, String> entry : results.runtimePublicObjectState.get(index).entrySet()) {
						resultsStr.append(entry.getKey() + " : " + entry.getValue());
					}
				}
				if (results.runtimePrivateObjectState.get(index) != null) {
					for (Map.Entry<String, String> entry : results.runtimePrivateObjectState.get(index).entrySet()) {
						resultsStr.append(entry.getKey() + " : " + entry.getValue());
					}
				}
				if (results.exception[index] != null) {
					resultsStr.append(" Thrown exception: "+results.exception[index]);
				}
				resultsStr.append(System.lineSeparator());

				logger.fine(resultsStr.toString());
			}
		}

		@Override
		public void visitBeforeStatement(ExecutableSequence es, int index) {

		}
	}

	private class SequenceTestCheckGenerator extends randoop.test.TestCheckGenerator {

		@Override
		public TestChecks<@SignatureBottom ?> generateTestChecks(ExecutableSequence eseq) {
			// TODO Auto-generated method stub
			return null;
		}

	}

	private void toJson(String appName) throws IllegalArgumentException, IOException {

		JsonObjectBuilder resultsObject = Json.createObjectBuilder();

		for (Map.Entry<String, SequenceResults> entry : id2ExecutionResults.entrySet()) {
			String seqId = entry.getKey();
			SequenceResults results = entry.getValue();

			JsonObject sequenceObject = results.toJson(id2Indices.get(seqId));

			resultsObject.add(seqId, sequenceObject);
		}

		JsonWriter writer = null;

		try {

			JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
			writer = writerFactory.createWriter(new FileOutputStream(new File(appName+"_"+ Constants.EXECUTOR_OUTFILE_SUFFIX)));
			writer.writeObject(resultsObject.build());

		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	private void getObjectState(Object object, String name, Map<String, String> objPublicState,  Map<String, String> objPrivateState) {

		String  objVal = getPrimitiveVal(object);

		if (objVal != null) {
			objPublicState.put(name,  objVal);
			return;
		}


		// First go over all public fields

		Field[] fields = object.getClass().getFields();

		Set<String> publicFields = new HashSet<String>();

		for (Field field : fields) {

			publicFields.add(field.getName());

			if (field.getType().equals(object.getClass())) {
				continue; // avoid endless recursive call
			}

			Object value;

			try {
				value = field.get(object);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}

			String fieldVal = getPrimitiveVal(value);

			if (fieldVal != null) {
				objPublicState.put(field.getName(), fieldVal);
			}
		}

		// Collect all potential getters - public methods that start with "get" and have no parameters

		Map<String, Method> allPublicGetters = new HashMap<String, Method>();

		for (Method publicMethod : object.getClass().getMethods()) {

			if (publicMethod.getName().startsWith("get") && publicMethod.getParameterCount() == 0) {
				allPublicGetters.put(publicMethod.getName().toLowerCase(), publicMethod);
			}
		}

		// Now go over other declared fields and check if they have a public getter

		fields = object.getClass().getDeclaredFields();

		for (Field field : fields) {

			if (publicFields.contains(field.getName())) {
				continue;
			}

			Method getterMethod = allPublicGetters.get("get"+field.getName().toLowerCase());

			if (getterMethod != null) {

				Object value;

				try {
					value = getterMethod.invoke(object);
				} catch (InvocationTargetException e) {
					continue; // Some other field values are probably missing in order to initialize this field
				} catch (IllegalAccessException | IllegalArgumentException e) {
					throw new RuntimeException(e);
				}

				String fieldVal = getPrimitiveVal(value);

				if (fieldVal != null) {
					objPrivateState.put(getterMethod.getName(), fieldVal);
				}
			}

		}
	}

	private String getPrimitiveVal(Object obj) {
		if (obj == null) {
			return TKLTEST_NULL_STRING;
		}

		if (ClassUtils.isPrimitiveOrWrapper(obj.getClass()) || obj.getClass().getName().equals("java.lang.String")) {
			return obj.toString();
		}

		return null;
	}

	private static String serializableToString(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( baos );
        oos.writeObject( o );
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

	private static JsonArray toJsonArray(List<Integer> list) {

		JsonArrayBuilder indices = Json.createArrayBuilder();

		for (Integer item : list) {

			indices.add(item);
		}

		return indices.build();
	}

	/**
	 *
	 * @param obj
	 * @return Serialized string if serializable, null otherwise
	 * @throws IOException
	 */

	static String attemptSerialize(Object obj) throws IOException {

		if ( ! Serializable.class.isAssignableFrom(obj.getClass())) {
			return null;
		}

		try {
			return serializableToString((Serializable) obj);
		} catch (NotSerializableException e) {
			return null;
		}
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
        options.addOption(Option.builder("all")
            .longOpt("record-all")
            .hasArg()
            .desc("Whether to collect also created runtime object results. If set to false,"
            		+ " only pass/fail results are recorded.")
            .type(Boolean.class)
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
                formatter.printHelp(SequenceExecutor.class.getName(), options, true);
                return null;
            }
        }
        catch (ParseException e) {
            logger.warning(e.getMessage());
            formatter.printHelp(SequenceExecutor.class.getName(), options, true);
        }

        // check whether required options are specified
        if (!cmd.hasOption("app") || !cmd.hasOption("seq") || !cmd.hasOption("all")) {
            formatter.printHelp(SequenceExecutor.class.getName(), options, true);
            return null;
        }
        return cmd;
    }

	public static void main(String args[]) throws FileNotFoundException, IOException, SecurityException, IllegalArgumentException, SequenceParseException {

		 // parse command-line options
        CommandLine cmd = parseCommandLineOptions(args);

        // if parser command-line is empty (which occurs if the help option is specified or a
        // parse exception occurs, exit
        if (cmd == null) {
            System.exit(0);
        }

        String appName = cmd.getOptionValue("app");
        String seqFilename = cmd.getOptionValue("seq");
        Boolean recAll = Boolean.parseBoolean(cmd.getOptionValue("all"));
        logger.info("Application name: "+appName);
        logger.info("Sequences file: "+seqFilename);
        logger.info("Record all results: "+recAll);

		new SequenceExecutor(appName, seqFilename, recAll);
	}
}
