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

import org.konveyor.tackle.testgen.rta.RapidTypeAnalysis;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;
import org.apache.commons.cli.*;
import soot.FastHierarchy;
import soot.Scene;
import soot.SootClass;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.*;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class for extracting proxy methods and analyzing their parameters
 * @author RACHELBRILL
 *
 */

public class CTDTestPlanGenerator {

	final boolean useAllCHATypes;
    final int maxCollectionNestDepth;
	final boolean addLocalRemoteTag;
	final int interactionLevel;

	private final String cpPrefix;
	private final String cpSuffix;

	private final String[] appDirs;

	private TypeAnalysisResults typeAnalysisResults;

	private int proxyModelsCounter = 0;
	private int proxyTestsCounter = 0;

	private final String applicationName;

	private TargetFetcher targetFetcher;

	private static final Logger logger = TackleTestLogger.getLogger(CTDTestPlanGenerator.class);

	private List<String> appClasspathEntries; // Application under test classpath entries
	private String refactoringPackagePrefix = null;
	private String partitionsFilePrefix = null;
	private String partitionsFileSuffix = null;
	private String partitionsFileSeparator = null;


	public CTDTestPlanGenerator(String appName, String fileName, String targetClassList, String partitionsCPPrefix, String partitionsCPSuffix, String monolithPath, 
			String classpathFile, boolean allCHA, int maxNestDepth, boolean addLocalRemote, int level, String refactoringPrefix, 
			String partitionsPrefix, String partitionsSuffix, String partitionsSeparator) throws IOException {

		applicationName = appName;
		cpPrefix = partitionsCPPrefix;
		cpSuffix = partitionsCPSuffix;
		appDirs =  monolithPath.split(File.pathSeparator);
		useAllCHATypes = allCHA;
		maxCollectionNestDepth = maxNestDepth;
		addLocalRemoteTag = addLocalRemote;
		interactionLevel = level;
		refactoringPackagePrefix  = refactoringPrefix;
		partitionsFilePrefix = partitionsPrefix;
		partitionsFileSuffix = partitionsSuffix;
		partitionsFileSeparator = partitionsSeparator;

		appClasspathEntries = Utils.getClasspathEntries(new File(classpathFile));

		if (fileName != null) {
			targetFetcher = new PartitionTargets(new File(fileName));
		} else {
			targetFetcher = new ClassListTargets(targetClassList);
		}

		Set<String> RTAClasses = new RapidTypeAnalysis().performAnalysis(monolithPath, appClasspathEntries);

		typeAnalysisResults = new TypeAnalysisResults(RTAClasses);
	}

	public void modelPartitions()
			throws IOException, ClassNotFoundException, SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException {

		proxyModelsCounter = 0;
		proxyTestsCounter = 0;

		JsonWriter writer = null;

		try {

			JsonObjectBuilder partitionBuilder = Json.createObjectBuilder();

			for (String partition : targetFetcher.getPartitions()) {

				JsonObject partitionObj = modelPartition(partition, typeAnalysisResults);
				typeAnalysisResults.resetCHA(); // we need to compute CHA per partition

				partitionBuilder.add(partition, partitionObj);
			}

			JsonObjectBuilder object = Json.createObjectBuilder();

			object.add("models_and_test_plans", partitionBuilder.build());

			JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
			writer = writerFactory.createWriter(new FileOutputStream(new File(applicationName+"_"+Constants.CTD_OUTFILE_SUFFIX)));

			writer.writeObject(object.build());

		} finally {
			if (writer != null) {
				writer.close();
			}
		}

		System.out.println("* Created a total of "+proxyTestsCounter+" test combinations for "+proxyModelsCounter+" target methods");
	}

