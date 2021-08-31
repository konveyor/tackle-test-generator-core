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

package org.konveyor.tackle.testgen.core.extender;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.konveyor.tackle.testgen.core.SequenceParser;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.utils.Pair;

import randoop.operation.CallableOperation;
import randoop.operation.ConstructorCall;
import randoop.operation.MethodCall;
import randoop.operation.Operation;
import randoop.operation.TypedClassOperation;
import randoop.operation.TypedOperation;
import randoop.org.apache.commons.io.output.NullPrintStream;
import randoop.org.apache.commons.lang3.RandomStringUtils;
import randoop.org.apache.commons.lang3.RandomUtils;
import randoop.sequence.Sequence;
import randoop.sequence.Variable;
import randoop.types.Type;

/**
 * Class for building and managing sequence pools for class construction, target method calls,
 * and primitive values. The sequence pools are populated by parsing and analyzing building-block
 * test sequences.
 *
 */
class SequencePool {

    private static final Logger logger = TackleTestLogger.getLogger(SequencePool.class);
    private static final boolean DEBUG = false;

    // map from class name to set of sequences that create instances of that class
    HashMap<String, SortedSet<Sequence>> classTestSeqPool;

    // map from method signature to set of sequences that invoke that method; the last statement
    // in the sequence is the call to the method
    HashMap<String, SortedSet<Sequence>> methodTestSeqPool;

    // constant pool for primitive values
    PrimitiveValuePool primitiveValuePool;

    // imports for the initial test sequence classes
    HashMap<String, List<String>> classImports;

    // test class setup and teardown methods
    Map<String, Set<String>> classBeforeAfterMethods;

    // information about different exceptions raised while creating sequence objects
    HashMap<String, Integer> parseExceptions;

    // set of signatures for proxy methods targeted for coverage in the CTD plan
    private Set<String> targetProxyMethodSignatures;

    int totalBaseSequences = 0;
    int parsedBaseSequencesFull = 0;
    int parsedBaseSequencesPartial = 0;
    int skippedBaseSequences = 0;
    int exceptionBaseSequences = 0;

    int parseErrorEOF = 0;

    SequencePool(List<ObjectNode> initialTestSeqs, Set<String> tgtProxyMethodSignatures, String appName)
        throws JsonGenerationException, JsonMappingException, IOException {

        this.classTestSeqPool = new HashMap<>();
        this.methodTestSeqPool = new HashMap<>();
        this.primitiveValuePool = new PrimitiveValuePool();
        this.classImports = new HashMap<>();
        this.classBeforeAfterMethods = new HashMap<>();
        this.parseExceptions = new HashMap<>();
        this.targetProxyMethodSignatures = tgtProxyMethodSignatures;
        initTestSequencePool(initialTestSeqs, appName);
    }


