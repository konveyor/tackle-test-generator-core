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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;
import org.konveyor.tackle.testgen.util.TackleTestLogger;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import randoop.sequence.Sequence;

public class ExtenderSummary {

    private static final Logger logger = TackleTestLogger.getLogger(ExtenderSummary.class);

    int totalTestPlanRows = 0;
    int covTestPlanRows__full = 0;
    int covTestPlanRows__partial = 0;
    public int covTestPlanRows__full_jee = 0;
    public int covTestPlanRows__partial_jee = 0;
    int covTestPlanRows__initSeq = 0;
    int uncovTestPlanRows__noInitSeq = 0;
    public int uncovTestPlanRows__execFail = 0;
    int uncovTestPlanRows__excp = 0;
    int uncovTestPlanRows__excp__OperationParse = 0;
    int uncovTestPlanRows__excp__randoop__IllegalArgument = 0;
    int uncovTestPlanRows__excp__ClassNotFound = 0;
    int uncovTestPlanRows__excp__UnsatisfiedLink = 0;
    int uncovTestPlanRows__excp__NoSuchMethod = 0;
    int uncovTestPlanRows__excp__NoArrayElementType = 0;
    int uncovTestPlanRows__excp__NonInstantiableType = 0;
    int uncovTestPlanRows__excp__UnsupportedCollectionType = 0;
    int uncovTestPlanRows__excp__UnsupportedMapType = 0;
    int uncovTestPlanRows__excp__exec__InvocationTarget = 0;
    int uncovTestPlanRows__excp__exec__IllegalAccess = 0;
    int uncovTestPlanRows__excp__exec__IllegalArgument = 0;
    int uncovTestPlanRows__excp__exec__Error = 0;
    int uncovTestPlanRows__excp__exec__Other = 0;

    Set<String> nonInstantiableTypes = new HashSet<>();
    Set<String> classNotFoundTypes = new HashSet<>();
    Set<String> seqExecExcpOther = new HashSet<>();
    
    Map<String, Integer> seqFailExcp = new HashMap<String, Integer>();
    
    int tway = -1;
    int totalMethodsOverOneRow = 0;
    double totalCTDCov = 0;
    double totalExistingCTDCov = 0;

    private SequencePool sequencePool;
    private ObjectNode testPlan;

    ExtenderSummary(ObjectNode testPlan) {
        this.testPlan = testPlan;
        this.testPlan.fieldNames().forEachRemaining(partitionName -> {
        	ObjectNode partitionTestPlan = (ObjectNode) this.testPlan.get(partitionName);
        	partitionTestPlan.fieldNames().forEachRemaining(className -> {
        		ObjectNode classTestPlan = (ObjectNode) partitionTestPlan.get(className);
        		classTestPlan.fieldNames().forEachRemaining(methodSig -> {
        			ObjectNode methodTestPlan = (ObjectNode) classTestPlan.get(methodSig);
                    this.totalTestPlanRows += methodTestPlan.get("test_plan").size();
                });
            });
        });
    }

    public void setSequencePool(SequencePool sequencePool) {
        this.sequencePool = sequencePool;
    }

