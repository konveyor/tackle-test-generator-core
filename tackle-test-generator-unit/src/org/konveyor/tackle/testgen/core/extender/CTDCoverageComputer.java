package org.konveyor.tackle.testgen.core.extender;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.konveyor.tackle.testgen.util.Constants;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import org.konveyor.tackle.testgen.util.Utils;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.javaparser.utils.Pair;

import randoop.org.apache.commons.io.FileUtils;

public class CTDCoverageComputer {
	
	private static final Logger logger = TackleTestLogger.getLogger(CTDCoverageComputer.class);
	
	
	public static Pair<Double,Double> calcCombinatorialCoverage(String methodSig, ArrayNode[] testPlanRows, 
			boolean[] execSuccess, boolean[] usedExisting, int tWay) {
		
		int ctdCovered = IntStream.range(0, execSuccess.length)
                .mapToObj(idx -> execSuccess[idx]).filter(b -> b).collect(Collectors.toList()).size();
		
		int ctdExistingCovered = IntStream.range(0, usedExisting.length)
                .mapToObj(idx -> usedExisting[idx]).filter(b -> b).collect(Collectors.toList()).size();
		
		if (ctdCovered == 0 && ctdExistingCovered == 0) {
			return new Pair<>(0.0, 0.0);
		}
		
		if (testPlanRows[0].size() == 1 || tWay == 1) {
			// CCMCL doesn't support computation of 1-way coverage, so we do it ourselves
			return computeOneWayCoverage(testPlanRows, execSuccess, usedExisting);
		}

		BufferedWriter covWriter = null;
		BufferedWriter covExistingWriter = null;
		BufferedWriter modelWriter = null;
		
		String methodNameForFile = methodSig.replaceAll("\\(", "__").replaceAll("\\)", "__").replaceAll("\\.", "_").replaceAll(",", "_").
				replaceAll("<", "_").replaceAll(">", "_");
		
		File covFile = new File(methodNameForFile+".csv");
		File covExistingFile = new File(methodNameForFile+"_existing.csv");
		File modelFile = new File(methodNameForFile+".txt");
		
		List<Set<String>> paramValues = new ArrayList<>();
		
		for (int i=0;i<testPlanRows[0].size(); i++) {
			paramValues.add(new HashSet<>());
		}

		try {
			
			if (ctdCovered > 0) {
				covWriter = new BufferedWriter(new FileWriter(covFile));
			}
			
			if (ctdExistingCovered > 0) {
				covExistingWriter = new BufferedWriter(new FileWriter(covExistingFile));
			}
			
			modelWriter = new BufferedWriter(new FileWriter(modelFile));
			
			int rowCtr = 0;
			
			for (ArrayNode testPlanRow : testPlanRows) {

				List<String> testPlanRowTypes = new ArrayList<>();
				testPlanRow.elements().forEachRemaining(entry -> {
					testPlanRowTypes.add(entry.get("type").asText());
				});
				
				for (int i=0;i<testPlanRowTypes.size(); i++) {
					paramValues.get(i).add(testPlanRowTypes.get(i));
				}
				
				if (ctdCovered > 0 && execSuccess[rowCtr]) {
					writeCSVLine(covWriter, testPlanRowTypes);
				}
				
				if (ctdExistingCovered > 0 && usedExisting[rowCtr]) {
					writeCSVLine(covExistingWriter, testPlanRowTypes);
				}
				
				rowCtr++;
			}
			
			writeModel(methodNameForFile, modelWriter, paramValues);
			
		} catch (IOException e) {
			logger.warning("Failed to open coverage file for method " + methodSig);
			return new Pair<>(-1.0, -1.0);
		} finally {
			try {
				if (covWriter != null) {
					covWriter.close();
				}
				if (covExistingWriter != null) {
					covExistingWriter.close();
				}
				if (modelWriter != null) {
					modelWriter.close();
				}
			} catch (IOException e) {
				logger.warning("Failed to close coverage file for method " + methodSig);
			}
		}
		
		int twayCov = tWay > paramValues.size()? paramValues.size() : tWay;
		
		double ctdCov = ctdCovered == 0? 0 : computeCoverage(covFile, modelFile, twayCov);
		
		double ctdExistingCov = ctdExistingCovered == 0? 0 : computeCoverage(covExistingFile, modelFile, twayCov);
		
		FileUtils.deleteQuietly(covFile);
		FileUtils.deleteQuietly(covExistingFile);
		FileUtils.deleteQuietly(modelFile);
		
		return new Pair<>(ctdCov, ctdExistingCov);
	}
	
