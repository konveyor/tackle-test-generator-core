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

package org.konveyor.tackle.testgen.rta;

import org.konveyor.tackle.testgen.core.DiffAssertionsGenerator;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.NewExpr;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Logger;

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

		Scene.v().getApplicationClasses().forEach(currentSootClass -> {

			if ( ! currentSootClass.isInterface()) {

				List<SootMethod> methods = currentSootClass.getMethods();
				for (SootMethod method : methods) {
					if (method.isAbstract() || method.isNative()) {
						continue;
					}
					Body body = method.retrieveActiveBody();
					UnitPatchingChain units = body.getUnits();
					for (Unit unit : units) {
						if (unit instanceof DefinitionStmt) {
							Value val = ((DefinitionStmt) unit).getRightOp();
							if (val instanceof NewExpr) {

								String valClassName = ((NewExpr) val).getBaseType().getClassName();

								Class<?> valClass;
								try {

									valClass = urlLoader.loadClass(valClassName);

									if (valClass.isAnonymousClass()) {
										logger.fine("Skipping anonymous class "+valClassName);
									} else if (Utils.isPrivateInnerClass(valClass)) {
										logger.fine("Skipping private inner class "+valClassName);
									} else if (isNonSerializableLibraryClass(valClass)) {
										logger.fine("Skipping non-serializable library class "+valClassName);
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
		});

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

	private boolean isNonSerializableLibraryClass(Class<?> theClass) {

		return  ! appClasses.contains(theClass.getName()) && ! Serializable.class.isAssignableFrom(theClass);
	}

    public static void toJson(String appName, Set<String> types) throws FileNotFoundException {
    	JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
		JsonWriter writer = writerFactory.createWriter(new FileOutputStream(new File(appName+"_"+ Constants.RTA_OUTFILE_SUFFIX)));

		JsonArrayBuilder typeArray = Json.createArrayBuilder();

		for (String type : types) {
			typeArray.add(type);
		}

		writer.writeArray(typeArray.build());
    }
}
