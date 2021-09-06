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

import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import randoop.operation.OperationParseException;
import randoop.operation.TypedClassOperation;
import randoop.operation.TypedOperation;
import randoop.sequence.Sequence;
import randoop.sequence.Variable;
import randoop.types.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class implements a sequence generator for constructing object of a given type.
 */

public class ConstructorSequenceGenerator {

    private static final Logger logger = TackleTestLogger.getLogger(ConstructorSequenceGenerator.class);

    /**
     * Attempts to generate a sequence for instantiating an object of the given type.
     * Sequence generation enumerates the available public constructors for the type, iterates over
     * them in order of the number of constructor parameters, and return the first successfully
     * created sequence. For each constructor, sequence generation is done recursively for each
     * parameter of the constructor if a sequence for the parameter is not found in the sequence pool.
     * @param typeName
     * @param isTestPlanParameter
     * @param sequencePool
     * @param currNestingDepth
     * @return
     */
    static Sequence createConstructorSequence(String typeName, Type type, boolean isTestPlanParameter,
                                              SequencePool sequencePool, int currNestingDepth)
        throws ClassNotFoundException, OperationParseException, NoSuchMethodException {

        Class<?> targetCls = Class.forName(typeName);

        // if target class is a test plan parameter and is interface, abstract, or non-public,
        // generate a null assignment sequence
        int clsModifiers = targetCls.getModifiers();
        if (isTestPlanParameter) {
            if (targetCls.isInterface() || Modifier.isAbstract(clsModifiers) ||
                Modifier.isPrivate(clsModifiers)) {
                return SequenceUtil.addNullAssignment(Type.forClass(targetCls), new Sequence());
            }
        }

        // get all public constructors for the class (declared and inherited)
        Set<Constructor<?>> classCtors = Arrays.stream(targetCls.getDeclaredConstructors())
            .filter(ctor -> (Modifier.isPublic(clsModifiers) && Modifier.isPublic(ctor.getModifiers()))
            		|| ( ! Modifier.isPublic(clsModifiers) && ! Modifier.isPrivate(ctor.getModifiers())))
            .collect(Collectors.toSet());
        classCtors.addAll(Arrays.asList(targetCls.getConstructors()));

        // get parameter counts for constructors, sort constructors by number of parameters,
        // and build map from parameter count to list of constructors
        List<Integer> ctorParamCounts = new ArrayList<>();
        Map<Integer, List<Constructor<?>>> paramCountCtorMap = new HashMap<>();
        for (Constructor<?> ctor : classCtors) {
            int paramCount = ctor.getParameterCount();
            if (!ctorParamCounts.contains(paramCount)) {
                ctorParamCounts.add(paramCount);
                paramCountCtorMap.put(paramCount, new ArrayList<>());
            }
            paramCountCtorMap.get(paramCount).add(ctor);
        }
        Collections.sort(ctorParamCounts);
//		Collections.reverse(ctorParamCounts);

        // iterate over constructors in order of parameter count and attempt to build a
        // constructor sequence; ignore constructors with non-primitive parameter types for which
        // a sequence does not already exist in the class sequence pool
        for (int paramCount : ctorParamCounts) {
            for (Constructor<?> ctor : paramCountCtorMap.get(paramCount)) {
                // check that either of these conditions holds for each parameter type: the type is
                // a primitive type, or (2) there exists a constructor sequence in the class
                // sequence pool for the type
//				if (!validateConstructorParameters(paramTypes)) {
//					continue;
//				}
                Sequence ctorSequence = createSequenceForConstructor(typeName,type, ctor, sequencePool,
                    false, currNestingDepth);
                if (ctorSequence != null) {
                    return ctorSequence;
                }
            }
        }

        // if no sequence could be created, iterate over the constructors again, this time
        // with the option of setting null values for those parameters for which a sequence
        // could not be created
        for (int paramCount : ctorParamCounts) {
            for (Constructor<?> ctor : paramCountCtorMap.get(paramCount)) {
                Sequence ctorSequence = createSequenceForConstructor(typeName, type, ctor, sequencePool,
                    true, currNestingDepth);
                if (ctorSequence != null) {
                    return ctorSequence;
                }
            }
        }

        // sequence could not be created using any of the type's constructors
        return null;
    }

