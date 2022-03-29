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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.konveyor.tackle.testgen.core.extender.SequenceUtil;
import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestJson;
import org.konveyor.tackle.testgen.util.TackleTestLogger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import randoop.sequence.Sequence;
import randoop.sequence.SequenceParseException;

/**
 * Receives a json file with sequences, creates a junit test file per
 * tested class with one method per sequence
 *
 * @author RACHELBRILL
 *
 */

public class JUnitTestExporter {


	private static final Logger logger = TackleTestLogger.getLogger(JUnitTestExporter.class);

	private static class TestClass {

		final List<String> sequences = new ArrayList<String>();
		final Set<String> imports = new HashSet<String>();
	}

	private final File testOutDir;

//	private final String applicationName;

	private final boolean addAssertUtilMethods;

	private int unitFileCounter = 0;


	/**
	 *
	 * @param seqFile a json file containing test sequences
	 * @param outDirParent output path
	 * @throws IOException
	 */
	public JUnitTestExporter(File seqFile, File outDirParent, boolean addAssertMethods, boolean isBadPath) throws IOException, SequenceParseException {

		this(outDirParent, addAssertMethods);

		Map<String, TestClass> junitTests = readSequences(seqFile);

		for (Map.Entry<String, TestClass> entry : junitTests.entrySet()) {
		    Map<String, List<String>> testSequences = new HashMap<>();
            String clsName = entry.getKey();
            List<String> importList = entry.getValue().imports.stream().collect(Collectors.toList());
            for (String seqStr : entry.getValue().sequences) {
                Sequence seq = SequenceParser.codeToSequence(seqStr, importList, clsName, true,
                    new ArrayList<>()).a;
                String targetMethodSig = SequenceUtil.getTargetMethod(seq);
                if (targetMethodSig == null) {
                    targetMethodSig = "method()V;";
                }
                if (!testSequences.containsKey(targetMethodSig)) {
                    testSequences.put(targetMethodSig, new ArrayList<>());
                }
                testSequences.get(targetMethodSig).add(seqStr);
            }
			writeUnitTest(clsName, testSequences, entry.getValue().imports, isBadPath);
		}
		logger.info("Wrote "+unitFileCounter+" junit test files");
	}

	public JUnitTestExporter(File outDirParent, boolean addAssertMethods) {
		addAssertUtilMethods = addAssertMethods;
        testOutDir = outDirParent;
		testOutDir.mkdirs();
	}

	private Map<String, TestClass> readSequences(File seqFile) throws JsonProcessingException, IOException {

		JsonNode mainNode = TackleTestJson.getObjectMapper().readTree(seqFile);

		Map<String, TestClass> junitTests = new HashMap<String, TestClass>();
		ObjectNode testSequences = (ObjectNode) mainNode.get("test_sequences");
		
		testSequences.elements().forEachRemaining(seqObj -> {
		
			String className = seqObj.get("class_name").asText();
			TestClass currentTests = junitTests.get(className);

			if (currentTests == null) {
				currentTests = new TestClass();
				junitTests.put(className, currentTests);
			}
			
			List<String> importList = StreamSupport.stream(
					Spliterators.spliteratorUnknownSize(seqObj.get("imports").elements(), 
							Spliterator.ORDERED), false)
			  .map(element -> element.textValue()).collect(Collectors.toList());
			
			currentTests.imports.addAll(importList);
			currentTests.sequences.add(seqObj.get("sequence").asText());
		});
		
		return junitTests;
	}