	private JsonObject modelPartition(String partitionName, TypeAnalysisResults typeAnalysisResults)
			throws IOException, ClassNotFoundException, SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException {

		logger.fine("Analyzing partition "+partitionName);
		URLClassLoader classLoader = targetFetcher.loadCurrentClasses(partitionName);

		List<String> classNames = targetFetcher.getTargetClasses(partitionName);

		Map<String, List<JavaMethodModel>> classToMethods = new HashMap<String, List<JavaMethodModel>>();

		for (String currentClassName : classNames) {
			List<JavaMethodModel> publicMethods = new ArrayList<JavaMethodModel>();
			Class<?> proxyClass = Class.forName(currentClassName, false, classLoader);
			if (Modifier.isPublic(proxyClass.getModifiers())) {
				for (Object method : targetFetcher.getTargetMethods(proxyClass, classLoader)) {
					publicMethods.add(new JavaMethodModel(partitionName, proxyClass, method, classLoader,
							useAllCHATypes, maxCollectionNestDepth));
				}
				if (!publicMethods.isEmpty()) {
					classToMethods.put(currentClassName, publicMethods);
				}
			}
		}

		CTDModeler modeler = new CTDModeler(targetFetcher, refactoringPackagePrefix);

		JsonObjectBuilder classesBuilder = Json.createObjectBuilder();

		for (Map.Entry<String, List<JavaMethodModel>> entry : classToMethods.entrySet()) {

			JsonObjectBuilder modelsBuilder = Json.createObjectBuilder();

			for (JavaMethodModel method : entry.getValue()) {

				int addedTests = modeler.analyzeParams(method, typeAnalysisResults, modelsBuilder, addLocalRemoteTag,
						interactionLevel, Class.forName(entry.getKey(), false, classLoader));

				if (addedTests > 0) {
					proxyModelsCounter++;
					proxyTestsCounter += addedTests;
				}
			}

			classesBuilder.add(entry.getKey(), modelsBuilder.build());
		}

		return classesBuilder.build();
	}


	abstract class TargetFetcher {

		abstract Set<String> getPartitions();

		abstract URLClassLoader loadCurrentClasses(String partitionName) throws MalformedURLException;

		abstract List<String> getTargetClasses(String partitionName);

		abstract String getLocalRemoteTag(JavaMethodModel method, String className);

		protected List<Object> getTargetMethods(Class<?> cls, URLClassLoader classLoader) {

			if (Utils.isPrivateInnerClass(cls)) {
				logger.fine("Skipping private inner class "+cls.getName());
				return Collections.emptyList();
			}

			List<Object> publicMethods = new ArrayList<Object>();

			/* We may encounter the same method twice if a method
			 * is overridden with a subtype of the original type as a return type*/
			Set<String> methodSigs = new HashSet<>();

			Map<Class<?>, Set<Class<?>>> classToImplementingClasses = new HashMap<>();


			Map<Class<?>, Set<Method>> parameterizedInterfaceMethods = initParameterizedInterfaces(cls);

			for (Method method : cls.getDeclaredMethods()) {

				if ( ! Modifier.isPublic(method.getModifiers())) {
					continue;
				}

				if (Modifier.isAbstract(method.getModifiers()) && ! hasImplementations(cls, method, classToImplementingClasses, classLoader)) {
					logger.fine("Skipping method "+cls.getName()+":"+method.getName()+" since it has no implementation");
					continue;
				}

				String methodSig = null;
				try {
					methodSig = Utils.getSignature(method);
					// remove return type
					methodSig = methodSig.substring(0, methodSig.lastIndexOf(')')+1);
				} catch (NoSuchFieldException | SecurityException | IllegalArgumentException
						| IllegalAccessException e) {
					// skip check in this case
					logger.warning("Unable to compute signature for "+method.getName());
				}

				if (methodSig != null && ! methodSigs.contains(methodSig) && ! isParameterizedMethodVersion(method, parameterizedInterfaceMethods)) {
					publicMethods.add(method);
					methodSigs.add(methodSig);
				}
			}

			for (Constructor<?> constr : cls.getConstructors()) {
				if (Modifier.isPublic(constr.getModifiers())) {
					publicMethods.add(constr);
				}
			}

			return publicMethods;
		}

