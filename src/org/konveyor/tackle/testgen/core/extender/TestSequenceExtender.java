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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.konveyor.tackle.testgen.core.DiffAssertionsGenerator;
import org.konveyor.tackle.testgen.core.JUnitTestExporter;
import org.konveyor.tackle.testgen.core.executor.JUnitExecutor;
import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;
import org.konveyor.tackle.testgen.util.TackleTestLogger;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.utils.Pair;

import randoop.operation.ConstructorCall;
import randoop.operation.MethodCall;
import randoop.operation.OperationParseException;
import randoop.operation.TypedClassOperation;
import randoop.operation.TypedOperation;
import randoop.org.apache.commons.io.output.NullPrintStream;
import randoop.sequence.Sequence;
import randoop.sequence.Variable;
import randoop.types.ArrayType;
import randoop.types.GenericClassType;
import randoop.types.InstantiatedType;
import randoop.types.ReferenceType;
import randoop.types.Substitution;
import randoop.types.Type;
import randoop.types.TypeVariable;

/**
 * Extends the initial (or building-block) test sequences created by the
 * test-sequence initializer.
 */
public class TestSequenceExtender {

	private static final Logger logger = TackleTestLogger.getLogger(TestSequenceExtender.class);

	private final static ObjectMapper mapper = TackleTestJson.getObjectMapper();

	// CTD test plan read from the JSON input file
	private ObjectNode testPlan;

	// initial test sequences read from the JSON input files
	private List<ObjectNode> initialTestSeqs = new ArrayList<ObjectNode>();

	// set of signatures for proxy methods targeted for coverage in the CTD plan
	private Set<String> tgtProxyMethodSignatures;

	// pool of class, method, and primitive-value sequences mined from the building-block test sequences
	private SequencePool sequencePool;

	// executor for running seauences as junit classes
	private JUnitExecutor junitExecutor;

	// map from partition_name to class_name::method_signature (for a proxy method)
	// to extended test sequence ids created for the method
	private Map<String, Map<String, Set<String>>> extTestSeq;

	// extended sequences with execution results
	private HashMap<String, SequenceExecutor.SequenceResults> execExtSeq = new HashMap<>();

	// map from sequence ID to extended sequence
	private HashMap<String, Sequence> seqIdMap = new HashMap<>();

	// map from sequence ID to code string representation of extended sequence with
	// diff assertions added
	private HashMap<String, String> seqWithDiffAsserts = new HashMap<>();

	// information about extended sequences that are discarded because they fail execution
    // on the monolith
	// partition --> proxy class --> proxy method --> test plan row --> sequence ID
//	private Map<String, Map<String, Map<String, Map<String, String>>>> discardedExtSeq;

	// extended sequences that could not be executed (seq ID --> error messages)
	private HashMap<String, String> seqExecErrors = new HashMap<>();

	// coverage information about proxy classes, methods, and test plan rows
	// partition --> proxy class --> proxy method --> test plan row -->
	// [COVERED, PARTIAL, UNCOVERED]
	private Map<String, Map<String, Map<String, Map<String, Constants.TestPlanRowCoverage>>>> coverageInfo;

	// summary information about sequence extension
    ExtenderSummary extSummary;

	private final String applicationName;

	private String outputDir = null;

	int totalInitSeqs = 0, testPlanMethods = 0, testPlanClasses = 0;

	int classTestPlanRows = 0;

	int classSeqCount = 0;

	int totalSeqCount = 0;
    int totalTestPlanRows = 0;

	private final boolean jeeSupport;

	private final boolean diffAssertions;

	private final int numSeqExecutions;

	public HashMap<String, SortedSet<Sequence>> getClassTestSequencePool() {
		return sequencePool.classTestSeqPool;
	}

	public HashMap<String, SortedSet<Sequence>> getMethodTestSequencePool() {
		return sequencePool.methodTestSeqPool;
	}

	public Map<String, Map<String, Map<String, Map<String, Constants.TestPlanRowCoverage>>>> getCoverageInformation() {
		return coverageInfo;
	}

	public HashMap<String, String> getSequenceExecutionErrors() {
		return seqExecErrors;
	}

	public Sequence getSequenceForID(String seqID) {
		return this.seqIdMap.get(seqID);
	}

	public HashMap<String, Integer> getParseExceptions() {
		return sequencePool.parseExceptions;
	}

	public Set<String> getNonInstantiableTypes() {
		return this.extSummary.nonInstantiableTypes;
	}

	/**
	 * Constructor for TestSequenceExtender
	 *
	 * @param appName          The name of the application under test
	 * @param testPlanFilename JSON file containing CTD test plans
	 * @param testSeqFilename  JSON file containing initial test sequences
	 * @throws IOException
	 */
	public TestSequenceExtender(String appName, String testPlanFilename, String testSeqFilename,
                                boolean mineConstructorSequences, String outputDir, boolean jee, int numExecutions,
                                boolean diffAssertions) throws IOException {

		this.applicationName = appName;
		this.outputDir = outputDir;
		this.jeeSupport = jee;
		this.numSeqExecutions = numExecutions;
		this.diffAssertions = diffAssertions;

		// read test plan from JSON file
		File testPlanFile = new File(testPlanFilename);
		if (!testPlanFile.isFile()) {
			throw new IOException(testPlanFile.getAbsolutePath() + " is not a valid file");
		}
		this.testPlan = (ObjectNode) mapper.readTree(testPlanFile).get("models_and_test_plans");

		// create set of targeted proxy methods
		this.tgtProxyMethodSignatures = new HashSet<>();
		this.testPlan.fieldNames().forEachRemaining(partitionName -> {
		    ObjectNode partitionTestPlan = (ObjectNode) this.testPlan.get(partitionName);
		    partitionTestPlan.fieldNames().forEachRemaining(className -> {
		        testPlanClasses++;
		        ObjectNode classTestPlan = (ObjectNode) partitionTestPlan.get(className);
		        classTestPlan.fieldNames().forEachRemaining(methodSig -> {
		            this.tgtProxyMethodSignatures.add(className+"::"+methodSig);
		            testPlanMethods++;
                });
            });
        });
        System.out.println("* Read test plans for: " + testPlanClasses + " classes, "+
            testPlanMethods + " methods");

		// read initial test sequences from JSON file
		String[] testSeqFileNames = testSeqFilename.split(",");
		for (String fileName : testSeqFileNames) {
			File testSeqFile = new File(fileName);
			if (!testSeqFile.isFile()) {
				throw new IOException(testSeqFile.getAbsolutePath() + " is not a valid file");
			}
			this.initialTestSeqs.add((ObjectNode) mapper.readTree(testSeqFile).get("test_sequences"));
		}

        for (ObjectNode initialTestSeq : initialTestSeqs) {
        	initialTestSeq.fieldNames().forEachRemaining(cls -> {
        		ObjectNode clsInfo = (ObjectNode) initialTestSeq.get(cls);
                totalInitSeqs += clsInfo.get("sequences").size();
            });
        }
        System.out.println("* Read "+totalInitSeqs+" base test sequences");
        System.out.println("* Starting sequence parsing");

        // create extension summary object
        this.extSummary = new ExtenderSummary(this.testPlan);

		// create sequence pool for classes and targeted proxy methods
        this.sequencePool = new SequencePool(this.initialTestSeqs, this.tgtProxyMethodSignatures, appName);
        augmentClassSequencePool();
        this.extSummary.setSequencePool(this.sequencePool);

        System.out.println("* Parsed initial test sequences for "+
            this.sequencePool.classImports.keySet().size()+" classes; total parsed sequences (full+partial): "+
            (this.sequencePool.parsedBaseSequencesFull+this.sequencePool.parsedBaseSequencesPartial));
        System.out.println("* Generating extended test sequences");

		this.extTestSeq = new HashMap<>();
		this.coverageInfo = new HashMap<>();
//		this.discardedExtSeq = new HashMap<>();

		// create junit executor object
        this.junitExecutor = new JUnitExecutor(this.applicationName, this.outputDir,
            this.sequencePool.classImports, this.sequencePool.classBeforeAfterMethods);

	}

	// boolean flag to keep track of whether a test plan row is partially covered
	private boolean rowPartiallyCovered = false;

	private ArrayNode[] currTestPlanRows = null;
	private int currTestPlanRowIndex = -1;
	private int currTestPlanRowParamIndex = -1;

    /**
     * Information needed for execution with JEE support for failing  test sequences generated
     * for a class
     */
	private class JEEExecutionInfo {
        List<String> failedSeqIds = new ArrayList<String>(); // we'll later try to run these via Junit
        Map<String, Boolean> seqIdToPartial = new HashMap<String, Boolean>();
        Map<String, String> seqIdToRowId = new HashMap<String, String>();
        Map<String, Map<String, Constants.TestPlanRowCoverage>> seqIdToCovInfo =
            new HashMap<String, Map<String, Constants.TestPlanRowCoverage>>();
    }

