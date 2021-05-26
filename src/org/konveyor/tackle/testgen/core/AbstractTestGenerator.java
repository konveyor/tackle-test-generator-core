package org.konveyor.tackle.testgen.core;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test generator interface
 * @author RACHELBRILL
 *
 */

public abstract class AbstractTestGenerator {

	// Coverage targets: map from classname to set of method signatures
	protected Map<String, Set<String>> targets = new HashMap<String, Set<String>>();

	protected String projectClasspath = "";

	/* Holds the path to the application classes */

	protected final List<String> targetClassesPath;


	public AbstractTestGenerator(List<String> targetPath) {
		targetClassesPath = targetPath;
	}

	/**
	 * Add the given method in the given class to the set of targets for test genereation
	 * @param className target class
	 * @param methodSig target method in class
	 */
    void addCoverageTarget(String className, String methodSig) {
        Set<String> methods = this.targets.get(className);
        if (methods == null) {
            methods = new HashSet<String>();
            this.targets.put(className, methods);
        }
        methods.add(methodSig);
	}

	/**
	 * Adds the given class as a coverage target for test generation (all methods in class are targeted)
	 * @param className
	 */
	void addCoverageTarget(String className) {
        if (!this.targets.containsKey(className)) {
            this.targets.put(className, new HashSet<String>());
        }
	}

	/**
	 * Removes the given class and method from the set of coverage targets
	 * @param className
	 * @param methodSig
	 */
    void removeCoverageTarget(String className, String methodSig) {
        if (this.targets.containsKey(className)) {
            this.targets.get(className).remove(methodSig);
        }
	}

	/**
	 * Removes the given class from the set of coverage targets
	 * @param className
	 */
	void removeCoverageTarget(String className) {
        this.targets.remove(className);
	}

	/**
	 * Sets coverage targets to the given (class, method set) map. If method set os empty, all methods
	 * in the class are targeted.
	 * @param targets
	 */
	void setCoverageTargets(Map<String, Set<String>> targets) {
        this.targets = targets;
	}

	Map<String, Set<String>> getCoverageTargets() {
        return this.targets;
	}

	void setProjectClasspath(String classpath) {
		this.projectClasspath = classpath;
	}

	abstract File getOutputDir();
	abstract void configure(Map<String, String> settings);
	abstract void generateTests() throws IOException, InterruptedException;
	abstract String getName();
	abstract AbstractJUnitTestImporter getJUnitTestImporter(File outputDir) throws IOException;
}
