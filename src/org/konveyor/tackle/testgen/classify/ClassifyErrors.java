package org.konveyor.tackle.testgen.classify;

import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.apache.commons.cli.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class ClassifyErrors {
    public enum ErrorType { SERIALIZATION, JSON_PARSING, DUMMY_FUNCTION, KLU_INTERFACE, SERVICE_INVOCATION, OTHER }

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

    private void parseErrorPatternsFile(String path) throws FileNotFoundException {
        InputStream fis = new FileInputStream(path);
        JsonReader reader = Json.createReader(fis);
        JsonArray fileObject = reader.readArray();
        reader.close();

        for (int objectInd=0; objectInd<fileObject.size(); objectInd++) {
            String errorType = fileObject.getJsonObject(objectInd).getString(ClassifyErrors.OUTPUT_ERROR_TYPE_TAG);
            Set<ClassifiedError> errorTypesSet = errorTypes.get(errorType);
            if (errorTypesSet==null) {
                errorTypesSet = new HashSet<>();
                errorTypes.put(errorType, errorTypesSet);
            }

            JsonArray patternResult = fileObject.getJsonObject(objectInd).getJsonArray(ClassifyErrors.INPUT_PATTERNS_TAG);
            for (int patternInd = 0; patternInd < patternResult.size(); patternInd++) {
                String output = patternResult.getJsonObject(patternInd).getString(OUTPUT_TAG);
                String message = patternResult.getJsonObject(patternInd).getString(ClassifyErrors.INPUT_MESSAGE_TAG);
                String exceptionType = patternResult.getJsonObject(patternInd).getString(INPUT_TYPE_TAG);
                String semanticTag = patternResult.getJsonObject(patternInd).getString(INPUT_SEMANTIC_TAG);
                JsonObject stackTrace = patternResult.getJsonObject(patternInd).getJsonObject(INPUT_STACK_TRACE_TAG);
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

    private void writeClassifiedErrorsFile() throws FileNotFoundException {
        String errorsFileName = this.testDir + "_" + OUTPUT_FILE;
        JsonWriter writer = null;
        try {
            JsonArrayBuilder jsonErrorTypesList = Json.createArrayBuilder();
            for (String currType: errorTypes.keySet()) { //add error type
                JsonArrayBuilder jsonErrorsList = Json.createArrayBuilder();
                for (ClassifiedError currError : errorTypes.get(currType)) {
                    if (currError.getErrorMessages().isEmpty()) {
                        continue;
                    }
                    JsonArrayBuilder jsonPartitionList = Json.createArrayBuilder();
                    for (String currPartition : currError.getErrorTests().keySet()) { //add partitions
                        Map<String, ArrayList<String>> partition = currError.getErrorTests().get(currPartition);
                        JsonObjectBuilder jsonPartition = Json.createObjectBuilder();
                        JsonArrayBuilder jsonTestFileList = Json.createArrayBuilder();
                        for (String currTestFile : partition.keySet()) { //add test files
                            ArrayList<String> testIds = partition.get(currTestFile);
                            JsonArrayBuilder jsonTestIdsList = Json.createArrayBuilder();
                            for (String currTestId : testIds) { //add test ids
                                jsonTestIdsList.add(currTestId);
                            }
                            JsonObjectBuilder jsonTestFile = Json.createObjectBuilder();
                            jsonTestFile.add(OUTPUT_TEST_CLASS_TAG, currTestFile).add(OUTPUT_TEST_METHODS_TAG, jsonTestIdsList);
                            jsonTestFileList.add(jsonTestFile);
                        }
                        jsonPartition.add(OUTPUT_PARTITION_TAG, currPartition).add(OUTPUT_TEST_CLASSES_TAG, jsonTestFileList);
                        jsonPartitionList.add(jsonPartition);
                    }
                    JsonArrayBuilder jsonMessages = Json.createArrayBuilder();
                    for (String currMessage : currError.getErrorMessages()) { //add messages
                        jsonMessages.add(currMessage);
                    }
                    JsonObjectBuilder jsonError = Json.createObjectBuilder();
                    jsonError.add(INPUT_PATTERN_TAG, currError.getOutput()).add(INPUT_TYPE_TAG, currError.getType()).
                        add(INPUT_SEMANTIC_TAG, currError.getSemanticTag()).add(INPUT_MESSAGE_TAG, jsonMessages).
                        add(OUTPUT_PARTITIONS_TAG, jsonPartitionList);
                    jsonErrorsList.add(jsonError);
                }
                JsonObjectBuilder jsonType = Json.createObjectBuilder();
                jsonType.add(OUTPUT_ERROR_TYPE_TAG, currType).add(OUTPUT_ERRORS_TAG, jsonErrorsList);
                jsonErrorTypesList.add(jsonType);
            }
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            writer = writerFactory.createWriter(new FileOutputStream((errorsFileName)));
            writer.writeArray(jsonErrorTypesList.build());

        } finally {
            if (writer != null) {
                writer.close();
            }
            logger.info("Classified errors file is located in " + errorsFileName);
        }
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