	/**
	 * Creates test sequences for covering the CTD test plan for each target proxy
	 * method. For a proxy method and test-plan row, selects an initial sequence
	 * that covers the method, removes statements corresponding to the method call,
	 * and adds new statements that invoke the method with parameter types specified
	 * in the test-plan row.
	 */
	public Map<String, Map<String, Set<String>>> createExtendedSequences() {

		// mapping from qualified method signatures to output-formatted method signature
        Map<String, String> formattedMethodSigMap = getQualifiedToFormattedMethodSignatureMap();

        // iterate over each partition
        this.testPlan.fieldNames().forEachRemaining(partition -> {

		    System.out.println("* Partition: "+partition);

		    // initialize extended sequence info for partition
			if (!this.extTestSeq.containsKey(partition)) {
				this.extTestSeq.put(partition, new HashMap<>());
			}

			// get test plan info for partition
			ObjectNode partitionTestPlan = (ObjectNode) this.testPlan.get(partition);

			// iterate over each class in partition
			partitionTestPlan.fieldNames().forEachRemaining(className -> {

			    System.out.println("* Processing class "+className);

                // get test plan info for class
			    ObjectNode classTestPlan = (ObjectNode) partitionTestPlan.get(className);

			    System.out.print("*   "+classTestPlan.size()+" methods ");
			    classSeqCount = 0;
			    classTestPlanRows = 0;

				// initialize data structures needed for JEE trial for failing extended sequences for class
				JEEExecutionInfo classJEEExecInfo = new JEEExecutionInfo();

				// iterate over each method in class
				classTestPlan.fieldNames().forEachRemaining(methodSig -> {

                    System.out.print(".");

                    // get test plan rows for method
					ObjectNode methodTestPlan = (ObjectNode) classTestPlan.get(methodSig);
					currTestPlanRows = mapper.convertValue(methodTestPlan.get("test_plan"), new TypeReference<ArrayNode[]>(){});
					classTestPlanRows +=  currTestPlanRows.length;

					// method signature qualified with the class name
                    String qualMethodSig = className + "::" + methodSig;

                    // get initialized coverage info for proxy method
					String[] msigTokens = formattedMethodSigMap.get(qualMethodSig).split(" ");
					String methodSigCovFmt = (msigTokens.length == 1) ? msigTokens[0] : msigTokens[1];
					Map<String, Constants.TestPlanRowCoverage> methodCovInfo = getProxyMethodCovInfo(partition,
                        className, methodSigCovFmt, currTestPlanRows);

					// get target method signature in format needed for creating Randoop statements
					// create randoop's MethodCall object for target method or constructor
					String parseableMethodSig = qualMethodSig;
					TypedClassOperation tgtMethodCall;
					try {
					    Pair<String, TypedClassOperation> methodSigTgtCallPair =
                            getTargetMethodCall(className, formattedMethodSigMap.get(qualMethodSig));
					    parseableMethodSig = methodSigTgtCallPair.a;
					    tgtMethodCall = methodSigTgtCallPair.b;
					} catch (OperationParseException ope) {
						logger.warning("Error parsing: " + parseableMethodSig);
						this.extSummary.uncovTestPlanRows__excp += currTestPlanRows.length;
						this.extSummary.uncovTestPlanRows__excp__OperationParse += currTestPlanRows.length;
						for (int rowCtr = 1; rowCtr <= currTestPlanRows.length; rowCtr++) {
							methodCovInfo.put(getTestPlanRowId(rowCtr), Constants.TestPlanRowCoverage.UNCOVERED_EXCP);
						}
						return;
					}
                    catch (NoClassDefFoundError ncdf) {
                        String errmsg = "Error parsing: " + parseableMethodSig + "\n" + ncdf;
                        logger.warning(errmsg);
                        this.extSummary.uncovTestPlanRows__excp += currTestPlanRows.length;
                        this.extSummary.uncovTestPlanRows__excp__ClassNotFound += currTestPlanRows.length;
                        this.extSummary.classNotFoundTypes.add(ncdf.getMessage());
                        for (int rowCtr = 1; rowCtr <= currTestPlanRows.length; rowCtr++) {
                            methodCovInfo.put(getTestPlanRowId(rowCtr), Constants.TestPlanRowCoverage.UNCOVERED_EXCP);
                        }
                        return;
                    }
                    catch (UnsatisfiedLinkError ule) {
                        String errmsg = "Error parsing: " + parseableMethodSig + "\n" + ule;
                        logger.warning(errmsg);
                        this.extSummary.uncovTestPlanRows__excp += currTestPlanRows.length;
                        this.extSummary.uncovTestPlanRows__excp__UnsatisfiedLink += currTestPlanRows.length;
                        this.extSummary.classNotFoundTypes.add(ule.getMessage());
                        for (int rowCtr = 1; rowCtr <= currTestPlanRows.length; rowCtr++) {
                            methodCovInfo.put(getTestPlanRowId(rowCtr), Constants.TestPlanRowCoverage.UNCOVERED_EXCP);
                        }
                        return;
                    }

					// if method is non-static/constructor and no test sequence exists for it in the
                    // sequence pool (i.e., a sequence that creates the receiver object), skip method
					if (!tgtMethodCall.isStatic() && !tgtMethodCall.isConstructorCall()) {
						if (!this.sequencePool.methodTestSeqPool.containsKey(qualMethodSig)
                            && !this.sequencePool.classTestSeqPool.containsKey(className)) {
							logger.warning("No initial method/class sequence exists for target method " +
                                qualMethodSig + " skipping");
							this.extSummary.uncovTestPlanRows__noInitSeq += currTestPlanRows.length;
							for (int rowCtr = 1; rowCtr <= currTestPlanRows.length; rowCtr++) {
								methodCovInfo.put(getTestPlanRowId(rowCtr),
                                    Constants.TestPlanRowCoverage.UNCOVERED_NO_INIT_SEQ);
							}
							return;
						}
					}

					// create extended test sequences for method
                    classSeqCount += createExtendedSequencesForMethod(partition, qualMethodSig,
                        parseableMethodSig, tgtMethodCall, classJEEExecInfo, methodCovInfo);

				});

				if (jeeSupport && ! classJEEExecInfo.failedSeqIds.isEmpty()) {
                    PrintStream origSysOut = System.out;
                    PrintStream origSysErr = System.err;
                    System.setOut(NullPrintStream.NULL_PRINT_STREAM);
                    System.setErr(NullPrintStream.NULL_PRINT_STREAM);
					this.junitExecutor.runFailedwithJEESupport(classJEEExecInfo.failedSeqIds, partition,
                        className, classJEEExecInfo.seqIdToPartial, classJEEExecInfo.seqIdToRowId,
                        classJEEExecInfo.seqIdToCovInfo, this.seqIdMap, this.extSummary);
                    System.setOut(origSysOut);
                    System.setErr(origSysErr);
                }

				totalTestPlanRows += classTestPlanRows;
				totalSeqCount += classSeqCount;

				System.out.println("");
				System.out.println("*   generated "+classSeqCount+" test sequences");
                System.out.print("*   -- class test-plan coverage rate: ");
                if (classTestPlanRows > 0) {
                    System.out.print(String.format("%.2f",
                        ((double)classSeqCount * 100) / ((double)classTestPlanRows))+"% ");
                }
                System.out.println("("+classSeqCount+"/"+classTestPlanRows+")");
			});
		});
        double totalCovRate = (double)totalSeqCount * 100 / (double)totalTestPlanRows;
        System.out.println("* === total CTD test-plan coverage rate: "+
            String.format("%.2f", totalCovRate)+"% ("+totalSeqCount+"/"+totalTestPlanRows+")");

        // add diff assertions if option specified
        int assertionCount = 0;
        if (this.diffAssertions) {
            assertionCount = this.addDiffAssertions();
        }

        // write summary JSON file
		try {
            this.extSummary.writeSummaryFile(this.applicationName, this.seqIdMap, this.extTestSeq,
            		this.execExtSeq, assertionCount);
            System.out.println("* wrote summary file for generation of CTD-amplified tests (JSON)");
        } catch (IOException fnfe) {
		    logger.warning("Error writing summary JSON: "+fnfe);
        }

		// print summary to stdout
//        this.extSummary.printSummaryInfo(this.seqIdMap, this.extTestSeq, this.execExtSeq,
//            this.discardedExtSeq, assertionCount);

        return this.extTestSeq;
	}

