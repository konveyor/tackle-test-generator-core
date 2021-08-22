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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import edu.uta.cse.fireeye.common.Parameter;
import edu.uta.cse.fireeye.common.Relation;
import edu.uta.cse.fireeye.common.SUT;
import edu.uta.cse.fireeye.common.TestGenProfile;
import edu.uta.cse.fireeye.common.TestSet;
import edu.uta.cse.fireeye.service.engine.IpoEngine;

/**
 * Collection/non-Collection, user versus non-user types, and remove versus local types.
 *
 * @author RACHELBRILL
 *
 */

class CTDModeler {

	private CTDTestPlanGenerator.TargetFetcher targetClassesFetcher;
	private String refactorPackagePrefix = null;

	private static final Logger logger = TackleTestLogger.getLogger(CTDModeler.class);

	CTDModeler(CTDTestPlanGenerator.TargetFetcher fetcher, String refactorPrefix) {
		targetClassesFetcher = fetcher;
		refactorPackagePrefix = refactorPrefix;
	}
	
	static class CTDModelAndTestPlan {
		
		final SUT model;
		final TestSet testPlan;
		
		CTDModelAndTestPlan(SUT model, TestSet testPlan) {
			this.model = model;
			this.testPlan = testPlan;
		}
	}

	public CTDModelAndTestPlan analyzeParams(JavaMethodModel method, TypeAnalysisResults typeAnalysisResults, boolean addLocalRemoteTag,
                             int interactionLevel, Class<?> cls)
			throws ClassNotFoundException, IOException, SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException {
		
		Type[] paramTypes = method.getParameterTypes();

		if (paramTypes.length == 0) {
			return null; // nothing to do here
		}
		
		Class<?>[] paramClasses = method.getParameterClasses();

		// Define a new CTD model for the current method

		SUT methodModel = new SUT(method.targetPartition + "::" + method.targetSootClass.getName()+"::"+method.getFormattedSignature());

		int attrInd=0;
		int paramInd=0;

		boolean isCollection;

		// Go over each parameter of the method and define one (or more in case of a collection) CTD attributes for it

		for (Type paramType : paramTypes) {

			isCollection = false;

			Class<?> paramClass = paramClasses[paramInd++];

			List<ModelAttribute> params;

			/* Order of checks is important for their correctness */

			if (Utils.isPrimitive(paramClass) || 
					JavaMethodModel.isUtilType(paramType.getTypeName(), refactorPackagePrefix)) {
				params = Collections.singletonList(new ModelAttribute(Collections.singletonList(paramClass), Collections.emptyMap(), "attr_"+attrInd));
			} else if (JavaMethodModel.isCollection(paramClass)) {
				isCollection = true;
				params = method.getCollectionAttributes(paramClass, paramType, attrInd, typeAnalysisResults, cls, method.getTypeParameters());
			} else { // user type or Java type that may have user type extensions
				List<Class<?>> concreteClasses = new ArrayList<Class<?>>(method.getAllConcreteTypes(paramClass, typeAnalysisResults));

				if (concreteClasses.isEmpty()) {
					throw new RuntimeException("Method "+method.targetClass.getName()+"."+method.getName()+" has an empty param "+paramType.getTypeName());
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
					throw new RuntimeException("Method "+method.targetClass.getName()+"."+method.getName()+" has an empty param "+paramType.getTypeName());
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

//			// disable stdout and stderr before calling test-plan generator
//            PrintStream origSysOut = System.out;
//            PrintStream origSysErr = System.err;
//            System.setOut(NullPrintStream.NULL_PRINT_STREAM);
//            System.setErr(NullPrintStream.NULL_PRINT_STREAM);

            TestSet resultTestPlan = generatePlan(methodModel);

//            // restore stdout and stderr
//            System.setOut(origSysOut);
//            System.setErr(origSysErr);

			return new CTDModelAndTestPlan(methodModel, resultTestPlan);
		}

		return null;
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

}