		private boolean isParameterizedMethodVersion(Method method, Map<Class<?>, Set<Method>> parameterizedInterfaceMethods) {

			for (Map.Entry<Class<?>, Set<Method>> entry : parameterizedInterfaceMethods.entrySet()) {

				for (Method interMethod : entry.getValue()) {

					if (interMethod.getName().equals(method.getName()) && interMethod.getParameterCount() == method.getParameterCount()) {
						TypeVariable<?>[] types = entry.getKey().getTypeParameters();
						Type[] methodParams = method.getGenericParameterTypes();
						Type[] interParams = interMethod.getGenericParameterTypes();

						boolean isMatch = true;

						for (int i=0;i< methodParams.length;i++) {
							if ( ! interParams[i].getTypeName().equals(methodParams[i].getTypeName()) &&
								 ( ! methodParams[i].getTypeName().equals("java.lang.Object") || ! isTypeParam(interParams[i], types))) {
								isMatch = false;
								break;
							}
						}

						if (isMatch) {
							return true;
						}
					}
				}
			}

			return false;
		}

		private boolean isTypeParam(Type type, TypeVariable<?>[] types) {

			for (TypeVariable<?> typeParam : types) {

				if (type.getTypeName().equals(typeParam.getTypeName())) {
					return true;
				}
			}

			return false;
		}

		private Map<Class<?>, Set<Method>> initParameterizedInterfaces(Class<?> cls) {
			Class<?>[] interfaces = cls.getInterfaces();
			Map<Class<?>, Set<Method>> parameterizedInterfaceMethods = new HashMap<>();
			for (Class<?> inter : interfaces) {
				TypeVariable<?>[] types = inter.getTypeParameters();
				if (types.length > 0) {
					Set<Method> paramMethods = new HashSet<>();
					for (Method method : inter.getDeclaredMethods()) {
						for (Type param : method.getGenericParameterTypes()) {
							if (isTypeParam(param, types)) {
								paramMethods.add(method);
								break;
							}
						}
					}
					if ( ! paramMethods.isEmpty()) {
						parameterizedInterfaceMethods .put(inter, paramMethods);
					}
				}
			}

			return parameterizedInterfaceMethods;
		}

		private boolean hasImplementations(Class<?> cls, Method method, Map<Class<?>, Set<Class<?>>> classToImplementingClasses, URLClassLoader classLoader) {

			Set<Class<?>> implementingClasses = classToImplementingClasses.get(cls);

			if (implementingClasses == null) {

				implementingClasses = new HashSet<Class<?>>();
				classToImplementingClasses.put(cls, implementingClasses);

				Scene.v().loadClassAndSupport(cls.getName());
				FastHierarchy classHierarchy = Scene.v().getOrMakeFastHierarchy();

				SootClass sootCls = Scene.v().getSootClass(cls.getName());
				Set<SootClass> implementingSootClasses = new HashSet<>(classHierarchy.getSubclassesOf(sootCls));
				implementingSootClasses.addAll(classHierarchy.getAllImplementersOfInterface(sootCls));

				for (SootClass implClsSoot : implementingSootClasses) {
					try {
						implementingClasses.add(Class.forName(implClsSoot.getName(), false, classLoader));
					} catch (ClassNotFoundException e) {
						throw new RuntimeException(e);
					}
				}
			}

			for (Class<?> implementingClass : implementingClasses) {

				if ( ! Modifier.isAbstract(implementingClass.getModifiers())) {
					return true;
				} else {
					try {
						implementingClass.getMethod(method.getName(), method.getParameterTypes());
						return true;
					} catch (NoSuchMethodException e) {
						// do nothing
					}
				}
			}

			return false;
		}
	}

	private class PartitionTargets extends TargetFetcher {

		class PartitionData {