    /**
     * Creates extended test sequences for a given target method and adds them to test sequences
     * for a partition. Records information about failed sequences for later JEE trial execution.
     * Updates global coverage data.
     *
     * @param partition
     * @param qualMethodSig
     * @param parseableMethodSig
     * @param tgtMethodCall
     * @param jeeExecInfo
     * @param methodCovInfo
     */
	private int createExtendedSequencesForMethod(String partition, String qualMethodSig,
                                                 String parseableMethodSig,
                                                 TypedClassOperation tgtMethodCall,
                                                 JEEExecutionInfo jeeExecInfo,
                                                 Map<String, Constants.TestPlanRowCoverage> methodCovInfo) {

        logger.info("=========>>> Generating " + currTestPlanRows.length
            + " test cases for proxy method: " + qualMethodSig + " <<<=========");
        int methodSeqCount = 0;

        String className = qualMethodSig.split("::")[0];

        // iterate over each row of test plan for method
        int rowCtr = 0;
        for (ArrayNode row : currTestPlanRows) {
            this.rowPartiallyCovered = false;
            this.currTestPlanRowIndex = rowCtr;
            String testPlanRowId = getTestPlanRowId(++rowCtr);

            // select from the candidate sequences available for target method
            Pair<Sequence, Boolean> candidateSeqCovPair = getCandidateSequenceForRow(
                className, qualMethodSig, tgtMethodCall, row
            );

            // extend candidate sequence based on the parameter types specified in the test plan row
            Sequence extendedSeq;
            if (candidateSeqCovPair.b) {
                extendedSeq = candidateSeqCovPair.a;
            } else {
                try {
                    extendedSeq = extendSequence(candidateSeqCovPair.a, row,
                        parseableMethodSig, tgtMethodCall);
                } catch (IllegalArgumentException e) {
                    logger.warning("Error creating extended sequence for row: " + row +
                        "\n" + e);
                    this.extSummary.uncovTestPlanRows__excp++;
                    this.extSummary.uncovTestPlanRows__excp__randoop__IllegalArgument++;
                    methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.UNCOVERED_EXCP);
                    continue;
                } catch (NonInstantiableTypeException nte) {
                    logger.warning(nte.getMessage());
                    this.extSummary.uncovTestPlanRows__excp__NonInstantiableType++;
                    methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.UNCOVERED_NON_INST_TYPE);
                    continue;
                } catch (RuntimeException re) {
                    logger.warning(re.getMessage());
                    this.extSummary.uncovTestPlanRows__excp++;
                    methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.UNCOVERED_EXCP);
                    continue;
                }
            }

            // generate sequence ID and add id, sequence to map
            String sequenceID = getSequenceID();
            this.seqIdMap.put(sequenceID, extendedSeq);
            jeeExecInfo.seqIdToRowId.put(sequenceID, testPlanRowId);
            jeeExecInfo.seqIdToPartial.put(sequenceID, this.rowPartiallyCovered);
            jeeExecInfo.seqIdToCovInfo.put(sequenceID, methodCovInfo);

            // check whether extended sequence can be executed
            try {
                if (executeSequence(sequenceID) == false) {
                    jeeExecInfo.failedSeqIds.add(sequenceID);
                    this.extSummary.uncovTestPlanRows__execFail++;
                    methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.UNCOVERED_EXEC_FAIL);
                    continue;
                }
            } catch (RuntimeException re) {
                logger.warning(re.getMessage());
                this.extSummary.uncovTestPlanRows__excp++;
                methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.UNCOVERED_EXCP);
                continue;
            }

            Map<String, Set<String>> partitionTestSeq = this.extTestSeq.get(partition);
            if (!partitionTestSeq.containsKey(qualMethodSig)) {
                partitionTestSeq.put(qualMethodSig, new HashSet<>());
            }
            // add extended sequence to sequence set and mark test plan row as covered
            partitionTestSeq.get(qualMethodSig).add(sequenceID);
            methodSeqCount++;
            if (this.rowPartiallyCovered) {
                methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.PARTIAL);
                this.extSummary.covTestPlanRows__partial++;
            } else {
                methodCovInfo.put(testPlanRowId, Constants.TestPlanRowCoverage.COVERED);
                this.extSummary.covTestPlanRows__full++;
            }
        }
        return methodSeqCount;
    }

    /**
     * Created mapping from qualified method signatures to output-formatted method signatures
     * @return
     */
    private Map<String, String> getQualifiedToFormattedMethodSignatureMap() {
        Map<String, String> methodToCovMethod = new HashMap<>();
        this.testPlan.fieldNames().forEachRemaining(partition -> {
            ObjectNode partitionTestPlan = (ObjectNode) this.testPlan.get(partition);
            partitionTestPlan.fieldNames().forEachRemaining(className -> {
            	ObjectNode classTestPlan = (ObjectNode) partitionTestPlan.get(className);
            	classTestPlan.fieldNames().forEachRemaining(methodSig -> {
                	ObjectNode methodTestPlan = (ObjectNode) classTestPlan.get(methodSig);
                    String qualMethodSig = className + "::" + methodSig;
                    methodToCovMethod.put(qualMethodSig, methodTestPlan.get("formatted_signature").asText());
                });
            });
        });
        return methodToCovMethod;
    }

    /**
     * Given a class name and method/constructor signature, creates an instance of Randoop
     * typed class operation object. Returns a pair consisting of Randoop-parseable method
     * signature and the typed class operation object.
     * @param className
     * @param fmtMethodSig
     * @return
     * @throws OperationParseException
     */
    private Pair<String, TypedClassOperation> getTargetMethodCall(String className, String fmtMethodSig)
        throws OperationParseException {
        String parseableMethodSig;
        TypedClassOperation targetMethodCall;
        if (fmtMethodSig.contains(" ")) {
            parseableMethodSig = className + "." + fmtMethodSig.split(" ")[1];
            targetMethodCall = MethodCall.parse(parseableMethodSig);
        } else {
            parseableMethodSig = className + ".<init>(" + fmtMethodSig.split("\\(")[1];
            logger.info("target method is constructor: " + parseableMethodSig);
            targetMethodCall = ConstructorCall.parse(parseableMethodSig);
        }
        return new Pair<>(parseableMethodSig, targetMethodCall);
    }

    /**
	 * Writes all extended sequences to JUnit test class files
	 *
	 * @throws IOException
	 */
	public void writeTestClasses(boolean withDiffAssertions) throws IOException {
		String outDirName = this.outputDir;
		if (outDirName == null) {
			// if output dir not specified, use default output dir name
			outDirName = applicationName + "-" + Constants.AMPLIFIED_TEST_CLASSES_OUTDIR;
		}
		for (String partition : this.extTestSeq.keySet()) {
			// create JUnit exporter for class
			File outDir = new File(outDirName + File.separator + partition);
			JUnitTestExporter testExporter = new JUnitTestExporter(outDir, withDiffAssertions);
			Map<String, Set<String>> partTestSeq = this.extTestSeq.get(partition);

			// group partition test sequences by class and method
			Map<String, Map<String, List<String>>> clsTestSeq = new HashMap<>();
			for (String cls : partTestSeq.keySet()) {
			    String[] classMethod = cls.split("::");
				String clsName = classMethod[0];
				String methodSig = classMethod[1];
				if (!clsTestSeq.containsKey(clsName)) {
					clsTestSeq.put(clsName, new HashMap<>());
				}
				Map<String, List<String>> methodTestSeq = clsTestSeq.get(clsName);
				if (!methodTestSeq.containsKey(methodSig)) {
				    methodTestSeq.put(methodSig, new ArrayList<>());
                }
				List<String> testSeq = methodTestSeq.get(methodSig);
				if (!withDiffAssertions) {
				    testSeq.addAll(partTestSeq.get(cls).stream()
                        .map(seqid -> this.seqIdMap.get(seqid).toCodeString().replaceAll("<Capture\\d+(,Capture\\d+)*>", ""))
                        .collect(Collectors.toList())
                    );
                }
				else {
                    testSeq.addAll(partTestSeq.get(cls).stream()
                        .map(seqid -> this.seqWithDiffAsserts.get(seqid))
                        .collect(Collectors.toList())
                    );
                }
//				methodTestSeq.get(methodSig).addAll(partTestSeq.get(cls));
			}

			// write class sequences to a JUnit test class file
            int testMethodCount = 0;
			for (String cls : clsTestSeq.keySet()) {
                Map<String, List<String>> methodTestSeq = clsTestSeq.get(cls);
//				for (String methodSig : methodTestSeq.keySet()) {
//                    List<String> seqList;
//				    if (!withDiffAssertions) {
//					    seqList = methodTestSeq.get(methodSig).stream()
//                            .map(seqid -> this.seqIdMap.get(seqid).toCodeString().replaceAll("<Capture\\d+>", ""))
//                            .collect(Collectors.toList());
//    				} else {
//	    				seqList = methodTestSeq.get(methodSig).stream().map(seqid -> this.seqWithDiffAsserts.get(seqid))
//		    					.collect(Collectors.toList());
//				    }
//                }
				Set<String> testImports = new HashSet<>();
				if (this.sequencePool.classImports.containsKey(cls)) {
					testImports.addAll(this.sequencePool.classImports.get(cls).stream().filter(impName -> {
						try {
							return impName.startsWith("org.evosuite") || impName.startsWith("static org.junit") ||
									! Modifier.isPrivate(Class.forName(impName).getModifiers());
						} catch (ClassNotFoundException | NoClassDefFoundError e) {
							return true; // treat class as non-private, either way may result in compilation issues for the resulting JUnit test
						}
					}).
							collect(Collectors.toSet()));
				}
				testExporter.writeUnitTest(cls, methodTestSeq, testImports);
				testMethodCount += methodTestSeq.values().stream().mapToInt(List::size).sum();
			}
			System.out.println("* wrote "+clsTestSeq.keySet().size()+" test class files to \""+
                outDirName+File.separator+partition+"\" with "+testMethodCount+" total test methods");
		}
	}

	/**
	 * Writes coverage information to JSON file
	 *
	 * @param coverageFileName
	 * @throws IOException
	 * @throws JsonMappingException
	 * @throws JsonGenerationException
	 */
	public void writeTestCoverageFile(String appName, String coverageFileName) throws JsonGenerationException, JsonMappingException, IOException {
		ObjectNode partCovJson = mapper.createObjectNode();
		for (String part : this.coverageInfo.keySet()) {
			Map<String, Map<String, Map<String, Constants.TestPlanRowCoverage>>> clsCovInfo = this.coverageInfo
					.get(part);
			ObjectNode clsCovJson = mapper.createObjectNode();
			for (String cls : clsCovInfo.keySet()) {
				Map<String, Map<String, Constants.TestPlanRowCoverage>> methodCovInfo = clsCovInfo.get(cls);
				ObjectNode methodCovJson = mapper.createObjectNode();
				for (String method : methodCovInfo.keySet()) {
					Map<String, Constants.TestPlanRowCoverage> rowCovInfo = methodCovInfo.get(method);
					ObjectNode rowCovJson = mapper.createObjectNode();
					for (String row : rowCovInfo.keySet()) {
						rowCovJson.put(row, rowCovInfo.get(row).name());
					}
					methodCovJson.set(method, rowCovJson);
				}
				clsCovJson.set(cls, methodCovJson);
			}
			partCovJson.set(part, clsCovJson);
		}
		String outFileName = appName+Constants.COVERAGE_FILE_JSON_SUFFIX;


		if (coverageFileName != null) {
			outFileName = coverageFileName;
		}
		mapper.writeValue(new File(outFileName), partCovJson);
		System.out.println("* wrote CTD test-plan coverage report (JSON)");
	}

	/**
	 * Adds diff assertions to all extended sequences that execute successfully.
	 */
	private int addDiffAssertions() {
		DiffAssertionsGenerator diffAssertGen = new DiffAssertionsGenerator(applicationName);
		for (String seqId : execExtSeq.keySet()) {
			logger.fine("Adding diff assertions to sequence: " + seqId);
			String seqStr = seqIdMap.get(seqId).toCodeString().replaceAll("<Capture\\d+(,Capture\\d+)*>", "");
			SequenceExecutor.SequenceResults seqRes = execExtSeq.get(seqId);
			// skip failing sequences
			if (!seqRes.passed) {
				continue;
			}

			String seqWithAssertStr = diffAssertGen.addAssertions(seqStr, seqRes);
			seqWithDiffAsserts.put(seqId, seqWithAssertStr);
		}
		int assertionCount = diffAssertGen.getAssertCount();
		System.out.println("* Added a total of " + assertionCount + " diff assertions across all sequences");
		return assertionCount;
	}

	/**
	 * Executes the given (extended) sequence and checks whether execution failed.
	 * If execution fails, records failed sequence.
	 *
	 * @param sequenceID Sequence to be executed
	 * @return boolean indicating whether sequence executes successfully
	 */
	private boolean executeSequence(String sequenceID) {
		SequenceExecutor seqExecutor = new SequenceExecutor(true);
		Set<String> errMsgs = new HashSet<>();
		Sequence extendedSeq = this.seqIdMap.get(sequenceID);
        PrintStream origSysOut = System.out;
        PrintStream origSysErr = System.err;
		try {
		    // disable stdout/stderr prints from sequence executor, which can occur from the app code
		    System.setOut(NullPrintStream.NULL_PRINT_STREAM);
		    System.setErr(NullPrintStream.NULL_PRINT_STREAM);
			SequenceExecutor.SequenceResults execResult = seqExecutor.executeSequence(sequenceID, extendedSeq,
					numSeqExecutions);
			this.execExtSeq.put(sequenceID, execResult);
			if (!execResult.passed) {
				// find the exception that caused the sequence to fail and add to the summary 
				for (int i=0; i<extendedSeq.size();i++) {
					if (execResult.exception[i] != null) {
						String excp = execResult.cause[i] != null? execResult.cause[i] : execResult.exception[i];
						Integer count = this.extSummary.seqFailExcp.get(excp);
						this.extSummary.seqFailExcp.put(excp, count == null? 1: count+1);
						break;
					}
				}
				errMsgs.add("Error executing sequence");
				errMsgs.addAll(
						Arrays.stream(execResult.causeMessage).filter(str -> str != null).collect(Collectors.toSet()));
				errMsgs.addAll(Arrays.stream(execResult.exceptionMessage).filter(str -> str != null)
						.collect(Collectors.toSet()));
			}
			return execResult.passed;
		} catch (RuntimeException re) {
			Throwable cause = re.getCause();
			if (cause instanceof InvocationTargetException) {
				this.extSummary.uncovTestPlanRows__excp__exec__InvocationTarget++;
			} else if (cause instanceof IllegalAccessException) {
				this.extSummary.uncovTestPlanRows__excp__exec__IllegalAccess++;
			} else if (cause instanceof IllegalArgumentException) {
				this.extSummary.uncovTestPlanRows__excp__exec__IllegalArgument++;
			} else {
				this.extSummary.uncovTestPlanRows__excp__exec__Other++;
				if (cause == null) {
					this.extSummary.seqExecExcpOther.add(re.getClass().getName());
				} else {
					this.extSummary.seqExecExcpOther.add(cause.getClass().getName());
					logger.warning("Root cause of runtime exception during sequence execution: "
							+ cause.getClass().getName() + "\n" + cause.getStackTrace());
				}
			}
			throw new RuntimeException("Error executing sequence: " + re.getMessage() + "\n" + extendedSeq, cause);
		} catch (Throwable e) {
			logger.warning("Error executing sequence: " + e);
			this.extSummary.uncovTestPlanRows__excp__exec__Error++;
			throw new RuntimeException(e);
		}
		finally {
		    // restore stdout and stderr
            System.setOut(origSysOut);
            System.setErr(origSysErr);
        }
	}

    /**
     * Given a target method and test plan row, returns pair consisting of a candidate sequence
     * from the method or class sequence pools and a boolean indicating whether the sequence
     * actually covers the test plan row.
     * TODO: if not covering bb sequence exists for a virtual method call, return a list of all
     * candidate sequences that create the receiver object for the target method call
     * @param clsName
     * @param qualMethodSig
     * @param tgtMethodCall
     * @param testPlanRow
     * @return
     */
	private Pair<Sequence, Boolean> getCandidateSequenceForRow(String clsName, String qualMethodSig,
                                                               TypedClassOperation tgtMethodCall,
                                                               ArrayNode testPlanRow) {
        Sequence candidateSeq = new Sequence();
        boolean coveringInitialSequenceExists = false;
        if (this.sequencePool.methodTestSeqPool.containsKey(qualMethodSig)) {
            SortedSet<Sequence> candidateSequences = this.sequencePool.methodTestSeqPool.get(qualMethodSig);

            // check whether any of the candidate sequences cover the test plan row
            Sequence coveringSeq = getCoveringSequence(testPlanRow, candidateSequences);
            if (coveringSeq != null) {
                logger.info("Found covering initial sequence: " + coveringSeq);
                coveringInitialSequenceExists = true;
                candidateSeq = coveringSeq;
                this.extSummary.covTestPlanRows__initSeq++;
            } else {
                // select a sequence from the set of candidate sequences and compute
                // constructor subsequence for the selected sequence
                candidateSeq = SequenceUtil.selectFromSequenceSet(candidateSequences);
                candidateSeq = SequenceUtil.getConstructorSubsequence(candidateSeq);
            }
        }
        else {
            // no bb sequence exists that covers the target method
            // if the target method is a virtual call, check whether a sequence creating
            // the receiver object exists in the class sequence pool; if it does, use it
            // as the candidate sequence to be extended
            if (!tgtMethodCall.isStatic() && !tgtMethodCall.isConstructorCall()) {
                if (this.sequencePool.classTestSeqPool.containsKey(clsName)) {
                    candidateSeq = SequenceUtil.selectFromSequenceSet(this.sequencePool.classTestSeqPool.get(clsName));
                }
            }
        }
	    return new Pair<>(candidateSeq, coveringInitialSequenceExists);
    }

    /**
     * Given a set of sequences, checks whether it contains a sequence that covers the given test
     * plan row. If such a sequence is found, returns the sequence; otherwise, returns null.
     * @param testPlanRow
     * @param sequences
     * @return
     */
	private Sequence getCoveringSequence(ArrayNode testPlanRow, SortedSet<Sequence> sequences) {
	    for (Sequence seq : sequences) {
	        if (SequenceUtil.isTestPlanRowCoveredBySequence(testPlanRow, seq)) {
	            return seq;
            }
        }
	    return null;
    }

	private int seqIdCtr = 1;

	private String getSequenceID() {
		return "ext_seq_" + seqIdCtr++;
	}

	// list to store variable indices while extending sequences
	private static List<Integer> tgtMethodInputs = new ArrayList<>();

	/**
	 * Creates extended sequence from the given initial sequence for covering the
	 * given test plan row.
	 *
	 * @param initSeq       Initial sequence to extend (null if target method is
	 *                      static)
	 * @param testplanRow   test plan row to be covered
	 * @param tgtMethodSig  qualified signature for the target method
	 * @param tgtMethodCall MethodCall object for the target method
	 * @return Sequence object for the extended (covering) sequence
	 */
	private Sequence extendSequence(Sequence initSeq, ArrayNode testplanRow, String tgtMethodSig,
			TypedClassOperation tgtMethodCall) {

		// reset target method input set
		tgtMethodInputs.clear();

		// initialize sequence to point to initial sequence (if non-null) or an empty sequence
		Sequence seq = initSeq;

		// if method call is not static or constructor call, add last variable, which is the receiver
        // object for the target method call, to the target method input var list
		if (!tgtMethodCall.isStatic() && !tgtMethodCall.isConstructorCall()) {
			tgtMethodInputs.add(initSeq.getLastVariable().getDeclIndex());
		}

		// synthesize randoop statements for each parameter according to the test plan and
		// extend sequence with those statements
		for (int i = 0; i < testplanRow.size(); i++) {
			ObjectNode param = (ObjectNode) testplanRow.get(i);
			this.currTestPlanRowParamIndex = i;
            String paramType = param.get("type").asText();
            logger.info("Synthesizing statement object for type: " + paramType);

            // process different types (array, collection, map, scalar) and update loop index
            // based on the number of elements consumed from the test plan row
            try {
                Type randoopType = getRandoopType(paramType);

                // process array instantiation
                if (randoopType.isArray()) {
                    logger.info("Creating array instantiation statement");
                    // process array type parameter based on types of objects to be added to array
                    seq = processArrayType(paramType, (ObjectNode) param.get("list_types"), seq);
                }

                // process collection creation
                else if (SequenceUtil.isCollectionType(randoopType)) {
                    logger.info("Creating statement for creating collection of elements");
                    // get type object for collection parameter to get type argument
                    int paramNum = i;
                    if (!tgtMethodCall.isStatic() && !tgtMethodCall.isConstructorCall()) {
                        paramNum++;
                    }
                    Type colParamType = tgtMethodCall.getInputTypes().get(paramNum);
                    List<ReferenceType> typeArgs = ExtenderUtil.getTypeArguments(colParamType);
                    ReferenceType typeArg = typeArgs.isEmpty() ? null : typeArgs.get(0);

                    // process collection type parameter and extend sequence
                    seq = processCollectionType(paramType, (ObjectNode) param.get("list_types"), typeArg,
                        true, seq);
                }

                // process map creation
                else if (SequenceUtil.isMapType(randoopType)) {
                    logger.info("Creating statement for creating map of key-value pairs");
                    // get type objects for map parameters to get type arguments for map key and map value
                    int paramNum = i;
                    if (!tgtMethodCall.isStatic() && !tgtMethodCall.isConstructorCall()) {
                        paramNum++;
                    }
                    Type mapParamType = tgtMethodCall.getInputTypes().get(paramNum);
                    List<ReferenceType> typeArgs = ExtenderUtil.getTypeArguments(mapParamType);
                    ReferenceType keyTypeArg = null, valTypeArg = null;
                    if (!typeArgs.isEmpty()) {
                        keyTypeArg = typeArgs.get(0);
                        valTypeArg = typeArgs.get(1);
                    }

                    // process map type parameter and extend sequence; the next element
                    // of the row gives the types of objects to be added to the map
                    seq = processMapType(paramType, keyTypeArg, valTypeArg,
                        (ObjectNode) param.get("key_types"),
                        (ObjectNode) param.get("value_types"), true, seq);
                }

                // default: process scalar type instantiation
                else {
                    seq = processScalarType(randoopType, true, seq);
                }
            } catch (ClassNotFoundException|NoClassDefFoundError cnfe) {
                String errmsg = "Class not found for type: " + paramType + " in signature " +
                    tgtMethodSig + "\n" + cnfe;
                logger.warning(errmsg);
                this.extSummary.uncovTestPlanRows__excp__ClassNotFound++;
                this.extSummary.classNotFoundTypes.add(cnfe.getMessage());
                throw new RuntimeException(errmsg, cnfe);
            } catch (UnsatisfiedLinkError ule) {
                String errmsg = "Unsatisfied link error for type: " + paramType + " in signature " +
                    tgtMethodSig + "\n" + ule;
                logger.warning(errmsg);
                this.extSummary.uncovTestPlanRows__excp__UnsatisfiedLink++;
                this.extSummary.classNotFoundTypes.add(ule.getMessage());
                throw new RuntimeException(errmsg, ule);
            } catch (OperationParseException ope) {
                String errmsg = "Operation parse error for type: " + paramType + " in signature " +
                    tgtMethodSig + "\n" + ope;
                logger.warning(errmsg);
                this.extSummary.uncovTestPlanRows__excp__OperationParse++;
                throw new RuntimeException(errmsg, ope);
            } catch (NoSuchMethodException nsme) {
                String errmsg = "Method/constructor not found for type: " + paramType + "in signature "
                    + tgtMethodSig + "\n" + nsme;
                logger.warning(errmsg);
                this.extSummary.uncovTestPlanRows__excp__NoSuchMethod++;
                throw new RuntimeException(errmsg, nsme);
            }
		}

		logger.info("Extending sequence with call to tgt method: " + seq);
		Sequence finalSeq = seq;

		// build list of variables from the list of variable indexes
		List<Variable> inputVars = tgtMethodInputs.stream()
            .map(idx -> finalSeq.getVariable(idx))
            .collect(Collectors.toList());

		// apply capture conversion for params of target methods
		tgtMethodCall = tgtMethodCall.applyCaptureConversion();

		// apply substitution if type parameters occur in the method call
        tgtMethodCall = (TypedClassOperation)SequenceUtil.performTypeSubstitution(tgtMethodCall);

		// extend sequence with call to the target method
		seq = seq.extend(tgtMethodCall, inputVars);

		logger.info("=== Created EXTENDED sequence ===\n" + seq);
		return seq;
	}

	/**
	 * Given a sequence, extends it with instantiation/assignment statements for the
	 * given scalar type, which could be a reference or a primitive type.
	 *
	 * @param scalarType      type which creation statements have to be added to
	 *                        sequence
	 * @param isTgtMethodParm boolean indicating whether the type is a parameter of
	 *                        the target method
	 * @param seq             Sequence to be extended
	 * @return Extended sequence
	 */
	private Sequence processScalarType(Type scalarType, boolean isTgtMethodParm, Sequence seq)
        throws NonInstantiableTypeException, ClassNotFoundException, OperationParseException {
//        String typeName = scalarType.getFqName();
		String typeName = scalarType.getRawtype().getBinaryName();
		if (scalarType.isPrimitive() || scalarType.isBoxedPrimitive() || scalarType.isString()) {
			// process primitive types
			seq = SequenceUtil.addPrimitiveAssignment(scalarType, seq, this.sequencePool);
		}
		else if (scalarType.isEnum()) {
		    // process enum types
            seq = SequenceUtil.addEnumAssignment(typeName, seq);
        }
		else {
			logger.info("Creating instantiation statement for type: " + typeName);
			Sequence typeInstSeq = null;
			// check whether sequences for creating type instance exist in class sequence pool
			if (this.sequencePool.classTestSeqPool.containsKey(typeName)) {
				// select random sequence from pool
				typeInstSeq = SequenceUtil.selectFromSequenceSet(this.sequencePool.classTestSeqPool.get(typeName));
			} else {
				// attempt to create a new sequence for instantiating this type
				try {
					typeInstSeq = ConstructorSequenceGenerator.createConstructorSequence(typeName, null,true,
                        this.sequencePool, 0);
				} catch (ClassNotFoundException | NoSuchMethodException cnfe) {
					logger.warning("Error creating constructor sequence for " + typeName + ": " + cnfe);
				}

				// if a sequence is not created, check whether constructor sequences for a subtype
				// of this type exist in the class sequence pool; if so, use a sequence for a subtype
				if (typeInstSeq == null) {
					SortedSet<Sequence> subtypeCtorSeqs = null;
					try {
						subtypeCtorSeqs = getSubtypeConstructorSequences(typeName, isTgtMethodParm);
						if (subtypeCtorSeqs != null) {
							typeInstSeq = SequenceUtil.selectFromSequenceSet(subtypeCtorSeqs);
						}
					} catch (ClassNotFoundException cnfe) {
						logger.warning("Error getting subtype constructor sequence for " + typeName + ": " + cnfe);
					}
				}
			}

			// if a constructor sequence for type exists, extend the given sequence with it
			if (typeInstSeq != null) {
			    seq = SequenceUtil.concatenate(seq, typeInstSeq);
			} else {
				String errmsg = "No constructor sequence found for type " + typeName
						+ " (or a subtype) in the class sequence pool; could not create new sequence";
				logger.warning(errmsg);
				this.extSummary.nonInstantiableTypes.add(typeName);
				if (isTgtMethodParm) {
					// if type is a target method param, throw exception indicating non-instantiable type
					throw new NonInstantiableTypeException(errmsg);
				} else {
					// if type is a not target method param (i.e., it is an element of array,
					// collection, or map), return the original sequence; in this case, the test
					// plan row can be partially covered (without this type, unless this is the
					// only type to be added to the array/collection/map)
					// alternatively, the type could be a parameter of a constructor of a type
					// to be created for the test plan row
					return seq;
				}
			}
		}
		// if type is target method parameter, add last var to list of target method param vars
		Variable lastVar = seq.getLastVariable();
		if (isTgtMethodParm) {
			tgtMethodInputs.add(lastVar.getDeclIndex());
		}
		return seq;
	}

	/**
	 * Given a sequence, extends it with an array creation statement for the given
	 * array type and array element spec from the CTD test plan.
	 *
	 * @param arrType     array type to create initialization statement for
	 * @param arrElemSpec specification of types to instantiate and add to array
	 * @param seq         Sequence to be extended
	 * @return Extended sequence
	 * @throws ClassNotFoundException
	 */
	private Sequence processArrayType(String arrType, ObjectNode arrElemSpec, Sequence seq)
        throws ClassNotFoundException, NoSuchMethodException, OperationParseException {

		// build list of types whose instances are to be added to the array
        List<String> elemTypes = mapper.convertValue(arrElemSpec.get("types"), new TypeReference<List<String>>(){});

		// list to hold uncovered array element types
		List<String> uncovElemTypes = new ArrayList<>();

		// list to store indexes of variables holding array element values
		List<Integer> arrElemVarsIdx = new ArrayList<>();

		// element type arguments if array elements are parameterized
        List<String> elemTypeParams = null;

        // extend sequence with statements for creating each element type; store the variable
		// holding element value
		for (String elemType : elemTypes) {
		    // set element type parameters; the assumption is that all elements would have the same
            // type parameters (TODO)
		    if (elemTypeParams == null && elemType.contains("<")) {
                elemTypeParams = Arrays.asList(elemType
                    .substring(elemType.indexOf('<')+1, elemType.indexOf('>'))
                    .split(",")
                );
            }
            Pair<Sequence,Integer> extSeq = processElement(elemType, arrElemSpec, seq);
            // if the sequence was extended, add the generated var to the array element var list
			if (extSeq.a.size() > seq.size()) {
			    seq = extSeq.a;
				arrElemVarsIdx.add(extSeq.b);
			} else {
				uncovElemTypes.add(elemType);
			}
		}

		// if no array elements could be created (because types could not be found in the
		// type pool), raise an exception as the test plan row cannot be covered without
		// creating new types
		if (arrElemVarsIdx.isEmpty()) {
			this.extSummary.uncovTestPlanRows__excp__NoArrayElementType++;
			throw new RuntimeException("Sequence generation for array type with elements " + elemTypes
                + " not found in class sequence pool");
		}

		// if fewer array element vars created than the elem types, the coverage requirement
		// is partially covered; some types could not be created
		if (uncovElemTypes.size() > 0) {
			logger.warning("Array type partially covered: " + uncovElemTypes.size() + "/" +
                elemTypes.size() + " uncovered types: " + uncovElemTypes);
			this.rowPartiallyCovered = true;
		}

		// create list of input vars for array elements
		Sequence finalSeq = seq;
		List<Variable> arrElemVars = arrElemVarsIdx.stream()
            .map(idx -> finalSeq.getVariable(idx))
            .collect(Collectors.toList());

        // if array elements are generic types, apply substitution based in the type parameters
        // specified for the elements
        ArrayType arrayType = ArrayType.forClass(Type.forFullyQualifiedName(arrType));
        Type arrayElemType = arrayType.getElementType();
        if (arrayElemType.isGeneric()) {
            List<TypeVariable> arrayElemTypeVars = ((GenericClassType)arrayElemType).getTypeParameters();
            List<ReferenceType> arrayElemTypeArgs = new ArrayList<>();
            for (String typeParam : elemTypeParams) {
                arrayElemTypeArgs.add(ReferenceType.forClass(Class.forName(typeParam)));
            }
            Substitution subst = new Substitution(arrayElemTypeVars, arrayElemTypeArgs);
            arrayType = arrayType.substitute(subst);
        }

        // extend the sequence with initialized array instantiation statement, with variables
        // holding array element values as input variables for the statement
        TypedOperation initArrInst = TypedOperation.createInitializedArrayCreation(
		    arrayType, arrElemVars.size());
		logger.info("Created initialized array creation statement: " + initArrInst);
		seq = seq.extend(initArrInst, arrElemVars);

		// add the variable index for array creation to target method inputs
		tgtMethodInputs.add(seq.getLastVariable().getDeclIndex());

		return seq;
	}

	/**
	 * Given a sequence, extends it with statements for creating and adding elements
	 * (according to the CTD element spec) to a collection object.
	 *
	 * @param colType
	 * @param colElemSpec
     * @param typeArgument
     * @param isTgtMethodParm
	 * @param seq
	 * @return
	 * @throws NoSuchMethodException
	 * @throws ClassNotFoundException
	 */
	private Sequence processCollectionType(String colType, ObjectNode colElemSpec,
                                           ReferenceType typeArgument, boolean isTgtMethodParm,
                                           Sequence seq)
        throws NoSuchMethodException, ClassNotFoundException, OperationParseException {

        // get instantiation info for creating collection instance to add elements to
        JavaCollectionTypes.InstantiationInfo instInfo = JavaCollectionTypes.getCollectionTypeInstantiationInfo(
            colType, typeArgument
        );
        InstantiatedType colInstType = instInfo.instantiatedType;
        Constructor<?> colCtor = instInfo.typeConstructor;
        Method colAddMethod = instInfo.addMethod;

		// extend sequence with collection instantiation statements
		TypedOperation colInstOper = TypedOperation.forConstructor(colCtor);
		if (colInstType != null) {
            colInstOper = colInstOper.substitute(colInstType.getTypeSubstitution());
        }
		seq = seq.extend(colInstOper);
		int colInstVarIdx = seq.getLastVariable().getDeclIndex();

		// build list of types whose instances are to be added to the collection
        List<String> elemTypes = mapper.convertValue(colElemSpec.get("types"), new TypeReference<List<String>>(){});

		// list to hold uncovered collection element types
		List<String> uncovElemTypes = new ArrayList<>();

		// extend sequence with instantiation statements for each element type
		// and add method call to add the variable to the sequence
		for (String elemType : elemTypes) {
//			Type rndElemType = getRandoopType(elemType);
//			Sequence extSeq = processScalarType(rndElemType, false, seq);
            Pair<Sequence,Integer> extSeq = processElement(elemType, colElemSpec, seq);
			if (extSeq.a.size() > seq.size()) {
				// if the sequence was extended, extend it with call to add method of collection
				// to add the created element
                seq = extSeq.a;
				TypedOperation colAddOper = TypedOperation.forMethod(colAddMethod);
//                Substitution colAddSubst = new Substitution(
//                    colAddOper.getTypeParameters(),
//                    ReferenceType.forClass(Object.class));
                if (colInstType != null) {
                    colAddOper = colAddOper.substitute(colInstType.getTypeSubstitution());
                }
                seq = seq.extend(colAddOper, seq.getVariable(colInstVarIdx), seq.getVariable(extSeq.b));
			}
			else {
				// otherwise record the element as uncovered
				uncovElemTypes.add(elemType);
			}
		}

		// add the variable index for collection creation to target method inputs
        if (isTgtMethodParm) {
            tgtMethodInputs.add(colInstVarIdx);
        }

		// if uncovered element types exist, the coverage requirement is partially covered;
        // some types could not be created
		if (uncovElemTypes.size() > 0) {
			logger.warning("Collection type partially covered: " + uncovElemTypes.size() + "/" +
                elemTypes.size() + " uncovered types: " + uncovElemTypes);
			this.rowPartiallyCovered = true;
		}

		return seq;
	}

    /**
     * Given a sequence, extends it with statements for creating and adding elements
     * (according to the CTD element spec) to a map object.
     *
     * @param mapType
     * @param keyTypeArgument
     * @param valueTypeArgument
     * @param keyElemSpec
     * @param valueElemSpec
     * @param seq
     * @return
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     */
    private Sequence processMapType(String mapType, ReferenceType keyTypeArgument, ReferenceType valueTypeArgument,
                                    ObjectNode keyElemSpec, ObjectNode valueElemSpec, boolean isTgtMethodParam,
                                    Sequence seq)
        throws NoSuchMethodException, ClassNotFoundException, OperationParseException {

        // get instantiation info for creating map instance to add elements to
        JavaCollectionTypes.InstantiationInfo instInfo = JavaCollectionTypes.getMapTypeInstantiationInfo(
            mapType, keyTypeArgument, valueTypeArgument
        );
        InstantiatedType mapInstType = instInfo.instantiatedType;
        Constructor<?> mapCtor = instInfo.typeConstructor;
        Method mapPutMethod = instInfo.addMethod;

        // extend sequence with map instantiation statements
        Substitution mapSubst = mapInstType.getTypeSubstitution();
        TypedOperation mapInstOper = TypedOperation.forConstructor(mapCtor).substitute(mapSubst);
        seq = seq.extend(mapInstOper);
        int mapInstVarIdx = seq.getLastVariable().getDeclIndex();

        // lists to hold uncovered key and value types
        List<String> uncovKeyTypes = new ArrayList<>();
        List<String> uncovValueTypes = new ArrayList<>();

        // extend sequence with statements for adding elements to the instantiated map
        seq = extendSequenceWithMapElements(keyElemSpec, valueElemSpec, seq, mapPutMethod, mapInstVarIdx,
            mapSubst, uncovKeyTypes, uncovValueTypes);

        // add the variable index for map creation to target method
        if (isTgtMethodParam) {
            tgtMethodInputs.add(mapInstVarIdx);
        }

        // if uncovered map key or value types exist, the coverage requirement is partially covered;
        // some types could not be created
        if (uncovKeyTypes.size() > 0 || uncovValueTypes.size() > 0) {
            logger.warning("Map type partially covered; uncovered key types: " + uncovKeyTypes +
                "; uncovered value types: " + uncovValueTypes);
            this.rowPartiallyCovered = true;
        }

        return seq;
    }

    /**
     * Extends a given sequence with statements for instantiating key and value objects (according
     * to the test plan specification) and adding key-value pairs to the object.
     * @param keyElemSpec
     * @param valueElemSpec
     * @param seq
     * @param mapPutMethod
     * @param mapInstVarIndex
     * @param uncovKeyTypes
     * @param uncovValueTypes
     * @return
     * @throws ClassNotFoundException
     */
    private Sequence extendSequenceWithMapElements(ObjectNode keyElemSpec, ObjectNode valueElemSpec,
                                                   Sequence seq, Method mapPutMethod,
                                                   int mapInstVarIndex, Substitution mapSubst,
                                                   List<String> uncovKeyTypes, List<String> uncovValueTypes)
        throws ClassNotFoundException, NoSuchMethodException, OperationParseException {

        // build lists of types for map keys and map values
        List<String> keyTypes = mapper.convertValue(keyElemSpec.get("types"), new TypeReference<List<String>>(){});
        List<String> valueTypes = mapper.convertValue(valueElemSpec.get("types"), new TypeReference<List<String>>(){});

        // create sequence for instantiating each key type
        List<Pair<Sequence, Integer>> keySequences = new ArrayList<>();
        for (String keyType : keyTypes) {
            Pair<Sequence,Integer> keySeq = processElement(keyType, keyElemSpec, new Sequence());
            if (keySeq.a.size() > 0) {
                keySequences.add(keySeq);
            } else {
                uncovKeyTypes.add(keyType);
            }
        }

        // create sequence for instantiating each value type
        List<Pair<Sequence, Integer>> valueSequences = new ArrayList<>();
        for (String valueType : valueTypes) {
            Pair<Sequence,Integer> valueSeq = processElement(valueType, valueElemSpec, new Sequence());
            if (valueSeq.a.size() > 0) {
                valueSequences.add(valueSeq);
            } else {
                uncovValueTypes.add(valueType);
            }
        }

        // add statements to the input sequence with key/value sequences and map put method for the
        // key/value variable
        boolean allKeySequencesProcessed = false, allValueSequencesProcessed = false;
        int keyIndex = 0, valueIndex = 0;

        // iterate until all key and value sequences are processed; the key and value sequences lists
        // can be of different lengths
        while (!allKeySequencesProcessed || !allValueSequencesProcessed) {

            int currSeqLen = seq.size();

            // extend sequence with key sequence
            Pair<Sequence,Integer> keySequence = keySequences.get(keyIndex++);
            if (keyIndex == keySequences.size()) {
                allKeySequencesProcessed = true;
                keyIndex = 0;
            }
            seq = SequenceUtil.concatenate(seq, keySequence.a);

            // extend sequence with value sequence
            Pair<Sequence,Integer> valueSequence = valueSequences.get(valueIndex++);
            if (valueIndex == valueSequences.size()) {
                allValueSequencesProcessed = true;
                valueIndex = 0;
            }
            seq = SequenceUtil.concatenate(seq, valueSequence.a);

            // extend sequence with map put parameters
            TypedOperation mapPutOperation = TypedOperation.forMethod(mapPutMethod);
            seq = seq.extend(mapPutOperation.substitute(mapSubst),
                seq.getVariable(mapInstVarIndex),
                seq.getVariable(currSeqLen + keySequence.b),
                seq.getVariable(currSeqLen + keySequence.a.size() + valueSequence.b));
        }
        return seq;
    }

    /**
     * Extends the given sequence with instantiation statement for the given element type. If the
     * element type is an array, collection, or map type, also creates statements for adding elements
     * to the array/collection/map. Returns a pair consisting of the extended sequence and the
     * index within the sequence where the variable corresponding to the core element is defined.
     * @param elemType
     * @param elemSpec
     * @param seq
     * @return
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     */
    private Pair<Sequence, Integer> processElement(String elemType, ObjectNode elemSpec, Sequence seq)
        throws ClassNotFoundException, NoSuchMethodException, OperationParseException {
        String typeName = elemType;
        if (elemType.contains("<")) {
            typeName = elemType.substring(0, elemType.indexOf('<'));
        }
        Type rndElemType = getRandoopType(typeName);
        int inputSeqSize = seq.size();

        // process elements of collection, map, array, or scalar type

        // element is a collection
        if (SequenceUtil.isCollectionType(rndElemType)) {
            // TODO: check nesting depth; throw exception if >1
            logger.info("Processing nested collection");

            // get type argument spec for collection
            Pattern pattern = Pattern.compile("^(.*)<(.*)>$");
            Matcher matcher = pattern.matcher(elemType);
            String colType = "";
            ReferenceType typeArgument = null;
            if (matcher.find()) {
                colType = matcher.group(1);
                String typeArgName = matcher.group(2);
                if (!typeArgName.isEmpty()) {
                    typeArgument = ReferenceType.forClass(Class.forName(typeArgName));
                }
            }

            // extend sequence with statements for creating and adding elements to collection
            seq = processCollectionType(colType, (ObjectNode) elemSpec.get("list_types"), typeArgument,
                false, seq);
            return new Pair<>(seq, inputSeqSize);
        }

        // element is a map
        if (SequenceUtil.isMapType(rndElemType)) {
            // TODO: check nesting depth; throw exception if >1
            logger.info("Processing nested map");

            // get key and value type argument spec for map
            Pattern pattern = Pattern.compile("^(.*)<(.*),\\s*(.*)>$");
            Matcher matcher = pattern.matcher(elemType);
            String mapType = "";
            ReferenceType keyTypeArgument = null, valueTypeArgument = null;
            if (matcher.find()) {
                mapType = matcher.group(1);
                String keyTypeArgName = matcher.group(2);
                if (!keyTypeArgName.isEmpty()) {
                    keyTypeArgument = ReferenceType.forClass(Class.forName(keyTypeArgName));
                }
                String valueTypeArgName = matcher.group(3);
                if (!valueTypeArgName.isEmpty()) {
                    valueTypeArgument = ReferenceType.forClass(Class.forName(valueTypeArgName));
                }
            }

            // extend sequence with statements for creating and adding elements to map
            seq = processMapType(mapType, keyTypeArgument, valueTypeArgument,
                (ObjectNode) elemSpec.get("key_types"),
                (ObjectNode) elemSpec.get("value_types"), false, seq);
            return new Pair<>(seq, inputSeqSize);
        }

        // element is an array
        if (rndElemType.isArray()) {
            // TODO: check nesting depth; throw exception if >1
            logger.info("Processing nested array");

            // extend sequence with statements for creating and adding elements to array
            seq = processArrayType(elemType, (ObjectNode) elemSpec.get("list_types"), seq);
            return new Pair<>(seq, seq.size()-1);
        }

        // map element is a scalar type
        seq = processScalarType(rndElemType, false, seq);
        return new Pair<>(seq, seq.size()-1);
    }

	/**
	 * Returns coverage map for the given partition, class, and method. Initializes
	 * coverage information for method to uncovered.
	 *
	 * @param partName     Name of partition
	 * @param clsName      Name of class
	 * @param methodSig    Signature of method
	 * @param testPlanRows Test plan rows for the method
	 * @return Map from test plan row to coverage constant string
	 */
	private Map<String, Constants.TestPlanRowCoverage> getProxyMethodCovInfo(String partName, String clsName,
			String methodSig, ArrayNode[] testPlanRows) {
		if (!this.coverageInfo.containsKey(partName)) {
			this.coverageInfo.put(partName, new HashMap<>());
		}
		Map<String, Map<String, Map<String, Constants.TestPlanRowCoverage>>> partCovInfo = this.coverageInfo
				.get(partName);
		if (!partCovInfo.containsKey(clsName)) {
			partCovInfo.put(clsName, new HashMap<>());
		}
		Map<String, Map<String, Constants.TestPlanRowCoverage>> clsCovInfo = partCovInfo.get(clsName);
		if (!clsCovInfo.containsKey(methodSig)) {
			clsCovInfo.put(methodSig, new HashMap<>());
		}
		Map<String, Constants.TestPlanRowCoverage> methodCovInfo = clsCovInfo.get(methodSig);
		// initialize coverage information for all rows to uncovered
		for (int rowCtr = 1; rowCtr <= testPlanRows.length; rowCtr++) {
			methodCovInfo.put(getTestPlanRowId(rowCtr), Constants.TestPlanRowCoverage.UNCOVERED);
		}
		return methodCovInfo;
	}

	/**
	 * Returns ID for a test plan row TODO; improve ID format?
	 *
	 * @param counter
	 * @return
	 */
	private String getTestPlanRowId(int counter) {
		return "test_plan_row_" + counter;
	}

	private Type getRandoopType(String typeName) throws ClassNotFoundException {
		Class<?> typeCls = Type.forFullyQualifiedName(typeName);
		return Type.forClass(typeCls);
	}

	/**
	 * Checks types that occur in the test plan, for which no constructor sequence
	 * exists in the the class sequence pool (populated from constructor
	 * subsequences in the building block test sequences. Attempts to create new
	 * constructor sequences for such classes and adds them to the class sequence
	 * pool.
	 */
	private void augmentClassSequencePool() {
		Set<String> testPlanClasses = this.tgtProxyMethodSignatures.stream()
            .map(sig -> sig.split("::")[0])
            .collect(Collectors.toSet());
		testPlanClasses.removeAll(this.sequencePool.classTestSeqPool.keySet());
		logger.info("Augmenting class sequence pool for constructor sequences for: " + testPlanClasses);
		for (String clsName : testPlanClasses) {
			try {
			    // skip generic class types
			    Type clsType = Type.forName(clsName);
			    if (clsType instanceof GenericClassType) {
			        continue;
                }
				ConstructorSequenceGenerator.createConstructorSequence(clsName, null,false, this.sequencePool, 0);
			} catch (ClassNotFoundException | NoClassDefFoundError | OperationParseException | NoSuchMethodException cnfe) {
                logger.warning("Error creating constructor sequence for " + clsName + ": " + cnfe.getMessage());
                this.extSummary.classNotFoundTypes.add(cnfe.getMessage());
            }
		}
	}

	/**
	 * Returns from the class sequence pool constructor sequences for a subtype of
	 * the given type name, if a subtype exists and is not specified as another
	 * coverage goal for the current parameter, or null otherwise.
	 *
	 * @param typeName
	 * @return
	 * @throws ClassNotFoundException
	 */
	private SortedSet<Sequence> getSubtypeConstructorSequences(String typeName, boolean isTgtMethodParam)
			throws ClassNotFoundException {

		// get all subtypes of the given type for which constructor sequences exist in the
		// class sequence pool
		Class<?> targetCls = Class.forName(typeName);
		SortedSet<String> subtypesWithCtorSeqs = new TreeSet<>();
		for (String clsName : this.sequencePool.classTestSeqPool.keySet()) {
			Class<?> seqPoolCls = Class.forName(clsName);
			if (targetCls.isAssignableFrom(seqPoolCls)) {
				subtypesWithCtorSeqs.add(seqPoolCls.getName());
//                return this.clsTestSeqPool.get(clsName);
			}
		}

		// return null if no such sequence exists
		if (subtypesWithCtorSeqs.size() == 0) {
			return null;
		}

		// compute other type coverage goals for the current parameter
		Set<String> otherTypesForParam = new HashSet<>();
		if (isTgtMethodParam) {
			// for non array/collection type, iterate over test plan rows and get the specification
			// for the current paramater
			for (ArrayNode testPlanRow : currTestPlanRows) {
				ObjectNode param = (ObjectNode) testPlanRow.get(currTestPlanRowParamIndex);
				Iterator<String> iter = param.fieldNames();
				while (iter.hasNext()) {
					String key = iter.next();
					// skip non-param keys
					if (key.startsWith("attr_")) {
						otherTypesForParam.add(param.get(key).asText());
						break;
					}
				}
			}
		} else {
			// for array/collection type, get all types in specification of the current test plan row
			ArrayNode testPlanRow = currTestPlanRows[currTestPlanRowIndex];
			ArrayNode colElemSpec = (ArrayNode) testPlanRow.get(currTestPlanRowParamIndex)
                .get("list_types");
			otherTypesForParam.addAll(mapper.convertValue(colElemSpec, new TypeReference<List<String>>(){}));
		}
		otherTypesForParam.remove(typeName);

		// remove all types from the subtype set that are specified as coverage goals
		subtypesWithCtorSeqs.removeAll(otherTypesForParam);

		// if no subtypes remain, return null
		if (subtypesWithCtorSeqs.size() == 0) {
			return null;
		}

		// return the constructor sequences for a remaining subtype
		return this.sequencePool.classTestSeqPool.get(subtypesWithCtorSeqs.first());
	}

	/**
	 * Parses command-line options and returns parsed command-line object. If help
	 * option is specified or a parse exception occurs, prints the help message and
	 * returns null.
	 *
	 * @param args
	 * @return
	 */
	private static CommandLine parseCommandLineOptions(String[] args) {
		Options options = new Options();

		// option for sequences file
		options.addOption(Option.builder("app").longOpt("application-name").hasArg()
				.desc("Name of the application under test").type(String.class).build());

		// option for input CTD test plan file
		options.addOption(Option.builder("tp").longOpt("test-plan").hasArg()
				.desc("Name of JSON file containing the CTD test plan").type(String.class).build());

		// option for input initial test sequence file
		options.addOption(Option.builder("ts").longOpt("test-sequence").hasArg()
				.desc("Name of JSON file containing the initial test sequences").type(String.class).build());

		// option for output coverage information file
		options.addOption(Option.builder("cf").longOpt("coverage-file").hasArg()
				.desc("Name of JSON file to which coverage information is written").type(String.class).build());

		// option for adding diff assertions to the extended test sequences
		options.addOption(Option.builder("da").longOpt("diff-assert")
				.desc("Add assertions for detecting state differences to the extended test cases").build());

		// option for running failed sequences with jee support to try and make them pass
		options.addOption(Option.builder("jee").longOpt("jee-support")
					.desc("Run failed sequences with JEE support and if they pass add them to final unit tests").build());

		// option for running failed sequences with jee support to try and make them pass
		options.addOption(Option.builder("ne").longOpt("num-executions").hasArg()
					.desc("Number of times to execute each sequence. Default is "+Constants.NUM_SEQUENCE_EXECUTION).type(Integer.class).build());


		// option for output directory in which generated tests are written
		options.addOption(Option.builder("od").longOpt("output-directory").hasArg()
				.desc("Name of directory to which generate tests are written").type(String.class).build());

		// help option
		options.addOption(Option.builder("h").longOpt("help").desc("Print this help message").build());

		HelpFormatter formatter = new HelpFormatter();

		// parse command line options
		CommandLineParser argParser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = argParser.parse(options, args);
			// if help option specified, print help message and return null
			if (cmd.hasOption("h")) {
				formatter.printHelp("TestSequenceExtender", options, true);
				return null;
			}
		} catch (ParseException e) {
			logger.warning(e.getMessage());
			formatter.printHelp("TestSequenceExtender", options, true);
		}

		// check whether required options are specified
		if (!cmd.hasOption("app") || !cmd.hasOption("tp") || !cmd.hasOption("ts")) {
			formatter.printHelp("TestSequenceExtender", options, true);
			return null;
		}
		return cmd;
	}

	public static void main(String[] args) throws IOException {
		// parse command-line options
		CommandLine cmd = parseCommandLineOptions(args);

		// if parser command-line is empty (which occurs if the help option is specified
		// or a parse exception occurs, exit
		if (cmd == null) {
			System.exit(0);
		}

		String appName = cmd.getOptionValue("app");
		String testPlanFilename = cmd.getOptionValue("tp");
		String testSeqFilename = cmd.getOptionValue("ts");
		logger.info("Application name: " + appName);
		logger.info("CTD test plan file: " + testPlanFilename);
		logger.info("Init test sequences file: " + testSeqFilename);
		String coverageFilename = null;
		if (cmd.hasOption("cf")) {
			coverageFilename = cmd.getOptionValue("cf");
		}
		String outputDir = null;
		if (cmd.hasOption("od")) {
			outputDir = cmd.getOptionValue("od");
		}

		boolean jee = false;
		if (cmd.hasOption("jee")) {
			jee = true;
			logger.info("Running with JEE support");
		}

		int numExecutions = Constants.NUM_SEQUENCE_EXECUTION;

		if (cmd.hasOption("ne")) {
			numExecutions = Integer.parseInt(cmd.getOptionValue("ne"));
		}

		logger.info("Number of executions per sequence: "+numExecutions);

		// check whether diff assertions need to be added
        boolean addDiffAsserts = false;
        if (cmd.hasOption("da")) {
            addDiffAsserts = true;
        }

		// create extended sequences
		TestSequenceExtender testSeqExt = new TestSequenceExtender(appName, testPlanFilename,
            testSeqFilename, true,
				outputDir, jee, numExecutions, addDiffAsserts);
		testSeqExt.createExtendedSequences();

		// write test classes
		testSeqExt.writeTestClasses(addDiffAsserts);

		// write coverage file
        testSeqExt.writeTestCoverageFile(appName, coverageFilename);
	}

	class NonInstantiableTypeException extends RuntimeException {
		public NonInstantiableTypeException(String errmsg) {
			super(errmsg);
		}
	}

}