	public void writeUnitTest(String className, Map<String, List<String>> testSequences, Set<String> testImports, boolean isBadPath) throws IOException {

		String unitTestClassName = className.replaceAll("\\.", "_")+(isBadPath? "_BadPath" : "")+"_Test";
		
		File outputFile;
		
		if (className.contains(".")) {
			String fileSep = File.separator;
			if (fileSep.equals("\\")) {
				fileSep = fileSep + "\\"; // for regex replacement
			}
			String packagePath =  className.substring(0, className.lastIndexOf('.')).replaceAll("\\.", fileSep);
			File packageDir = new File(testOutDir.getAbsoluteFile(), packagePath);
			packageDir.mkdirs();
			outputFile = new File(packageDir, unitTestClassName+".java");
		} else {
			outputFile = new File(testOutDir, unitTestClassName+".java");
		}
		
		
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		
		try {
			
			if (className.contains(".")) {
				writer.write("package "+className.substring(0, className.lastIndexOf('.'))+";");
				writer.newLine();
				writer.newLine();
			}

			for (String imp : testImports) {
				writer.write("import "+imp+";");
				writer.newLine();
			}
            if ( ! testImports.contains("static org.junit.Assert.assertEquals")) {
                writer.write("import static org.junit.Assert.assertEquals;");
                writer.newLine();
            }
            if ( ! testImports.contains("static org.junit.Assert.assertNull")) {
                writer.write("import static org.junit.Assert.assertNull;");
                writer.newLine();
            }
            if ( ! testImports.contains("static org.junit.Assert.fail")) {
                writer.write("import static org.junit.Assert.fail;");
                writer.newLine();
            }
			if ( ! testImports.contains("org.junit.Test")) {
				writer.write("import org.junit.Test;");
				writer.newLine();
			}
			writer.newLine();
			writer.write("public class "+unitTestClassName+" {");
			writer.newLine();

			Map<String, Integer> methodTestCounters = new HashMap<>();

			for (String methodSig : testSequences.keySet()) {
			    List<String> methodSequences = testSequences.get(methodSig);
			    String methodName = methodSig.split("\\(")[0];
                if (methodName.equals("<init>")) {
                    String[] clsNameTokens = className.split("\\.");
                    methodName = clsNameTokens[clsNameTokens.length-1];
                }

                int testCounter = 0;
			    if (methodTestCounters.containsKey(methodName)) {
                    testCounter = methodTestCounters.get(methodName);
                }
                for (String sequence : methodSequences) {
                    writer.write("\t@Test");
                    writer.newLine();
                    writer.write("\tpublic void test_" + methodName + "_" + (testCounter++) + "() throws Throwable {");
                    writer.newLine();
                    String[] lines = sequence.split("\\r\\n");
                    for (String line : lines) {
                        writer.write("\t\t" + line);
                        writer.newLine();
                    }
                    writer.write("\t}");
                    writer.newLine();
                    writer.newLine();
                }
                methodTestCounters.put(methodName, testCounter);
            }
			if (addAssertUtilMethods) {

				writer.write(addFieldAccessMethod());
			}

			writer.write("}");
			writer.newLine();
			unitFileCounter++;

		} finally {
			writer.close();
		}
	}

	public File writeJEEUnitTest(String className, List<String> testSequences, List<String> testImports, Set<String> beforeAfterMethods) throws IOException {
		String unitTestClassName = className.replaceAll("\\.", "_")+"_JEE_Test";

		File junitFile = new File(testOutDir, unitTestClassName+".java");

		BufferedWriter writer = new BufferedWriter(new FileWriter(junitFile));

		try {

			for (String imp : testImports) {
				writer.write("import "+imp+";");
				writer.newLine();
			}

			writer.write("import org.junit.BeforeClass;");
			writer.newLine();
			writer.write("import org.junit.Before;");
			writer.newLine();
			writer.write("import org.junit.After;");
			writer.newLine();
			writer.write("import org.junit.AfterClass;");
			writer.newLine();
			String[] imports = new String[] {"org.junit.runner.RunWith",
					"org.evosuite.runtime.EvoRunner",
					"org.evosuite.runtime.EvoRunnerParameters",
					"org.evosuite.runtime.annotation.EvoSuiteExclude"};
			for (String imp : imports) {
				if ( ! testImports.contains(imp)) {
					writer.write("import "+imp+";");
					writer.newLine();
				}
			}
			writer.newLine();
			writer.write("@RunWith(EvoRunner.class) @EvoRunnerParameters(mockJVMNonDeterminism = true, useVFS = true, useVNET = true, resetStaticState = true, "
					+ "separateClassLoader = true, useJEE = true)");
			writer.newLine();
			writer.write("public class "+unitTestClassName+" {");
			writer.newLine();
			writer.newLine();

			String scaffoldingClassName = className + Constants.EVOSUITE_HELP_CLASS_SUFFIX;

			writer.write("\tprivate "+scaffoldingClassName+" scaffolding = new "+scaffoldingClassName+"();");
			writer.newLine();
			writer.newLine();
			writeBAClassMethod(writer, scaffoldingClassName, beforeAfterMethods, "BeforeClass", Constants.EVOSUITE_BEFORE_CLASS_METHOD);
			writeBAClassMethod(writer, scaffoldingClassName, beforeAfterMethods, "AfterClass", Constants.EVOSUITE_AFTER_CLASS_METHOD);
			writeBATestMethod(writer, beforeAfterMethods, "Before", Constants.EVOSUITE_BEFORE_TEST_METHOD);
			writeBATestMethod(writer, beforeAfterMethods, "After", Constants.EVOSUITE_AFTER_TEST_METHOD);

			int testCounter = 0;

			for (String sequence : testSequences) {
				writer.write("\t@Test");
				writer.newLine();
				writer.write("\tpublic void test"+(testCounter++)+"() throws Throwable {");
				writer.newLine();
				String[] lines = sequence.split("\\r\\n");
				for (String line : lines) {
					writer.write("\t\t"+line);
					writer.newLine();
				}
				writer.write("\t}");
				writer.newLine();
				writer.newLine();
			}

			if (addAssertUtilMethods) {

				writer.write(addFieldAccessMethod());
			}

			writer.write("}");
			writer.newLine();
			unitFileCounter++;

		} finally {
			writer.close();
		}

		return junitFile;

	}

