package org.konveyor.tackle.testgen.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import edu.uta.cse.fireeye.common.Parameter;
import edu.uta.cse.fireeye.common.SUT;
import edu.uta.cse.fireeye.common.TestSet;

class ModelJsonConvertor {
	
	
	static JsonObject addModel(SUT methodModel, TestSet testPlan, JavaMethodModel method) 
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

		JsonObjectBuilder modelObjectBuilder = Json.createObjectBuilder();

		modelObjectBuilder.add("formatted_signature", method.getFormattedSignature());

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

		modelObjectBuilder.add("attributes", attrList.build());


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

		modelObjectBuilder.add("test_plan", testsList.build());
		
		return modelObjectBuilder.build();
	}

	private static Map<Parameter, Integer> getParamsTestLocations(ArrayList<Parameter> parameters,
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


	private static void getValueRecursive(SUT methodModel, JsonObjectBuilder attrValObject, int[] test, int indexToAdd, List<Integer> combinedAttrs,
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


	private static String getParamTestValue(Parameter param, int[] test, Map<Parameter, Integer> paramToTestLoc) {
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


	private static void setReferredAttr(String attrName, int attrIndex, List<String> attrNames, int[] refersTo) {
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
