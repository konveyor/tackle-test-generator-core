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

package org.konveyor.tackle.testgen.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.evosuite.shaded.org.springframework.util.ClassUtils;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates test sequences based on CTD output to be used as building blocks for final test generation.
 * Interacts with an external test generator via a test generation API.
 * @author RACHELBRILL
 *
 */

public class TestSequenceInitializer {

	private File ctdModelsFile;
	private List<String> appClasspath;
	private String monolithAppPath;
	private final List<AbstractTestGenerator> testGenerators = new ArrayList<AbstractTestGenerator>();
	private final URLClassLoader classLoader;

	private final String testGeneratorName;

	private final String applicationName;

	private final int timeLimit;
	
	private final boolean targetSpecificMethods;
	
	private final boolean addBaseAssertions;

	public static final int DEFAULT_TIME_LIMIT = -1;

	private static final Logger logger = TackleTestLogger.getLogger(AbstractJUnitTestImporter.class);

	private volatile Map<String, String> threadsErrorMessages = new HashMap<>();
	
	private static ObjectMapper mapper = TackleTestJson.getObjectMapper();

	public TestSequenceInitializer(String appName, String ctdModelsFileName, String appPath, String appClasspathFileName, String testGenName, int timeLimit,
			boolean targetMethods, boolean baseAssertions)
			throws IOException {

		monolithAppPath = appPath;
		applicationName = appName;
		testGeneratorName = testGenName;
		this.timeLimit = timeLimit;
		targetSpecificMethods = targetMethods;
		addBaseAssertions = baseAssertions;

		File file = new File(ctdModelsFileName);
		if (!file.isFile()) {
			throw new IOException(file.getAbsolutePath() + " is not a valid file");
		}
		this.ctdModelsFile = file;

		file = new File(appClasspathFileName);
		if (!file.isFile()) {
			throw new IOException(file.getAbsolutePath() + " is not a valid file");
		}
		appClasspath = Utils.getClasspathEntries(file);
		/* Separating appClasspath from monolithAppPath, because test generators might want to
		 * handle them in different ways
		 */
		List<String> monoEntries = Arrays.asList(monolithAppPath.split(File.pathSeparator));

		if (testGeneratorName.equals(EvoSuiteTestGenerator.class.getSimpleName())) {
			testGenerators.add(new EvoSuiteTestGenerator(monoEntries, appName));
		} else if (testGeneratorName.equals(RandoopTestGenerator.class.getSimpleName())) {
			testGenerators.add(new RandoopTestGenerator(monoEntries, appName));
		} else if (testGeneratorName.equals(Constants.COMBINED_TEST_GENERATOR_NAME)) {
			testGenerators.add(new EvoSuiteTestGenerator(monoEntries, appName));
			testGenerators.add(new RandoopTestGenerator(monoEntries, appName));
		} else {
			throw new IllegalArgumentException("Unknown test generator: "+testGeneratorName);
		}

		List<String> classpathForClassLoader = new ArrayList<String>(appClasspath);
		classpathForClassLoader.addAll(monoEntries);

		classLoader = new URLClassLoader(Utils.entriesToURL(classpathForClassLoader), ClassLoader.getSystemClassLoader());
	}

