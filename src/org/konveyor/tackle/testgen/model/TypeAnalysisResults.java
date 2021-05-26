package org.konveyor.tackle.testgen.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A class that holds CHA and RTA results for reuse in the same partition
 * @author RACHELBRILL
 *
 */

class TypeAnalysisResults {

	private final Set<String> RTAClasses;

	private Map<Class<?>, Set<Class<?>>> CHASubClasses = new HashMap<Class<?>, Set<Class<?>>>();
	private Map<Class<?>, Set<Class<?>>> CHASuperClasses = new HashMap<Class<?>, Set<Class<?>>>();

	TypeAnalysisResults(Set<String> RTAResults) {

		RTAClasses = Collections.unmodifiableSet(RTAResults);
	}

	Set<Class<?>> getSubClasses(Class<?> type) {
		return CHASubClasses.get(type);
	}

	void setSubClasses(Class<?> type, Set<Class<?>> subclasses) {
		CHASubClasses.put(type, subclasses);
	}

	Set<Class<?>> getSuperClasses(Class<?> type) {
		return CHASuperClasses.get(type);
	}

	void setSuperClasses(Class<?> type, Set<Class<?>> superClasses) {
		CHASuperClasses.put(type, superClasses);
	}

	boolean inRTAResults(String className) {
		return RTAClasses.contains(className);
	}

	Set<String> getRTATypes() {

		return Collections.unmodifiableSet(RTAClasses);
	}

	void resetCHA() {
		CHASubClasses.clear();
		CHASuperClasses.clear();
	}

}
