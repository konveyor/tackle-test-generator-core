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

import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class RandoopTestGenerator extends AbstractTestGenerator {

    public RandoopTestGenerator(List<String> targetPath, String appName) {
		super(targetPath);
		randoopOutputDir = new File(appName+RANDOOP_OUTPUT_DIR_NAME_SUFFIX);
	}

	// public constants
    public static final String RANDOOP_OUTPUT_DIR_NAME_SUFFIX = "-randoop-tests";
    public static final String RANDOOP_METHODLIST_FILE_NAME = "randoop-methodlist.txt";
    public static final String RANDOOP_CLASSLIST_FILE_NAME = "randoop-classlist.txt";

    private static final String RANDOOP_JAR = "lib/download"+File.separator+"randoop-all-"+ Constants.RANDOOP_VERSION+".jar";
    private File randoopOutputDir;
    //private Map<String, SootClass> appSootClasses = new HashMap<>();

    // randoop configuration options
    enum RandoopOptions {
        COMPILABLE,             // boolean: whether to check that generated sequences can be compiled
        ERROR_REVEALING_TESTS,  // boolean: whether to output error-revealing tests
        REGRESSION_TESTS,       // boolean: whether to output regression tests
        REGRESSION_ASSERTIONS,  // boolean: whether to include assertions in regression tests
        TIME_LIMIT              // int: max number of seconds to spend generating tests
    }

    private boolean errorRevealingTests = false;  // default: error-revealing tests are not generated
    private boolean regressionTests = true;  // default: reegression tests are generated
    private boolean regressionAssertions = true;  // defualt: regression assertions are generated
    private boolean compilable = true;  // default: compile check performed on sequemces
    private int timeLimit = 100;  // default time limit: 100s (randoop default)

    private static final Logger logger = TackleTestLogger.getLogger(RandoopTestGenerator.class);

    public void configure(Map<String, String> settings) {
        for (String key : settings.keySet()) {
            if (key.equals(RandoopOptions.COMPILABLE.name())) {
                this.compilable = Boolean.valueOf(settings.get(key));
            }
            else if (key.equals(RandoopOptions.ERROR_REVEALING_TESTS.name())) {
                this.errorRevealingTests = Boolean.valueOf(settings.get(key));
            }
            else if (key.equals(RandoopOptions.REGRESSION_TESTS.name())) {
                this.regressionTests = Boolean.valueOf(settings.get(key));
            }
            else if (key.equals(RandoopOptions.REGRESSION_ASSERTIONS.name())) {
                this.regressionAssertions = Boolean.valueOf(settings.get(key));
            }
            else if (key.equals(RandoopOptions.TIME_LIMIT.name())) {
                this.timeLimit = Integer.valueOf(settings.get(key));
            } else {
                throw new IllegalArgumentException("Unknown Radoop option: "+key);
            }
        }
    }

    public File getOutputDir() {
        return this.randoopOutputDir;
    }

    /**
     * Generates test cases by calling randoop using the --classlist and --methodlist options
     */
    public void generateTests() throws IOException, InterruptedException {
        //this.initializeSoot();

        // write classlist to file: these are the classes under test
        //FileUtils.writeLines(new File(RANDOOP_CLASSLIST_FILE_NAME), this.targets.keySet());

        // build command for invoking randoop


        for (String className : this.targets.keySet()) {

			String classpath = this.projectClasspath + File.pathSeparator + RANDOOP_JAR + File.pathSeparator;
			classpath += Utils.entriesToClasspath(targetClassesPath);
			List<String> randoopOpts = new ArrayList<String>(
					Arrays.asList("java", "-Xmx3000m", "-Xbootclasspath/a:lib/download/replacecall-"+Constants.RANDOOP_VERSION+".jar",
							"-javaagent:lib/download/replacecall-"+Constants.RANDOOP_VERSION+".jar",
							"-classpath", classpath, "randoop.main.Main", "gentests"));
			randoopOpts.add("--testclass=" + className);

			// build methodlist and write to file: these are the methods that can be called in test cases
	        //Set<String> methodlist = buildRandoopMethodlistOpt(className);
			Set<String> methodlist = this.targets.get(className);
	        logger.info("method list size: "+methodlist.size());
	        FileUtils.writeLines(new File(RANDOOP_METHODLIST_FILE_NAME), methodlist);
			//randoopOpts.add("--methodlist=" + RANDOOP_METHODLIST_FILE_NAME);
			randoopOpts.add("--time-limit=" + Integer.toString(this.timeLimit));
			randoopOpts.add("--check-compilable=" + this.compilable);
			randoopOpts.add("--no-error-revealing-tests=" + !this.errorRevealingTests);
			randoopOpts.add("--no-regression-tests=" + !this.regressionTests);
			randoopOpts.add("--no-regression-assertions=" + !this.regressionAssertions);
			randoopOpts.add("--junit-output-dir=" + randoopOutputDir.getAbsolutePath());
			randoopOpts.add("--regression-test-basename="+className.replaceAll("\\.", "_"));
			logger.info(System.getProperty("java.class.path"));

			// build and run command using process building
			ProcessBuilder randoopProcBld = new ProcessBuilder(randoopOpts);
			randoopProcBld.inheritIO();
			logger.info("Running Randoop process: " + randoopProcBld.command());
			Process randoopProc = randoopProcBld.start();
			int exitCode = randoopProc.waitFor();
			logger.info("exit code: " + exitCode);
        }

    }

//    private Set<String> buildRandoopMethodlistOpt(String cls) {
//        HashSet<String> methodlist = new HashSet<String>();
//
//        // initialize worklist of methods to process
//		ArrayList<SootMethod> worklist = new ArrayList<SootMethod>();
//
//		// skip target classes not found on project classpath
//		if (!this.appSootClasses.containsKey(cls)) {
//			logger.warning(
//					"Skipping test target class " + cls + " not found on project classpath " + this.projectClasspath);
//			return Collections.emptySet();
//		}
//		Set<String> methods = this.targets.get(cls);
//		if (methods.size() == 0) {
//			for (SootMethod sootMtd : this.appSootClasses.get(cls).getMethods()) {
//				if (!sootMtd.isStaticInitializer()) {
//					worklist.add(sootMtd);
//					logger.info("added method " + sootMtd.getBytecodeSignature() + " to worklist");
//				}
//			}
//		} else {
//			/*
//			 * Soot doesn't find method based on bytecode level signatures, so we cannot use
//			 * getMethod
//			 */
//
//			Map<String, SootMethod> bytecodeSig2Method = new HashMap<String, SootMethod>();
//
//			for (SootMethod sootMtd : this.appSootClasses.get(cls).getMethods()) {
//				bytecodeSig2Method.put(getBytecodeSignature(sootMtd), sootMtd);
//			}
//
//			for (String methodSig : methods) {
//				// Need to get rid of generics information in signature because Soot doesn't
//				// support it..
//				String methodSigNoGeneric = methodSig;
//				String initPrefix = null;
//				if (methodSig.startsWith("<")) {
//					methodSigNoGeneric = methodSig.substring(methodSig.indexOf('>') + 1);
//					initPrefix = methodSig.substring(0, methodSig.indexOf('>') + 1);
//				}
//				methodSigNoGeneric = methodSigNoGeneric.replaceAll("<.*>", "");
//				SootMethod sootMtd = bytecodeSig2Method
//						.get(initPrefix == null ? methodSigNoGeneric : initPrefix + methodSigNoGeneric);
//				if (sootMtd == null) {
//					throw new RuntimeException("Unable to find method for signature " + methodSig);
//				}
//				worklist.add(sootMtd);
//			}
//		}
//        logger.info("init worklist size: "+worklist.size());
//        HashSet<String> procClasses = new HashSet<>();
//
//        // iterate over worklist until it is empty: remove method, add it to methodlist,
//        // add constructors for each param type of method to worklist
//        while (worklist.size() > 0) {
//            SootMethod currMethod = worklist.remove(0);
//            logger.info("processing worklist method: "+getRandoopFormatSignature(currMethod));
//            methodlist.add(getRandoopFormatSignature(currMethod));
//            for (Type paramType : currMethod.getParameterTypes()) {
//                String paramTypeStr = paramType.toString();
//                // skip parameter if its type us already processed or does not occur in the set of loaded classes
//                if (procClasses.contains(paramTypeStr) || !this.appSootClasses.containsKey(paramTypeStr)) {
//                    continue;
//                }
//                // add all constructors of class to methodlist
//                SootClass paramClass = this.appSootClasses.get(paramTypeStr);
//                for (SootMethod method : paramClass.getMethods()) {
//                    if (method.isConstructor() && !method.isStaticInitializer()) {
//                        worklist.add(method);
//                    }
//                }
//                // add class to the set of processed types
//                procClasses.add(paramTypeStr);
//            }
//        }
//        return methodlist;
//    }
//
//    private void initializeSoot() {
//        if (this.projectClasspath == "") {
//            throw new RuntimeException("project classpath not set");
//        }
//
//        Utils.initSootClasses(targetClassesPath,
//        		null); // TODO: check why setting the classpath causes Soot not to find app classes..
//
////        G.reset();
////       Options.v().set_process_dir(new ArrayList<>(Arrays.asList(projectClasspath)));
////       Options.v().set_allow_phantom_refs(true);
////        Options.v().set_include_all(true);
////		Scene.v().loadNecessaryClasses();
//
//        for (SootClass cls : Scene.v().getApplicationClasses()) {
//            this.appSootClasses.put(cls.getName(), cls);
//            logger.info("loaded class: "+cls+ " "+cls.getMethodCount());
//            // for (SootMethod mtd : cls.getMethods()) {
//            //     logger.info(getBytecodeSignature(mtd));
//            //     logger.info(mtd.getSubSignature());
//            //     for (Type type : mtd.getParameterTypes()) {
//            //         logger.info("ParamType: "+type.toString());
//            //         logger.info("ParamTypeQuoted: "+type.toQuotedString());
//            //     }
//            // }
//        }
//        logger.info("Soot classes loaded: "+this.appSootClasses.size());
//    }

//    private String getRandoopFormatSignature(SootMethod method) {
//        String[] sig = method.getSubSignature().split(" ");
//        return method.getDeclaringClass().getName() + "." + sig[sig.length-1];
//    }
//
//    private String getBytecodeSignature(SootMethod method) {
//        String[] bcodeSig = method.getBytecodeSignature().split(" ");
//        String mthdNoReturn = bcodeSig[bcodeSig.length-1];
//        String result = mthdNoReturn.substring(0, mthdNoReturn.lastIndexOf(">"));
//        if (result.startsWith("<init>") ||result.startsWith("<clinit>")) {
//        	result = result.substring(0, result.length()-1); // Remove return value
//        }
//        return result;
//    }


    public static void main(String[] args) throws IOException, InterruptedException {
        String classpath = args[0];
        logger.info("project classpath: "+classpath);
        String appName = args[1];
        logger.info("application name: "+appName);
        List<String> classpathEntries = Arrays.asList(classpath.split(File.pathSeparator));
        RandoopTestGenerator randoopTestgen = new RandoopTestGenerator(classpathEntries, appName);
        randoopTestgen.setProjectClasspath(classpath);

        // set configuration options
        Map<String, String> config = new HashMap<>();
        config.put("TIME_LIMIT", "10");
        randoopTestgen.configure(config);

        // randoopTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.ejb3.TradeSLSBRemote");
        // randoopTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.ejb3.TradeSLSBLocal");
        // randoopTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.util.MDBStats");
        // randoopTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.ejb3.TradeSLSBBean");
        randoopTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.TradeAction");
        randoopTestgen.generateTests();
    }

	@Override
	String getName() {
		return RandoopTestGenerator.class.getSimpleName();
	}

	@Override
	AbstractJUnitTestImporter getJUnitTestImporter(File outputDir) throws IOException {
		return new RandoopJUnitTestImporter(outputDir, projectClasspath.isEmpty()? Collections.emptyList() : Arrays.asList(projectClasspath.split(File.pathSeparator)));
	}

}