			Set<String> proxyClasses;
			Set<String> localClasses;

			PartitionData(Set<String> proxy, Set<String> local) {
				proxyClasses = proxy;
				localClasses = local;
			}

		}

		/* Map from each proxy class to its remote classes */

		private Map<String, PartitionData> proxyClassesToRemoteClasses = new HashMap<String, PartitionData>();

		private PartitionTargets(File partitionFile) throws IOException {
			initClassesData(partitionFile);
		}

		private void initClassesData(File inputFile) throws IOException {

			if (!inputFile.isFile()) {
				throw new IOException(inputFile.getName() + " is not a valid file name");
			}

			InputStream fis = new FileInputStream(inputFile);
	        JsonReader reader = Json.createReader(fis);
	        JsonObject mainObject = reader.readObject();
	        reader.close();

	        Set<String> keys = mainObject.keySet();

	        for (String partition : keys) {
	        	readCurrentPartitionClasses(partition, mainObject.getJsonObject(partition));
	        }
		}

		private void readCurrentPartitionClasses(String partition, JsonObject partitionData) {

			Set<String> currentProxyClasses = new HashSet<String>();
			Set<String> currentRemoteClasses = new HashSet<String>();
			proxyClassesToRemoteClasses.put(partition, new PartitionData(currentProxyClasses, currentRemoteClasses));

			JsonArray proxyFiles = partitionData.getJsonArray("Proxy");

			for (JsonValue currentFile : proxyFiles) {
				currentProxyClasses.add(getClassName(currentFile.toString()));
			}

			JsonArray otherFiles = partitionData.getJsonArray("Service");
			for (JsonValue currentFile : otherFiles) {
				currentRemoteClasses.add(getClassName(currentFile.toString()));
			}

			otherFiles = partitionData.getJsonArray("Real");
			for (JsonValue currentFile : otherFiles) {
				currentRemoteClasses.add(getClassName(currentFile.toString()));
			}

			otherFiles = partitionData.getJsonArray("Util");
			for (JsonValue currentFile : otherFiles) {
				currentRemoteClasses.add(getClassName(currentFile.toString()));
			}
		}
		
		private String getClassName(String fileName) {
			return Utils.fileToClass(fileName, fileName.substring(0, fileName.indexOf(partitionsFilePrefix)+
					partitionsFilePrefix.length()-1), partitionsFileSuffix, partitionsFileSeparator);
		}

		public Set<String> getPartitions() {
			return proxyClassesToRemoteClasses.keySet();
		}

		@Override
		public String getLocalRemoteTag(JavaMethodModel method, String className) {
			String invokedComponent = null;
			for (Map.Entry<String, PartitionData> entry : proxyClassesToRemoteClasses.entrySet()) {
				if (entry.getValue().localClasses.contains(method.targetClass.getName())) {
					invokedComponent = entry.getKey();
					break;
				}
			}

			if (invokedComponent == null) {
				throw new RuntimeException("Couldn't find "+method.targetClass.getName()+" in components data");
			}

			if (invokedComponent.equals(method.targetPartition)) {
				throw new RuntimeException(method.targetClass.toString()+" resides in same component as proxy caller from "+method.targetPartition);
			}

			String label;

			if (proxyClassesToRemoteClasses.get(method.targetPartition).proxyClasses
					.contains(className)) {
				label = "remote/";
				if (proxyClassesToRemoteClasses.get(invokedComponent).proxyClasses
						.contains(className)) {
					label += "remote";
				} else {
					label += "local";
				}

			} else {
				label = " local/remote";
			}

			return label;
		}