    /**
     * Prints summary information about test generation to stdout
     *
     * @param seqIdMap
     * @param extTestSeq
     * @param execExtSeq
     */
    void printSummaryInfo(HashMap<String, Sequence> seqIdMap,
                          Map<String, Map<String, Set<String>>> extTestSeq,
                          HashMap<String, SequenceExecutor.SequenceResults> execExtSeq,
                          int assertionCount) {
        System.out.println("\n==== Summary of CTD-amplified test generation ===");
        System.out.println(" Total base sequences: " + this.sequencePool.totalBaseSequences);
        System.out.println(" Parsed base sequences (full): " + this.sequencePool.parsedBaseSequencesFull);
        System.out.println(" Parsed base sequences (partial): " + this.sequencePool.parsedBaseSequencesPartial);
        System.out.println(" Skipped base sequences: " + this.sequencePool.skippedBaseSequences);
        System.out.println(" Exception base sequences: " + this.sequencePool.exceptionBaseSequences);
        System.out.println(" Method test sequence pool key size: " + this.sequencePool.methodTestSeqPool.keySet().size());
        System.out.println(" Class test sequence pool key size: " + this.sequencePool.classTestSeqPool.keySet().size());
        System.out.println("***");
        System.out.println(" Total generated extended sequences: " + seqIdMap.keySet().size());
        System.out.println(" Executed extended sequences: " + execExtSeq.keySet().size());
        System.out.println(" Final extended sequences: " + extTestSeq.values().stream()
            .flatMap(clseq -> clseq.values().stream()).mapToInt(seql -> seql.size()).sum());
        System.out.println(" Total failing sequences: " + this.uncovTestPlanRows__execFail);
        System.out.println(" Diff assertions added: "+assertionCount);
        System.out.println(" Total test plan rows: " + this.totalTestPlanRows);
        System.out.println(" Covered test plan rows (full): " + this.covTestPlanRows__full);
        System.out.println(" Covered test plan rows (partial): " + this.covTestPlanRows__partial);
        System.out.println(" Covered test plan rows (full jee): " + this.covTestPlanRows__full_jee);
        System.out.println(" Covered test plan rows (partial jee): " + this.covTestPlanRows__partial_jee);
        System.out.println(" Covered test plan rows by BB sequences (no extension done): "+
            this.covTestPlanRows__initSeq);
        System.out.println("***");
        System.out.println(" Uncovered test plan rows (no initial sequence for target method): "
            + this.uncovTestPlanRows__noInitSeq);
        System.out.println(" Uncovered test plan rows (non-instantiable type in test plan row): "
            + this.uncovTestPlanRows__excp__NonInstantiableType);
        System.out.println(" Uncovered test plan rows (execution of extended sequence failed): "
            + this.uncovTestPlanRows__execFail);
        System.out.println(
            " Uncovered test plan rows (exception during sequence extension): " + this.uncovTestPlanRows__excp);
        System.out.println("    Randoop OperationParseException: " + this.uncovTestPlanRows__excp__OperationParse);
        System.out.println("    IllegalArgumentException (Randoop API): "
            + this.uncovTestPlanRows__excp__randoop__IllegalArgument);
        System.out.println("    ClassNotFoundException: " + this.uncovTestPlanRows__excp__ClassNotFound);
        System.out.println("    UnsatisfiedLinkError: " + this.uncovTestPlanRows__excp__UnsatisfiedLink);
        System.out.println("    NoSuchMethodException: " + this.uncovTestPlanRows__excp__NoSuchMethod);
        System.out.println("    NoArrayElementType: " + this.uncovTestPlanRows__excp__NoArrayElementType);
        System.out.println("    UnsupportedCollectionType: " + this.uncovTestPlanRows__excp__UnsupportedCollectionType);
        System.out.println("    InvocationTargetException (sequence execution): "
            + this.uncovTestPlanRows__excp__exec__InvocationTarget);
        System.out.println("    IllegalAccessException (sequence execution): "
            + this.uncovTestPlanRows__excp__exec__IllegalAccess);
        System.out.println("    IllegalArgumentException (sequence execution): "
            + this.uncovTestPlanRows__excp__exec__IllegalArgument);
        System.out.println("    Error (sequence execution): " + this.uncovTestPlanRows__excp__exec__Error);
        System.out.println("    Other (sequence execution): " + this.uncovTestPlanRows__excp__exec__Other);
        System.out.println(" Sequence execution exceptions (other): " + this.seqExecExcpOther);
        System.out.println(" Parse exceptions: " + this.sequencePool.parseExceptions);
        System.out.println(" Non-instantiable types: " + this.nonInstantiableTypes);
        System.out.println(" Class not found types: " + this.classNotFoundTypes);
    }

