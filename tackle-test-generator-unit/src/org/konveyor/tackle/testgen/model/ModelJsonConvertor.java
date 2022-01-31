package org.konveyor.tackle.testgen.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.uta.cse.fireeye.common.Parameter;
import edu.uta.cse.fireeye.common.SUT;
import edu.uta.cse.fireeye.common.TestSet;

class ModelJsonConvertor {
	
	
	static ObjectNode addModel(SUT methodModel, TestSet testPlan, JavaMethodModel method) 
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		
		ObjectMapper mapper = TackleTestJson.getObjectMapper();

		ObjectNode modelNode = mapper.createObjectNode();

		modelNode.put("formatted_signature", method.getFormattedSignature());

		ArrayNode attrList = mapper.createArrayNode();

		int[] refersTo = new int[methodModel.getNumOfParams()];

		Arrays.fill(refersTo, -1);

		int attrIndex=0;

		List<String> attrNames = new ArrayList<>();

		for (Parameter attr : methodModel.getParameters()) {
			attrNames.add(attr.getName());
		}

		for (Parameter attr : methodModel.getParameters()) {

			ObjectNode attrNode = mapper.createObjectNode();

			String  attrName = attr.getName();

			// Handle attributes that are detailing the content of collections represented by other attributes

			if (attrName.endsWith(Constants.LIST_TAG) || attrName.endsWith(Constants.MAP_KEY_TAG) || attrName.endsWith(Constants.MAP_VALUE_TAG)) {
				setReferredAttr(attrName, attrIndex, attrNames, refersTo);
			}

			attrNode.put("attribute_name", attrName);

			ArrayNode valList = mapper.createArrayNode();

			int i=0;
			for (String val : attr.getValues()) {
				valList.add(getValObject("val_"+(i++), val));
			}

			attrNode.set("values", valList);

			attrList.add(attrNode);

			attrIndex++;
		}

		modelNode.set("attributes", attrList);


		/* Compute components of related attributes */

		Map<Integer, List<Integer>> relatedAttributes = computeRelatedComponents(refersTo);

		ArrayNode testsList = mapper.createArrayNode();

		/* In ACTS, the order of parameters in the test plan may be different from their order in the model, because
		 * they are sorted according to their domain size. Hence we need to sync the two orders */

		List<Parameter> sortedParams = testPlan.getParams();
		Map<Parameter, Integer> paramToTestLoc = getParamsTestLocations(methodModel.getParameters(), sortedParams);
		List<int[]> testPlanRows = testPlan.getMatrix();

		for (int[] test : testPlanRows) {

			ArrayNode testArray = mapper.createArrayNode();

			for (int i=0; i < methodModel.getNumOfParams(); i++) {

				List<Integer> combinedAttrs = relatedAttributes.get(i);

				if (combinedAttrs == null) {
					continue; // handled as part of another attribute
				} else if (combinedAttrs.isEmpty()) {
					// not a collection attribute
					testArray.add(getSingleValTestObject(getParamTestValue(methodModel.getParam(i), test, paramToTestLoc)));
				} else {

					// this is a collection attribute - capture all related attributes and values that relate to this single collection

					ObjectNode attrValNode = mapper.createObjectNode();
					getValueRecursive(methodModel, attrValNode, test, i, combinedAttrs, refersTo, attrNames, paramToTestLoc);

					testArray.add(attrValNode);
				}
			}

			testsList.add(testArray);
		}

		modelNode.set("test_plan", testsList);
		
		return modelNode;
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


	private static void getValueRecursive(SUT methodModel, ObjectNode attrValNode, int[] test, int indexToAdd, List<Integer> combinedAttrs,
			int[] refersTo, List<String> attrNames, Map<Parameter, Integer> paramToTestLoc) {

		String attrName = attrNames.get(indexToAdd);
		String valueToAdd = getParamTestValue(methodModel.getParam(indexToAdd), test, paramToTestLoc);

		if (attrName.endsWith(Constants.LIST_TAG) || attrName.endsWith(Constants.MAP_KEY_TAG) || attrName.endsWith(Constants.MAP_VALUE_TAG)) {

			ObjectNode collectNode = TackleTestJson.getObjectMapper().createObjectNode();
			collectNode.set("types", getArrayFromVal(valueToAdd));

			for (int ind : combinedAttrs) {
				if (refersTo[ind] == indexToAdd) {
					getValueRecursive(methodModel, collectNode, test, ind, combinedAttrs, refersTo, attrNames, paramToTestLoc);
				}
			}

			attrValNode.set(attrName.substring(attrName.lastIndexOf('_')+1)+"_types", collectNode);

		} else {
			String actualVal = (valueToAdd.contains(" ")? valueToAdd.substring(0, valueToAdd.indexOf(" ")) : valueToAdd);
			attrValNode.put("type", actualVal);
			for (int ind : combinedAttrs) {
				if (refersTo[ind] == indexToAdd) {
					getValueRecursive(methodModel, attrValNode, test, ind, combinedAttrs, refersTo, attrNames, paramToTestLoc);
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


	private static ObjectNode getSingleValTestObject(String val) {
		ObjectNode attrValNode = TackleTestJson.getObjectMapper().createObjectNode();

		String actualVal = (val.contains(" ")? val.substring(0, val.indexOf(" ")) : val);

		attrValNode.put("type", actualVal);

		return attrValNode;
	}

	private static ArrayNode getArrayFromVal(String listVal) {
		String[] listVals = listVal.split(" ");
		
		ArrayNode listNode = TackleTestJson.getObjectMapper().createArrayNode();

		for (String lVal : listVals) {
			listNode.add(lVal);
		}

		return listNode;
	}

	private static ObjectNode getValObject(String attr, String val) {
		
		ObjectNode attrValNode = TackleTestJson.getObjectMapper().createObjectNode();
		String[] vals = val.split(" ");
		List<String> filteredVals = new ArrayList<String>();

		for (String currentVal : vals) {
			if ( ! currentVal.trim().isEmpty()) {
				filteredVals.add(currentVal.trim());
			}
		}

		if (filteredVals.size() == 1) {
			attrValNode.put(attr, filteredVals.get(0));
		} else if (filteredVals.size() == 2 && (filteredVals.get(1).endsWith("local") || filteredVals.get(1).endsWith("remote"))) {
			attrValNode.put(attr, filteredVals.get(0));
			attrValNode.put("locality_status:", filteredVals.get(1));
		} else { // a collection value
			ArrayNode valList = TackleTestJson.getObjectMapper().createArrayNode();
			for (String innerVal : filteredVals) {
				valList.add(innerVal);
			}
			attrValNode.set(attr, valList);
		}
		return attrValNode;
	}

}
