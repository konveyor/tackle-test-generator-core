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

package org.konveyor.tackle.testgen.classify;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ClassifyErrors {
    public enum ErrorType { SERIALIZATION, JSON_PARSING, DUMMY_FUNCTION, SERVICE_INVOCATION, OTHER }

    Map<String,Set<ClassifiedError>> errorTypes = new HashMap<>();
    ClassifiedError unclassifiedError = new ClassifiedError("UnclassifiedError", "", "UnclassifiedError","", null);
    private final List<String> appPackages = new ArrayList<>();
    private final String testDir;
    public static final String TEST_SUITES_FILE_NAME = "TESTS-TestSuites.xml";
    public static final String OUTPUT_FILE = "classifiedErrors.json";
    public static final String INPUT_ERROR_TAG = "error";
    public static final String INPUT_PATTERN_TAG = "pattern";
    public static final String INPUT_PATTERNS_TAG = "patterns";
    public static final String INPUT_MESSAGE_TAG = "message";
    public static final String INPUT_TYPE_TAG = "type";
    public static final String INPUT_SEMANTIC_TAG = "semanticTag";
    public static final String INPUT_STACK_TRACE_TAG = "stackTrace";
    public static final String INPUT_CLASS_NAME_TAG = "classname";
    public static final String INPUT_NAME_TAG = "name";
    public static final String OUTPUT_ERROR_TYPE_TAG = "errorType";
    public static final String OUTPUT_ERRORS_TAG = "errors";
    public static final String OUTPUT_PARTITION_TAG = "partition";
    public static final String OUTPUT_TEST_CLASSES_TAG = "testClasses";
    public static final String OUTPUT_PARTITIONS_TAG = "partitions";
    public static final String OUTPUT_TEST_CLASS_TAG = "testClass";
    public static final String OUTPUT_TEST_METHODS_TAG = "testMethods";
    public static final String OUTPUT_TAG = "output";

    private static final Logger logger = TackleTestLogger.getLogger(ClassifyErrors.class);
    
    final static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    ClassifyErrors(String[] reportsPath, String errorsFilePath, String[] appPackages, String testDir)
        throws ParserConfigurationException, IOException, SAXException {
        this.testDir = testDir;
        for (String appPackage: appPackages) {
            if (appPackage.contains("*")) {
                // Handle cases of generalization of several packages
                this.appPackages.add(appPackage.substring(0, appPackage.indexOf("*")));
            } else {
                this.appPackages.add(appPackage);
            }
        }
        parseErrorPatternsFile(errorsFilePath);
        for (String partitionDir: reportsPath) {
            File newDir = new File(partitionDir + File.separator + TEST_SUITES_FILE_NAME);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(newDir);
            String partition = partitionDir.substring(partitionDir.lastIndexOf(File.separator)+1);
            parseXml(doc.getDocumentElement(), partition);
        }
        writeClassifiedErrorsFile();
    }

    private void parseXml(Element elem, String partition) {
        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals(INPUT_ERROR_TAG)) {
                    Element childElem = (Element)childNode;
                    String message = childElem.getAttribute(INPUT_MESSAGE_TAG);
                    String type = childElem.getAttribute(INPUT_TYPE_TAG);
                    String stackTrace = childElem.getFirstChild().getTextContent();
                    String testFile = elem.getAttribute(INPUT_CLASS_NAME_TAG);
                    String testId = elem.getAttribute(INPUT_NAME_TAG);
                    handleError(partition, testFile, testId, message, type, stackTrace);
                }
                parseXml((Element)childNode, partition);
            }
        }
    }

    private void parseErrorPatternsFile(String path) throws JsonProcessingException, IOException {
    	
    	ArrayNode fileArray = (ArrayNode) mapper.readTree(new File(path));
    	
        for (int objectInd=0; objectInd<fileArray.size(); objectInd++) {
            String errorType = fileArray.get(objectInd).get(ClassifyErrors.OUTPUT_ERROR_TYPE_TAG).asText();
            Set<ClassifiedError> errorTypesSet = errorTypes.get(errorType);
            if (errorTypesSet==null) {
                errorTypesSet = new HashSet<>();
                errorTypes.put(errorType, errorTypesSet);
            }

            ArrayNode patternResult = (ArrayNode) fileArray.get(objectInd).get(ClassifyErrors.INPUT_PATTERNS_TAG);
            for (int patternInd = 0; patternInd < patternResult.size(); patternInd++) {
                String output = patternResult.get(patternInd).get(OUTPUT_TAG).asText();
                String message = patternResult.get(patternInd).get(ClassifyErrors.INPUT_MESSAGE_TAG).asText();
                String exceptionType = patternResult.get(patternInd).get(INPUT_TYPE_TAG).asText();
                String semanticTag = patternResult.get(patternInd).get(INPUT_SEMANTIC_TAG).asText();
                ObjectNode stackTrace = (ObjectNode) patternResult.get(patternInd).get(INPUT_STACK_TRACE_TAG);
                ClassifiedError classifiedError = new ClassifiedError(message, exceptionType, output, semanticTag, stackTrace);
                errorTypesSet.add(classifiedError);
            }
        }
        // Add instance for all unclassified errors
        errorTypes.get(ErrorType.OTHER.name()).add(unclassifiedError);
    }

    private void handleError(String partition, String testFile, String testId, String message, String exceptionType, String stackTrace) {
        for (String errorType: errorTypes.keySet()) {
            for (ClassifiedError pattern : errorTypes.get(errorType)) {
                if (pattern.isPatternSatisfied(message, exceptionType, stackTrace, testFile, appPackages)) {
                    pattern.addTest(partition, testFile, testId, message);
                    return;
                }
            }
        }
        unclassifiedError.addTest(partition, testFile, testId, message);
    }

	private void writeClassifiedErrorsFile() throws JsonGenerationException, JsonMappingException, IOException {
		String errorsFileName = this.testDir + "_" + OUTPUT_FILE;
		ArrayNode jsonErrorTypesList = mapper.createArrayNode();
		
		for (String currType : errorTypes.keySet()) { // add error type
			ArrayNode jsonErrorsList = mapper.createArrayNode();
			for (ClassifiedError currError : errorTypes.get(currType)) {
				if (currError.getErrorMessages().isEmpty()) {
					continue;
				}
				ArrayNode jsonPartitionList = mapper.createArrayNode();
				for (String currPartition : currError.getErrorTests().keySet()) { // add partitions
					Map<String, ArrayList<String>> partition = currError.getErrorTests().get(currPartition);
					ObjectNode jsonPartition = mapper.createObjectNode();
					ArrayNode jsonTestFileList = mapper.createArrayNode();
					for (String currTestFile : partition.keySet()) { // add test files
						ArrayList<String> testIds = partition.get(currTestFile);
						ArrayNode jsonTestIdsList = mapper.valueToTree(testIds);
						ObjectNode jsonTestFile = mapper.createObjectNode();
						jsonTestFile.put(OUTPUT_TEST_CLASS_TAG, currTestFile);
						jsonTestFile.set(OUTPUT_TEST_METHODS_TAG, jsonTestIdsList);
						jsonTestFileList.add(jsonTestFile);
					}
					jsonPartition.put(OUTPUT_PARTITION_TAG, currPartition);
					jsonPartition.set(OUTPUT_TEST_CLASSES_TAG, jsonTestFileList);
					jsonPartitionList.add(jsonPartition);
				}
				ArrayNode jsonMessages = mapper.valueToTree(currError.getErrorMessages());
				ObjectNode jsonError = mapper.createObjectNode();
				jsonError.put(INPUT_PATTERN_TAG, currError.getOutput());
				jsonError.put(INPUT_TYPE_TAG, currError.getType());
				jsonError.put(INPUT_SEMANTIC_TAG, currError.getSemanticTag());
				jsonError.set(INPUT_MESSAGE_TAG, jsonMessages);
				jsonError.set(OUTPUT_PARTITIONS_TAG, jsonPartitionList);
				jsonErrorsList.add(jsonError);
			}
			ObjectNode jsonType = mapper.createObjectNode();
			jsonType.put(OUTPUT_ERROR_TYPE_TAG, currType);
			jsonType.set(OUTPUT_ERRORS_TAG, jsonErrorsList);
			jsonErrorTypesList.add(jsonType);
		}

		mapper.writeValue(new File(errorsFileName), jsonErrorTypesList);

    }

    private static CommandLine parseCommandLineOptions(String[] args) {
        Options options = new Options();

        // option for reports path
        options.addOption(Option.builder("pt")
            .longOpt("reports-path")
            .hasArg()
            .desc("Path to the reports folders")
            .type(String.class)
            .build()
        );

        options.addOption(Option.builder("ep")
            .longOpt("error-patterns")
            .hasArg()
            .desc("Path to file of error patterns")
            .type(String.class)
            .build()
        );

        options.addOption(Option.builder("ap")
            .longOpt("app-packages")
            .hasArg()
            .desc("Application Packages")
            .type(String.class)
            .build()
        );

        options.addOption(Option.builder("td")
            .longOpt("test-dir")
            .hasArg()
            .desc("Test Directory")
            .type(String.class)
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
                formatter.printHelp(ClassifyErrors.class.getName(), options, true);
                return null;
            }
            // check whether required options are specified
            if (!cmd.hasOption("pt")) {
                formatter.printHelp(ClassifyErrors.class.getName(), options, true);
                return null;
            }
            if (!cmd.hasOption("ep")) {
                formatter.printHelp(ClassifyErrors.class.getName(), options, true);
                return null;
            }
            if (!cmd.hasOption("ap")) {
                formatter.printHelp(ClassifyErrors.class.getName(), options, true);
                return null;
            }
            if (!cmd.hasOption("td")) {
                formatter.printHelp(ClassifyErrors.class.getName(), options, true);
                return null;
            }
        }
        catch (ParseException e) {
            logger.warning(e.getMessage());
            formatter.printHelp(ClassifyErrors.class.getName(), options, true);
        }
        return cmd;
    }

    public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {
        System.out.println("Failure classification currently not supported");

//        // parse command-line options
//        CommandLine cmd = parseCommandLineOptions(args);
//
//        // if parser command-line is empty (which occurs if the help option is specified or a
//        // parse exception occurs, exit
//        if (cmd == null) {
//            System.exit(0);
//        }
//
//        String reportPath = cmd.getOptionValue("pt");
//        logger.info("Reports path: "+ reportPath);
//        String[] reportsPath = reportPath.split(",");
//
//        String errorPatterns = cmd.getOptionValue("ep");
//        logger.info("Error Patterns: "+ errorPatterns);
//
//        String appPackage = cmd.getOptionValue("ap");
//        logger.info("Application Packages: "+ appPackage);
//        String[] appPackages = appPackage.split(",");
//
//        String testDir = cmd.getOptionValue("td");
//        logger.info("Test Directory: "+ testDir);
//
//        new ClassifyErrors(reportsPath, errorPatterns, appPackages, testDir);
    }
}
