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

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import edu.uta.cse.fireeye.common.Parameter;
import edu.uta.cse.fireeye.common.Relation;
import edu.uta.cse.fireeye.common.SUT;
import edu.uta.cse.fireeye.common.TestGenProfile;
import edu.uta.cse.fireeye.common.TestSet;
import edu.uta.cse.fireeye.service.engine.IpoEngine;
import randoop.org.apache.commons.io.output.NullPrintStream;

/**
 * Collection/non-Collection, user versus non-user types, and remove versus local types.
 *
 * @author RACHELBRILL
 *
 */

class CTDModeler {

	private CTDTestPlanGenerator.TargetFetcher targetClassesFetcher;

	private static final Logger logger = TackleTestLogger.getLogger(CTDModeler.class);

	CTDModeler(CTDTestPlanGenerator.TargetFetcher fetcher) {
		targetClassesFetcher = fetcher;
	}


	public int analyzeParams(JavaMethodModel method, TypeAnalysisResults typeAnalysisResults, JsonObjectBuilder models, boolean addLocalRemoteTag,
                             int interactionLevel, Class<?> cls)
			throws ClassNotFoundException, IOException, SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException {

		Type[] paramTypes = method.getParameterTypes();

		if (paramTypes.length == 0) {
			return 0; // nothing to do here
		}

		Class<?>[] paramClasses = method.getParameterClasses();

		// Define a new CTD model for the current method

		SUT methodModel = new SUT(method.proxyPartition + "::" + method.proxySootClass.getName()+"::"+method.getFormattedSignature());

		int attrInd=0;
		int paramInd=0;

		boolean isCollection;

		// Go over each parameter of the method and define one (or more in case of a collection) CTD attributes for it

		for (Type paramType : paramTypes) {

			isCollection = false;

			Class<?> paramClass = paramClasses[paramInd++];

			List<ModelAttribute> params;

			/* Order of checks is important for their correctness */

			if (Utils.isPrimitive(paramClass) || JavaMethodModel.isUtilType(paramType.getTypeName())) {
				params = Collections.singletonList(new ModelAttribute(Collections.singletonList(paramClass), Collections.emptyMap(), "attr_"+attrInd));
			} else if (JavaMethodModel.isCollection(paramClass)) {
				isCollection = true;
				params = method.getCollectionAttributes(paramClass, paramType, attrInd, typeAnalysisResults, cls, method.getTypeParameters());
			} else { // user type or Java type that may have user type extensions
				List<Class<?>> concreteClasses = new ArrayList<Class<?>>(method.getAllConcreteTypes(paramClass, typeAnalysisResults));

				if (concreteClasses.isEmpty()) {
					logger.warning("Method "+method.getName()+" has an empty param "+paramType.getTypeName());
					return 0; // param has no concrete type - skip this method as it is not being called from the code
				}

				Map<String, String> labels = new HashMap<String, String>();
				if (addLocalRemoteTag) {
					for (Class<?> currentClass : concreteClasses) {
						if ( ! Utils.isJavaType(currentClass.getTypeName())) {
							String label = targetClassesFetcher.getLocalRemoteTag(method, currentClass.getName());
							labels.put(currentClass.getTypeName(), label);
						}
					}
				}
				params = Collections.singletonList(new ModelAttribute(concreteClasses, labels, "attr_"+attrInd));
			}

			attrInd++;

			for (ModelAttribute param : params) {

				if (param.getTypes().isEmpty()) {
					logger.warning("Method "+method.getName()+" has an empty param "+paramType.getTypeName());
					return 0; // param has no concrete type - skip this method as it is not being called from the code
				}

				Parameter currentAttr = methodModel.addParam(param.getName());
				currentAttr.setType(Parameter.PARAM_TYPE_ENUM);
				String valName = "";
				for (Class<?> type : param.getTypes()) {
					String valType = type.getTypeName().trim();

					if (valType.isEmpty()) {
						throw new RuntimeException("encountered an empty type");
					}
					if (param.getTypeParam() != null && ! valType.contains("[]")) {
						String firstType = param.getTypeParam()[0].getTypeName().trim();
						String secondType =  param.getTypeParam().length > 1 ? param.getTypeParam()[1].getTypeName().trim() : null;

						if ( ! Utils.isTypeParamOfClass(firstType, type)) {
							valType += "<"+firstType;

							if (secondType != null &&
									! Utils.isTypeParamOfClass(secondType, type)) {
								valType += ","+secondType;
							}

							valType +=">";
						}
					}

					if (isCollection) {
						valName += (valType+" ");
					} else {
						valName = valType;
						String label = param.getRLLabel(valType);
						if (label != null) {
							valName+= (" "+label);
						}

						currentAttr.addValue(valName.trim());
					}
				}

				if (isCollection) {
					currentAttr.addValue(valName.trim());
				}
			}
		}

		int numAttributes = methodModel.getNumOfParams();

		if (numAttributes > 0) {
			// Define interaction coverage requirements for this model via a relation between the model attributes
			int actualInteractionCoverage = interactionLevel <= numAttributes? interactionLevel : numAttributes;
			Relation r = new Relation(actualInteractionCoverage);
			for (Parameter attr : methodModel.getParameters()) {
				r.addParam (attr);
			}
			// add this relation into the CTD model
			methodModel.addRelation(r);

			// disable stdout and stderr before calling test-plan generator
            PrintStream origSysOut = System.out;
            PrintStream origSysErr = System.err;
            System.setOut(NullPrintStream.NULL_PRINT_STREAM);
            System.setErr(NullPrintStream.NULL_PRINT_STREAM);

            TestSet resultTestPlan = generatePlan(methodModel);

            // restore stdout and stderr
            System.setOut(origSysOut);
            System.setErr(origSysErr);

            addModel(methodModel, models, resultTestPlan, method);

			return resultTestPlan.getNumOfTests();
		}

		return 0;
	}

