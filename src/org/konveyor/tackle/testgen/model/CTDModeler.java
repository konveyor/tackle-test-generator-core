package org.konveyor.tackle.testgen.model;

import java.io.File;
import java.io.IOException;
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

import com.ibm.contest.shared.applicationLimitation.LimitationException;
import com.ibm.contest.shared.java5.focusutils.Task;
import com.ibm.focus.exceptions.ErrorMessageException;
import com.ibm.focus.model.Attribute;
import com.ibm.focus.model.Status;
import com.ibm.focus.model.cp.CartesianProductAttribute;
import com.ibm.focus.model.cp.CartesianProductModel;
import com.ibm.focus.model.cp.CoverageRequirements;
import com.ibm.focus.reports.CombinatorialAlgorithmsInputProcessor;
import com.ibm.focus.reports.CombinatorialAlgorithmsInputProcessor.BadPathHandling;
import com.ibm.focus.reports.CombinatorialTestDesign;
import com.ibm.focus.reports.ctdInputs.CtdEnhancementInput;
import com.ibm.focus.traces.DifferentAttributesEncounteredException;
import com.ibm.focus.traces.IllegalTaskInfo.IllegalTasksEncounteredException;
import com.ibm.focus.usage.UsageReportSender.ReportMode;
import com.ibm.focus.utils.ILicenseHandler;
import com.ibm.focus.utils.InvalidFileException;
import com.ibm.focus.utils.LicenseFactory;
import com.ibm.focus.utils.LimitedVersionExceptions.ClientNetworkLicenseException;
import com.ibm.focus.utils.LimitedVersionExceptions.DisabledFeatureException;
import com.ibm.focus.utils.LimitedVersionExceptions.InternalLicenseException;
import com.ibm.focus.utils.LimitedVersionExceptions.LicenseExpiredException;
import com.ibm.focus.utils.LimitedVersionExceptions.LimitedVersionException;
import com.ibm.focus.utils.OperationInterruptedException;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

/**
 * Collection/non-Collection, user versus non-user types, and remove versus local types.
 *
 * @author RACHELBRILL
 *
 */

class CTDModeler {

	private ILicenseHandler ctdModelLicense;
	private CTDTestPlanGenerator.TargetFetcher targetClassesFetcher;

	private static final Logger logger = TackleTestLogger.getLogger(CTDModeler.class);

	CTDModeler(CTDTestPlanGenerator.TargetFetcher fetcher)
			throws LicenseExpiredException, InternalLicenseException, ClientNetworkLicenseException, LimitedVersionException, LimitationException {
		targetClassesFetcher = fetcher;
		/* Read the license once to avoid performance overhead */
		ctdModelLicense = LicenseFactory.getLicenseHandler(false);
	}


