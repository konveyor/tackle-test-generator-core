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

package org.konveyor.tackle.testgen.util;

import soot.G;
import soot.Scene;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Utils {

	public static String getSignature(Method theMethod)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

		Field gSig = Method.class.getDeclaredField("signature");
		gSig.setAccessible(true);
		String sig = (String) gSig.get(theMethod);
		if (sig != null)
			return theMethod.getName() + sig.replace('.', '/');

		StringBuilder sb = new StringBuilder(theMethod.getName() + "(");
		for (Class<?> c : theMethod.getParameterTypes()) {
			sb.append((sig = Array.newInstance(c, 0).toString()).substring(1, sig.indexOf('@')));
		}
		return sb.append(')').append(theMethod.getReturnType() == void.class ? "V"
				: (sig = Array.newInstance(theMethod.getReturnType(), 0).toString()).substring(1, sig.indexOf('@')))
				.toString().replace('.', '/');
	}

	public static String getSignature(Constructor<?> constructor)
			throws SecurityException, IllegalArgumentException {

		String sig;

		StringBuilder sb = new StringBuilder("<init>(");
		for (Class<?> c : constructor.getParameterTypes()) {
			sb.append((sig = Array.newInstance(c, 0).toString()).substring(1, sig.indexOf('@')));
		}
		return sb.append(')').toString().replace('.', '/');
	}
	
	/**
	 * Retrieves a Java class name from its file name
	 * @param fileName The Java file
	 * @param rootDir The root directory in which the package is located 
	 * @param suffix The file suffix to be ignored 
	 * @param separator The file separator
	 * @return
	 */

	public static String fileToClass(String fileName, String rootDir, String suffix, String separator) {
		String root = rootDir;

		if  (! root.endsWith(File.separator)) {
			root = root + File.separator;
		}

		String className = fileName.substring(root.length(), fileName.length()-suffix.length());
		if (separator.equals("\\")) {
			separator = separator + "\\";
		}
		className = className.replaceAll(separator, ".");
		return className;
	}

	public static boolean isJavaType(String paramName) {
		return paramName.startsWith("java.") || paramName.startsWith("sun.") || paramName.startsWith("javax.");
	}

	public static boolean isPrimitive(Class<?> paramType) {
		return paramType.isPrimitive();
	}

	public static List<String> getClasspathEntries(File file) throws IOException {

		List<String> classpath = new ArrayList<String>();

		BufferedReader reader = new BufferedReader(new FileReader(file));

		try {

			String line;

			while ((line = reader.readLine()) != null) {
				if ( ! line.trim().isEmpty()) {
					classpath.add(line);
				}
			}
		} finally {
			reader.close();
		}

		return classpath;
	}

	public static URL[] entriesToURL(List<String> classpathEntries) throws MalformedURLException {

		List<URL> urls = new ArrayList<URL>();

		for (String url : classpathEntries) {
			
			if ( ! url.trim().isEmpty()) {

				File entryFile = new File(url);

				if (!entryFile.exists()) {
					throw new IllegalArgumentException(entryFile.getAbsolutePath() + " doesn't exist");
				}

				urls.add(entryFile.toURI().toURL());
			}
		}

        URL[] classLoaderUrls = new URL[urls.size()];
        urls.toArray(classLoaderUrls);

        return classLoaderUrls;
	}

	public static String entriesToClasspath(List<String> classpathEntries) {

		StringBuilder classpathS = new StringBuilder();

		for (String entry : classpathEntries) {
			if ( ! entry.trim().isEmpty()) {
				classpathS.append(entry);
				classpathS.append(File.pathSeparator);
			}
		}

		if (classpathS.length() == 0) {
			return classpathS.toString();
		}

        return classpathS.substring(0, classpathS.length()-1);
	}

	public static void initSootClasses(List<String> processDirs, String classpath) {
		G.reset();
		soot.options.Options.v().set_process_dir(processDirs);
	    soot.options.Options.v().set_allow_phantom_refs(true);
	    soot.options.Options.v().set_include_all(true);
	    Scene.v().releaseFastHierarchy();
	    if (classpath != null) {
	    	Scene.v().setSootClassPath(classpath);
	    }
	    Scene.v().loadNecessaryClasses();
	}

    public static boolean isPrivateInnerClass(Class<?> theClass) {

		return theClass.getEnclosingClass() != null && Modifier.isPrivate(theClass.getModifiers());
	}

	public static boolean isTypeParam(String typeName, TypeVariable<?>[] typeParams) {

		for (TypeVariable<?> typeParam : typeParams) {

			if (typeName.equals(typeParam.getTypeName())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isTypeParamOfClass(String typeName, Class<?> cls) {

		if (isTypeParam(typeName, cls.getTypeParameters())) {
			return true;
		}

		Class<?> outerClass = cls.getEnclosingClass();

		if (outerClass != null && isTypeParam(typeName, outerClass.getTypeParameters())) {
			return true;
		}

		return false;
	}
	
	/**
	 * Determines whether the given type can be instantiated by a test for target class.
	 * Assumes the test class is in the same package as the target class.
	 * @param type
	 * @param targetClass
	 * @return
	 */
	
	public static boolean canBeInstantiated(Class<?> type, Class<?> targetClass) {
		
		Class<?> typeToCheck = type;
		
		while (typeToCheck.isArray()) {
			typeToCheck = typeToCheck.getComponentType();
		}
		
		/*
		 * Type can be instantiated if it is not private and either of these two conditions hold: 
		 * 1. Type is public and is primitive/enum or belongs to some package 
		 * 2. Type is in the same package as the class under test 
		 */
		
		int typeModifiers = typeToCheck.getModifiers();
		
		if (Modifier.isPrivate(typeModifiers)) {
			return false;
		}
		
		if (Modifier.isPublic(typeModifiers)) {
			
			return (typeToCheck.isPrimitive() || typeToCheck.isEnum() || typeToCheck.getPackage() != null);
		}
		
		// Enums don't have package info but can be used in the same package as the class they are in
		// Hence need a separate check
		
		if (typeToCheck.isEnum()) {
			return true;
		}
		
		Package pkg = typeToCheck.getPackage();
		
		return pkg != null && targetClass.getPackage() != null && pkg.getName().equals(targetClass.getPackage().getName());
	}

	public static String getEvoSuiteJarPath(String jarName) {
        Optional<String> evosuiteJarPath = Arrays.stream(
            System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(elem -> elem.contains(jarName))
            .findFirst();
        if (!evosuiteJarPath.isPresent()) {
            throw new RuntimeException("EvoSuite jar \""+ jarName +"\" not found");
        }
        return evosuiteJarPath.get();
    }

}
