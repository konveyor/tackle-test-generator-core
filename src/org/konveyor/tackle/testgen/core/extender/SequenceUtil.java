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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import randoop.operation.CallableOperation;
import randoop.operation.ConstructorCall;
import randoop.operation.EnumConstant;
import randoop.operation.MethodCall;
import randoop.operation.OperationParseException;
import randoop.operation.TypedOperation;
import randoop.sequence.Sequence;
import randoop.sequence.Statement;
import randoop.sequence.Variable;
import randoop.types.ArrayType;
import randoop.types.GenericClassType;
import randoop.types.ReferenceType;
import randoop.types.Substitution;
import randoop.types.Type;
import randoop.types.TypeVariable;
import randoop.util.SimpleArrayList;

/**
 * General utility methods for manipulating sequences.
 *
 */
public class SequenceUtil {

    private static final Logger logger = TackleTestLogger.getLogger(SequenceUtil.class);

    /**
     * Different sorting criteria for sequence sets
     */
    enum SequenceSetSort {
        SEQUENCE_SIZE,
        SEQUENCE_CODE_STRING_LENGTH
    }

    /**
     * Creates a new sequence that is a subsequence of the given sequence,
     * containing statements between start index (inclusive) and end index
     * (exclusive).
     *
     * @param seq
     * @param startIndex
     * @param endIndex
     * @return
     */
    static Sequence createSubsequence(Sequence seq, int startIndex, int endIndex) {
        SimpleArrayList<Statement> stmtList = new SimpleArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            stmtList.add(seq.getStatement(i));
        }
        return new Sequence(stmtList);
    }

    /**
     * Concatenates the suffix sequence to the given sequence and returns the extended sequence
     * @param seq
     * @param suffixSeq
     * @return
     */
    public static Sequence concatenate(Sequence seq, Sequence suffixSeq) {
        int seqSize = seq.size();
        for (int i = 0; i < suffixSeq.size(); i++) {
            Sequence finalSeq = seq;
            List<Variable> inputVars = suffixSeq.getInputs(i).stream()
                .map(var -> new Variable(finalSeq, var.getDeclIndex() + seqSize))
                .collect(Collectors.toList());
            seq = seq.extend(suffixSeq.getStatement(i), inputVars);
        }
        return seq;
    }

    /**
     * Creates a new sequence set as a sorted set on sequence size
     * @return
     */
    static SortedSet<Sequence> newSequenceSet(SequenceSetSort sortCriterion) {
        if (sortCriterion == SequenceSetSort.SEQUENCE_SIZE) {
            return new TreeSet<Sequence>(Comparator.comparingInt(s -> s.size()));
        }
        else if (sortCriterion == SequenceSetSort.SEQUENCE_CODE_STRING_LENGTH) {
            return new TreeSet<Sequence>(Comparator.comparingInt(s -> s.toCodeString().length()));
        }
        throw new RuntimeException("Unknown sequence set sort criterion: "+sortCriterion);
    }

    /**
     * Performs type substitution on the given typed operation if the output type is generic and
     * contains type parameters. Replaces type parameters in the operation with instantiated type
     * (java.lang.Object).
     *
     * @param typedOperation operation to perform type substitution on
     * @return updated operation with type parameters replaced with instantiated types
     */
    static TypedOperation performTypeSubstitution(TypedOperation typedOperation) {
        Type outputType = typedOperation.getOutputType();
        if (outputType.isGeneric()) {
            List<TypeVariable> typeVars = ((GenericClassType)outputType).getTypeParameters();
            List<ReferenceType> typeRefs = typeVars.stream()
                .map(typeVar -> ReferenceType.forClass(Object.class))
                .collect(Collectors.toList());
            Substitution typeSubst = new Substitution(typeVars, typeRefs);
            typedOperation = typedOperation.substitute(typeSubst);
        }
        return typedOperation;
    }

    /**
     * Performs type substitution on generic output types of statements in the given sequence
     * and returns a new sequence with type parameters replaced with instantiated type
     * (java.lang.Object).
     *
     * @param sequence
     * @return
     */
    static Sequence performTypeSubstitution(Sequence sequence) {
        Sequence newSequence = new Sequence();
        for (int i = 0; i < sequence.size(); i++) {
            TypedOperation typedOperation = sequence.getStatement(i).getOperation();
            typedOperation = performTypeSubstitution(typedOperation);
            Sequence finalSeq = newSequence;
            List<Variable> inputVars = sequence.getInputs(i).stream()
                .map(var -> new Variable(finalSeq, var.getDeclIndex()))
                .collect(Collectors.toList());
            newSequence = newSequence.extend(typedOperation, inputVars);
        }
        return newSequence;
    }


    /**
     * Checks whether the given sequence covers the given test plan row
     * @param testPlanRow
     * @param sequence
     * @return
     */
    public static boolean isTestPlanRowCoveredBySequence(ArrayNode testPlanRow, Sequence sequence) {
        // build list of parameter types for the last method call in the sequence
        List<Type> methodCallParamTypes = new ArrayList<>();
        TypedOperation methodcallOper = sequence.getStatement(sequence.size() - 1).getOperation();
        methodcallOper.getInputTypes().forEach(type -> methodCallParamTypes.add(type));

        // if any param type is a collection or map type or an array of non-primitive types,
        // return false; in such cases, the test plan would typically require objects of specific
        // types to be added to the collection/map/array
        for (Type paramType : methodCallParamTypes) {
            if (isCollectionType(paramType) || isMapType(paramType))  {
                // TODO: check parameter/argument types of collection/map
                return false;
            }
            if (paramType.isArray()) {
                Type elemType = ((ArrayType)paramType).getElementType();
                if (!(elemType.isPrimitive() || elemType.isBoxedPrimitive() || elemType.isString())) {
                    return false;
                }
            }
        }
        // build list of parameter type names
        List<String> methodCallParamTypeNames = methodCallParamTypes.stream()
            .map(type -> type.getBinaryName())
            .collect(Collectors.toList());

        // remove "this" parameter for virtual calls
        if (!methodcallOper.isStatic() && !methodcallOper.isConstructorCall()) {
            methodCallParamTypeNames.remove(0);
        }

        // build list of param types specified in the test plan row
        List<String> testPlanRowTypes = new ArrayList<>();
        testPlanRow.elements().forEachRemaining(entry -> {
        	testPlanRowTypes.add(entry.get("type").asText());
        });
        	
        // if the two lists are equal, the sequence covers the test plan row
        if (methodCallParamTypeNames.equals(testPlanRowTypes)) {
            return true;
        }
        return false;
    }
    
    public static boolean hasCompoundTypes(ArrayNode modelRows) {
    	
    	Iterator<JsonNode> iter = modelRows.elements();
    	
    	while (iter.hasNext()) {
    		
    		JsonNode node = iter.next();
    		
    		JsonNode name = node.get("attribute_name");
    		
    		if (name != null) {
    			String attrName = name.asText();
    			if (attrName.endsWith(Constants.LIST_TAG) || attrName.endsWith(Constants.MAP_KEY_TAG) || 
    					attrName.endsWith(Constants.MAP_VALUE_TAG)) {
    				return true;
    				
    			}
    		}
    	}
    	
        return false;
    }

    /**
     * Finds and returns signature of the target method or constructor call for the given sequence.
     * The target call is identified as the last method or constructor call that occurs in the
     * sequence (ignoring calls to assert methods).
     * @param sequence
     * @return
     */
    public static String getTargetMethod(Sequence sequence) {
        // iterate in reverse order over each statement in the sequence
        for (int i = sequence.size() - 1; i >= 0; i--) {
            CallableOperation callableOper = sequence.getStatement(i).getOperation().getOperation();
            // skip calls to assert methods
            if (callableOper.getName().startsWith("assert")) {
                continue;
            }
            // check whether call is to a method or constructor and return signature
            try {
                if (callableOper.isMethodCall()) {
                    return Utils.getSignature(((MethodCall) callableOper).getMethod());
                } else {
                    return Utils.getSignature(((ConstructorCall) callableOper).getConstructor());
                }
            }
            catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    static boolean isCollectionType(Type paramType) {
        Type collType = Type.forClass(Collection.class);
        if (paramType.isSubtypeOf(collType)) {
            return true;
        }
        return false;
    }

    static boolean isMapType(Type paramType) {
        Type mapType = Type.forClass(Map.class);
        Type dictType = Type.forClass(Dictionary.class);
        if (paramType.isSubtypeOf(mapType) || paramType.isSubtypeOf(dictType)) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether the given operation (method/constructor call or field access) occurs on a
     * non-public class member
     * @param operation
     * @return
     */
    static boolean isNonPublicMemberOperation(CallableOperation operation) {
        AccessibleObject accObj = operation.getReflectionObject();
        if (accObj instanceof Member) {
            int modifiers = ((Member)accObj).getModifiers();
            return !Modifier.isPublic(modifiers);
        }
        return false;
    }

    /**
     * Checks whether the given sequence contains an operation (method/constructor call or field access)
     * on a non-public class member
     * @param sequence
     * @return
     */
    static boolean containsNonPublicMemberOperation(Sequence sequence) {
        for (int i = 0; i < sequence.size(); i++) {
            CallableOperation operation = sequence.getStatement(i).getOperation().getOperation();
            if (isNonPublicMemberOperation(operation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a primitive assignment statement for the given type and adds the statement to the
     * given sequence.
     * @param type Primitive type to create assignment statement for
     * @param seq Sequence to be extended with the assignment statement
     * @return Updated sequence with primitive assignment appended
     */
    static Sequence addPrimitiveAssignment(Type type, Sequence seq, SequencePool sequencePool) {
        Object val = sequencePool.primitiveValuePool.getRandomValueOfType(type.getBinaryName());
        logger.info("Creating primitive/string value assignment statement");
        TypedOperation primAssign = TypedOperation.createPrimitiveInitialization(type, val);
        return seq.extend(primAssign);
    }

    /**
     * Creates an enum assignment statement for the given enum type by selecting the first value
     * from the list of defined enum values; adds the assignment statement to the given sequence.
     * @param typeName Name of enum type to create assignment statement for
     * @param seq
     * @return Updated sequence with enum assignment appended
     * @throws ClassNotFoundException
     * @throws OperationParseException
     */
    static Sequence addEnumAssignment(String typeName, Sequence seq)
        throws ClassNotFoundException, OperationParseException {
        logger.info("Creating assignment statement for enum type");
        Class<?> enumCls = Class.forName(typeName);
        List<?> enumValues = Arrays.asList(enumCls.getEnumConstants());
        TypedOperation enumAssign = EnumConstant.parse(typeName +":"+enumValues.get(0).toString());
        return seq.extend(enumAssign);
    }

    /**
     * Creates a null-assignment statement for the given type and appends it to the given sequence.
     * @param type Type for which to create null assignment
     * @param seq Sequence to be extended with null assignment
     * @return
     */
    static Sequence addNullAssignment(Type type, Sequence seq) {
        TypedOperation nullAssignStmt = TypedOperation.createNullOrZeroInitializationForType(type);
        return seq.extend(nullAssignStmt);
    }

    /**
     * Given a sequence that ends at a virtual method call, returns the subsequence
     * that creates the receiver object for that call.
     *
     * @return
     */
    static Sequence getConstructorSubsequence(Sequence seq) {
        // traverse backward until the statement that defines the receiver type is reached
        // TODO: Do not remove statements that assign values to parameters of primitive types
        String receiverVar = seq.getInputs(seq.size() - 1).get(0).getName();
        logger.fine("Receiver var for last method call in seq: " + receiverVar);
        int endIndex = 0;
        for (int i = seq.size() - 2; i >= 0; i--) {
            String defVar = seq.getVariable(i).getName();
            logger.fine("DefVar: " + defVar);
            if (defVar.equals(receiverVar)) {
                endIndex = i + 1;
                break;
            }
        }
        if (endIndex > 0) {
            logger.info("Computing subsequence 0:" + endIndex);
            return createSubsequence(seq, 0, endIndex);
        }
        throw new RuntimeException(
            "Construct call that assigns to receiver variable " + receiverVar + " not found in sequence: " + seq);
    }

    /**
     * Selects a sequence randomly from the given set of sequences
     *
     * @param seqSet
     * @return
     */
    static Sequence selectFromSequenceSet(SortedSet<Sequence> seqSet) {
        return seqSet.last();
//		return seqSet.stream().skip(new Random().nextInt(seqSet.size())).findFirst().get();
    }

}