		@Override
		public URLClassLoader loadCurrentClasses(String partitionName) throws MalformedURLException {

			// Getting the jar URL which contains target class

			String[] suffixDirs = cpSuffix.split(File.pathSeparator);

			List<String> currentPathClasses = new ArrayList<String>(appClasspathEntries);
			List<String> currentPartitionClasses = new ArrayList<String>();

			for (String suffix : suffixDirs) {
				currentPartitionClasses.add(cpPrefix + File.separator + partitionName + File.separator + suffix);
			}

			currentPathClasses.addAll(currentPartitionClasses);

			Utils.initSootClasses(currentPartitionClasses, Utils.entriesToClasspath(currentPathClasses));

			// Create a new URLClassLoader
			URLClassLoader urlClassLoader = new URLClassLoader(Utils.entriesToURL(currentPathClasses),
					ClassLoader.getSystemClassLoader());

			return urlClassLoader;

		}

		@Override
		public List<String> getTargetClasses(String partitionName) {

			return new ArrayList<String>(proxyClassesToRemoteClasses.get(partitionName).proxyClasses);
		}
	}

	private class ClassListTargets extends TargetFetcher {

		List<String> targetClasses;

		private ClassListTargets(String targetList) throws IOException {

			if (targetList != null) {
				targetClasses = Arrays.asList(targetList.split("::"));
			} else {

				targetClasses = new ArrayList<String>();

				for (String appDirOrJarName : appDirs) {

					File appDirOrJar = new File(appDirOrJarName);

					if (appDirOrJar.getName().endsWith(".jar")) {

						ZipInputStream zip = null;

						try {
							zip = new ZipInputStream(new FileInputStream(appDirOrJar));
							for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
								if (!entry.isDirectory() && entry.getName().endsWith(".class") && ! entry.getName().endsWith(Constants.EXCLUDED_TARGET_CLASS_SUFFIX)) {
									// This ZipEntry represents a class. Now, what class does it represent?
									String className = entry.getName().replace('/', '.'); // including ".class"
									targetClasses.add(className.substring(0, className.length() - ".class".length()));
								}
							}
						} finally {
							if (zip != null) {
								zip.close();
							}
						}

					} else if (appDirOrJar.isDirectory()) {
						targetClasses.addAll(Files.walk(Paths.get(appDirOrJar.getAbsolutePath()))
								.filter(path -> path.toFile().isFile() && path.toFile().getName().endsWith(".class") &&
										! path.toFile().getName().endsWith(Constants.EXCLUDED_TARGET_CLASS_SUFFIX))
								.map(path -> Utils.fileToClass(path.toFile().getAbsolutePath(), appDirOrJar.getAbsolutePath(), ".class", File.separator)).
								collect(Collectors.toList()));
					} else {
						throw new IllegalArgumentException("Unrecognized app entry type: "+appDirOrJar.getAbsolutePath());
					}
				}
			}
		}

		public Set<String> getPartitions() {
			return Collections.singleton("monolithic");
		}

		@Override
		public String getLocalRemoteTag(JavaMethodModel method, String className) {
			throw new UnsupportedOperationException();
		}

		@Override
		public URLClassLoader loadCurrentClasses(String partitionName) throws MalformedURLException {
			List<String> currentPathClasses = new ArrayList<String>(appClasspathEntries);
			List<String> currentPartitionClasses = new ArrayList<String>();

			for (String appDir : appDirs) {
				currentPartitionClasses.add(appDir);
			}

			currentPathClasses.addAll(currentPartitionClasses);

			Utils.initSootClasses(currentPartitionClasses, Utils.entriesToClasspath(currentPathClasses));

			// Create a new URLClassLoader
			URLClassLoader urlClassLoader = new URLClassLoader(Utils.entriesToURL(currentPathClasses),
					ClassLoader.getSystemClassLoader());

			return urlClassLoader;
		}

