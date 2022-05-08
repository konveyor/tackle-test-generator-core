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

package org.konveyor.tackle.testgen.core.extender;

import randoop.types.*;

import java.util.ArrayList;
import java.util.List;

public class ExtenderUtil {

    /**
     * Returns a list of strings representing the binary names of the declared type arguments
     * for a parameterized collection/map type or empty list if the type is not parameterized.
     * For a wildcard type, the list contains "java.lang.Object" as the type to be instantiated.
     * @param type
     * @return
     */
    static List<ReferenceType> getTypeArguments(Type type) throws ClassNotFoundException {
        String wildcardTypeArg = "java.lang.Object";
        List<ReferenceType> typeArgsRefTypes = new ArrayList<>();
        if (type.isParameterized()) {
            ParameterizedType parameterizedType = (ParameterizedType)type;
            List<TypeArgument> typeArgs = parameterizedType.getTypeArguments();
            for (TypeArgument typeArg : typeArgs) {
                if (typeArg.isWildcard()) {
                    typeArgsRefTypes.add(ReferenceType.forClass(Class.forName(wildcardTypeArg)));
                }
                else {
                    typeArgsRefTypes.add(((ReferenceArgument)typeArg).getReferenceType());
                }
            }
        }
        return typeArgsRefTypes;
    }

}