	private TestSet generatePlan(SUT methodModel) {

		TestGenProfile.instance().setRandstar(TestGenProfile.ON);

		// Create an IPO engine object
		IpoEngine engine = new IpoEngine(methodModel);

		// build a test set
		engine.build();

		// get the resulting test set
		return engine.getTestSet();
	}


	private void addModel(SUT methodModel, JsonObjectBuilder models,
			TestSet testPlan, JavaMethodModel method) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

		JsonObjectBuilder modelObject = Json.createObjectBuilder();

		modelObject.add("formatted_signature", method.getFormattedSignature());

		JsonArrayBuilder attrList = Json.createArrayBuilder();

		int[] refersTo = new int[methodModel.getNumOfParams()];

		Arrays.fill(refersTo, -1);

		int attrIndex=0;

		List<String> attrNames = new ArrayList<>();

		for (Parameter attr : methodModel.getParameters()) {
			attrNames.add(attr.getName());
		}

		for (Parameter attr : methodModel.getParameters()) {

			JsonObjectBuilder attrObject = Json.createObjectBuilder();

			String  attrName = attr.getName();

			// Handle attributes that are detailing the content of collections represented by other attributes

			if (attrName.endsWith(JavaMethodModel.LIST_TAG) || attrName.endsWith(JavaMethodModel.MAP_KEY_TAG) || attrName.endsWith(JavaMethodModel.MAP_VALUE_TAG)) {
				setReferredAttr(attrName, attrIndex, attrNames, refersTo);
			}

			attrObject.add("attribute_name", attrName);

			JsonArrayBuilder valList = Json.createArrayBuilder();

			int i=0;
			for (String val : attr.getValues()) {
				valList.add(getValObject("val_"+(i++), val));
			}

			attrObject.add("values", valList.build());

			attrList.add(attrObject.build());

			attrIndex++;
		}

		modelObject.add("attributes", attrList.build());


		/* Compute components of related attributes */

		Map<Integer, List<Integer>> relatedAttributes = computeRelatedComponents(refersTo);

		JsonArrayBuilder testsList = Json.createArrayBuilder();

		/* In ACTS, the order of parameters in the test plan may be different from their order in the model, because
		 * they are sorted according to their domain size. Hence we need to sync the two orders */

		List<Parameter> sortedParams = testPlan.getParams();
		Map<Parameter, Integer> paramToTestLoc = getParamsTestLocations(methodModel.getParameters(), sortedParams);
		List<int[]> testPlanRows = testPlan.getMatrix();

		for (int[] test : testPlanRows) {

			JsonArrayBuilder testArray = Json.createArrayBuilder();

			for (int i=0; i < methodModel.getNumOfParams(); i++) {

				List<Integer> combinedAttrs = relatedAttributes.get(i);

				if (combinedAttrs == null) {
					continue; // handled as part of another attribute
				} else if (combinedAttrs.isEmpty()) {
					// not a collection attribute
					testArray.add(getSingleValTestObject(getParamTestValue(methodModel.getParam(i), test, paramToTestLoc)));
				} else {

					// this is a collection attribute - capture all related attributes and values that relate to this single collection

					JsonObjectBuilder attrValObject = Json.createObjectBuilder();
					getValueRecursive(methodModel, attrValObject, test, i, combinedAttrs, refersTo, attrNames, paramToTestLoc);

					testArray.add(attrValObject.build());
				}
			}

			testsList.add(testArray.build());
		}

