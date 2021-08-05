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

package org.konveyor.tackle.testgen.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import soot.FastHierarchy;
import soot.Scene;
import soot.SootClass;

/**
 * Holds and computes information related to target methods
 * @author RACHELBRILL
 *
 */

public class JavaMethodModel {

    final String targetPartition;
    final Class<?> targetClass;
    final SootClass targetSootClass;
    // target method can be either a java reflection Constructor or Method
    final Object targetMethod;
	private final FastHierarchy classHierarchy;

	private final int maxCollectionDepth;

	private static final String CLASS_NAME_PATTERN = "(([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*)";
	private static final String EXTEND_STRING = "\\? extends "+CLASS_NAME_PATTERN;
	private static final Pattern EXTEND_PATTERN = Pattern.compile(EXTEND_STRING);
	private static final String SUPER_STRING = "\\? super "+CLASS_NAME_PATTERN;
	private static final Pattern SUPER_PATTERN = Pattern.compile(SUPER_STRING);
	private static final String JAVA_OBJECT_NAME = "java.lang.Object";
	private final URLClassLoader classLoader;
	static final String LIST_TAG = "_list";
	static final String MAP_KEY_TAG = "_key";
	static final String MAP_VALUE_TAG = "_value";
	
	private static final Logger logger = TackleTestLogger.getLogger(JavaMethodModel.class);

	private static class CollectionTypeInfo {

		private Class<?> parentListType = null;
		private Class<?> parentMapType = null;
		private List<Class<?>> listTypes;
		private List<Class<?>> mapKeyTypes;
		private List<Class<?>> mapValueTypes;

		private Type[] mapTypeParamForKey = null;
		private Type[] mapTypeParamForValue = null;

		private CollectionTypeInfo() {
			listTypes = new ArrayList<Class<?>>();
			mapKeyTypes = new ArrayList<Class<?>>();
			mapValueTypes = new ArrayList<Class<?>>();
		}
	}

	JavaMethodModel(String partition, Class<?> theClass, Object method, URLClassLoader classLoader, int maxDepth)
			throws IllegalArgumentException {

		if ( ! (method instanceof Method || method instanceof Constructor)) {
			throw new IllegalArgumentException("Method argument must be a Method or Constructor type");
		}

		targetPartition = partition;
		targetClass = theClass;
		targetSootClass = Scene.v().loadClassAndSupport(theClass.getName());
		targetMethod = method;
		this.classLoader = classLoader;
		maxCollectionDepth = maxDepth;
		classHierarchy = Scene.v().getOrMakeFastHierarchy();
	}

	String getSignature() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

