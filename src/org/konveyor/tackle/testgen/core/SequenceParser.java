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

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.typesystem.NullType;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.konveyor.tackle.testgen.util.TackleTestLogger;
import randoop.operation.TypedOperation;
import randoop.sequence.Sequence;
import randoop.sequence.SequenceParseException;
import randoop.sequence.Statement;
import randoop.sequence.Variable;
import randoop.types.GenericClassType;
import randoop.types.ReferenceType;
import randoop.types.Substitution;
import randoop.types.TypeVariable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parses sequences of code and translates to Randoop sequences according to its parsing API
 * @author RACHELBRILL
 *
 */

public class SequenceParser {

	/**
	 * Assumes the code contains simple statements, e.g., parameters to method and constructor calls are
	 * all variable names
	 * @param code
	 * @return
	 * @throws SequenceParseException
	 */

	private static final String  lineSep = System.lineSeparator();

	private static final String ADDED_VARS_PREFIX = "tkltestVar";

	private static int addedVarsCounter = 0;
	private static int statementCounter = 0;

	private static boolean VERBOSE = false;

	private static final Logger logger = TackleTestLogger.getLogger(SequenceParser.class);

	static {
		TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
		TypeSolver classLoaderTypeSolver = new ClassLoaderTypeSolver(ClassLoader.getSystemClassLoader());
		CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
		combinedSolver.add(reflectionTypeSolver);
		combinedSolver.add(classLoaderTypeSolver);
		JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);
		StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
	}

	// These constants are not defined in Randoop so defining them here instead..

	private static final String RANDOOP_METHOD_CALL = "MethodCall";
	private static final String RANDOOP_CONSTRUCTOR_CALL = "ConstructorCall";
	private static final String RANDOOP_FIELD_GET = "FieldGet";
	private static final String RANDOOP_FIELD_SET = "FieldSet";
	private static final String RANDOOP_PRIMITIVE_ASSIGNMENT = "NonreceiverTerm";
	private static final String RANDOOP_ARRAY_CREATION = "InitializedArrayCreation";

	/**
	 *
	 * @param code
	 * @param imports
	 * @param forClass
	 * @param addPackageDeclaration
	 * @param originalIndices
	 * @return Randoop sequence corresponding to input sequence, and the indices of original statements in the Randoop sequence.
	 * Note that original statements may be modified, e.g., constants passed as arguments to a method may be replaced with variables holding the constant value
	 * @throws SequenceParseException
	 */

	public static Sequence codeToSequence(String code, List<String> imports, String forClass, boolean addPackageDeclaration, List<Integer> originalIndices)
			throws SequenceParseException {

		String augmentedCode = augmentCode(code, imports, forClass, addPackageDeclaration);

		addedVarsCounter = 0;

		Map<Integer, NodeList<Type>> indexToParameterTypes = new HashMap<Integer, NodeList<Type>>();

		List<String> formattedStatements = statementsToRandoopStatement(augmentedCode, originalIndices, indexToParameterTypes);

		if (VERBOSE) {
			for (String formatted : formattedStatements) {
				logger.fine(formatted);
			}
		}

		Sequence seq = Sequence.parse(formattedStatements);

		if ( ! indexToParameterTypes.isEmpty()) {
			seq = addParameterTypes(seq, indexToParameterTypes);
		}

		return seq;
	}

	private static Sequence addParameterTypes(Sequence seq, Map<Integer, NodeList<Type>> indexToParameterTypes) {

		Sequence result = new Sequence();

		for (int i=0; i<seq.size(); i++) {

			Statement statement = seq.getStatement(i);
			List<Integer> oldIndices = seq.getInputsAsAbsoluteIndices(i);
			List<Variable> inputs = new ArrayList<Variable>();

			for (int ind : oldIndices) {
				inputs.add(result.getVariable(ind));
			}

			if (indexToParameterTypes.containsKey(i)) {
				if ( ! statement.isConstructorCall()) {
					throw new IllegalArgumentException("Expected constructor statement but received "+statement.getOperation().getName());
				}

				randoop.types.Type param = statement.getDeclaringClass();
				TypedOperation op = statement.getOperation();

				List<TypeVariable> colElemTypeVars = ((GenericClassType) param).getTypeParameters();
		        List<ReferenceType> colElemTypeArgs = new ArrayList<>();
				for (Type typeParam : indexToParameterTypes.get(i)) {
					Class<?> typeClass = null;

					try {
						typeClass = Class.forName(typeParam.resolve().describe());
					} catch (ClassNotFoundException e) {
						throw new RuntimeException(e);
					}

					colElemTypeArgs.add(ReferenceType.forClass(typeClass));
				}
				Substitution subst = new Substitution(colElemTypeVars, colElemTypeArgs);
				TypedOperation substOp = op.substitute(subst);
				Statement substStatement = new Statement(substOp);
				result = result.extend(substStatement, inputs);
			} else {
				result = result.extend(statement, inputs);
			}
		}

		return result;
	}

	private static String augmentCode(String code, List<String> imports, String forClass, boolean addPackageDeclaration) {

		StringBuilder result = new StringBuilder();

		if (addPackageDeclaration && forClass.contains(".")) {
			result.append("package "+forClass.substring(0, forClass.lastIndexOf('.'))+";");
		}

		for (String imp : imports) {
			result.append("import "+imp+";"+lineSep);
		}

		result.append("public class "+forClass.replaceAll("\\.", "_")+"_Test {"+lineSep);
		result.append("public void test0()  throws Throwable  {"+lineSep);
		result.append(code.replaceAll("\\\\r\\\\n", lineSep));
		result.append("}"+lineSep);
		result.append("}"+lineSep);

		return result.toString();
	}

	private static List<String> statementsToRandoopStatement(String code, List<Integer> originalIndices, Map<Integer, NodeList<Type>> indexToParameterTypes) {

		List<String> formattedStatements = new ArrayList<String>();

		CompilationUnit compUnit = StaticJavaParser.parse(code);

		statementCounter = 0;

		compUnit.findAll(ExpressionStmt.class).forEach(statement -> {
			try {
				formattedStatements.addAll(parseStatement(statement, originalIndices, indexToParameterTypes));
				statementCounter = formattedStatements.size();
			} catch (ClassNotFoundException | NoSuchFieldException | SecurityException e) {
				throw new RuntimeException(e);
			}
		});

		return formattedStatements;
	}

	/* One statement can result in multiple Randoop statements, because we need to create additional variables for constant values and fields passed as parameters */

	private static List<String> parseStatement(ExpressionStmt statement, List<Integer> originalIndices, Map<Integer, NodeList<Type>> indexToParameterTypes)
			throws ClassNotFoundException, NoSuchFieldException, SecurityException {

		List<String> randoopStatements = new ArrayList<String>();

		Node node = statement.getChildNodes().get(0);

		if (node instanceof VariableDeclarationExpr) {

			parseVariableDec((VariableDeclarationExpr) node, randoopStatements, indexToParameterTypes);

		} else if (node instanceof MethodCallExpr) {

			parseMethodCall((MethodCallExpr) node, randoopStatements);
		} else if (node instanceof AssignExpr) {

			parseAssignmentExpression((AssignExpr) node, randoopStatements, indexToParameterTypes);
		} else {
			throw new IllegalArgumentException("Encoutered unrecognized statement: "+statement.toString());
		}

		originalIndices.add(statementCounter+randoopStatements.size()-1);

		return randoopStatements;
	}

	private static void parseVariableDec(VariableDeclarationExpr varDec, List<String> randoopStatements, Map<Integer, NodeList<Type>> indexToParameterTypes)
			throws ClassNotFoundException, NoSuchFieldException, SecurityException {

		VariableDeclarator var = varDec.getVariable(0);

		StringBuilder randoopStatement = new StringBuilder(var.getNameAsString()+" = ");

		List<Node> childNodes = var.getChildNodes();

		if (childNodes.size() != 3) {
			throw new IllegalArgumentException("Unexpected number of nodes in "+varDec.toString());
		}

		Node decType = childNodes.get(2);

		randoopStatement.append(parseAssignmentValue(var.getType().resolve(), (Expression) decType, randoopStatements, indexToParameterTypes));

		randoopStatements.add(randoopStatement.toString());
	}

	private static void parseAssignmentExpression(AssignExpr expr, List<String> randoopStatements, Map<Integer, NodeList<Type>> indexToParameterTypes)
			throws ClassNotFoundException, NoSuchFieldException, SecurityException {

		StringBuilder randoopStatement = new StringBuilder();
		Expression target = expr.getTarget();

		if (target instanceof FieldAccessExpr) {

			randoopStatement.append(getNextVar()+" = "+getFieldAccessStatement((FieldAccessExpr) target, false));
		} else {
			throw new IllegalArgumentException("Unexpected target expression type "+target.toString());
		}

		Expression value = expr.getValue();

		String valueAssignmentStatement = parseAssignmentValue(target.calculateResolvedType(), value, randoopStatements, indexToParameterTypes);

		if (value instanceof NameExpr) {
			randoopStatement.append(valueAssignmentStatement);
		} else {
			String nextVar = getNextVar();
			randoopStatements.add(nextVar+" = "+valueAssignmentStatement);
			randoopStatement.append(nextVar);
		}

		randoopStatements.add(randoopStatement.toString());
	}

	private static String parseAssignmentValue(ResolvedType targetType, Expression value, List<String> randoopStatements, Map<Integer, NodeList<Type>> indexToParameterTypes)
			throws ClassNotFoundException, NoSuchFieldException, SecurityException {

		StringBuilder valueAsRandoopStatement = new StringBuilder();

		Expression valueExpr = getInnerExpression(value);

		NodeList<Type> parameterTypes = null;

		if (valueExpr instanceof ObjectCreationExpr) {

			ObjectCreationExpr constr = (ObjectCreationExpr) valueExpr;

			if (constr.getChildNodes().get(0) instanceof ClassOrInterfaceType) {
				ClassOrInterfaceType type = (ClassOrInterfaceType) constr.getChildNodes().get(0);
				if (type.getTypeArguments().isPresent()) {
					parameterTypes = type.getTypeArguments().get();
				}
			}

			valueAsRandoopStatement.append(RANDOOP_CONSTRUCTOR_CALL+" : ");

			ResolvedConstructorDeclaration constDec = constr.resolve();

			valueAsRandoopStatement.append(constDec.getQualifiedSignature().replaceAll("<E>", "").replaceAll("<T>", "")+" : ");

			List<ResolvedParameterDeclaration> argTypes = new ArrayList<ResolvedParameterDeclaration>();

			for (int i=0; i<constDec.getNumberOfParams(); i++) {
				argTypes.add(constDec.getParam(i));
			}

			valueAsRandoopStatement.append(handleMethodArguments(constr.getArguments(), argTypes, randoopStatements));

		} else if (valueExpr instanceof FieldAccessExpr) {
			valueAsRandoopStatement.append(getFieldAccessStatement((FieldAccessExpr) valueExpr, true));
		} else if (targetType.isArray() && (valueExpr instanceof ArrayCreationExpr || valueExpr instanceof NullLiteralExpr)) {
			valueAsRandoopStatement.append(getArrayCreationStatement(valueExpr.toString(),
					valueExpr instanceof NullLiteralExpr? null : ((ArrayCreationExpr) valueExpr).getInitializer().isPresent()?
							((ArrayCreationExpr) valueExpr).getInitializer().get() : null, targetType.asArrayType().getComponentType(), randoopStatements));
		} else if (valueExpr instanceof LiteralExpr || valueExpr instanceof UnaryExpr) {
			valueAsRandoopStatement.append(getNonReceiverStatement(valueExpr, targetType));
		} else if (valueExpr instanceof MethodCallExpr) {
			valueAsRandoopStatement.append(getMethodCallStatement((MethodCallExpr) valueExpr, randoopStatements));
		} else if (valueExpr instanceof NameExpr) {
			valueAsRandoopStatement.append(((NameExpr) valueExpr).getNameAsString());
		} else {
			throw new IllegalArgumentException("Unexpected value assignment type "+valueExpr.toString());
		}

		if (parameterTypes != null) {
			indexToParameterTypes.put(statementCounter+randoopStatements.size(), parameterTypes);
		}

		return valueAsRandoopStatement.toString();
	}

	private static void parseMethodCall(MethodCallExpr methodCall, List<String> randoopStatements)
			throws ClassNotFoundException, NoSuchFieldException, SecurityException {

		StringBuilder randoopStatement = new StringBuilder(getNextVar()+" = ");

		randoopStatement.append(getMethodCallStatement(methodCall, randoopStatements));

		randoopStatements.add(randoopStatement.toString());
	}

	private static String getMethodCallStatement(MethodCallExpr methodCall, List<String> randoopStatements) throws ClassNotFoundException, NoSuchFieldException, SecurityException {

		StringBuilder randoopStatement = new StringBuilder(RANDOOP_METHOD_CALL+" : ");

		ResolvedMethodDeclaration methodDec = methodCall.resolve();

		randoopStatement.append(methodDec.getQualifiedSignature().replaceAll("<E>", "").replaceAll("<T>", "")+" : ");

		if ( ! methodDec.isStatic()) {
			// If not static we need to add the receiver object identifier as an input var
			randoopStatement.append(methodCall.getScope().get().asNameExpr()+" ");
		}

		List<ResolvedParameterDeclaration> argTypes = new ArrayList<ResolvedParameterDeclaration>();

		for (int i=0; i<methodDec.getNumberOfParams(); i++) {
			argTypes.add(methodDec.getParam(i));
		}

		randoopStatement.append(handleMethodArguments(methodCall.getArguments(), argTypes, randoopStatements));

		return randoopStatement.toString();
	}

	private static String handleMethodArguments(NodeList<Expression> arguments, List<ResolvedParameterDeclaration> argTypes, List<String> addedStatements) throws ClassNotFoundException, NoSuchFieldException, SecurityException {

		StringBuilder vars = new StringBuilder();

		int argIndex = 0;

		for (Expression expr : arguments) {

			if (expr instanceof NameExpr) {

				vars.append(((NameExpr) expr).getNameAsString());
				vars.append(" ");
			} else {

				expr = getInnerExpression(expr);

				if (expr instanceof NameExpr) {
					vars.append(((NameExpr) expr).getNameAsString());
					vars.append(" ");
				} else {

					// Need to define a new variable to hold this argument content

					String nextVar = getNextVar();
					vars.append(nextVar);
					vars.append(" ");

					if (expr instanceof FieldAccessExpr) {

						addedStatements.add(nextVar + " = " + getFieldAccessStatement((FieldAccessExpr) expr, true));

					} else if (expr instanceof LiteralExpr || expr instanceof UnaryExpr) {

						ResolvedType type = expr.calculateResolvedType();

						if (type.isNull()) {
							ResolvedParameterDeclaration paramType = argTypes.get(argIndex);

							if (paramType.getType().isArray()) {

								ResolvedType arrayType = paramType.getType().asArrayType().getComponentType();

								addedStatements.add(nextVar + " = " + getArrayCreationStatement(
										"new " + arrayType.describe() + "[0]", null, arrayType, addedStatements));

							} else {
								addedStatements.add(nextVar + " = " + getNonReceiverStatement(expr, argTypes.get(argIndex).getType()));
							}
						} else {
							addedStatements
									.add(nextVar + " = " + getNonReceiverStatement(expr, type));
						}

					} else {
						throw new IllegalArgumentException("Unsupported argument type: '" + expr.toString() + "'");
					}

				}

			}
			argIndex++;
		}

		return vars.toString();
	}

	private static String getArrayCreationStatement(String arrayCreateExpr, ArrayInitializerExpr initExpr, ResolvedType elementType, List<String> randoopStatements) {

		int length;

		if (arrayCreateExpr.equals("null")) {
			length = 0;
		} else {

			String content = arrayCreateExpr.substring(arrayCreateExpr.toString().indexOf("new ")+"new ".length());

			long count = content.chars().filter(ch -> ch == '[').count();

			if (count > 1) {
				throw new IllegalArgumentException("Currently do not support parsing of multi-dimensional arrays");
			}

			int openBr = content.indexOf('[');
			int closeBr = content.indexOf(']');
			String lengthStr = content.substring(openBr + 1, closeBr);
			if (lengthStr.length() > 0) {
				length = Integer.parseInt(lengthStr);
			if (initExpr != null && (length != initExpr.getValues().size())) {
				throw new IllegalArgumentException(
						"Unexpected number of initialized values at " + arrayCreateExpr.toString());
			}
			} else {
				if (initExpr == null) {
					throw new IllegalArgumentException("Missing array initialization at " + arrayCreateExpr.toString());
				}
				length = initExpr.getValues().size();
			}
		}

		String defaultVal = elementType.isPrimitive()? "0" : "null";

		StringBuilder extraVars = new StringBuilder();

		for (int i=0;i<length;i++) {

			String nextVar;

			if (initExpr == null) {
				nextVar = getNextVar();
				randoopStatements.add(nextVar+" = "+getNonReceiverStatement(defaultVal, elementType));
			} else {

				Node initVal = initExpr.getValues().get(i);

				Expression actualExpr = getInnerExpression((Expression) initVal);

				if (actualExpr instanceof NameExpr) {
					nextVar = initVal.toString();
				} else if (actualExpr instanceof LiteralExpr || actualExpr instanceof UnaryExpr) {
					nextVar = getNextVar();
					randoopStatements.add(nextVar+" = "+getNonReceiverStatement(actualExpr, elementType));
				} else {
					throw new RuntimeException("Unexpected array initialization type "+actualExpr.toString());
				}
			}

			extraVars.append(nextVar+" ");
		}

		return RANDOOP_ARRAY_CREATION+" : "+elementType.describe()+"["+Integer.toString(length)+"] : "+extraVars.toString();
	}

	private static String getFieldAccessStatement(FieldAccessExpr expr, boolean isGet) throws ClassNotFoundException, NoSuchFieldException, SecurityException {

		ResolvedType resType = expr.getScope().calculateResolvedType();

		String className = resType.describe();

		Class<?> theClass;

		try {
			theClass = Class.forName(className);
		} catch (ClassNotFoundException e) {
			// try as an inner class
			int lastDot = className.lastIndexOf('.');
			if (lastDot > -1) {
				className = className.substring(0, lastDot)+'$'+className.substring(lastDot+1);
				theClass = Class.forName(className);
			} else {
				throw e;
			}
		}

		String statement =  (isGet? RANDOOP_FIELD_GET : RANDOOP_FIELD_SET)+" : "+className+".<"+(isGet? "get" : "set")+">("+expr.getNameAsString()+") : ";

		Field field = theClass.getField(expr.getNameAsString());

		if (! java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
			// If not static we need to add the object identifier as an input var
			statement += expr.getScope().asNameExpr()+" ";
		}

		return statement;
	}

	private static String getNonReceiverStatement(Expression expr, ResolvedType type) {

		String exprStr = expr.toString();

		// patch to handle Randoop parsing restrictions for long values
		if ((expr instanceof LongLiteralExpr || (expr instanceof UnaryExpr && ((UnaryExpr) expr).getExpression() instanceof LongLiteralExpr))
				&& exprStr.endsWith("L")) {
			exprStr = exprStr.substring(0, exprStr.length()-1);
		} else if (expr instanceof CharLiteralExpr) {
			// String is 'c' so we need second entry
			char c = exprStr.charAt(1);
			exprStr = Integer.toString((int) c);
		} else if (expr instanceof DoubleLiteralExpr || (expr instanceof UnaryExpr && ((UnaryExpr) expr).getExpression() instanceof DoubleLiteralExpr) &&
				(exprStr.endsWith("d") || exprStr.endsWith("D"))) {
			exprStr = exprStr.substring(0, exprStr.length()-1);
		}

		ResolvedType exprType = expr.calculateResolvedType();

		return getNonReceiverStatement(exprStr, exprType instanceof NullType? type : exprType);
	}

	private static String getNonReceiverStatement(String exprStr, ResolvedType type) {
		return RANDOOP_PRIMITIVE_ASSIGNMENT+" : "+type.describe()+":"+exprStr+" :";
	}

	private static Expression getInnerExpression(Expression expr) {

		Expression returnExpr = expr;

		while (returnExpr instanceof EnclosedExpr || returnExpr instanceof CastExpr) {
			if (returnExpr instanceof EnclosedExpr) {
				returnExpr = ((EnclosedExpr) returnExpr).getInner();
			} else {
				returnExpr = ((CastExpr) returnExpr).getExpression();
			}
		}

		return returnExpr;
	}

	private static String getNextVar() {
		return ADDED_VARS_PREFIX+(addedVarsCounter++);
	}

	/** Remove assertions from sequence if they refer to non-declared variables
	 * */