		modelObject.add("test_plan", testsList.build());
		models.add(method.getSignature(), modelObject.build());

	}

	private Map<Parameter, Integer> getParamsTestLocations(ArrayList<Parameter> parameters,
			List<Parameter> sortedParams) {
		Map<Parameter, Integer> paramToTestLoc = new HashMap<>();

		for (int i=0; i<parameters.size(); i++) {

			Parameter current = parameters.get(i);

			for (int j=0; i<parameters.size(); j++) {

				if (current.getName().equals(sortedParams.get(j).getName())) {
					paramToTestLoc.put(current, j);
					break;
				}
			}
		}

		return paramToTestLoc;
	}


	private void getValueRecursive(SUT methodModel, JsonObjectBuilder attrValObject, int[] test, int indexToAdd, List<Integer> combinedAttrs,
			int[] refersTo, List<String> attrNames, Map<Parameter, Integer> paramToTestLoc) {

		String attrName = attrNames.get(indexToAdd);
		String valueToAdd = getParamTestValue(methodModel.getParam(indexToAdd), test, paramToTestLoc);

		if (attrName.endsWith(JavaMethodModel.LIST_TAG) || attrName.endsWith(JavaMethodModel.MAP_KEY_TAG) || attrName.endsWith(JavaMethodModel.MAP_VALUE_TAG)) {

			JsonObjectBuilder collectObject = Json.createObjectBuilder();
			collectObject.add("types", getArrayFromVal(valueToAdd));

			for (int ind : combinedAttrs) {
				if (refersTo[ind] == indexToAdd) {
					getValueRecursive(methodModel, collectObject, test, ind, combinedAttrs, refersTo, attrNames, paramToTestLoc);
				}
			}

			attrValObject.add(attrName.substring(attrName.lastIndexOf('_')+1)+"_types", collectObject.build());

		} else {
			String actualVal = (valueToAdd.contains(" ")? valueToAdd.substring(0, valueToAdd.indexOf(" ")) : valueToAdd);
			attrValObject.add("type", actualVal);
			for (int ind : combinedAttrs) {
				if (refersTo[ind] == indexToAdd) {
					getValueRecursive(methodModel, attrValObject, test, ind, combinedAttrs, refersTo, attrNames, paramToTestLoc);
				}
			}
		}
	}


	private String getParamTestValue(Parameter param, int[] test, Map<Parameter, Integer> paramToTestLoc) {
		return param.getValue(test[paramToTestLoc.get(param)]);
	}


	private static Map<Integer, List<Integer>> computeRelatedComponents(int[] refersTo) {
		Map<Integer, List<Integer>> relatedIndices = new HashMap<Integer, List<Integer>>();

		Set<Integer> handledIndices = new HashSet<Integer>();

		int currentReferred = 0;

		while (handledIndices.size() < refersTo.length) {

			List<Integer> referringIndices = new ArrayList<Integer>();
			relatedIndices.put(currentReferred, referringIndices);
			handledIndices.add(currentReferred);

			Set<Integer> watched = new HashSet<>(Collections.singleton(currentReferred));

			int i=currentReferred+1;

			while (i < refersTo.length) {
				if ( ! handledIndices.contains(i)) {
					if (watched.contains(refersTo[i])) {
						watched.add(i);
						referringIndices.add(i);
						handledIndices.add(i);
					}
				}
				i++;
			}

			// Find next free attribute to be referred to

			while (handledIndices.contains(currentReferred)) {
				currentReferred++;
			}
		}

		return relatedIndices;
	}


	private void setReferredAttr(String attrName, int attrIndex, List<String> attrNames, int[] refersTo) {
		for (int j = 0; j < attrIndex; j++) {
			String prevAttrName = attrNames.get(j);
			if (attrName.equals(prevAttrName + attrName.substring(attrName.lastIndexOf('_')))) {
				refersTo[attrIndex] = j;
				return;
			}
		}

		throw new RuntimeException("Could not find referred collection attribute");
	}


	private static JsonObject getSingleValTestObject(String val) {
		JsonObjectBuilder attrValObject = Json.createObjectBuilder();

		String actualVal = (val.contains(" ")? val.substring(0, val.indexOf(" ")) : val);

		attrValObject.add("type", actualVal);

		return attrValObject.build();
	}

	private static JsonArray getArrayFromVal(String listVal) {
		String[] listVals = listVal.split(" ");

		JsonArrayBuilder listBuilder = Json.createArrayBuilder();

		for (String lVal : listVals) {
			listBuilder.add(lVal);
		}

		return listBuilder.build();
	}

	private static JsonObject getValObject(String attr, String val) {
		JsonObjectBuilder attrValObject = Json.createObjectBuilder();
		String[] vals = val.split(" ");
		List<String> filteredVals = new ArrayList<String>();

		for (String currentVal : vals) {
			if ( ! currentVal.trim().isEmpty()) {
				filteredVals.add(currentVal.trim());
			}
		}

		if (filteredVals.size() == 1) {
			attrValObject.add(attr, filteredVals.get(0));
		} else if (filteredVals.size() == 2 && (filteredVals.get(1).endsWith("local") || filteredVals.get(1).endsWith("remote"))) {
			attrValObject.add(attr, filteredVals.get(0));
			attrValObject.add("locality_status:", filteredVals.get(1));
		} else { // a collection value
			JsonArrayBuilder valList = Json.createArrayBuilder();
			for (String innerVal : filteredVals) {
				valList.add(innerVal);
			}
			attrValObject.add(attr, valList.build());
		}
		return attrValObject.build();
	}

}