	public void createInitialTests() throws IOException, SecurityException, IllegalArgumentException {

		ObjectNode mainNode = (ObjectNode) mapper.readTree(ctdModelsFile);

		ObjectNode modelsNode = (ObjectNode) mainNode.get("models_and_test_plans");

        Set<String> reachedClasses = new HashSet<String>();
        
        modelsNode.elements().forEachRemaining(classesNode -> {
        	
        	classesNode.fieldNames().forEachRemaining(receiverClassName -> {
        		
        		ObjectNode classNode = (ObjectNode) classesNode.get(receiverClassName);
        		
        		// Note: we are targeting not only the target method but also its receiver class, because the extender will reuse the receiver object generation
    			// to invoke the target method with different parameter combinations
    			Class<?> receiverClass;

    			try {
    				receiverClass = ClassUtils.forName(receiverClassName, classLoader);
    			} catch (Throwable e) {
    				logger.warning("Unable to load target class "+receiverClassName+": "+e.getMessage());
    				return;
    			}
    			Constructor<?>[] receiverConstructors = null;
    			
    			if ( ! targetSpecificMethods) {
    				for (AbstractTestGenerator testGenerator : testGenerators) {
    					testGenerator.addCoverageTarget(receiverClassName);
    				}
				} else {
					try {
						receiverConstructors = receiverClass.getConstructors();
						for (Constructor<?> constr : receiverConstructors) {
							String constrSig = Utils.getSignature(constr);
							for (AbstractTestGenerator testGenerator : testGenerators) {
								testGenerator.addCoverageTarget(receiverClassName, constrSig);
							}
						}
					} catch (Throwable e) {
						logger.warning("Unable to load target class " + receiverClassName + " constructors: "
								+ e.getMessage());
						return;
					}
				}
    			
    			classNode.fieldNames().forEachRemaining(methodSig -> {
    				ObjectNode methodNode = (ObjectNode) classNode.get(methodSig);
    				
        			if (targetSpecificMethods) {
        				for (AbstractTestGenerator testGenerator : testGenerators) {
        					testGenerator.addCoverageTarget(receiverClassName, methodSig);
        				}
        			}
        			addParameterTargets(methodNode, reachedClasses);
    				
    			});
    			
        	});
        	
        });
        
        for (AbstractTestGenerator testGenerator : testGenerators) {
        	testGenerator.setProjectClasspath(Utils.entriesToClasspath(appClasspath));
        }
        Map<String, Map<String, String>> generatorDedicatedSettings = new HashMap<String, Map<String, String>>();

        if (testGeneratorName.equals(EvoSuiteTestGenerator.class.getSimpleName()) || testGeneratorName.equals(Constants.COMBINED_TEST_GENERATOR_NAME)) {
        	Map<String, String> evoSuiteSettings = new HashMap<String, String>();
        	evoSuiteSettings.put(EvoSuiteTestGenerator.Options.ASSERTIONS.name(), String.valueOf(addBaseAssertions));
        	evoSuiteSettings.put(EvoSuiteTestGenerator.Options.CRITERION.name(), "BRANCH");
        	if (timeLimit != DEFAULT_TIME_LIMIT) {
        		evoSuiteSettings.put(EvoSuiteTestGenerator.Options.SEARCH_BUDGET.name(), String.valueOf(timeLimit));
        	}
        	generatorDedicatedSettings.put(EvoSuiteTestGenerator.class.getSimpleName(), evoSuiteSettings);
        }

        if (testGeneratorName.equals(RandoopTestGenerator.class.getSimpleName()) || testGeneratorName.equals(Constants.COMBINED_TEST_GENERATOR_NAME)) {
        	Map<String, String> randoopSettings = new HashMap<String, String>();
        	randoopSettings.put(RandoopTestGenerator.RandoopOptions.REGRESSION_ASSERTIONS.name(), String.valueOf(addBaseAssertions));
        	if (timeLimit != DEFAULT_TIME_LIMIT) {
        		randoopSettings.put(RandoopTestGenerator.RandoopOptions.TIME_LIMIT.name(), String.valueOf(timeLimit));
        	}
        	generatorDedicatedSettings.put(RandoopTestGenerator.class.getSimpleName(), randoopSettings);
        }

        for (AbstractTestGenerator testGenerator : testGenerators) {
        	testGenerator.configure(generatorDedicatedSettings.get(testGenerator.getName()));
        }

        List<TestGeneratorInvoker> testGeneratorThreads = new ArrayList<TestGeneratorInvoker>();

		for (AbstractTestGenerator testGenerator : testGenerators) {
			Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread th, Throwable ex) {
                    String threadName = ((TestGeneratorInvoker)th).testGenerator.toString();
                    int splitStrInd = threadName.indexOf('@');
                    if (splitStrInd!=-1) {
                        threadName = threadName.substring(0,splitStrInd);
                    }
                    threadsErrorMessages.put(threadName, ex.getMessage());
                }
            };
			TestGeneratorInvoker thread = new TestGeneratorInvoker(testGenerator);
			thread.setUncaughtExceptionHandler(h);
			thread.start();
			testGeneratorThreads.add(thread);
        }

		for (TestGeneratorInvoker thread : testGeneratorThreads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				// do nothing
			}
		}

		if (!threadsErrorMessages.isEmpty()) {
            String errorMessage = "";
            for (String threadName: threadsErrorMessages.keySet()) {
                String threadError = threadName + ": " + threadsErrorMessages.get(threadName) + System.lineSeparator();
                errorMessage += threadError;
            }
            throw new IOException(errorMessage);
        }
	}

	private void addParameterTargets(ObjectNode modelObject, Set<String> reachedClasses) throws LinkageError {
		ArrayNode attrsArray = (ArrayNode) modelObject.get("attributes");
    	for (int j = 0; j < attrsArray.size(); j++) {
    		ObjectNode attrObject = (ObjectNode) attrsArray.get(j);
    		ArrayNode valuesArray = (ArrayNode) attrObject.get("values");
    		for (int k = 0; k < valuesArray.size(); k++) {
    			JsonNode valueObject = valuesArray.get(k).get("val_"+String.valueOf(k));
    			List<String> targetClasses;
    			if (valueObject instanceof ArrayNode) {
    				targetClasses = new ArrayList<String>();
    				ArrayNode singleValueArray = (ArrayNode) valueObject;
    				for (int m = 0; m < singleValueArray.size(); m++) {
    					targetClasses.add(singleValueArray.get(m).asText());
    				}
    			} else {
    				targetClasses = Collections.singletonList(((ObjectNode)valuesArray.get(k)).get("val_"+String.valueOf(k)).asText());
    			}

    			for (String targetClass : targetClasses) {

    				if ( ! reachedClasses.contains(targetClass)) {
    					reachedClasses.add(targetClass);
						// TODO: replace by generic class name for arrays and check !theClass.isArray()
						if (!targetClass.endsWith("[]") && !Utils.isJavaType(targetClass)) {
							Class<?> theClass;
							try {
								theClass = ClassUtils.forName(targetClass, classLoader);
							} catch (Throwable e) {
								logger.warning("Unable to load target class "+targetClass+": "+e.getMessage());
			    				continue;
							}
							if (!Utils.isPrimitive(theClass)) {

								if ( ! targetSpecificMethods) {
									for (AbstractTestGenerator testGenerator : testGenerators) {
										testGenerator.addCoverageTarget(targetClass);
									}
								} else {

									Constructor<?>[] constructors;

									try {
										constructors = theClass.getConstructors();
										for (Constructor<?> constr : constructors) {
											String constrSig = Utils.getSignature(constr);
											for (AbstractTestGenerator testGenerator : testGenerators) {
												if (targetSpecificMethods) {
													testGenerator.addCoverageTarget(targetClass, constrSig);
												}
											}
										}
									} catch (Throwable e) {
										logger.warning("Unable to load target class " + targetClass + " constructors: "
												+ e.getMessage());
										continue;
									}
								}

							}
						}
    				}
    			}
    		}
    	}
	}

	private class TestGeneratorInvoker extends Thread {

		AbstractTestGenerator testGenerator;

		private TestGeneratorInvoker(AbstractTestGenerator testGenerator) {
			this.testGenerator = testGenerator;
		}

		public void run() {
			try {
				testGenerator.generateTests();
				exportTestSequences(testGenerator.getOutputDir());
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}

		}

		private void exportTestSequences(File testsOutputDir) throws IOException {

			AbstractJUnitTestImporter testImporter = testGenerator.getJUnitTestImporter(testsOutputDir);

			testImporter.importSequences();

			ObjectNode sequencesObject = mapper.createObjectNode();

			List<String> beforeAfterCodeClasses = new ArrayList<String>();

			for (String className : testGenerator.getCoverageTargets().keySet()) {

				Set<String> sequences = testImporter.getSequences(className);
				Set<String> imports = testImporter.getImports(className);

				if (sequences == null || sequences.isEmpty()) {
					logger.warning("Could not generate sequences for class: "+className);
					continue;
				}

				ArrayNode seqList = mapper.createArrayNode();

				for (String sequence : sequences) {

					seqList.add(sequence);
				}

				ArrayNode importList = mapper.createArrayNode();

				for (String imp : imports) {

					importList.add(imp);
				}

				ObjectNode contentObject = mapper.createObjectNode();

				contentObject.set("sequences", seqList);
				contentObject.set("imports", importList);
				ArrayNode codeList = mapper.createArrayNode();

				List<String> beforeAfterSegs = testImporter.beforeAfterCodeSegments(className);

				if ( ! beforeAfterSegs.isEmpty()) {
					beforeAfterCodeClasses.add(className);
				}

				for (String codeSeg : beforeAfterSegs) {
					codeList.add(codeSeg);
				}

				contentObject.set("before_after_code_segments", codeList);

				sequencesObject.set(className, contentObject);
			}

			ObjectNode object = mapper.createObjectNode();

			object.set("test_sequences", sequencesObject);

			object.put("test_generation_tool", testGenerator.getName());
			
			mapper.writeValue(new File(applicationName+"_"+testGenerator.getName()+"_"+
					Constants.INITIALIZER_OUTPUT_FILE_NAME_SUFFIX), object);

			String classpath = System.getProperty("java.class.path")+File.pathSeparator+Utils.entriesToClasspath(appClasspath)+File.pathSeparator+monolithAppPath;

			if ( ! beforeAfterCodeClasses.isEmpty()) {
				testImporter.compileBeforeAfterCode(beforeAfterCodeClasses, classpath);
			}
		}
	}



	private static CommandLine parseCommandLineOptions(String[] args) {
        Options options = new Options();

        // option for application name
        options.addOption(Option.builder("app")
                .longOpt("application-name")
                .hasArg()
                .desc("Name of the application under test")
                .type(String.class)
                .build()
        );

      // option for application path
        options.addOption(Option.builder("pt")
            .longOpt("application-path")
            .hasArg()
            .desc("Path to the application under test")
            .type(String.class)
            .build()
        );

        // option for application classpath file name
        options.addOption(Option.builder("clpt")
            .longOpt("application-classpath")
            .hasArg()
            .desc("Name of file containing application classpath entries")
            .type(String.class)
            .build()
        );

        // option for CTD test plan file
        options.addOption(Option.builder("tp")
                .longOpt("test-plan")
                .hasArg()
                .desc("Name of JSON file containing the CTD test plan")
                .type(String.class)
                .build()
        );

     // option for test generator
        options.addOption(Option.builder("tg")
                .longOpt("test-generator")
                .hasArg()
                .desc("Name of test generator. Default is RandoopTestGenerator.")
                .type(String.class)
                .build()
        );

     // option for time limit
        options.addOption(Option.builder("tl")
                .longOpt("time-limit")
                .hasArg()
                .desc("Time limit for test generator per class. Default is tool's specific default.")
                .type(Integer.class)
                .build()
        );
        
     // option for targeting specific methods
        options.addOption(Option.builder("tm")
                .longOpt("target-methods")
                .desc("Target specific methods rather than entire classes under test.")
                .type(Boolean.class)
                .build()
        );
        
        
     // option for adding assertions to base tests
        options.addOption(Option.builder("ba")
                .longOpt("base-tests-assertions")
                .desc("Generate assertions in base tests. This option should be used for techique evaluation only.")
                .type(Boolean.class)
                .build()
        );

        // help option
        options.addOption(Option.builder("h")
            .longOpt("help")
            .desc("Print this help message")
            .build()
        );

        HelpFormatter formatter = new HelpFormatter();

        // parse command line options
        CommandLineParser argParser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = argParser.parse(options, args);
            // if help option specified, print help message and return null
            if (cmd.hasOption("h")) {
                formatter.printHelp(TestSequenceInitializer.class.getName(), options, true);
                return null;
            }
        }
        catch (ParseException e) {
            logger.warning(e.getMessage());
            formatter.printHelp(TestSequenceInitializer.class.getName(), options, true);
        }

        // check whether required options are specified
        if (!cmd.hasOption("tp") || !cmd.hasOption("app") || !cmd.hasOption("pt") || !cmd.hasOption("clpt")) {
            formatter.printHelp(TestSequenceInitializer.class.getName(), options, true);
            return null;
        }

        return cmd;
    }


	public static void main(String args[]) throws FileNotFoundException, IOException, SecurityException, IllegalArgumentException {

		 // parse command-line options
        CommandLine cmd = parseCommandLineOptions(args);

        // if parser command-line is empty (which occurs if the help option is specified or a
        // parse exception occurs, exit
        if (cmd == null) {
            System.exit(0);
        }

        String appName = cmd.getOptionValue("app");
        String testPlanFilename = cmd.getOptionValue("tp");
        String appPath = cmd.getOptionValue("pt");
        String classpathFilename = cmd.getOptionValue("clpt");

        String testGenerator = RandoopTestGenerator.class.getSimpleName();

        if (cmd.hasOption("tg")) {
        	testGenerator = cmd.getOptionValue("tg");
        }

        int timeLimit = DEFAULT_TIME_LIMIT;

        if (cmd.hasOption("tl")) {
        	timeLimit = Integer.valueOf(cmd.getOptionValue("tl"));
        }
        
        boolean targetMethods = false;
        
        if (cmd.hasOption("tm")) {
        	targetMethods = true;
        }
        
        boolean baseAssertions = false;
        
        if (cmd.hasOption("ba")) {
        	baseAssertions = true;
        }

        logger.info("Application name: "+appName);
        logger.info("CTD test plan file: "+testPlanFilename);
        logger.info("Application path: "+appPath);
        logger.info("Application classpath file name: "+classpathFilename);
        logger.info("Test generator name: "+testGenerator);
        logger.info("Time limit per class: "+timeLimit);
        if (targetMethods) {
        	logger.info("Targeting specific methods");
        }
        if (baseAssertions) {
        	logger.info("Adding assertions to base tests");
        }

		new TestSequenceInitializer(appName, testPlanFilename, appPath, classpathFilename, testGenerator, timeLimit, targetMethods, baseAssertions).
		createInitialTests();
	}



}