	public int analyzeParams(JavaMethodModel method, TypeAnalysisResults typeAnalysisResults, JsonObjectBuilder models, boolean addLocalRemoteTag,
                             int interactionLevel, Class<?> cls)
			throws ClassNotFoundException, LimitedVersionException, IOException, InvalidFileException, ErrorMessageException,
			SecurityException, IllegalArgumentException, IllegalTasksEncounteredException, DifferentAttributesEncounteredException,
			OperationInterruptedException, NoSuchFieldException, IllegalAccessException {

		Type[] paramTypes = method.getParameterTypes();

		if (paramTypes.length == 0) {
			return 0; // nothing to do here
		}

		Class<?>[] paramClasses = method.getParameterClasses();

		File parentFolder = new File(System.getProperty("user.dir"));

		File modelFile = new File(parentFolder, method.proxyPartition + "::" + method.proxySootClass.getName()+"::"+
			method.getFormattedSignature() + ".model");

		CartesianProductModel ctdModel = new CartesianProductModel(modelFile, true, ctdModelLicense, ReportMode.UNDEFINED);

		int attrInd=0;
		int paramInd=0;

		boolean isCollection;

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

				ctdModel.addAttribute();
				CartesianProductAttribute currentAttr = ctdModel.getAttributes().get(ctdModel.getAttributes().size()-1);
				currentAttr.setName(param.getName());
				currentAttr.setType(Attribute.AttributeType.STRING);
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

						currentAttr.addValue(new Attribute.Value(valName.trim(), ""));
					}
				}

				if (isCollection) {
					currentAttr.addValue(new Attribute.Value(valName.trim(), ""));
				}
			}
		}

		int numAttributes = ctdModel.getAttributes().size();

		if (numAttributes > 0) {
			int actualInteractionCoverage = interactionLevel <= numAttributes? interactionLevel : numAttributes;
			CoverageRequirements tWay = CoverageRequirements.createSingleRequirement(actualInteractionCoverage, ctdModel.getAttributes().getNames(), ctdModel);
			tWay.setName(actualInteractionCoverage+"-way");
			ctdModel.getCoverageRequirementsAlternatives().addCoverageRequirementsAlternative(tWay);
			if (! validateModel(ctdModel)) {
				throw new RuntimeException("Encountered a model with problems. See console log for details");
			}


			List<Task> resultTestPlan = generatePlan(ctdModel);
			addModel(ctdModel, models, resultTestPlan, method);

			return resultTestPlan.size();
		}

		return 0;
	}

	private boolean validateModel(CartesianProductModel ctdModel) throws IOException {

		ctdModel.attributesChanged();
		ctdModel.changed(); // triggers model validation
		if (ctdModel.getStatus() != Status.VALID && ctdModel.getStatus() != Status.WARNING) {
			logger.warning("Model "+ctdModel.getName()+" has problems");
		}
		Set<Attribute> attrsWithProblems = ctdModel.getAttributes().getProblems();
		for (Attribute attr : attrsWithProblems) {
			logger.warning("Attribute "+attr.getName()+" has problems");
			for (String problem : attr.getProblemsStrToStatusMap().keySet()) {
				logger.warning(problem);
            }
		}
		return attrsWithProblems.size() == 0;
	}

	private List<Task> generatePlan(CartesianProductModel ctdModel)
			throws ErrorMessageException, IOException, IllegalTasksEncounteredException, DifferentAttributesEncounteredException, DisabledFeatureException, OperationInterruptedException {

		CombinatorialAlgorithmsInputProcessor inputProcessor = new CombinatorialAlgorithmsInputProcessor(ctdModel, null,
				ctdModel.getDefaultCoverageRequirements(), ctdModel.getNegativeCoverageRequirements(),
				Collections.emptyList(), false, false, BadPathHandling.CREATE_ONLY_GOOD_PATH, ctdModel.getDontCares(), null, null);

		CtdEnhancementInput ctdInput = new CtdEnhancementInput(ctdModel, null, inputProcessor, null, false, -1, -1, 0, 0, null);
		CombinatorialTestDesign result = new CombinatorialTestDesign(ctdInput, null);

		return result.solution.goodPathSolution;
	}


	private void addModel(CartesianProductModel ctdModel, JsonObjectBuilder models,
			List<Task> testPlan, JavaMethodModel method) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

		JsonObjectBuilder modelObject = Json.createObjectBuilder();

		modelObject.add("formatted_signature", method.getFormattedSignature());

		JsonArrayBuilder attrList = Json.createArrayBuilder();

		int[] refersTo = new int[ctdModel.getAttributes().size()];

		Arrays.fill(refersTo, -1);

		int attrIndex=0;

		for (CartesianProductAttribute attr : ctdModel.getAttributes()) {

			JsonObjectBuilder attrObject = Json.createObjectBuilder();

			String  attrName = attr.getName();

			if (attrName.endsWith(JavaMethodModel.LIST_TAG) || attrName.endsWith(JavaMethodModel.MAP_KEY_TAG) || attrName.endsWith(JavaMethodModel.MAP_VALUE_TAG)) {
				setReferredAttr(attrName, attrIndex, ctdModel.getAttributes().getNames(), refersTo);
			}

			attrObject.add("attribute_name", attrName);

			JsonArrayBuilder valList = Json.createArrayBuilder();

			int i=0;
			for (String val : attr.getSetOfValues()) {
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

		for (Task test : testPlan) {

			JsonArrayBuilder testArray = Json.createArrayBuilder();

			for (int i=0; i < test.size(); i++) {

				List<Integer> combinedAttrs = relatedAttributes.get(i);

				if (combinedAttrs == null) {
					continue; // handled as part of another attribute
				} else if (combinedAttrs.isEmpty()) {
					// not a collection attribute
					testArray.add(getSingleValTestObject(test.getValueAt(i)));
				} else {

					JsonObjectBuilder attrValObject = Json.createObjectBuilder();
					getValueRecursive(attrValObject, test, i, combinedAttrs, refersTo, ctdModel.getAttributes().getNames());

					testArray.add(attrValObject.build());
				}
			}

			testsList.add(testArray.build());
		}

		modelObject.add("test_plan", testsList.build());
		models.add(method.getSignature(), modelObject.build());

	}

	private void getValueRecursive(JsonObjectBuilder attrValObject, Task test, int indexToAdd, List<Integer> combinedAttrs,
			int[] refersTo, ArrayList<String> attrNames) {

		String attrName = attrNames.get(indexToAdd);
		String valueToAdd = test.getValueAt(indexToAdd);

		if (attrName.endsWith(JavaMethodModel.LIST_TAG) || attrName.endsWith(JavaMethodModel.MAP_KEY_TAG) || attrName.endsWith(JavaMethodModel.MAP_VALUE_TAG)) {

			JsonObjectBuilder collectObject = Json.createObjectBuilder();
			collectObject.add("types", getArrayFromVal(valueToAdd));

			for (int ind : combinedAttrs) {
				if (refersTo[ind] == indexToAdd) {
					getValueRecursive(collectObject, test, ind, combinedAttrs, refersTo, attrNames);
				}
			}

			attrValObject.add(attrName.substring(attrName.lastIndexOf('_')+1)+"_types", collectObject.build());

		} else {
			String actualVal = (valueToAdd.contains(" ")? valueToAdd.substring(0, valueToAdd.indexOf(" ")) : valueToAdd);
			attrValObject.add("type", actualVal);
			for (int ind : combinedAttrs) {
				if (refersTo[ind] == indexToAdd) {
					getValueRecursive(attrValObject, test, ind, combinedAttrs, refersTo, attrNames);
				}
			}
		}
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
