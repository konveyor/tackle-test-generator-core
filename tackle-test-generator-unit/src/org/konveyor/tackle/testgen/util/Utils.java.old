package org.konveyor.tackle.testgen.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

import soot.G;
import soot.Scene;

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
				classpath.add(line);
			}
		} finally {
			reader.close();
		}

		return classpath;
	}

	public static URL[] entriesToURL(List<String> classpathEntries) throws MalformedURLException {

		List<URL> urls = new ArrayList<URL>();

		for (String url : classpathEntries) {

			File entryFile = new File(url);

			if ( ! entryFile.exists()) {
				throw new IllegalArgumentException(entryFile.getAbsolutePath()+" doesn't exist");
			}

			urls.add(entryFile.toURI().toURL());
		}

        URL[] classLoaderUrls = new URL[urls.size()];
        urls.toArray(classLoaderUrls);

        return classLoaderUrls;
	}

	public static String entriesToClasspath(List<String> classpathEntries) {

		StringBuilder classpathS = new StringBuilder();

		for (String entry : classpathEntries) {
			classpathS.append(entry);
			classpathS.append(File.pathSeparator);
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

	/* Temporarily using Json as config file, will switch to toml later */

	public static JsonObject readConfig() throws IOException {

		InputStream fis = new FileInputStream("config.json");
        JsonReader reader = Json.createReader(fis);
        JsonObject configObject = reader.readObject();
        reader.close();

        return configObject;
	}

	public static void writeConfig(JsonObject config) throws IOException {

		JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
		JsonWriter writer = writerFactory.createWriter(new FileOutputStream(new File("config.json")));

		writer.writeObject(config);
        writer.close();
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
}