    /**
     * Initializes test sequence pools for classes and methods (from the CTD test
     * plan)
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    private void initTestSequencePool(List<ObjectNode> initialTestSeqs, String appName) throws JsonGenerationException, JsonMappingException, IOException {
    	
    	ObjectMapper mapper = TackleTestJson.getObjectMapper();

    	ObjectNode parseErrorSequencesInfo = mapper.createObjectNode();

        // iterate over each class in JSON info about initial sequences
        for (ObjectNode initialTestSeq : initialTestSeqs) {
        	
        	initialTestSeq.fieldNames().forEachRemaining(cls -> {

            	ObjectNode clsInfo = (ObjectNode) initialTestSeq.get(cls);
                ArrayNode sequences = (ArrayNode) clsInfo.get("sequences");
                ArrayNode imports = (ArrayNode) clsInfo.get("imports");
                List<String> importList = mapper.convertValue(imports, 
                		new TypeReference<List<String>>(){}); 
                		
                if (this.classImports.get(cls) == null) {
                    this.classImports.put(cls, new ArrayList<String>());
                }
                this.classImports.get(cls).addAll(importList);

                ArrayNode beforeAfterMethods = (ArrayNode) clsInfo.get("before_after_code_segments");
                List<String> beforeAfterMethodsList = mapper.convertValue(beforeAfterMethods, 
                		new TypeReference<List<String>>(){});
                		
                if (this.classBeforeAfterMethods.get(cls) == null) {
                    this.classBeforeAfterMethods.put(cls, new HashSet<String>());
                }

                this.classBeforeAfterMethods.get(cls).addAll(beforeAfterMethodsList);

                totalBaseSequences += sequences.size();
                logger.info("Initial sequences for " + cls + ": " + sequences.size());
                logger.info("Imports: " + importList);

                // iterate over each string sequence for class and parse it into a randoop
                // sequence object
                
                sequences.elements().forEachRemaining(seq -> {
                
                    String testSeq = seq.asText();
                    logger.fine("- " + testSeq);
                    PrintStream origSysOut = System.out;
                    PrintStream origSysErr = System.err;
                    try {
                        // disable stdout/stderr prints from sequence parsing
                        System.setOut(NullPrintStream.NULL_PRINT_STREAM);
                        System.setErr(NullPrintStream.NULL_PRINT_STREAM);

                        // create randoop sequence object by parsing the string representation of sequence
                        Pair<Sequence, Boolean> parsedSeqPair = SequenceParser.codeToSequence(testSeq, importList, cls, true,
                            new ArrayList<Integer>());
                        Sequence randoopSeq = parsedSeqPair.a;

                        // restore stdout and stderr
                        System.setOut(origSysOut);
                        System.setErr(origSysErr);

                        logger.fine("Randoop test sequence: " + randoopSeq);
                        // update counters for fully/partially parsed sequences and skipped sequences
                        if (randoopSeq.size() > 0) {
                            if (parsedSeqPair.b) {
                                parsedBaseSequencesFull++;
                            } else {
                                parsedBaseSequencesPartial++;
                            }
                        } else {
                            skippedBaseSequences++;
                        }

                        // if the sequence has generic output types, perform type substitution
                        if (hasGenericTypesOutputType(randoopSeq)) {
                            randoopSeq = SequenceUtil.performTypeSubstitution(randoopSeq);
                        }

                        // mine all constructor sequences and add them to the class sequence pool
                        Map<String, Set<Sequence>> ctorSequences = getAllConstructorSequences(randoopSeq);
                        for (String ctorCls : ctorSequences.keySet()) {
                            if (!this.classTestSeqPool.containsKey(ctorCls)) {
                                this.classTestSeqPool.put(ctorCls,
                                    SequenceUtil.newSequenceSet(SequenceUtil.SequenceSetSort.SEQUENCE_SIZE));
                            }
                            this.classTestSeqPool.get(ctorCls).addAll(ctorSequences.get(ctorCls));
                        }

                        // update method sequence pool
                        updateMethodTestPool(randoopSeq);

                        // update value pool for primitive types
                        updatePrimitiveValuePool(randoopSeq);
                    } catch (Throwable e) {
                        // restore stdout and stderr
                        System.setOut(origSysOut);
                        System.setErr(origSysErr);

                        // if exception occurs in creating randoop sequence, record exception information
                        // for debugging
                        logger.warning("Error parsing sequence for class " + cls + ":\n" + testSeq);
                        logger.warning(e.getMessage());
                        logger.warning("Stack trace:");
                        for (StackTraceElement elem : e.getStackTrace()) {
                            logger.warning(elem.toString());
                        }
                        int excpCount = 1;
                        String excpType = e.getClass().getName();
                        if (this.parseExceptions.containsKey(excpType)) {
                            excpCount = this.parseExceptions.get(excpType) + 1;
                        }
                        this.parseExceptions.put(excpType, excpCount);
                        exceptionBaseSequences++;

                        ObjectNode parseErrorInfo = mapper.createObjectNode();
                        parseErrorInfo.put("exception_type", excpType);
                        parseErrorInfo.put("exception_msg", e.getMessage());
                        parseErrorInfo.put("sequence", testSeq);
                        parseErrorSequencesInfo.set(exceptionBaseSequences+"::"+cls, parseErrorInfo);
                    }
                    System.out.print("*   Full:" + parsedBaseSequencesFull +
                        "  Part:" + parsedBaseSequencesPartial +
                        "  Skip:" + skippedBaseSequences +
                        "  Excp:" + exceptionBaseSequences +
                        "\r");
                });
            });
        }

//        System.out.println("\n* Total parsed base sequences: " +
//            (parsedBaseSequencesFull + parsedBaseSequencesPartial));
//        System.out.println("* Skipped base sequences: " + skippedBaseSequences);
//        System.out.println("* Base sequences causing parse exceptions: " + parseExceptions.values()
//            .stream().mapToInt(Integer::intValue).sum());
        System.out.println("\n* Class sequence pool: " + classTestSeqPool.keySet().size() + " classes, " +
            classTestSeqPool.values().stream().mapToInt(Collection::size).sum() + " sequences");
        System.out.println("* Method sequence pool: " + methodTestSeqPool.keySet().size() + " methods, " +
            methodTestSeqPool.values().stream().mapToInt(Collection::size).sum() + " sequences");

        // in debug mode, write sequences resulting in parse errors to json file
        if (DEBUG) {
            ObjectNode parseErrorsObj = mapper.createObjectNode();
            parseErrorsObj.set("parse_error_sequences", parseErrorSequencesInfo);
            
            mapper.writeValue(new File(appName + Constants.SEQUENCE_PARSE_ERRORS_FILE_JSON_SUFFIX), parseErrorsObj);
        }

        logger.info("=======> Test sequence pool init done: total_seq=" + totalBaseSequences + "; parsed_seq="
            + parsedBaseSequencesFull);
        logger.info("Class sequence pool: " + classTestSeqPool.keySet().size() + " classes; "
            + classTestSeqPool.values().stream().collect(Collectors.summarizingInt(Set::size))
            + " total constructor sequences");
    }

    /**
     * Checks whether any of the statements in the given sequence has a generic output type
     * (which would require type substitution)
     * @param sequence
     * @return
     */
    private boolean hasGenericTypesOutputType(Sequence sequence) {
        for (int i = 0; i < sequence.size(); i++) {
            TypedOperation typedOper = sequence.getStatement(i).getOperation();
            Type outputType = typedOper.getOutputType();
            if (outputType.isGeneric()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given a sequence, mines all object constructing subsequences and returns a
     * map from fully qualified class names to mined constructor subsequences for
     * the classes.
     *
     * @param seq Sequence to be analyzed
     * @return Map from class names to mined constructor subsequences
     */
    private Map<String, Set<Sequence>> getAllConstructorSequences(Sequence seq) {
        HashMap<String, Set<Sequence>> minedSequences = new HashMap<>();

        // iterate over sequence to identify constructor calls
        for (int i = seq.size() - 1; i >= 0; i--) {
            CallableOperation oper = seq.getStatement(i).getOperation().getOperation();
            if (oper.isConstructorCall()) {
                logger.fine("Constructor call operation: " + oper);
                ConstructorCall ctorCall = (ConstructorCall) oper;
                String callClsName = ctorCall.getConstructor().getDeclaringClass().getName();

                // compute subsequence for constructor call
                Sequence ctorSeq = getSequenceForConstructorCall(seq, i);

                // skip subsequence if it contains an operation on a non-public class member
                if (SequenceUtil.containsNonPublicMemberOperation(ctorSeq)) {
                    continue;
                }

                // add it to the set of sequences for this class
                if (!minedSequences.containsKey(callClsName)) {
                    minedSequences.put(callClsName, new HashSet<>());
                }
                minedSequences.get(callClsName).add(ctorSeq);
            }
        }
        return minedSequences;
    }

    /**
     * Given a sequence and the index for a constructor call in the sequence,
     * creates and returns a new sequence that consists of statements in the
     * backward data slice of the constructor call. The returned subsequence thus
     * consists of only those statements that are relevant for the constructor call;
     * it can be used to create an object of that type during sequence extension.
     *
     * @param seq           sequence to be analyzed
     * @param ctorCallIndex index of constructor call
     * @return New sequence consisting of statements relevant for the constructor
     *         call
     */
    private Sequence getSequenceForConstructorCall(Sequence seq, int ctorCallIndex) {
        // indexes for statements in the sequence that need to be included in the ctor sequence
        List<Integer> stmtIndexes = new ArrayList<>();
        stmtIndexes.add(ctorCallIndex);

        // initialize worklist of relevant variables for computing the data slice
        List<Variable> varWorklist = new ArrayList<>();
        for (Variable var : seq.getInputs(ctorCallIndex)) {
            varWorklist.add(var);
        }

        // identify indexes of relevant statements for the constructor call by computing a
        // data slice: statements that define a relevant var
        for (int i = ctorCallIndex - 1; i >= 0; i--) {
            Variable defVar = seq.getVariable(i);
            logger.fine("DefVar: " + defVar);

            // if a relevant var is defined at the statement, record its index and update
            // the set of relevant variables
            if (varWorklist.contains(defVar)) {
                stmtIndexes.add(0, i);
                varWorklist.remove(defVar);
                varWorklist.addAll(seq.getInputs(i));

                // stop backward traversal if there no more relevant variables
                if (varWorklist.isEmpty()) {
                    break;
                }
            }
        }

        // construct a sequence by adding statements at the computed indexes
        logger.fine("Creating constructor sequence from indexes: " + stmtIndexes);
        Sequence ctorSeq = new Sequence();

        // map var names to their defining index in the new sequence
        HashMap<String, Integer> varDefIndexMap = new HashMap<>();

        // iterate over statement indexes, and extend the new sequence with the statement at
        // each index
        for (int i = 0; i < stmtIndexes.size(); i++) {
            int index = stmtIndexes.get(i);

            // add defined variable to the var-index map
            varDefIndexMap.put(seq.getVariable(index).getName(), i);

            // create list of input variables with the new sequence set as the owning sequence
            Sequence finalCtorSeq = ctorSeq;
            List<Variable> inputVars = seq.getInputs(index).stream()
                .map(var -> new Variable(finalCtorSeq, varDefIndexMap.get(var.getName())))
                .collect(Collectors.toList());

            // extend sequence with statement
            ctorSeq = ctorSeq.extend(seq.getStatement(index), inputVars);
        }
        return ctorSeq;
    }

    /**
     * Checks the methods covered in the given sequence and adds the subsequence
     * upto the method call to the sequence pool for those methods
     *
     * @param seq
     */
    private void updateMethodTestPool(Sequence seq) {
        // iterate over each statement in sequence
        for (int i = 0; i < seq.size(); i++) {
            Operation oper = seq.getStatement(i).getOperation();
            // skip statement is operation is neither a method call nor a constructor call
            if (!oper.isConstructorCall() && !oper.isMethodCall()) {
                continue;
            }
            TypedClassOperation typedOper = (TypedClassOperation) oper;

            // if operation is on a non-public member (method, constructor, field), return since the
            // sequence cannot be user for test generation
            if (SequenceUtil.isNonPublicMemberOperation(typedOper.getOperation())) {
                return;
            }

            // get signature of the called method/constructor
            String calleeSig = null;
            String calleeClsName = null;
            try {
                if (oper.isMethodCall()) {
                    MethodCall methodCall = (MethodCall) typedOper.getOperation();
                    Method method = methodCall.getMethod();
                    calleeClsName = method.getDeclaringClass().getName();
                    calleeSig = Utils.getSignature(method);
                } else {
                    ConstructorCall ctorCall = (ConstructorCall) typedOper.getOperation();
                    Constructor<?> ctor = ctorCall.getConstructor();
                    calleeClsName = ctor.getDeclaringClass().getName();
                    calleeSig = Utils.getSignature(ctor);
                }
            } catch (Exception e) {
                logger.warning("Error getting signature for called method/constructor: " + typedOper);
            }
            if (calleeClsName == null || calleeSig == null) {
                continue;
            }
            String fqCalleeSig = calleeClsName + "::" + calleeSig;

            // if called method/constructor signature occurs in the targeted set,
            // add sequence to sequence pool for that method/constructor
            if (this.targetProxyMethodSignatures.contains(fqCalleeSig)) {
                logger.fine("Sequence covers target proxy method: " + fqCalleeSig);
                if (!this.methodTestSeqPool.containsKey(fqCalleeSig)) {
                    this.methodTestSeqPool.put(fqCalleeSig,
                        SequenceUtil.newSequenceSet(SequenceUtil.SequenceSetSort.SEQUENCE_SIZE));
                }
                this.methodTestSeqPool.get(fqCalleeSig).add(SequenceUtil.createSubsequence(seq, 0, i + 1));
            }
        }
    }

    /**
     * Identifies occurrences of primitive values in the given sequence and adds
     * them to the value pool for primitive types (including strings).
     *
     * @param seq
     */
    private void updatePrimitiveValuePool(Sequence seq) {
        for (int i = 0; i < seq.size(); i++) {
            Operation oper = seq.getStatement(i).getOperation();
            if (oper.isNonreceivingValue()) {
                Object primValue = oper.getValue();
                if (primValue != null) {
                    this.primitiveValuePool.addValueToPool(primValue);
                }
            }
        }
    }

    class PrimitiveValuePool {
        HashSet<Byte> byteValuePool = new HashSet<>();
        HashSet<Character> charValuePool = new HashSet<>();
        HashSet<Integer> intValuePool = new HashSet<>();
        HashSet<Long> longValuePool = new HashSet<>();
        HashSet<Short> shortValuePool = new HashSet<>();
        HashSet<Float> floatValuePool = new HashSet<>();
        HashSet<Double> doubleValuePool = new HashSet<>();
        HashSet<String> strValuePool = new HashSet<>();
        HashSet<Boolean> boolValuePool = new HashSet<>(Arrays.asList(true, false));

        <T> T getRandomValueOfType(String type) {
            if (type.equals("byte") || type.equals("java.lang.Byte")) {
                if (byteValuePool.isEmpty()) {
                    byte randomByte = RandomUtils.nextBytes(1)[0];
                    byteValuePool.add(randomByte);
                }
                return (T) getRandomValueFromSet(byteValuePool);
            }
            if (type.equals("char") || type.equals("java.lang.Character")) {
                if (charValuePool.isEmpty()) {
                    char randomChar = RandomStringUtils.randomAlphanumeric(1).charAt(0);
                    charValuePool.add(randomChar);
                }
                return (T) getRandomValueFromSet(charValuePool);
            }
            if (type.equals("int") || type.equals("java.lang.Integer")) {
                if (intValuePool.isEmpty()) {
                    int randomInt = RandomUtils.nextInt();
                    intValuePool.add(randomInt);
                }
                return (T) getRandomValueFromSet(intValuePool);
            }
            if (type.equals("long") || type.equals("java.lang.Long")) {
                if (longValuePool.isEmpty()) {
                    long randomLong = RandomUtils.nextLong();
                    longValuePool.add(randomLong);
                }
                return (T) getRandomValueFromSet(longValuePool);
            }
            if (type.equals("short") || type.equals("java.lang.Short")) {
                if (shortValuePool.isEmpty()) {
                    short randomShort = (short)RandomUtils.nextInt();
                    shortValuePool.add(randomShort);
                }
                return (T) getRandomValueFromSet(shortValuePool);
            }
            if (type.equals("float") || type.equals("java.lang.Float")) {
                if (floatValuePool.isEmpty()) {
                    float randomFloat = RandomUtils.nextFloat();
                    floatValuePool.add(randomFloat);
                }
                return (T) getRandomValueFromSet(floatValuePool);
            }
            if (type.equals("double") || type.equals("java.lang.Double")) {
                if (doubleValuePool.isEmpty()) {
                    double randomDouble = RandomUtils.nextDouble();
                    doubleValuePool.add(randomDouble);
                }
                return (T) getRandomValueFromSet(doubleValuePool);
            }
            if (type.equals("java.lang.String")) {
                if (strValuePool.isEmpty()) {
                    String randomStr = RandomStringUtils.randomAlphanumeric(10);
                    strValuePool.add(randomStr);
                }
                return (T) getRandomValueFromSet(strValuePool);
            }
            if (type.equals("boolean") || type.equals("java.lang.Boolean")) {
                if (boolValuePool.isEmpty()) {
                    boolean randomBool = RandomUtils.nextBoolean();
                    boolValuePool.add(randomBool);
                }
                return (T) getRandomValueFromSet(boolValuePool);
            }
            throw new AssertionError("Unknown primitive type: " + type);
        }

        void addValueToPool(Object value) {
            if (value instanceof Byte) {
                byteValuePool.add((Byte) value);
            } else if (value instanceof Character) {
                charValuePool.add((Character) value);
            } else if (value instanceof Integer) {
                intValuePool.add((Integer) value);
            } else if (value instanceof Long) {
                longValuePool.add((Long) value);
            } else if (value instanceof Short) {
                shortValuePool.add((Short) value);
            } else if (value instanceof Float) {
                floatValuePool.add((Float) value);
            } else if (value instanceof Double) {
                doubleValuePool.add((Double) value);
            } else if (value instanceof String) {
                if (!((String) value).contains("\"")) {
                    strValuePool.add((String) value);
                }
            } else if (!(value instanceof Boolean)) {
                logger.info("value: " + value);
                throw new AssertionError("Unknown primitive type: " + value.getClass());
            }
        }

        private <T> T getRandomValueFromSet(HashSet<T> values) {
            return values.stream().skip(new Random().nextInt(values.size())).findFirst().get();
        }
    }

}
