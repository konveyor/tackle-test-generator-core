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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.konveyor.tackle.testgen.core.DiffAssertionsGenerator;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.NewExpr;
import soot.jimple.internal.JSpecialInvokeExpr;

/**
 * Performs rapid type analysis to retrieve all types instantiated in the monolith application
 * @author RACHELBRILL
 *
 */

public class RapidTypeAnalysis {

	private static final Logger logger = TackleTestLogger.getLogger(DiffAssertionsGenerator.class);

	private Set<String> appClasses = new HashSet<String>();

	public Set<String> performAnalysis(String appClasspath, List<String> classpath) throws MalformedURLException {

		String[] classDirs = appClasspath.split(File.pathSeparator);

		List<String> classesDirs = new ArrayList<String>();

		for (String dirName : classDirs) {
			if ( ! dirName.isEmpty()) {
				File classesDir = new File(dirName);

				if ( ! classesDir.exists()) {
					throw new IllegalArgumentException(classesDir.getAbsolutePath()+" is not a legal directory");
				}
				classpath.add(classesDir.getAbsolutePath());
				classesDirs.add(classesDir.getAbsolutePath());
			}
		}

		classpath.add(System.getProperty("java.home")+File.separator+"lib"+File.separator+"rt.jar");

		Utils.initSootClasses(classesDirs, Utils.entriesToClasspath(classpath));

		Scene.v().getApplicationClasses().forEach(currentSootClass -> {
			appClasses.add(currentSootClass.getName());
		});

		URLClassLoader urlLoader = new URLClassLoader(Utils.entriesToURL(classpath), ClassLoader.getSystemClassLoader());

		return performAnalysis(urlLoader);

	}

	private Set<String> performAnalysis(URLClassLoader urlLoader) {

		Set<String> initializedTypes = new HashSet<String>();
		
		final Iterator<SootClass> iter = Scene.v().getApplicationClasses().snapshotIterator();
		
		while (iter.hasNext()) {

			SootClass currentSootClass = iter.next();

			if ( ! currentSootClass.isInterface()) {
				
				List<SootMethod> methods = currentSootClass.getMethods();
				for (SootMethod method : methods) {
					if (method.isAbstract() || method.isNative()) {
						continue;
					}
					
					Body body = method.retrieveActiveBody();
					
					Iterator<Unit> unitIter = body.getUnits().snapshotIterator();
					
					while (unitIter.hasNext()) {
						
						Unit unit = unitIter.next();
						
						Iterator<ValueBox> valIter = unit.getUseBoxes().iterator();
						
						while (valIter.hasNext()) {
							Value val = valIter.next().getValue();
							
							String valClassName = null;
	
							if (val instanceof NewExpr) {

								valClassName = ((NewExpr) val).getBaseType().getClassName();
							} else if (val instanceof JSpecialInvokeExpr && val.toString().contains("void <init>")) {
								valClassName = ((JSpecialInvokeExpr) val).getMethodRef().getDeclaringClass().getName();
							}
							
							if (valClassName != null) {

								Class<?> valClass;
								try {

									valClass = urlLoader.loadClass(valClassName);

									if (valClass.isAnonymousClass()) {
										logger.fine("Skipping anonymous class "+valClassName);
									} else if (Utils.isPrivateInnerClass(valClass)) {
										logger.fine("Skipping private inner class "+valClassName);
									//} else if (isNonSerializableLibraryClass(valClass)) {
									//	logger.fine("Skipping non-serializable library class "+valClassName);
									} else if ( ! hasPublicConstructor(valClass)) {
										logger.fine("Skipping class "+valClassName+" with no public constructors");
									} else {
										initializedTypes.add(valClassName);
									}
								} catch (ClassNotFoundException | SecurityException e) {
									logger.warning("Skipping class "+valClassName+" due to exception "+e.getMessage());
								}
							}
						}
					}
				}
			}
		}
		
		return initializedTypes;
	}

	private boolean hasPublicConstructor(Class<?> valClass) {

		for (Constructor<?> constr : valClass.getConstructors()) {

			if (Modifier.isPublic(constr.getModifiers())) {
				return true;
			}
		}

		return false;
	}

	//private boolean isNonSerializableLibraryClass(Class<?> theClass) {

	//	return  ! appClasses.contains(theClass.getName()) && ! Serializable.class.isAssignableFrom(theClass);
	//}

    public static void toJson(String appName, Set<String> types) throws JsonGenerationException, JsonMappingException, IOException {
    	
    	CTDTestPlanGenerator.mapper.writeValue(new File(appName+"_"+ Constants.RTA_OUTFILE_SUFFIX), types);
    }
}
