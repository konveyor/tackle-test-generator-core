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

package org.konveyor.tackle.testgen.core.extender;

import randoop.types.GenericClassType;
import randoop.types.InstantiatedType;
import randoop.types.JDKTypes;
import randoop.types.ReferenceType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/**
 * Utility class for computing instantiation information for different Java collection types.
 */
public class JavaCollectionTypes {

    /**
     * Inner class storing instantiation information for Java collection and map types.
     */
    static class InstantiationInfo {
        InstantiatedType instantiatedType;
        Constructor<?> typeConstructor;
        Method addMethod;

        InstantiationInfo(InstantiatedType instType, Constructor<?> constructor, Method method) {
            this.instantiatedType = instType;
            this.typeConstructor = constructor;
            this.addMethod = method;
        }
    }

    /**
     * Returns instantiation information for different concrete collection types in the Java API.
     * @param type
     * @param typeArgument
     * @return
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     */
    static InstantiationInfo getCollectionTypeInstantiationInfo(String type, ReferenceType typeArgument)
        throws ClassNotFoundException, NoSuchMethodException {

        Class<?> typeClass = Class.forName(type);

        // for specific types, create related instantiation info
        if (type.equals("java.util.concurrent.ArrayBlockingQueue")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.ARRAY_BLOCKING_QUEUE_TYPE, typeArgument),
                BlockingQueue.class.getConstructor(),
                BlockingQueue.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.ArrayDeque")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.ARRAY_DEQUE_TYPE, typeArgument),
                ArrayDeque.class.getConstructor(),
                ArrayDeque.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.ArrayList")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.ARRAY_LIST_TYPE, typeArgument),
                ArrayList.class.getConstructor(),
                ArrayList.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.concurrent.ConcurrentLinkedQueue")) {
            return new InstantiationInfo(getInstantiatedType(
                JDKTypes.CONCURRENT_LINKED_QUEUE_TYPE, typeArgument),
                ConcurrentLinkedQueue.class.getConstructor(),
                ConcurrentLinkedQueue.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.concurrent.ConcurrentSkipListSet")) {
            return new InstantiationInfo(getInstantiatedType(
                JDKTypes.CONCURRENT_SKIP_LIST_SET_TYPE, typeArgument),
                ConcurrentSkipListSet.class.getConstructor(),
                ConcurrentSkipListSet.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.DelayQueue")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.DELAY_QUEUE_TYPE, typeArgument),
                DelayQueue.class.getConstructor(),
                DelayQueue.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.EnumSet")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.ENUM_SET_TYPE, typeArgument),
                EnumSet.class.getConstructor(),
                EnumSet.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.HashSet")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.HASH_SET_TYPE, typeArgument),
                HashSet.class.getConstructor(),
                HashSet.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.concurrent.LinkedBlockingDeque")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.LINKED_BLOCKING_DEQUE_TYPE, typeArgument),
                LinkedBlockingDeque.class.getConstructor(),
                LinkedBlockingDeque.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.concurrent.LinkedBlockingQueue")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.LINKED_BLOCKING_QUEUE_TYPE, typeArgument),
                LinkedBlockingQueue.class.getConstructor(),
                LinkedBlockingQueue.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.LinkedHashSet")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.LINKED_HASH_SET_TYPE, typeArgument),
                LinkedHashSet.class.getConstructor(),
                LinkedHashSet.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.LinkedList")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.LINKED_LIST_TYPE, typeArgument),
                LinkedList.class.getConstructor(),
                LinkedList.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.concurrent.LinkedTransferQueue")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.LINKED_TRANSFER_QUEUE_TYPE, typeArgument),
                LinkedTransferQueue.class.getConstructor(),
                LinkedTransferQueue.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.concurrent.PriorityBlockingQueue")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.PRIORITY_BLOCKING_QUEUE_TYPE, typeArgument),
                PriorityBlockingQueue.class.getConstructor(),
                PriorityBlockingQueue.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.concurrent.PriorityQueue")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.PRIORITY_QUEUE_TYPE, typeArgument),
                PriorityQueue.class.getConstructor(),
                PriorityQueue.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.Stack")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.STACK_TYPE, typeArgument),
                Stack.class.getConstructor(),
                Stack.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.concurrent.SynchronousQueue")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.SYNCHRONOUS_QUEUE_TYPE, typeArgument),
                SynchronousQueue.class.getConstructor(),
                SynchronousQueue.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.TreeSet")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.TREE_SET_TYPE, typeArgument),
                TreeSet.class.getConstructor(),
                TreeSet.class.getMethod("add", Object.class)
            );
        }
        if (type.equals("java.util.Vector")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.VECTOR_TYPE, typeArgument),
                Vector.class.getConstructor(),
                Vector.class.getMethod("add", Object.class)
            );
        }

        // for a set type, by default, create instantiation info for java.util.HashSet
        if (java.util.Set.class.isAssignableFrom(typeClass)) {
            return new InstantiationInfo(getInstantiatedType(
                JDKTypes.HASH_SET_TYPE, typeArgument),
                HashSet.class.getConstructor(),
                HashSet.class.getMethod("add", Object.class)
            );
        }

        // by default, create instantiation info for java.util.ArrayList
        return new InstantiationInfo(
            getInstantiatedType(JDKTypes.ARRAY_LIST_TYPE, typeArgument),
            ArrayList.class.getConstructor(),
            ArrayList.class.getMethod("add", Object.class)
        );
    }

    /**
     * Returns instantiation information for different concrete map types in the Java API.
     * @param type
     * @param keyTypeArgument
     * @param valueTypeArgument
     * @return
     * @throws NoSuchMethodException
     */
    static InstantiationInfo getMapTypeInstantiationInfo(String type, ReferenceType keyTypeArgument,
                                                         ReferenceType valueTypeArgument)
        throws NoSuchMethodException {

        // for specific types, create related instantiation info
        if (type.equals("java.util.concurrent.ConcurrentHashMap")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.CONCURRENT_HASH_MAP_TYPE, keyTypeArgument, valueTypeArgument),
                ConcurrentHashMap.class.getConstructor(),
                ConcurrentHashMap.class.getMethod("put", Object.class, Object.class)
            );
        }
        if (type.equals("java.util.concurrent.ConcurrentSkipListMap")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.CONCURRENT_SKIP_LIST_MAP_TYPE, keyTypeArgument, valueTypeArgument),
                ConcurrentSkipListMap.class.getConstructor(),
                ConcurrentSkipListMap.class.getMethod("put", Object.class, Object.class)
            );
        }
        if (type.equals("java.util.EnumMap")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.ENUM_MAP_TYPE, keyTypeArgument, valueTypeArgument),
                EnumMap.class.getConstructor(),
                EnumMap.class.getMethod("put", Object.class, Object.class)
            );
        }
        if (type.equals("java.util.HashMap")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.HASH_MAP_TYPE, keyTypeArgument, valueTypeArgument),
                HashMap.class.getConstructor(),
                HashMap.class.getMethod("put", Object.class, Object.class)
            );
        }
        if (type.equals("java.util.HashTable")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.HASH_TABLE_TYPE, keyTypeArgument, valueTypeArgument),
                Hashtable.class.getConstructor(),
                Hashtable.class.getMethod("put", Object.class, Object.class)
            );
        }
        if (type.equals("java.util.IdentityHashMap")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.IDENTITY_HASH_MAP_TYPE, keyTypeArgument, valueTypeArgument),
                IdentityHashMap.class.getConstructor(),
                IdentityHashMap.class.getMethod("put", Object.class, Object.class)
            );
        }
        if (type.equals("java.util.LinkedHashMap")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.LINKED_HASH_MAP_TYPE, keyTypeArgument, valueTypeArgument),
                HashMap.class.getConstructor(),
                HashMap.class.getMethod("put", Object.class, Object.class)
            );
        }
        if (type.equals("java.util.TreeMap")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.TREE_MAP_TYPE, keyTypeArgument, valueTypeArgument),
                TreeMap.class.getConstructor(),
                TreeMap.class.getMethod("put", Object.class, Object.class)
            );
        }
        if (type.equals("java.util.WeakHashMap")) {
            return new InstantiationInfo(
                getInstantiatedType(JDKTypes.WEAK_HASH_MAP_TYPE, keyTypeArgument, valueTypeArgument),
                WeakHashMap.class.getConstructor(),
                WeakHashMap.class.getMethod("put", Object.class, Object.class)
            );
        }

        // by default, create instantiation info for java.util.HashMap
        return new InstantiationInfo(
            getInstantiatedType(JDKTypes.HASH_MAP_TYPE, keyTypeArgument, valueTypeArgument),
            HashMap.class.getConstructor(),
            HashMap.class.getMethod("put", Object.class, Object.class)
        );
    }

    private static InstantiatedType getInstantiatedType(GenericClassType genericType,
                                                        ReferenceType typeArgument) {
        if (typeArgument != null) {
            return genericType.instantiate(typeArgument);
        }
        // if type argument is unspecified, use java.lang.Object
        return genericType.instantiate(ReferenceType.forClass(Object.class));
//        return genericType.instantiate();
    }

    private static InstantiatedType getInstantiatedType(GenericClassType genericType,
                                                        ReferenceType keyTypeArgument,
                                                        ReferenceType valueTypeArgument) {
        if (keyTypeArgument != null && valueTypeArgument != null) {
            return genericType.instantiate(keyTypeArgument, valueTypeArgument);
        }
        // if key and value type arguments are unspecified, use java.lang.Object
        return genericType.instantiate(ReferenceType.forClass(Object.class),
            ReferenceType.forClass(Object.class));
//        return genericType.instantiate();
    }

}