		@Override
		public List<String> getTargetClasses(String partitionName) {

			return targetClasses;
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
            .desc("Path to the monolith application under test")
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

        // option for Partition file
        options.addOption(Option.builder("pf")
                .longOpt("partition-file")
                .hasArg()
                .desc("Name of Partition json file containing partitioning information. Only one of partition-file and class-list should be specified.")
                .type(String.class)
                .build()
        );

      // option for Partition file
        options.addOption(Option.builder("cl")
                .longOpt("class-list")
                .hasArg()
                .desc("List of classes to target, separated by double colons. Only one of partition-file and class-list should be specified.")
                .type(String.class)
                .build()
        );

        // option for application path prefix
        options.addOption(Option.builder("pp")
            .longOpt("application-path-prefix")
            .hasArg()
            .desc("Prefix of path to the refactored application under test")
            .type(String.class)
            .build()
        );

        // option for application path suffix
        options.addOption(Option.builder("ps")
            .longOpt("application-path-suffix")
            .hasArg()
            .desc("Suffix of path to the refactored application under test")
            .type(String.class)
            .build()
        );

     // option for CHA types
        options.addOption(Option.builder("cha")
            .longOpt("all-cha-types")
            .hasArg()
            .desc("Whether to consider all Java types discovered during Class Hierarchy Analysis or only application classes and collections. Default is true.")
            .type(Boolean.class)
            .build()
        );

     // option for collection depth
        options.addOption(Option.builder("nd")
            .longOpt("collection-nest-depth")
            .hasArg()
            .desc("Maximal nesting level when constructing coolections. Default is 2.")
            .type(Integer.class)
            .build()
        );

        // option for local remote tag
        options.addOption(Option.builder("lr")
            .longOpt("local-remote-tag")
            .hasArg()
            .desc("Add a tag to param types in the CTD model, indicating whether they were locally/remotely defined with respect to the current proxy method invocation. "
            		+ "Default is true.")
            .type(Boolean.class)
            .build()
        );

     // option for interaction coverage
        options.addOption(Option.builder("ic")
            .longOpt("interaction-coverage")
            .hasArg()
            .desc("Interaction coverage level for CTD test plan generation. "
            		+ "Default is 1.")
            .type(Integer.class)
            .build()
        );
        
        // option for refactoring package prefix
        
        options.addOption(Option.builder("rp")
                .longOpt("refactoring-package-prefix")
                .hasArg()
                .desc("Package prefix of utility classes added during refactoring")
                .type(String.class)
                .build()
            );
        
        // option for partitions file classes prefix
        
        options.addOption(Option.builder("pcp")
                .longOpt("partitions-file-classes-prefix")
                .hasArg()
                .desc("Prefix preceding class names in partitions file")
                .type(String.class)
                .build()
            );
        
        // option for partitions file classes prefix
        
        options.addOption(Option.builder("pcs")
                .longOpt("partitions-file-classes-suffix")
                .hasArg()
                .desc("Prefix succeeding class names in partitions file")
                .type(String.class)
                .build()
            );
        
        // option for partitions file classes separator
        
        options.addOption(Option.builder("pfs")
                .longOpt("partitions-file-classes-separator")
                .hasArg()
                .desc("File separator used in partitions file")
                .type(String.class)
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
                formatter.printHelp(CTDTestPlanGenerator.class.getName(), options, true);
                return null;
            }
        }
        catch (ParseException e) {
            logger.warning(e.getMessage());
            formatter.printHelp(CTDTestPlanGenerator.class.getName(), options, true);
        }

        // check whether required options are specified
        if (!cmd.hasOption("app") || !cmd.hasOption("pt") || !cmd.hasOption("clpt")) {
            formatter.printHelp(CTDTestPlanGenerator.class.getName(), options, true);
            return null;
        }

        // cannot have both partition file and target class list
        if (cmd.hasOption("pf") && cmd.hasOption("cl")) {
        	formatter.printHelp(CTDTestPlanGenerator.class.getName(), options, true);
            return null;
        }

        // if partition file is specified we must have refactored app details

        if (cmd.hasOption("pf") && ( ! cmd.hasOption("pp") || ! cmd.hasOption("ps"))) {
        	formatter.printHelp(CTDTestPlanGenerator.class.getName(), options, true);
            return null;
        }