//    public static String cleanSeqAssetions(String seqWithAssert) {
//
//    	StringBuilder cleanedSeq = new StringBuilder();
//
//    	final BlockStmt block;
//
//    	try {
//
//    		block = StaticJavaParser.parseBlock("{"+seqWithAssert+"}");
//    	} catch (ParseProblemException e) {
//    		logger.warning("Skipping assertion cleanup due to: "+e.getMessage());
//    		return seqWithAssert;
//    	}
//
//    	block.findAll(Statement.class).forEach(statement -> {
//
//			if (!statement.isBlockStmt()) {
//
//				// can't use JavaParser AssertStmt because it assumes as single argument
//				if (!statement.isExpressionStmt() || !statement.asExpressionStmt().getExpression().isMethodCallExpr()
//						|| statement.asExpressionStmt().getExpression().asMethodCallExpr().getName()
//								.equals("assertEquals")) {
//					cleanedSeq.append(statement.toString());
//					cleanedSeq.append(lineSep);
//				} else {
//					MethodCallExpr methodExpr = statement.asExpressionStmt().getExpression().asMethodCallExpr();
//					if (isDefined(methodExpr, block)) {
//						cleanedSeq.append(statement.toString());
//						cleanedSeq.append(lineSep);
//					}
//				}
//			}
//
//    	});
//
//    	return cleanedSeq.toString();
//    }


