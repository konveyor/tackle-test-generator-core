/*
 * Copyright IBM Corporation 2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.konveyor.tackle.testgen.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import soot.SootClass;

/**
 * A class that holds CHA and RTA results for reuse in the same partition
 * @author RACHELBRILL
 *
 */

class TypeAnalysisResults {

	private final Set<String> RTAClasses;

	private Map<Class<?>, Set<SootClass>> CHASubClasses = new HashMap<>();
	private Map<Class<?>, Set<SootClass>> CHASuperClasses = new HashMap<>();

	TypeAnalysisResults(Set<String> RTAResults) {

		RTAClasses = Collections.unmodifiableSet(RTAResults);
	}

	Set<SootClass> getSubClasses(Class<?> type) {
		return CHASubClasses.get(type);
	}

	void setSubClasses(Class<?> type, Set<SootClass> subclasses) {
		CHASubClasses.put(type, subclasses);
	}

	Set<SootClass> getSuperClasses(Class<?> type) {
		return CHASuperClasses.get(type);
	}

	void setSuperClasses(Class<?> type, Set<SootClass> superClasses) {
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