	private static Pair<Double,Double> computeOneWayCoverage(ArrayNode[] testPlanRows, 
			 boolean[] execSuccess, boolean[] usedExisting) {
		
		List<Set<String>> totalValues = new ArrayList<>();
		List<Set<String>> coveredValues = new ArrayList<>();
		List<Set<String>> coveredExisting = new ArrayList<>();
		
		for (int i=0;i<testPlanRows[0].size(); i++) {
			totalValues.add(new HashSet<>());
			coveredValues.add(new HashSet<>());
			coveredExisting.add(new HashSet<>());
		}
		
		int rowCtr = 0;
		
		for (ArrayNode testPlanRow : testPlanRows) {

			List<String> testPlanRowTypes = new ArrayList<>();
			testPlanRow.elements().forEachRemaining(entry -> {
				testPlanRowTypes.add(entry.get("type").asText());
			});
			
			for (int i=0;i<testPlanRowTypes.size(); i++) {
				totalValues.get(i).add(testPlanRowTypes.get(i));
			}
			
			if (execSuccess[rowCtr]) {
				for (int i=0;i<testPlanRowTypes.size(); i++) {
					coveredValues.get(i).add(testPlanRowTypes.get(i));
				}
			}
			
			if (usedExisting[rowCtr]) {
				for (int i=0;i<testPlanRowTypes.size(); i++) {
					coveredExisting.get(i).add(testPlanRowTypes.get(i));
				}
			}
			
			rowCtr++;
		}
		
		int ctdCov = 0, ctdExistingCov = 0, totalVals = 0;
		
		for (int i=0;i<totalValues.size();i++) {
			ctdCov += coveredValues.get(i).size();
			ctdExistingCov += coveredExisting.get(i).size();
			totalVals += totalValues.get(i).size();
		}
		
		return new Pair<>(((double) ctdCov)/totalVals, ((double) ctdExistingCov)/totalVals);
	}

	private static void writeModel(String modelName, BufferedWriter writer, List<Set<String>> paramValues) throws IOException {
		
		writer.write("[System]");
		writer.newLine();
		writer.write("Name: "+modelName);
		writer.newLine();
		writer.newLine();
		writer.write("[Parameter]");
		writer.newLine();
		int i=0;
		for (Set<String> values : paramValues) {
			writer.write((i++)+" (enum) :" );
			for (String value : values) {
				writer.write(value+", ");
			}
			writer.newLine();
		}
	}

	private static void writeCSVLine(BufferedWriter writer, List<String> line) throws IOException {
		
		for (String type : line) {
			//TODO: handle compound types by duplicating row according to the cross product
			writer.write(type+",");
		}
		writer.newLine();
	}
	
	private static double computeCoverage(File inputFile, File modelFile, int twayCov) {
		List<String> processArgs = new ArrayList<String>();
		processArgs.add("java");
		processArgs.add("-jar");
		processArgs.add(Utils.getJarPath(Constants.CCM_JAR_NAME));
		processArgs.add("--inputfile");
		processArgs.add(inputFile.getAbsolutePath());
		processArgs.add("--ACTSfile");
		processArgs.add(modelFile.getAbsolutePath());
		processArgs.add("--tway");
		
		processArgs.add(String.valueOf(twayCov)); 
		
		ProcessBuilder processExecutorPB = new ProcessBuilder(processArgs);
		processExecutorPB.redirectError(Redirect.INHERIT);

		Process processExecutorP;
		
		String resLine = "Total "+twayCov+"-way coverage: ";
		
		BufferedReader reader = null;
		processExecutorP = null;
		
		try {
			processExecutorP = processExecutorPB.start();
			reader = 
	                new BufferedReader(new InputStreamReader(processExecutorP.getInputStream()));
			String line = null;
			double resCov = 0;
			while ( (line = reader.readLine()) != null) {
				if (line.startsWith(resLine)) {
					resCov = Double.parseDouble(line.substring(resLine.length()));
					return resCov;
				}
			}
			return -1.0;
		} catch (IOException | NumberFormatException e) {
			logger.warning("Failed to compute CTD coverage: "+e.getMessage());
			return -1.0;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					logger.warning("Failed to close reader stream: "+e.getMessage());
				}
			}
			if (processExecutorP != null && processExecutorP.isAlive()) {
				processExecutorP.destroyForcibly();
			}
		}
	}

}