        return cmd;
    }



	public static void main(String args[])
			throws IOException, ClassNotFoundException, SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException {


		 // parse command-line options
        CommandLine cmd = parseCommandLineOptions(args);

        // if parser command-line is empty (which occurs if the help option is specified or a
        // parse exception occurs, exit
        if (cmd == null) {
            System.exit(0);
        }

        String appName = cmd.getOptionValue("app");
        String appPath = cmd.getOptionValue("pt");
        String classpathFilename = cmd.getOptionValue("clpt");

        logger.info("Application name: "+appName);
        logger.info("Application path: "+appPath);
        logger.info("Application classpath file name: "+classpathFilename);

        String partitionFileName = null;

        if (cmd.hasOption("pf")) {
        	partitionFileName = cmd.getOptionValue("pf");
        	logger.info("Partition file name: "+partitionFileName);
        }

        String targetClassList = null;

        if (cmd.hasOption("cl")) {
        	targetClassList = cmd.getOptionValue("cl");
        	logger.info("Target class list: "+targetClassList);
        }

        String partitionPrefix = null;

        if (cmd.hasOption("pp")) {
        	partitionPrefix = cmd.getOptionValue("pp");
        	logger.info("Partitioned application path prefix: "+partitionPrefix);
        }

        String partitionSuffix = null;

        if (cmd.hasOption("ps")) {
        	partitionSuffix = cmd.getOptionValue("ps");
        	logger.info("Partitioned application path suffix: "+partitionSuffix);
        }

        boolean allCHA = true;

        if (cmd.hasOption("cha")) {
        	allCHA = Boolean.parseBoolean(cmd.getOptionValue("cha"));
        	logger.info("Use all CHA types: "+allCHA);
        }

        int maxDepth = 3;

        if (cmd.hasOption("nd")) {
        	maxDepth = Integer.parseInt(cmd.getOptionValue("nd"));
        	logger.info("Max collection nesting depth: "+maxDepth);
        }

        boolean addLocalRemote = (partitionFileName != null);

        if (cmd.hasOption("lr")) {
        	addLocalRemote = Boolean.parseBoolean(cmd.getOptionValue("lr"));
        	logger.info("Add local/remote tag: "+addLocalRemote);
        }

        int interactionLevel = 1;

        if (cmd.hasOption("ic")) {
        	interactionLevel = Integer.parseInt(cmd.getOptionValue("ic"));
        	System.out.println("* CTD interaction level: "+interactionLevel);
        }
        
        String refactoringPrefix = null;
        if (cmd.hasOption("rp")) {
        	refactoringPrefix = cmd.getOptionValue("rp");
        	System.out.println("* Refactoring package prefix: "+refactoringPrefix);
        }
        
        String partitionsPrefix =  "main/java/";
        if (cmd.hasOption("pcp")) {
        	partitionsPrefix = cmd.getOptionValue("pcp");
        	System.out.println("* Partitions file classes prefix: "+partitionsPrefix);
        }
        
        String partitionsSuffix =  ".java\"";
        if (cmd.hasOption("pcs")) {
        	partitionsSuffix = cmd.getOptionValue("pcs");
        	System.out.println("* Partitions file classes suffix: "+partitionsSuffix);
        }
        
        String partitionsSeparator =  "/";
        if (cmd.hasOption("pfs")) {
        	partitionsSeparator = cmd.getOptionValue("pfs");
        	System.out.println("* Partitions file classes seaprator: "+partitionsSeparator);
        }

		CTDTestPlanGenerator analyzer = new CTDTestPlanGenerator(appName, partitionFileName, targetClassList, partitionPrefix, partitionSuffix, appPath, classpathFilename,
				allCHA, maxDepth, addLocalRemote, interactionLevel, refactoringPrefix, partitionsPrefix, partitionSuffix, partitionsSeparator);
		analyzer.modelPartitions();
	}
}