	private void writeBAClassMethod(BufferedWriter writer, String scaffoldingClassName, Set<String> beforeAfterMethods, String beforeAfterTag, String methodName)
			throws IOException {

		if (beforeAfterMethods.contains(methodName)) {
			writer.write("\t@"+beforeAfterTag);
			writer.newLine();
			writer.write("\tpublic static void "+beforeAfterTag.toLowerCase()+"() { ");
			writer.newLine();
			writer.write("\t\t"+scaffoldingClassName+"."+methodName+"();");
			writer.newLine();
			writer.write("\t}");
			writer.newLine();
			writer.newLine();
		}
	}

	private void writeBATestMethod(BufferedWriter writer, Set<String> beforeAfterMethods, String beforeAfterTag, String methodName)
			throws IOException {
		if (beforeAfterMethods.contains(methodName)) {
			writer.write("\t@"+beforeAfterTag);
			writer.newLine();
			writer.write("\tpublic void "+beforeAfterTag.toLowerCase()+"() { ");
			writer.newLine();
			writer.write("\t\tscaffolding."+methodName+"();");
			writer.newLine();
			writer.write("\t}");
			writer.newLine();
			writer.newLine();
		}
	}



	private String addFieldAccessMethod() {
		StringBuilder code = new StringBuilder();

		code.append("\tprivate java.lang.Object "+DiffAssertionsGenerator.FIELD_UTIL_METHOD_NAME+
				"(java.lang.Object obj, String fieldName) throws java.lang.reflect.InvocationTargetException, "
				+ "java.lang.SecurityException, java.lang.IllegalArgumentException, java.lang.IllegalAccessException {");
		code.append(System.getProperty("line.separator"));
		code.append("\t\ttry {");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t\tjava.lang.reflect.Field field = obj.getClass().getField(fieldName);");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t\treturn field.get(obj);");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t} catch (java.lang.NoSuchFieldException e) {");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t\tfor (java.lang.reflect.Method publicMethod : obj.getClass().getMethods()) {");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t\t\tif (publicMethod.getName().startsWith(\"get\") && publicMethod.getParameterCount() == 0 && ");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t\t\t\tpublicMethod.getName().toLowerCase().equals(\"get\"+fieldName.toLowerCase())) {");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t\t\t\treturn publicMethod.invoke(obj);");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t\t\t}");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t\t}");
		code.append(System.getProperty("line.separator"));
		code.append("\t\t}");
		code.append(System.getProperty("line.separator"));
		code.append("\t\tthrow new IllegalArgumentException(\"Could not find field or getter \"+fieldName+\" for class \"+obj.getClass().getName());");
		code.append(System.getProperty("line.separator"));
		code.append("\t}");
		code.append(System.getProperty("line.separator"));

		return code.toString();
	}

//	private java.lang.Object getFieldValue(java.lang.Object obj, String fieldName)
//	throws java.lang.SecurityException, java.lang.IllegalArgumentException, java.lang.IllegalAccessException,
//	java.lang.reflect.InvocationTargetException {
//
//		try {
//			java.lang.reflect.Field field = obj.getClass().getField(fieldName);
//			return field.get(obj);
//		} catch (java.lang.NoSuchFieldException e) {
//			for (java.lang.reflect.Method publicMethod : obj.getClass().getMethods()) {
//
//				if (publicMethod.getName().startsWith("get") && publicMethod.getParameterCount() == 0 &&
//						publicMethod.getName().toLowerCase().equals("get"+fieldName.toLowerCase())) {
//					return publicMethod.invoke(obj);
//				}
//			}
//		}
//
//		throw new IllegalArgumentException("Could not find field or getter "+fieldName+" for class "+obj.getClass().getName());
//	}

	private static CommandLine parseCommandLineOptions(String[] args) {
        Options options = new Options();

        // option for sequences file
        options.addOption(Option.builder("seq")
                .longOpt("sequences")
                .hasArg()
                .desc("Name of JSON file containing the extended sequences")
                .type(String.class)
                .build()
        );

     // option for output dir path
        options.addOption(Option.builder("od")
            .longOpt("output-directory")
            .hasArg()
            .desc("Path to output directory that will contain the junit tests")
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
                formatter.printHelp(JUnitTestExporter.class.getName(), options, true);
                return null;
            }
        }
        catch (ParseException e) {
            logger.warning(e.getMessage());
            formatter.printHelp(JUnitTestExporter.class.getName(), options, true);
        }

        // check whether required options are specified
        if (!cmd.hasOption("seq") || !cmd.hasOption("od")) {
            formatter.printHelp(JUnitTestExporter.class.getName(), options, true);
            return null;
        }
        return cmd;
    }

	public static void main(String args[]) throws IOException, SequenceParseException {

		 // parse command-line options
        CommandLine cmd = parseCommandLineOptions(args);

        // if parser command-line is empty (which occurs if the help option is specified or a
        // parse exception occurs, exit
        if (cmd == null) {
            System.exit(0);
        }

        String sequencesFile = cmd.getOptionValue("seq");
        String outputDir = cmd.getOptionValue("od");
        logger.info("Sequences file: "+sequencesFile);
        logger.info("Output directory path: "+outputDir);

		new JUnitTestExporter(new File(sequencesFile), new File(outputDir), true, false);
	}

}
