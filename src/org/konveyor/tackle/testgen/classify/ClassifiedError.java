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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;


public class ClassifiedError {
    private final String message;
    private final String type;
    private final String output;
    private final StackTracePattern stackTrace;
    private final String semanticTag;

    private final Set<String> errorMessages = new HashSet<>();
    private final Map<String, Map<String, ArrayList<String>>> errorTests = new HashMap<>();

    public static final String CAUSED_BY = "Caused by:";

    ClassifiedError(String message, String type, String output, String semanticTag, ObjectNode stackTrace) {
        this.message = message;
        this.type = type;
        this.output = output;
        this.semanticTag = semanticTag;
        this.stackTrace = new ClassifiedError.StackTracePattern(stackTrace);
    }

    public String getType() { return type; }

    public String getOutput() { return output; }

    public String getSemanticTag() { return semanticTag; }

    public Set<String> getErrorMessages() { return errorMessages; }

    public Map<String, Map<String, ArrayList<String>>> getErrorTests() { return errorTests; }

    public boolean isPatternSatisfied(String errMessage, String errExceptionType, String errStackTrace,
                                      String errTestFile, List<String> appPackages) {
        String errCausedBy = "";
        if (errStackTrace.contains(CAUSED_BY)) {
            //causedBy was set to the line in stack trace which contains the caused by string
            errCausedBy = errStackTrace.substring(errStackTrace.indexOf(CAUSED_BY));
            errCausedBy = errCausedBy.substring(0, errCausedBy.indexOf("\n"));
        }
        String[] stackTraceLines = errStackTrace.split("\n");
        String errStackTraceText = errStackTrace;
        if (stackTrace.getPosition()>=0) {
            // Sets text to the whole stack trace or the specific line if position was defined
            errStackTraceText = stackTraceLines[stackTrace.getPosition()];
        }
        String errStackTraceDirect = "";
        if (!stackTrace.getDirect().isEmpty() && stackTraceLines.length >=2) {
            // Direct/indirect calls is determined when the exception was identified right after the test file call
            // We find the inner call of the test file and then validate there is only one call to a function in the application
            int testFileIndex = 0;
            for (;testFileIndex<stackTraceLines.length;testFileIndex++) {
                if (stackTraceLines[testFileIndex].contains(errTestFile)) {
                    break;
                }
            }
            errStackTraceDirect = "false";
            for (String appPackage: appPackages) {
                if (stackTraceLines[testFileIndex - 1].contains(appPackage) &&
                    !stackTraceLines[testFileIndex - 2].contains(appPackage)) {
                    errStackTraceDirect = "true";
                    break;
                }
            }
        }

        return (message.isEmpty() || errMessage.contains(message)) &&
            (type.isEmpty() || errExceptionType.equals(type)) &&
            (stackTrace.getText().isEmpty() || errStackTraceText.contains(stackTrace.getText())) &&
            (stackTrace.getRootCause().isEmpty() || errCausedBy.contains(stackTrace.getRootCause())) &&
            (stackTrace.getDirect().isEmpty() || errStackTraceDirect.equals(stackTrace.getDirect()));
    }

    public void addTest(String partition, String testFile, String testId, String message) {
        errorMessages.add(message);
        Map<String, ArrayList<String>> partitionsError = errorTests.get(partition);
        if (partitionsError==null) {
            partitionsError = new HashMap<>();
            errorTests.put(partition, partitionsError);
        }
        ArrayList<String> testFileErrors = partitionsError.get(testFile);
        if (testFileErrors==null) {
            testFileErrors = new ArrayList<>();
            partitionsError.put(testFile, testFileErrors);
        }
        testFileErrors.add(testId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassifiedError that = (ClassifiedError) o;
        return Objects.equals(output, that.output) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(output, type);
    }

    public static class StackTracePattern {
        private final String text;
        private final int position;
        private final String rootCause;
        private final String direct;

        StackTracePattern(ObjectNode stackTrace) {
            if (stackTrace!=null) {
                this.text = stackTrace.get("text").asText();
                String jsonPosition = stackTrace.get("position").asText();
                this.position = jsonPosition.isEmpty() ? -1 : Integer.parseInt(jsonPosition);
                this.rootCause = stackTrace.get("rootCause").asText();
                this.direct = stackTrace.get("direct").asText();
            }
            else {
                this.text = "";
                this.position = -1;
                this.rootCause = "";
                this.direct = "";
            }
        }

        public String getText() { return text; }

        public int getPosition() { return position; }

        public String getRootCause() { return rootCause; }

        public String getDirect() { return direct; }
    }
}
