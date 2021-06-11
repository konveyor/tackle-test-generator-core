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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Holds information about a specific parameter
 * @author RACHELBRILL
 *
 */

class ModelAttribute {

	private final List<Class<?>> types;
	private final Map<String, String> typeToRLLabel;
	private final String name;
	private final Type[] typeParam;

	ModelAttribute(List<Class<?>> types, Map<String, String> typeToRLLabel, String name, Type[] typeParam) {
		this.types = types;
		this.typeToRLLabel = typeToRLLabel;
		this.name = name;
		this.typeParam = typeParam;
	}

	ModelAttribute(List<Class<?>> types, Map<String, String> typeToRLLabel, String name) {
		this(types, typeToRLLabel, name, null);
	}


	String getName() {
		return name;
	}

	List<Class<?>> getTypes() {
		return types;
	}

	Type[] getTypeParam() {
		return typeParam;
	}

	String getRLLabel(String type) {
		return typeToRLLabel.get(type);
	}

}