//    private static boolean isDefined(MethodCallExpr methodExpr, BlockStmt block) {
//
//    	for (int i=0;i<methodExpr.getArguments().size(); i++) {
//			Expression argExpr = methodExpr.getArgument(i);
//			NameExpr receiverName = null;
//
//			if (argExpr.isNameExpr()) {
//				receiverName = argExpr.asNameExpr();
//			} else if (argExpr.isMethodCallExpr()) {
//				MethodCallExpr methodArg = argExpr.asMethodCallExpr();
//				if ( ! methodArg.getName().asString().equals(DiffAssertionsGenerator.FIELD_UTIL_METHOD_NAME)) {
//					receiverName = methodArg.getScope().get().asNameExpr();
//				}
//			}
//
//			if (receiverName != null) {
//				if ( ! resolve(receiverName.getNameAsString(), block)) {
//					return false;
//				}
//			}
//		}
//
//    	return true;
//    }
//
    /* Need our own resolve implementation, because JavaParser's resolve requires a complete compilation unit
     * which we don't have yet at this point
     */

    static boolean resolve(String varName, BlockStmt block) {

    	return getResolvedDeclaration(varName, block) != null;
    }

    private static VariableDeclarator getResolvedDeclaration(String varName, BlockStmt block) {

    	List<ExpressionStmt> experssions = block.findAll(ExpressionStmt.class);

    	for (ExpressionStmt exprStmt : experssions) {
    		if (exprStmt.getExpression().isVariableDeclarationExpr()) {
    			VariableDeclarationExpr varDec = exprStmt.getExpression().asVariableDeclarationExpr();
    			VariableDeclarator var = varDec.getVariable(0);
    			if (var.getName().asString().equals(varName)) {
    				return var;
    			}
    		}
    	}

    	return null;
    }

    /**
     * Returns false for primitive wrapper
     * @param varName
     * @param code
     * @return
     */

	public static boolean isPrimitiveType(String varName, String code) {

		final BlockStmt block;

		try {
    		block = StaticJavaParser.parseBlock("{"+code+"}");
    	} catch (ParseProblemException e) {
    		logger.warning("Skipping block due to: "+e.getMessage());
    		return true;
    	}

		VariableDeclarator varDec = getResolvedDeclaration(varName, block);

		return varDec != null && varDec.getType().isPrimitiveType();
	}
}