    /**
     * Creates sequence for invoking the given constructor
     * @param typeName
     * @param ctor
     * @param sequencePool
     * @return
     * @throws ClassNotFoundException
     * @throws OperationParseException
     * @throws NoSuchMethodException
     */
    private static Sequence createSequenceForConstructor(String typeName, Type type, Constructor<?> ctor,
                                                         SequencePool sequencePool,
                                                         boolean createDefaultNull,
                                                         int currNestingDepth)
        throws ClassNotFoundException, OperationParseException, NoSuchMethodException {

        // collect parameter types of constructor
        List<Type> paramTypes = Arrays.stream(ctor.getGenericParameterTypes())
            .map(paramType -> Type.forType(paramType))
            .collect(Collectors.toList());

        // initialize sequence
        Sequence ctorSequence = new Sequence();

        // list to store variables holding constructor parameter values
        List<Integer> ctorParamVarsIdx = new ArrayList<>();

        // create sequence for instantiation each constructor parameter
        boolean paramSeqCreated = true;
        for (Type paramType : paramTypes) {
            Sequence extSeq = createConstructorParameter(paramType, ctorSequence, createDefaultNull,
                sequencePool, currNestingDepth);
            if (extSeq.size() > ctorSequence.size()) {
                ctorParamVarsIdx.add(extSeq.getLastVariable().getDeclIndex());
                ctorSequence = extSeq;
            } else {
                // sequence could not be extended for parameter
                logger.warning("Error creating constructor sequence for: " + ctor
                    + "\n    could not create sequence for parameter type " + paramType.getBinaryName());
                paramSeqCreated = false;
                break;
            }
        }

        // if parameter sequence could not be created, try the next constructor
        if (!paramSeqCreated) {
            return null;
        }

        // create list of input vars for constructor call
        Sequence finalCtorSeq = ctorSequence;
        List<Variable> ctorParamVars = ctorParamVarsIdx.stream()
            .map(idx -> finalCtorSeq.getVariable(idx))
            .collect(Collectors.toList());

        // extend sequence with call to constructor after applying capture conversion and
        // type substitution to it
        TypedClassOperation ctorCallOper = TypedOperation.forConstructor(ctor)
            .applyCaptureConversion();
        if (type != null && type instanceof InstantiatedType) {
            ctorCallOper = ctorCallOper.substitute(((InstantiatedType)type).getTypeSubstitution());
        }
        else {
            ctorCallOper = (TypedClassOperation) SequenceUtil.performTypeSubstitution(ctorCallOper);
        }
        ctorSequence = ctorSequence.extend(ctorCallOper, ctorParamVars);

        // add sequence to the class sequence pool and return it
        SortedSet<Sequence> seqSet = SequenceUtil.newSequenceSet(SequenceUtil.SequenceSetSort.SEQUENCE_SIZE);
        seqSet.add(ctorSequence);
        sequencePool.classTestSeqPool.put(typeName, seqSet);
        return ctorSequence;
    }