		if (targetMethod instanceof Method) {
			return Utils.getSignature((Method) targetMethod);
		} else {
			return Utils.getSignature((Constructor<?>) targetMethod);
		}
	}

	String getFormattedSignature() throws SecurityException, IllegalArgumentException {

		StringBuilder sb = new StringBuilder();

		if (targetMethod instanceof Method) { // for constructors we don't need the return type
			Class<?> returnType = ((Method) targetMethod).getReturnType();
			sb.append(returnType==void.class? "void": returnType.getTypeName());
			sb.append(" ");
		}

		sb.append(getName()+"(");
		for(Class<?> c : getParameterClasses()) {
			sb.append(c.getTypeName());
			sb.append(',');
		}
		if (sb.toString().endsWith(",")) {
			sb.deleteCharAt(sb.length()-1); // remove last comma
		}
		sb.append(')');
	    return sb.toString();
	}

	Type[] getParameterTypes() {
		if (targetMethod instanceof Method) {
			return ((Method) targetMethod).getGenericParameterTypes();
		} else {
			return ((Constructor<?>) targetMethod).getGenericParameterTypes();
		}
	}

	Class<?>[] getParameterClasses() {
		if (targetMethod instanceof Method) {
			return ((Method) targetMethod).getParameterTypes();
		} else {
			return ((Constructor<?>) targetMethod).getParameterTypes();
		}
	}

	String getName() {
		if (targetMethod instanceof Method) {
			return ((Method) targetMethod).getName();
		} else {
			return ((Constructor<?>) targetMethod).getName();
		}
	}

	TypeVariable<?>[] getTypeParameters() {
		if (targetMethod instanceof Method) {
			return ((Method) targetMethod).getTypeParameters();
		} else {
			return ((Constructor<?>) targetMethod).getTypeParameters();
		}
	}

	Set<Class<?>> getAllConcreteTypes(Class<?> paramType, TypeAnalysisResults typeAnalysisResults) throws ClassNotFoundException {

		Set<Class<?>>  resultTypes = typeAnalysisResults.getSubClasses(paramType);

		if (resultTypes != null) {
			return resultTypes;
		}
		
		SootClass paramClass = Scene.v().getSootClass(paramType.getName());
		Set<SootClass> classes = new HashSet<>(classHierarchy.getSubclassesOf(paramClass));
		classes.addAll(classHierarchy.getAllImplementersOfInterface(paramClass));
		
		resultTypes = getRelevantTypes(paramClass, classes, typeAnalysisResults);
		
		typeAnalysisResults.setSubClasses(paramType, resultTypes);
		
		return resultTypes;
	}

	private Set<Class<?>> getAllSuperTypes(Class<?> paramType, TypeAnalysisResults typeAnalysisResults) throws ClassNotFoundException {

		Set<Class<?>>  resultTypes = typeAnalysisResults.getSuperClasses(paramType);

		if (resultTypes != null) {
			return resultTypes;
		}

		resultTypes = new HashSet<>();
		SootClass paramClass = Scene.v().getSootClass(paramType.getName());
		Set<SootClass> classes = getAllSuperclasses(paramClass);
		
		resultTypes = getRelevantTypes(paramClass, classes, typeAnalysisResults);

		typeAnalysisResults.setSuperClasses(paramType, resultTypes);

		return resultTypes;
	}
	
	private Set<Class<?>> getRelevantTypes(SootClass paramClass, Set<SootClass> classes, TypeAnalysisResults typeAnalysisResults) throws ClassNotFoundException {
		
		Set<Class<?>> resultTypes = new HashSet<>();
		Set<Class<?>> allConcreteTypes = new HashSet<>();
		for (SootClass currentSootClass : classes) {
			
			Class<?> currentClass;
			try {
				currentClass = classLoader.loadClass(currentSootClass.toString());
			} catch (ClassNotFoundException | NoClassDefFoundError e) {
				logger.warning(e.getMessage());
				continue;
			}

			if (currentSootClass.isConcrete() && Modifier.isPublic(currentClass.getModifiers())) {
				allConcreteTypes.add(currentClass);
				if (typeAnalysisResults.inRTAResults(currentSootClass.getName())) {
					resultTypes.add(currentClass);
				}
			}
		}
		
		Class<?> theParamClass = classLoader.loadClass(paramClass.toString());
		
		if (paramClass.isConcrete() && Modifier.isPublic(paramClass.getModifiers())) {
			allConcreteTypes.add(theParamClass);
			resultTypes.add(theParamClass);
		}
		
		if (allConcreteTypes.isEmpty()) {
			logger.warning("No concrete classes found in hierarchy of "+paramClass.getName());
			// use the formal abstract and/or non-public type of the param 
			resultTypes.add(theParamClass);
		} else {
		
			if (resultTypes.isEmpty()) {
				// use only CHA results
				resultTypes.addAll(allConcreteTypes);
			}
		}

		return resultTypes;
	}

	private Set<SootClass> getAllSuperclasses(SootClass theClass) {
		Set<SootClass> superClasses = new HashSet<SootClass>();

		SootClass parentClass = theClass.getSuperclass();

		while (parentClass != null && ! parentClass.getName().equals(JAVA_OBJECT_NAME)) {

			superClasses.add(parentClass);
			parentClass = parentClass.getSuperclass();
		}

		return superClasses;
	}


	static boolean isCollection(Class<?> paramType) {

		return paramType.isArray() || Collection.class.isAssignableFrom(paramType) || Map.class.isAssignableFrom(paramType);
	}

	List<ModelAttribute> getCollectionAttributes(Class<?> paramClass, Type paramType, int ind, TypeAnalysisResults typeAnalysisResults,
                                                 Class<?> cls, TypeVariable<?>[] methodTypeParameters)
			throws ClassNotFoundException {

		List<ModelAttribute> params = new ArrayList<ModelAttribute>();

		Type[] genParTypes = null;
		if (paramType instanceof ParameterizedType) {
			genParTypes = ((ParameterizedType) paramType).getActualTypeArguments();
		} else if (paramType instanceof GenericArrayType) {
			genParTypes = new Type[]{((GenericArrayType) paramType).getGenericComponentType()};
		} else if (paramClass.isArray()) {
			genParTypes = new Type[]{paramClass.getComponentType()};
        } else if (Collection.class.isAssignableFrom(paramClass)) {
        	genParTypes = new Type[]{Class.forName(JAVA_OBJECT_NAME)};
        } else if  (Map.class.isAssignableFrom(paramClass)) {
        	genParTypes = new Type[]{Class.forName(JAVA_OBJECT_NAME), Class.forName(JAVA_OBJECT_NAME)};
        } else {
        	throw new RuntimeException("Unrecognized collection type: "+paramClass.getName());
        }

		CollectionTypeInfo possibleTypesInfo = typeInferenceForCollection(paramClass, genParTypes, typeAnalysisResults, cls, methodTypeParameters);

		List<Class<?>> possibleTypes = new ArrayList<Class<?>>();

		if (possibleTypesInfo.parentListType != null) {
			possibleTypes.add(possibleTypesInfo.parentListType);
		}
		if (possibleTypesInfo.parentMapType != null) {
			possibleTypes.add(possibleTypesInfo.parentMapType);
		}

		String attrName = "attr_"+ind;

		if (possibleTypesInfo.listTypes.isEmpty() && (possibleTypesInfo.mapKeyTypes.isEmpty() || possibleTypesInfo.mapValueTypes.isEmpty())) {
			// no instantiation for collection types, return an empty param so we skip this method
			params.add(new ModelAttribute(Collections.emptyList(), Collections.emptyMap(), attrName));
			return params;
		}

		params.add(new ModelAttribute(possibleTypes, Collections.emptyMap(), attrName));

		if ( ! possibleTypesInfo.listTypes.isEmpty()) {
			getCollectionAttributes(possibleTypesInfo.listTypes, params, 1, attrName+LIST_TAG, typeAnalysisResults,
					possibleTypesInfo.mapTypeParamForKey, cls, methodTypeParameters);
		}


		// It can be the case that only key or value has types due to rapid type analysis
		if ( ! possibleTypesInfo.mapKeyTypes.isEmpty() && ! possibleTypesInfo.mapValueTypes.isEmpty()) {
			getCollectionAttributes(possibleTypesInfo.mapKeyTypes, params, 1, attrName+MAP_KEY_TAG, typeAnalysisResults,
					possibleTypesInfo.mapTypeParamForKey, cls, methodTypeParameters);
			getCollectionAttributes(possibleTypesInfo.mapValueTypes, params, 1, attrName+MAP_VALUE_TAG, typeAnalysisResults,
					possibleTypesInfo.mapTypeParamForValue, cls, methodTypeParameters);
		}

		return params;
	}

	private CollectionTypeInfo typeInferenceForCollection(Class<?> paramClass, Type[] paramType, TypeAnalysisResults typeAnalysisResults,
			Class<?> cls, TypeVariable<?>[] methodTypeParams) throws ClassNotFoundException {

		CollectionTypeInfo typeInfo = new CollectionTypeInfo();

		// We need to assign type params for key and value before handling arrays, because an array can still contain key and value types for its component type

		if (paramType[0] instanceof ParameterizedType) {
			typeInfo.mapTypeParamForKey = ((ParameterizedType) paramType[0]).getActualTypeArguments();
		}

		if (paramType.length > 1 && paramType[1] instanceof ParameterizedType) {
			typeInfo.mapTypeParamForValue = ((ParameterizedType) paramType[1]).getActualTypeArguments();
		}

		if (paramClass.isArray()) {
			typeInfo.parentListType = paramClass;
			Class<?> componentType = paramClass.getComponentType();
			if (componentType.isPrimitive()) {
				typeInfo.listTypes.add(componentType);
			} else if (componentType.isArray()) {
				typeInfo.mapTypeParamForKey = new Type[]{componentType.getComponentType()};
				typeInfo.listTypes.add(componentType);
			} else {
				typeInfo.listTypes.addAll(getAllConcreteTypes(componentType, typeAnalysisResults));
			}
			return typeInfo; // return now cause it cannot be another collection
		}

		String keyTypeClassName = paramType[0].getTypeName();
		String valueTypeClassName = paramType.length > 1? paramType[1].getTypeName() : null;

		boolean[] keyIsSuper = new boolean[] {false};
		boolean[] valueIsSuper = new boolean[] {false};

		keyTypeClassName = resolveTypeParamClass(keyTypeClassName, cls, methodTypeParams, keyIsSuper);

		if (valueTypeClassName != null) {

			valueTypeClassName = resolveTypeParamClass(valueTypeClassName, cls, methodTypeParams, valueIsSuper);
		}

		Class<?> keyClass;

		if (checkIfCollectionParam(keyTypeClassName)) {
			String collectionType = getCollectionType(keyTypeClassName);
			keyClass = Class.forName(collectionType, false, classLoader);
		} else {
			keyClass = Class.forName(keyTypeClassName, false, classLoader);
		}

		if (isSubClassOrClass(paramClass, Collection.class)) {
			typeInfo.parentListType = paramClass;

			initTypeInfo(keyClass, null, typeInfo.listTypes, null,
					keyIsSuper[0], valueIsSuper[0], typeAnalysisResults);

			return typeInfo;
		}

		if (isSuperClassOrClass(paramClass, Collection.class)) {
			typeInfo.parentListType = Class.forName("java.util.Collection");

			initTypeInfo(keyClass, null, typeInfo.listTypes, null,
					keyIsSuper[0], valueIsSuper[0], typeAnalysisResults);
		}

		Class<?> valueClass = null;

		if (valueTypeClassName != null) {
			if (checkIfCollectionParam(valueTypeClassName)) {
				String collectionType = getCollectionType(valueTypeClassName);
				valueClass = Class.forName(collectionType, false, classLoader);
			} else {
				valueClass = Class.forName(valueTypeClassName, false, classLoader);
			}
		}

		if (isSubClassOrClass(paramClass, Map.class)) {
			typeInfo.parentMapType = paramClass;

			initTypeInfo(keyClass, valueClass, typeInfo.mapKeyTypes, typeInfo.mapValueTypes,
					keyIsSuper[0], valueIsSuper[0], typeAnalysisResults);

			return typeInfo; // return now cause it cannot be also a super class of Map
		}


		if (isSuperClassOrClass(paramClass, Map.class)) {
			typeInfo.parentMapType = Class.forName("java.util.Map");

			initTypeInfo(keyClass, valueClass, typeInfo.mapKeyTypes, typeInfo.mapValueTypes,
					keyIsSuper[0], valueIsSuper[0], typeAnalysisResults);
		}

		return typeInfo;
	}

	private void initTypeInfo(Class<?> keyClass, Class<?> valueClass,
			List<Class<?>> possibelTypesKey, List<Class<?>> possibelTypesValue,
			boolean keyIsSuper, boolean valueIsSuper, TypeAnalysisResults typeAnalysisResults)
					throws ClassNotFoundException {

		if (keyClass.isArray()) {
			possibelTypesKey.add(keyClass);
		} else if (keyIsSuper) {
			possibelTypesKey.addAll(getAllSuperTypes(keyClass, typeAnalysisResults));
		} else {
			possibelTypesKey.addAll(getAllConcreteTypes(keyClass, typeAnalysisResults));
		}

		if (valueClass != null) {

			if (valueClass.isArray()) {
				possibelTypesValue.add(valueClass);
			} else if (valueIsSuper) {
				possibelTypesValue.addAll(getAllSuperTypes(valueClass, typeAnalysisResults));
			} else {
				possibelTypesValue.addAll(getAllConcreteTypes(valueClass, typeAnalysisResults));
			}
		}
	}

	private String resolveTypeParamClass(String paramName, Class<?> cls, TypeVariable<?>[] methodTypeParams, boolean[] isSuper) {

		String typeParamName = paramName;

		if (typeParamName.equals("?") || typeParamName.equals("E") ||
				typeParamName.equals("? extends E") || Utils.isTypeParamOfClass(typeParamName, cls) || Utils.isTypeParam(typeParamName, methodTypeParams)) {
			typeParamName = JAVA_OBJECT_NAME;

		} else {
			String result = checkIsPattern(typeParamName, EXTEND_PATTERN);
			if (result == null) {
				result = checkIsPattern(typeParamName, SUPER_PATTERN);
				if (result != null) {
					isSuper[0] = true;
				}
			}
			if (result != null) {
				if (Utils.isTypeParamOfClass(result, cls) || Utils.isTypeParam(result, methodTypeParams)) {
					typeParamName = JAVA_OBJECT_NAME;
				} else {
					typeParamName = result;
				}
			}
		}

		return typeParamName;
	}

	private static String checkIsPattern(String text, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		if (matcher.matches()) {
			return matcher.group(1);
		}
		return null;
	}

	private static boolean checkIfCollectionParam(String type) {
		return (type.contains("<") && type.contains(">")) || type.contains("[]");
	}

	private static String getCollectionType(String type) {
		if (type.contains("<")) {
			return type.substring(0, type.indexOf('<'));
		}
		return "[L" + type.substring(0, type.indexOf('[')) + ";"; // must be an array
	}

	private void getCollectionAttributes(List<Class<?>> paramClasses, List<ModelAttribute> params, int depth, String attrName,
                                         TypeAnalysisResults typeAnalysisResults, Type[] typeParam, Class<?> cls, TypeVariable<?>[] methodTypeParams)
					throws ClassNotFoundException {

		List<Class<?>> valueTypes = new ArrayList<Class<?>>();
		List<Class<?>> collectionTypes = new ArrayList<Class<?>>();

		for (Class<?> currentClass : paramClasses) {
			if  (isCollection(currentClass)) {
				if (depth < maxCollectionDepth) {
					collectionTypes.add(currentClass);
					valueTypes.add(currentClass);
				}
			} else {
				valueTypes.add(currentClass);
			}
		}

		if (valueTypes.isEmpty()) {
			// Reached depth limit but only collection types are allowed
			return;
		}

		// TODO: put actual labels map
		params.add(new ModelAttribute(valueTypes, Collections.emptyMap(), attrName, typeParam));

		// collectionTypes is not empty at this point only if depth is not reached and there are collection types in possible types
		// We check only the first collection. Based on it, we create matching next level attributes for all collection types in upper levels.
		// The reason we can do that is that regardless of the concrete collection type, the types of objects inside the collection are always the same.
		if ( ! collectionTypes.isEmpty()) {
			Class<?> currentTypeClass = collectionTypes.get(0);
			if (typeParam == null) {
				// Can happen with Object extended by a collection

				if (currentTypeClass.isArray()) {
					typeParam = new Type[1];
					typeParam[0] = currentTypeClass.getComponentType();
				} else if (currentTypeClass.getGenericSuperclass() instanceof ParameterizedType) {
					typeParam = ((ParameterizedType) currentTypeClass
                        .getGenericSuperclass()).getActualTypeArguments();
				} else {
					int nTypes = currentTypeClass.getTypeParameters().length;
					if (nTypes == 0) {
						if (Collection.class.isAssignableFrom(currentTypeClass)) {
							nTypes = 1;
						} else if (Map.class.isAssignableFrom(currentTypeClass)) {
							nTypes = 2;
						} else {
							throw new IllegalArgumentException("Unsupported collection type with undetectable type parameters: "+currentTypeClass.getName());
						}
					}
					typeParam = new Type[nTypes];
					Type objType = Class.forName(JAVA_OBJECT_NAME);
					Arrays.fill(typeParam, objType);
				}
			}
			CollectionTypeInfo typeInfo = typeInferenceForCollection(currentTypeClass, typeParam, typeAnalysisResults, cls, methodTypeParams);
			if ( ! typeInfo.listTypes.isEmpty()) {
				getCollectionAttributes(typeInfo.listTypes, params, depth+1, attrName+LIST_TAG, typeAnalysisResults, typeInfo.mapTypeParamForKey,
						cls, methodTypeParams);
			}
			if ( ! typeInfo.mapKeyTypes.isEmpty() && ! typeInfo.mapValueTypes.isEmpty()) {
				getCollectionAttributes(typeInfo.mapKeyTypes, params, depth+1, attrName+MAP_KEY_TAG, typeAnalysisResults,
						typeInfo.mapTypeParamForKey, cls, methodTypeParams);
				getCollectionAttributes(typeInfo.mapValueTypes, params, depth+1, attrName+MAP_VALUE_TAG, typeAnalysisResults,
						typeInfo.mapTypeParamForValue, cls, methodTypeParams);
			}
		}
	}

	static boolean isSubClassOrClass(Class<?> paramClass, Class<?> theClass) {
		return theClass.isAssignableFrom(paramClass);
	}

	static boolean isSuperClassOrClass(Class<?> paramClass, Class<?> theClass) {
		return paramClass.isAssignableFrom(theClass);
	}

	static boolean isUtilType(String paramName, String refactorPrefix) {
		return (refactorPrefix != null && 
				paramName.startsWith(refactorPrefix));
	}
}
