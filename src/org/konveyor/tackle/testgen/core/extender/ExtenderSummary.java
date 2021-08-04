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

import org.konveyor.tackle.testgen.core.executor.SequenceExecutor;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import randoop.sequence.Sequence;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.*;
import java.util.logging.Logger;

public class ExtenderSummary {

    private static final Logger logger = TackleTestLogger.getLogger(ExtenderSummary.class);

    int totalTestPlanRows = 0;
    int covTestPlanRows__full = 0;
    int covTestPlanRows__partial = 0;
    public int covTestPlanRows__full_jee = 0;
    public int covTestPlanRows__partial_jee = 0;
    int covTestPlanRows__initSeq = 0;
    int uncovTestPlanRows__noInitSeq = 0;
    int uncovTestPlanRows__execFail = 0;
    int uncovTestPlanRows__excp = 0;
    int uncovTestPlanRows__excp__OperationParse = 0;
    int uncovTestPlanRows__excp__randoop__IllegalArgument = 0;
    int uncovTestPlanRows__excp__ClassNotFound = 0;
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

    private SequencePool sequencePool;
    private JsonObject testPlan;

    ExtenderSummary(JsonObject testPlan) {
        this.testPlan = testPlan;
        for (String partitionName : this.testPlan.keySet()) {
            JsonObject partitionTestPlan = this.testPlan.getJsonObject(partitionName);
            for (String className : partitionTestPlan.keySet()) {
                JsonObject classTestPlan = partitionTestPlan.getJsonObject(className);
                for (String methodSig : classTestPlan.keySet()) {
                    JsonObject methodTestPlan = classTestPlan.getJsonObject(methodSig);
                    this.totalTestPlanRows += methodTestPlan.getJsonArray("test_plan").size();
                }
            }
        }
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
     * @param discardedExtSeq
     */
    void printSummaryInfo(HashMap<String, Sequence> seqIdMap,
                          Map<String, Map<String, Set<String>>> extTestSeq,
                          HashMap<String, SequenceExecutor.SequenceResults> execExtSeq,
                          Map<String, Map<String, Map<String, Map<String, String>>>> discardedExtSeq,
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
        System.out.println(" Total failing sequences: " + discardedExtSeq.values().stream()
            .flatMap(clseq -> clseq.values().stream()).flatMap(metseq -> metseq.values().stream())
            .flatMap(rowseq -> rowseq.values().stream()).count());
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
     * @throws FileNotFoundException
     */
    void writeSummaryFile(String appName, HashMap<String, Sequence> seqIdMap,
                          Map<String, Map<String, Set<String>>> extTestSeq,
                          HashMap<String, SequenceExecutor.SequenceResults> execExtSeq,
                          Map<String, Map<String, Map<String, Map<String, String>>>> discardedExtSeq,
                          int assertionCount)
        throws FileNotFoundException {
        JsonObjectBuilder summaryJson = Json.createObjectBuilder();

        // add information about building-block sequences
        JsonObjectBuilder bbSeqInfo = Json.createObjectBuilder();
        bbSeqInfo.add("base_sequences", this.sequencePool.totalBaseSequences);
        bbSeqInfo.add("parsed_base_sequences_full", this.sequencePool.parsedBaseSequencesFull);
        bbSeqInfo.add("parsed_base_sequences_partial", this.sequencePool.parsedBaseSequencesPartial);
        bbSeqInfo.add("skipped_base_sequences", this.sequencePool.skippedBaseSequences);
        bbSeqInfo.add("exception_base_sequences", this.sequencePool.exceptionBaseSequences);
        bbSeqInfo.add("method_sequence_pool_keys", this.sequencePool.methodTestSeqPool.keySet().size());
        bbSeqInfo.add("class_sequence_pool_keys", this.sequencePool.classTestSeqPool.keySet().size());
        summaryJson.add("building_block_sequences_info", bbSeqInfo);

        // add information about generated sequences
        JsonObjectBuilder extSeqInfo = Json.createObjectBuilder();
        extSeqInfo.add("generated_sequences", seqIdMap.keySet().size());
        extSeqInfo.add("executed_sequences", execExtSeq.keySet().size());
        extSeqInfo.add("failing_sequences", discardedExtSeq.values().stream()
            .flatMap(clseq -> clseq.values().stream())
            .flatMap(metseq -> metseq.values().stream())
            .flatMap(rowseq -> rowseq.values().stream())
            .count());
        extSeqInfo.add("final_sequences", extTestSeq.values().stream()
            .flatMap(clseq -> clseq.values().stream())
            .mapToInt(seql -> seql.size())
            .sum());
        extSeqInfo.add("diff_assertions", assertionCount);
        summaryJson.add("extended_sequences_info", extSeqInfo);

        // add information about coverage of test plan rows
        JsonObjectBuilder covInfo = Json.createObjectBuilder();
        covInfo.add("test_plan_target_methods", this.testPlan.keySet().size());
        covInfo.add("test_plan_rows", this.totalTestPlanRows);
        covInfo.add("rows_covered_full", this.covTestPlanRows__full);
        covInfo.add("rows_covered_partial", this.covTestPlanRows__partial);
        covInfo.add("rows_covered_full_jee", this.covTestPlanRows__full_jee);
        covInfo.add("rows_covered_partial_jee", this.covTestPlanRows__partial_jee);
        covInfo.add("rows_covered_bb_sequences", this.covTestPlanRows__initSeq);
        summaryJson.add("test_plan_coverage_info", covInfo);

        // add information about uncovered test plan rows
        JsonObjectBuilder uncovInfo = Json.createObjectBuilder();
        uncovInfo.add("no_bb_sequence_for_target_method", this.uncovTestPlanRows__noInitSeq);
        uncovInfo.add("non_instantiable_param_type", this.uncovTestPlanRows__excp__NonInstantiableType);
        uncovInfo.add("execution_failed", this.uncovTestPlanRows__execFail);
        uncovInfo.add("exception_during_extension", this.uncovTestPlanRows__excp);
        JsonObjectBuilder excpInfo = Json.createObjectBuilder();
        excpInfo.add("randoop_operation_parse_exception", this.uncovTestPlanRows__excp__OperationParse);
        excpInfo.add("randoop_illegal_argument_exception", this.uncovTestPlanRows__excp__randoop__IllegalArgument);
        excpInfo.add("class_not_found_exception", this.uncovTestPlanRows__excp__ClassNotFound);
        excpInfo.add("no_such_method_exception", this.uncovTestPlanRows__excp__NoSuchMethod);
        excpInfo.add("no_array_element_type", this.uncovTestPlanRows__excp__NoArrayElementType);
        excpInfo.add("unsupported_collection_type", this.uncovTestPlanRows__excp__UnsupportedCollectionType);
        excpInfo.add("sequence_exec_invocation_target_exception", this.uncovTestPlanRows__excp__exec__InvocationTarget);
        excpInfo.add("sequence_exec_illegal_access_exception", this.uncovTestPlanRows__excp__exec__IllegalAccess);
        excpInfo.add("sequence_exec_illegal_argument_exception", this.uncovTestPlanRows__excp__exec__IllegalArgument);
        excpInfo.add("sequence_exec_error", this.uncovTestPlanRows__excp__exec__Error);
        excpInfo.add("sequence_exec_other_exception", this.uncovTestPlanRows__excp__exec__Other);
        uncovInfo.add("exception_info", excpInfo);
        summaryJson.add("uncovered_test_plan_rows_info", uncovInfo);

        // add exception types thrown during sequence execution
        JsonArrayBuilder execExcpTypes = Json.createArrayBuilder();
        this.seqExecExcpOther.forEach(excp -> execExcpTypes.add(excp));
        summaryJson.add("execution_exception_types_other", execExcpTypes);

        // add parse exception types
        JsonObjectBuilder parseExcpTypes = Json.createObjectBuilder();
        this.sequencePool.parseExceptions.forEach((excp, count) -> parseExcpTypes.add(excp, count));
        summaryJson.add("parse_exception_types", parseExcpTypes);

        // add non-instantiable types
        JsonArrayBuilder nonInstTypes = Json.createArrayBuilder();
        this.nonInstantiableTypes.forEach(type -> nonInstTypes.add(type));
        summaryJson.add("non_instantiable_types", nonInstTypes);

        // add class-not-found types
        JsonArrayBuilder cnfTypes = Json.createArrayBuilder();
        this.classNotFoundTypes.forEach(type -> cnfTypes.add(type));
        summaryJson.add("class_not_found_types", cnfTypes);

        // write JSON file
        String outFileName = appName+Constants.EXTENDER_SUMMARY_FILE_JSON_SUFFIX;
        FileOutputStream fos = new FileOutputStream(new File(outFileName));
        JsonWriterFactory writerFactory = Json
            .createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        try (JsonWriter jsonWriter = writerFactory.createWriter(fos)) {
            jsonWriter.writeObject(summaryJson.build());
        }
    }

}