    /**
     * Extends the given sequence with statements for instantiating the given parameter type
     * (for constructor sequence generation).
     * @param paramType
     * @param sequence
     * @param createDefaultNull
     * @param sequencePool
     * @return
     * @throws ClassNotFoundException
     * @throws OperationParseException
     */
    static Sequence createConstructorParameter(Type paramType, Sequence sequence, boolean createDefaultNull,
                                               SequencePool sequencePool, int currNestingDepth)
        throws ClassNotFoundException, OperationParseException, NoSuchMethodException {

        String typeName = paramType.getRawtype().getBinaryName();

        // primitive type parameter
        if (paramType.isPrimitive() || paramType.isBoxedPrimitive() || paramType.isString()) {
            return SequenceUtil.addPrimitiveAssignment(paramType, sequence, sequencePool);
        }

        // enum type parameter
        if (paramType.isEnum()) {
            return SequenceUtil.addEnumAssignment(typeName, sequence);
        }

        // if array type, create an empty array
        if (paramType.isArray()) {
            ArrayType arrayType = ArrayType.forClass(Type.forFullyQualifiedName(typeName));
            TypedOperation arrayCreateStmt = TypedOperation.createInitializedArrayCreation(arrayType, 0);
            return sequence.extend(arrayCreateStmt);
        }

        // if collection type, create an empty collection object of the specified type
        if (SequenceUtil.isCollectionType(paramType)) {
            List<ReferenceType> typeArgs = ExtenderUtil.getTypeArguments(paramType);
            ReferenceType typeArg = typeArgs.isEmpty() ? null : typeArgs.get(0);
            JavaCollectionTypes.InstantiationInfo instInfo = JavaCollectionTypes
                .getCollectionTypeInstantiationInfo(typeName, typeArg);
            TypedOperation colInstOper = TypedOperation.forConstructor(instInfo.typeConstructor);
            if (instInfo.instantiatedType != null) {
                colInstOper = colInstOper.substitute(instInfo.instantiatedType.getTypeSubstitution());
            }
            return sequence.extend(colInstOper);
        }

        if (SequenceUtil.isMapType(paramType)) {
            List<ReferenceType> typeArgs = ExtenderUtil.getTypeArguments(paramType);
            ReferenceType keyTypeArg = null, valTypeArg = null;
            if (!typeArgs.isEmpty()) {
                keyTypeArg = typeArgs.get(0);
                valTypeArg = typeArgs.get(1);
            }
            JavaCollectionTypes.InstantiationInfo instInfo = JavaCollectionTypes
                .getMapTypeInstantiationInfo(typeName, keyTypeArg, valTypeArg);
            Substitution mapSubst = instInfo.instantiatedType.getTypeSubstitution();
            TypedOperation mapInstOper = TypedOperation.forConstructor(instInfo.typeConstructor)
                .substitute(mapSubst);
            return sequence.extend(mapInstOper);
        }

        // if the type occurs in the class sequence pool, sample a sequence from the pool
        if (sequencePool.classTestSeqPool.containsKey(typeName)) {
            Sequence typeInstSeq = SequenceUtil.selectFromSequenceSet(sequencePool.classTestSeqPool.get(typeName));
            return SequenceUtil.concatenate(sequence, typeInstSeq);
        }

        // if a subtype of the declared type occurs in the class sequence pool, sample a
        // sequence from the available ones
        SortedSet<Sequence> subtypeCtorSeqs = getSubtypeConstructorSequences(typeName, sequencePool);
        if (subtypeCtorSeqs != null) {
            Sequence typeInstSeq = SequenceUtil.selectFromSequenceSet(subtypeCtorSeqs);
            return SequenceUtil.concatenate(sequence, typeInstSeq);
        }

        // recursively attempt to create new constructor sequence if max recursion depth not reached
        // recursive depth is not constrained
        if (Constants.CONSTRUCTOR_SEQUENCE_GEN_MAX_DEPTH == -1 ||
            currNestingDepth < Constants.CONSTRUCTOR_SEQUENCE_GEN_MAX_DEPTH) {
            Sequence typeInstSeq = null;
            if (paramType.isClassOrInterfaceType()) {
                ClassOrInterfaceType clsIntType = (ClassOrInterfaceType) paramType;
                List<Type> potentialInstantiationTypes = new ArrayList<>();
                if (clsIntType.isInterface() || clsIntType.isAbstract()) {
                    // TODO: get all implementing classes for interface and all non-abstract subclasses for abstract class
                    potentialInstantiationTypes.addAll(getConcreteTypes(clsIntType));
                } else {
                    potentialInstantiationTypes.add(paramType);
                }

                // iterate over the list of instantiable types and attempt to create constructor sequence
                for (Type type : potentialInstantiationTypes) {
                    typeInstSeq = createConstructorSequence(type.getRawtype().getBinaryName(), type,false,
                        sequencePool, currNestingDepth + 1);
                    if (typeInstSeq != null) {
                        return SequenceUtil.concatenate(sequence, typeInstSeq);
                    }
                }
            }
        }

        // if nothing succeeds and create null option specified, create a null assignment statement for parameter
        if (createDefaultNull) {
            return SequenceUtil.addNullAssignment(paramType, sequence);
        }

        return sequence;
    }

    /**
     * Returns the set of subtype constructor sequences from the sequence pool for the given type.
     * @param typeName
     * @param sequencePool
     * @return
     * @throws ClassNotFoundException
     */
    private static SortedSet<Sequence> getSubtypeConstructorSequences(String typeName, SequencePool sequencePool)
        throws ClassNotFoundException {

        // get all subtypes of the given type for which constructor sequences exist in the class sequence pool
        Class<?> targetCls = Class.forName(typeName);
        SortedSet<String> subtypesWithCtorSeqs = new TreeSet<>();
        for (String clsName : sequencePool.classTestSeqPool.keySet()) {
            Class<?> seqPoolCls = Class.forName(clsName);
            if (targetCls.isAssignableFrom(seqPoolCls)) {
                subtypesWithCtorSeqs.add(seqPoolCls.getName());
            }
        }

        // return null if no such sequence exists
        if (subtypesWithCtorSeqs.size() == 0) {
            return null;
        }

        // return the constructor sequences for a remaining subtype
        return sequencePool.classTestSeqPool.get(subtypesWithCtorSeqs.first());
    }

    private static List<Type> getConcreteTypes(ClassOrInterfaceType clsIntType) {
        return new ArrayList<>();
    }

    /**
     * Checks that either of these conditions holds for rach parameter type in the
     * given array of (constructor) parameter types: (1) the parameter type is a
     * primitive type or (2) a constructor sequence for the parameter type in the
     * class sequence pool.
     *
     * @param paramTypes
     * @return
     * @throws ClassNotFoundException
     */
//	private static boolean validateConstructorParameters(List<Type> paramTypes) throws ClassNotFoundException {
//		boolean valResult = true;
//		for (Type paramType : paramTypes) {
//			if (paramType.isPrimitive() || paramType.isBoxedPrimitive() || paramType.isString()) {
//				continue;
//			}
//			if (!this.sequencePool.classTestSeqPool.containsKey(paramType.getBinaryName())) {
//				valResult = false;
//				break;
//			}
//		}
//		return valResult;
//	}

}