    /**
     * Writes summary information about test generation to a JSON file
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonGenerationException
     */
    void writeSummaryFile(String appName, HashMap<String, Sequence> seqIdMap,
                          Map<String, Map<String, Set<String>>> extTestSeq,
                          HashMap<String, SequenceExecutor.SequenceResults> execExtSeq,
                          int assertionCount)
        throws JsonGenerationException, JsonMappingException, IOException {

    	ObjectMapper mapper = TackleTestJson.getObjectMapper();

        ObjectNode summaryJson = mapper.createObjectNode();

        // add information about building-block sequences
        ObjectNode bbSeqInfo = mapper.createObjectNode();
        bbSeqInfo.put("base_sequences", this.sequencePool.totalBaseSequences);
        bbSeqInfo.put("parsed_base_sequences_full", this.sequencePool.parsedBaseSequencesFull);
        bbSeqInfo.put("parsed_base_sequences_partial", this.sequencePool.parsedBaseSequencesPartial);
        bbSeqInfo.put("skipped_base_sequences", this.sequencePool.skippedBaseSequences);
        bbSeqInfo.put("exception_base_sequences", this.sequencePool.exceptionBaseSequences);
        bbSeqInfo.put("method_sequence_pool_keys", this.sequencePool.methodTestSeqPool.keySet().size());
        bbSeqInfo.put("class_sequence_pool_keys", this.sequencePool.classTestSeqPool.keySet().size());
        summaryJson.set("building_block_sequences_info", bbSeqInfo);

        // add information about generated sequences
        ObjectNode extSeqInfo = mapper.createObjectNode();
        extSeqInfo.put("generated_sequences", seqIdMap.keySet().size());
        extSeqInfo.put("executed_sequences", execExtSeq.keySet().size());
        extSeqInfo.put("failing_sequences", this.uncovTestPlanRows__execFail);
        extSeqInfo.put("final_sequences", extTestSeq.values().stream()
            .flatMap(clseq -> clseq.values().stream())
            .mapToInt(seql -> seql.size())
            .sum());
        extSeqInfo.put("diff_assertions", assertionCount);
        summaryJson.set("extended_sequences_info", extSeqInfo);

        // add information about coverage of test plan rows
        ObjectNode covInfo = mapper.createObjectNode();
        covInfo.put("test_plan_target_methods", this.testPlan.size());
        covInfo.put("test_plan_rows", this.totalTestPlanRows);
        covInfo.put("rows_covered_full", this.covTestPlanRows__full);
        covInfo.put("rows_covered_partial", this.covTestPlanRows__partial);
        covInfo.put("rows_covered_full_jee", this.covTestPlanRows__full_jee);
        covInfo.put("rows_covered_partial_jee", this.covTestPlanRows__partial_jee);
        covInfo.put("rows_covered_bb_sequences", this.covTestPlanRows__initSeq);
        summaryJson.set("test_plan_coverage_info", covInfo);
        
     // add information about combinatorial coverage of test plan rows
        if (tway > -1) {
        	ObjectNode ctdCovInfo = mapper.createObjectNode();
        	ctdCovInfo.put("interaction_level", this.tway);
        	ctdCovInfo.put("methods_over_one_row", this.totalMethodsOverOneRow);
        	ctdCovInfo.put("average_ctd_cov", this.totalMethodsOverOneRow == 0? "0" : String.format("%.2f", this.totalCTDCov/this.totalMethodsOverOneRow));
        	ctdCovInfo.put("average_ctd_cov_bb_sequences", this.totalMethodsOverOneRow == 0? "0" : String.format("%.2f", this.totalExistingCTDCov/this.totalMethodsOverOneRow));
        	summaryJson.set("ctd_coverage_info", ctdCovInfo);
        }

        // add information about uncovered test plan rows
        ObjectNode uncovInfo = mapper.createObjectNode();
        uncovInfo.put("no_bb_sequence_for_target_method", this.uncovTestPlanRows__noInitSeq);
        uncovInfo.put("non_instantiable_param_type", this.uncovTestPlanRows__excp__NonInstantiableType);
        uncovInfo.put("execution_failed", this.uncovTestPlanRows__execFail);
        uncovInfo.put("exception_during_extension", this.uncovTestPlanRows__excp);
        ObjectNode excpInfo = mapper.createObjectNode();
        excpInfo.put("randoop_operation_parse_exception", this.uncovTestPlanRows__excp__OperationParse);
        excpInfo.put("randoop_illegal_argument_exception", this.uncovTestPlanRows__excp__randoop__IllegalArgument);
        excpInfo.put("class_not_found_exception", this.uncovTestPlanRows__excp__ClassNotFound);
        excpInfo.put("unsatisfied_link_error", this.uncovTestPlanRows__excp__UnsatisfiedLink);
        excpInfo.put("no_such_method_exception", this.uncovTestPlanRows__excp__NoSuchMethod);
        excpInfo.put("no_array_element_type", this.uncovTestPlanRows__excp__NoArrayElementType);
        excpInfo.put("unsupported_collection_type", this.uncovTestPlanRows__excp__UnsupportedCollectionType);
        excpInfo.put("sequence_exec_invocation_target_exception", this.uncovTestPlanRows__excp__exec__InvocationTarget);
        excpInfo.put("sequence_exec_illegal_access_exception", this.uncovTestPlanRows__excp__exec__IllegalAccess);
        excpInfo.put("sequence_exec_illegal_argument_exception", this.uncovTestPlanRows__excp__exec__IllegalArgument);
        excpInfo.put("sequence_exec_error", this.uncovTestPlanRows__excp__exec__Error);
        excpInfo.put("sequence_exec_other_exception", this.uncovTestPlanRows__excp__exec__Other);
        uncovInfo.set("exception_info", excpInfo);
        summaryJson.set("uncovered_test_plan_rows_info", uncovInfo);

        // add exception types thrown during sequence execution
        ArrayNode execExcpTypes = mapper.createArrayNode();
        this.seqExecExcpOther.forEach(excp -> execExcpTypes.add(excp));
        summaryJson.set("execution_exception_types_other", execExcpTypes);

        // add parse exception types
        ObjectNode parseExcpTypes =  mapper.valueToTree(this.sequencePool.parseExceptions);
        summaryJson.set("parse_exception_types", parseExcpTypes);

        // add non-instantiable types
        ArrayNode nonInstTypes = mapper.createArrayNode();
        this.nonInstantiableTypes.forEach(type -> nonInstTypes.add(type));
        summaryJson.set("non_instantiable_types", nonInstTypes);

        // add class-not-found types
        ArrayNode cnfTypes = mapper.createArrayNode();
        this.classNotFoundTypes.forEach(type -> cnfTypes.add(type));
        summaryJson.set("class_not_found_types", cnfTypes);
        
        ObjectNode execFailTypes = mapper.createObjectNode();

        // add execution failure exception types count
        this.seqFailExcp.forEach((key, value) -> execFailTypes.put(key, value));
        summaryJson.set("execution_fail_exception_types", execFailTypes);

        // write JSON file
        String outFileName = appName+Constants.EXTENDER_SUMMARY_FILE_JSON_SUFFIX;
        mapper.writeValue(new File(outFileName), summaryJson);
    }

}
