/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.compiler.parser;

import org.ballerinalang.compiler.parser.BallerinaParserErrorHandler.Action;
import org.ballerinalang.compiler.parser.BallerinaParserErrorHandler.Solution;

import java.io.InputStream;

public class BallerinaParser {

    private final BallerinaParserListener listner = new BallerinaParserListener();
    private final BallerinaParserErrorHandler errorHandler;
    private final TokenReader tokenReader;

    public BallerinaParser(BallerinaLexer lexer) {
        this.tokenReader = new TokenReader(lexer);
        this.errorHandler = new BallerinaParserErrorHandler(tokenReader, listner, this);
    }

    public BallerinaParser(InputStream inputStream) {
        this(new BallerinaLexer(inputStream));
    }

    public BallerinaParser(String source) {
        this(new BallerinaLexer(source));
    }

    public void parse() {
        parse(ParserRuleContext.COMP_UNIT);
    }

    /**
     * Parse the input starting from a given context.
     * 
     * @param context Context to start parsing
     */
    public void parse(ParserRuleContext context) {
        switch (context) {
            case COMP_UNIT:
                parseCompUnit();
                break;
            case EXTERNAL_FUNC_BODY:
                parseExternalFunctionBody();
                break;
            case FUNC_BODY:
                parseFunctionBody();
                break;
            case OPEN_BRACE:
                parseOpenBrace();
                break;
            case CLOSE_BRACE:
                parseCloseBrace();
                break;
            case FUNC_DEFINITION:
                parseFunctionDefinition();
                break;
            case FUNC_NAME:
                parseFunctionName();
                break;
            case FUNC_SIGNATURE:
                parseFunctionSignature();
                break;
            case OPEN_PARENTHESIS:
                parseOpenParenthesis();
                break;
            case PARAMETER:
                parseParameter();
                break;
            case PARAM_LIST:
                parseParamList();
                break;
            case RETURN_TYPE_DESCRIPTOR:
                parseReturnTypeDescriptor();
                break;
            case TYPE_DESCRIPTOR:
                parseTypeDescriptor();
                break;
            case ASSIGN_OP:
                parseAssignOp();
                break;
            case ANNOTATION_ATTACHMENT:
            case EXTERNAL_KEYWORD:
                parseExternalKeyword();
                break;
            case FUNC_BODY_BLOCK:
                parseFunctionBodyBlock();
                break;
            case SEMICOLON:
                parseStatementEnd();
                break;
            case CLOSE_PARENTHESIS:
                parseCloseParenthesis();
                break;
            case VARIABLE_NAME:
                parseVariableName();
                break;
            case EXPRESSION:
                parseExpression();
                break;
            case STATEMENT:
                parseStatement();
                break;
            case VAR_DECL_STMT:
                parseVariableDeclStmt();
                break;
            case ASSIGNMENT_STMT:
                parseAssignmentStmt();
                break;
            case BINARY_EXPR_RHS:
                parseBinaryExprRhs();
                break;
            case FOLLOW_UP_PARAM:
                parseFollowUpParameter();
                break;
            case VAR_DECL_STMT_RHS:
                parseVarDeclRhs();
                break;
            case TOP_LEVEL_NODE:
            default:
                throw new IllegalStateException("Cannot re-parse rule:" + context);

        }
    }

    /*
     * Private methods
     */

    private Token peek() {
        return this.tokenReader.peekNonTrivia();
    }

    private Token consume() {
        return this.tokenReader.consumeNonTrivia();
    }

    private Solution recover(Token token, ParserRuleContext currentCtx) {
        return this.errorHandler.recover(currentCtx, token);
    }

    private void switchContext(ParserRuleContext context) {
        this.errorHandler.pushContext(context);
    }

    private void revertContext() {
        this.errorHandler.popContext();
    }

    /**
     * Parse a given input and returns the AST. Starts parsing from the top of a compilation unit.
     */
    private void parseCompUnit() {
        switchContext(ParserRuleContext.COMP_UNIT);
        Token token = peek();
        while (token.kind != TokenKind.EOF) {
            parseTopLevelNode();
            token = peek();
        }

        this.listner.exitCompUnit();
        revertContext();
    }

    /**
     * Parse top level nodes.
     */
    private void parseTopLevelNode() {
        Token token = peek();
        switch (token.kind) {
            case PUBLIC:
                parseModifier();
                break;
            case FUNCTION:
                parseFunctionDefinition();
                break;
            default:
                this.errorHandler.removeInvalidToken();
                break;
        }
    }

    /**
     * Parse access modifiers.
     */
    private void parseModifier() {
        this.listner.exitModifier(consume());
    }

    /**
     * <p>
     * Parse function definition. A function definition has the following structure.
     * </p>
     * <code>
     * function-defn := FUNCTION identifier function-signature function-body
     * </code>
     */
    private void parseFunctionDefinition() {
        switchContext(ParserRuleContext.FUNC_DEFINITION);

        this.listner.exitSyntaxNode(consume()); // 'function' keyword. This is already verified

        parseFunctionName();
        parseFunctionSignature();
        parseFunctionBody();

        this.listner.exitFunctionDefinition();

        revertContext();
    }

    private void parseFunctionName() {
        Token token = peek();
        if (token.kind == TokenKind.IDENTIFIER) {
            this.listner.exitFunctionName(consume()); // function name
        } else {
            recover(token, ParserRuleContext.FUNC_NAME);
        }
    }

    /**
     * <p>
     * Parse function signature. A function signature has the following structure.
     * </p>
     * <code>
     * function-signature := ( param-list ) return-type-descriptor
     * </code>
     */
    private void parseFunctionSignature() {
        switchContext(ParserRuleContext.FUNC_SIGNATURE);
        parseOpenParenthesis();
        parseParamList();
        parseCloseParenthesis();
        parseReturnTypeDescriptor();
        this.listner.exitFunctionSignature();
        revertContext();
    }

    /**
     * Parse open parenthesis.
     */
    private void parseOpenParenthesis() {
        Token token = peek();
        if (token.kind == TokenKind.OPEN_PARENTHESIS) {
            this.listner.exitSyntaxNode(consume()); // (
        } else {
            recover(token, ParserRuleContext.OPEN_PARENTHESIS);
        }
    }

    /**
     * Parse close parenthesis.
     */
    private void parseCloseParenthesis() {
        Token token = peek();
        if (token.kind == TokenKind.CLOSE_PARENTHESIS) {
            this.listner.exitSyntaxNode(consume()); // )
        } else {
            recover(token, ParserRuleContext.CLOSE_PARENTHESIS);
        }
    }

    /**
     * <p>
     * Parse parameter list.
     * </p>
     * <code>
     * param-list := required-params [, defaultable-params] [, rest-param]
     *     <br/>&nbsp;| defaultable-params [, rest-param]
     *     <br/>&nbsp;| [rest-param]
     * <br/><br/>
     * required-params := required-param (, required-param)*
     * <br/><br/>
     * required-param := [annots] [public] type-descriptor [param-name]
     * <br/><br/>
     * defaultable-params := defaultable-param (, defaultable-param)*
     * <br/><br/>
     * defaultable-param := [annots] [public] type-descriptor [param-name] default-value
     * <br/><br/>
     * rest-param := [annots] type-descriptor ... [param-name]
     * <br/><br/>
     * param-name := identifier
     * </code>
     */
    private void parseParamList() {
        switchContext(ParserRuleContext.PARAM_LIST);

        Token token = peek();
        if (!isEndOfParametersList(token)) {
            // comma precedes the first parameter, which doesn't exist
            this.listner.addEmptyNode();
            parseParameters();
        }

        this.listner.exitParamList();
        revertContext();
    }

    /**
     * Parse parameters, separated by commas.
     */
    private void parseParameters() {
        parseParameter();

        Token token = peek();
        if (isEndOfParametersList(token)) {
            return;
        }

        parseFollowUpParameter();
    }

    /**
     * Parse a single parameter. Parameter can be a required parameter, a defaultable
     * parameter, or a rest parameter.
     */
    private void parseParameter() {
        switchContext(ParserRuleContext.PARAMETER);
        Token token = peek();

        if (token.kind == TokenKind.PUBLIC) {
            parseModifier();
        }

        parseTypeDescriptor();

        // Rest Parameter
        token = peek();
        if (token.kind == TokenKind.ELLIPSIS) {
            this.listner.exitSyntaxNode(consume());
            parseVariableName();
            this.listner.exitRestParameter();
            revertContext();
            return;
        }

        parseVariableName();

        // Required parameters
        token = peek();
        if (isEndOfParameter(token)) {
            this.listner.exitRequiredParameter();
            revertContext();
            return;
        }

        // Defaultable parameters
        parseAssignOp();
        parseExpression();
        this.listner.exitDefaultableParameter();
        revertContext();
    }

    /**
     * Parse a parameter that follows another parameter.
     */
    private void parseFollowUpParameter() {
        Token token = peek();
        if (token.kind == TokenKind.COMMA) {
            parseComma();
        } else {
            Solution solution = recover(token, ParserRuleContext.FOLLOW_UP_PARAM);

            // If the current rule was recovered by removing a token,
            // then this entire rule is already parsed while recovering.
            // so we done need to parse the remaining of this rule again.
            // Proceed only if the recovery action was an insertion.
            if (solution.action == Action.REMOVE) {
                return;
            }
        }

        parseParameters();
    }

    /**
     * Parse comma.
     */
    private void parseComma() {
        Token token = peek();
        if (token.kind == TokenKind.COMMA) {
            this.listner.exitSyntaxNode(consume()); // parse ","
        } else {
            recover(token, ParserRuleContext.COMMA);
        }
    }

    /**
     * @param token
     * @return
     */
    private boolean isEndOfParameter(Token token) {
        switch (token.kind) {
            case CLOSE_BRACE:
            case CLOSE_PARENTHESIS:
            case CLOSE_BRACKET:
            case SEMICOLON:
            case COMMA:
            case PUBLIC:
            case FUNCTION:
            case EOF:
            case RETURNS:
                return true;
            default:
                return false;
        }
    }

    private boolean isEndOfParametersList(Token token) {
        switch (token.kind) {
            case CLOSE_BRACE:
            case CLOSE_PARENTHESIS:
            case CLOSE_BRACKET:
            case OPEN_BRACE:
            case SEMICOLON:
            case PUBLIC:
            case FUNCTION:
            case EOF:
            case RETURNS:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse return type descriptor of a function. A return type descriptor has the following structure.
     * 
     * <code>return-type-descriptor := [ returns annots type-descriptor ]</code>
     */
    private void parseReturnTypeDescriptor() {
        switchContext(ParserRuleContext.RETURN_TYPE_DESCRIPTOR);

        // If the return type is not present, simply return
        Token token = peek();
        if (token.kind == TokenKind.RETURNS) {
            this.listner.exitSyntaxNode(consume()); // 'returns' keyword
        } else {
            // recover(token, ParserRuleContext.RETURN_TYPE_DESCRIPTOR);
            this.listner.addEmptyNode();
            return;
        }

        parseAnnotations();

        parseTypeDescriptor();

        this.listner.exitReturnTypeDescriptor();
        revertContext();
    }

    /**
     * <p>
     * Parse a type descriptor. A type descriptor has the following structure.
     * </p>
     * <code>type-descriptor :=
     *      &nbsp;simple-type-descriptor<br/>
     *      &nbsp;| structured-type-descriptor<br/>
     *      &nbsp;| behavioral-type-descriptor<br/>
     *      &nbsp;| singleton-type-descriptor<br/>
     *      &nbsp;| union-type-descriptor<br/>
     *      &nbsp;| optional-type-descriptor<br/>
     *      &nbsp;| any-type-descriptor<br/>
     *      &nbsp;| anydata-type-descriptor<br/>
     *      &nbsp;| byte-type-descriptor<br/>
     *      &nbsp;| json-type-descriptor<br/>
     *      &nbsp;| type-descriptor-reference<br/>
     *      &nbsp;| ( type-descriptor )
     * </br>    
     * type-descriptor-reference := qualified-identifier</code>
     */
    private void parseTypeDescriptor() {
        Token token = peek();
        if (token.kind == TokenKind.TYPE) {
            this.listner.exitTypeDescriptor(consume()); // type descriptor
        } else {
            recover(token, ParserRuleContext.TYPE_DESCRIPTOR);
        }

        // TODO: only supports built-in types. Add others.
    }

    /**
     * 
     */
    private void parseAnnotations() {
        // TODO
        this.listner.exitAnnotations();
    }

    /**
     * <p>
     * Parse function body. A function body has the following structure.
     * </p>
     * <code>
     * function-body := function-body-block | external-function-body
     * external-function-body := = annots external ;
     * function-body-block := { [default-worker-init, named-worker-decl+] default-worker }
     * </code>
     */
    private void parseFunctionBody() {
        Token token = peek();
        parseFunctionBody(token.kind);
    }

    /**
     * Parse function body, given the next token kind.
     * 
     * @param tokenKind Next token kind
     */
    private void parseFunctionBody(TokenKind tokenKind) {
        switch (tokenKind) {
            case ASSIGN:
                parseExternalFunctionBody();
                break;
            case OPEN_BRACE:
                parseFunctionBodyBlock();
                break;
            default:
                Token token = peek();
                Solution solution = recover(token, ParserRuleContext.FUNC_BODY);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.INSERT) {
                    parseFunctionBody(solution.tokenKind);
                }
                break;
        }

        this.listner.exitFunctionBody();
    }

    /**
     * <p>
     * Parse function body block. A function body block has the following structure.
     * </p>
     * 
     * <code>
     * function-body-block := { [default-worker-init, named-worker-decl+] default-worker }</br>
     * default-worker-init := sequence-stmt</br>
     * default-worker := sequence-stmt</br>
     * named-worker-decl := worker worker-name return-type-descriptor { sequence-stmt }</br>
     * worker-name := identifier</br>
     * </code>
     */
    private void parseFunctionBodyBlock() {
        switchContext(ParserRuleContext.FUNC_BODY_BLOCK);
        parseOpenBrace();
        parseStatements(); // TODO: allow workers
        parseCloseBrace();
        this.listner.exitFunctionBodyBlock();
        revertContext();
    }

    /**
     * Check whether the given token is an end of a block.
     * 
     * @param token Token to check
     * @return <code>true</code> if the token represents an end of a block. <code>false</code> otherwise
     */
    private boolean isEndOfBlock(TokenKind tokenKind) {
        switch (tokenKind) {
            case CLOSE_BRACE:
            case PUBLIC:
            case FUNCTION:
            case EOF:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse variable name
     */
    private void parseVariableName() {
        Token token = peek();
        if (token.kind == TokenKind.IDENTIFIER) {
            this.listner.exitFunctionName(consume()); // function name
            return;
        } else {
            recover(token, ParserRuleContext.VARIABLE_NAME);
        }
    }

    /**
     * Parse open brace.
     */
    private void parseOpenBrace() {
        Token token = peek();
        if (token.kind == TokenKind.OPEN_BRACE) {
            this.listner.exitSyntaxNode(consume()); // {
        } else {
            recover(token, ParserRuleContext.OPEN_BRACE);
        }
    }

    /**
     * Parse right brace.
     */
    private void parseCloseBrace() {
        Token token = peek();
        if (token.kind == TokenKind.CLOSE_BRACE) {
            this.listner.exitSyntaxNode(consume()); // }
        } else {
            recover(token, ParserRuleContext.CLOSE_BRACE);
        }
    }

    /**
     * <p>
     * Parse external function body. An external function body has the following structure.
     * </p>
     * <code>
     * external-function-body := = annots external ;
     * </code>
     */
    private void parseExternalFunctionBody() {
        switchContext(ParserRuleContext.EXTERNAL_FUNC_BODY);
        parseAssignOp();
        parseAnnotations();
        parseExternalKeyword();
        parseStatementEnd();
        this.listner.exitExternalFunctionBody();
        revertContext();
    }

    /**
     * Parse semicolon.
     */
    private void parseStatementEnd() {
        Token token = peek();
        if (token.kind == TokenKind.SEMICOLON) {
            this.listner.exitSyntaxNode(consume()); // ';'
        } else {
            recover(token, ParserRuleContext.SEMICOLON);
        }
    }

    /**
     * Parse <code>external</code> keyword.
     */
    private void parseExternalKeyword() {
        Token token = peek();
        if (token.kind == TokenKind.EXTERNAL) {
            this.listner.exitSyntaxNode(consume()); // 'external' keyword
        } else {
            recover(token, ParserRuleContext.EXTERNAL_KEYWORD);
        }
    }

    /*
     * Operators
     */

    /**
     * Parse assign operator.
     */
    private void parseAssignOp() {
        Token token = peek();
        if (token.kind == TokenKind.ASSIGN) {
            this.listner.exitOperator(consume()); // =
        } else {
            recover(token, ParserRuleContext.ASSIGN_OP);
        }
    }

    /**
     * Check whether the given token kind is a binary operator.
     * 
     * @param kind Token kind
     * @return <code>true</code> if the token kind refers to a binary operator. <code>false</code> otherwise
     */
    private boolean isBinaryOperator(TokenKind kind) {
        switch (kind) {
            case ADD:
            case SUB:
            case DIV:
            case MUL:
            case GT:
            case LT:
            case EQUAL_GT:
            case EQUAL:
            case REF_EQUAL:
                return true;
            default:
                return false;
        }
    }

    /*
     * Statements
     */

    /**
     * Parse statements, until an end of a block is reached.
     */
    private void parseStatements() {
        // TODO: parse statements/worker declrs
        Token token = peek();
        while (!isEndOfBlock(token.kind)) {
            parseStatement();
            token = peek();
        }
    }

    /**
     * Parse a single statement.
     */
    private void parseStatement() {
        Token token = peek();
        parseStatement(token.kind);
    }

    /**
     * Parse a single statement, given the next token kind.
     * 
     * @param tokenKind Next tokenKind
     */
    private void parseStatement(TokenKind tokenKind) {
        switch (tokenKind) {
            case TYPE:
                // TODO: add other statements that starts with a type
                parseVariableDeclStmt();
                break;
            case IDENTIFIER:
                parseAssignmentStmt();
                break;
            default:
                // If the next token in the token stream does not match to any of the statements and
                // if it is not the end of statement, then try to fix it and continue.
                if (!isEndOfBlock(tokenKind)) {
                    Token token = peek();
                    Solution solution = recover(token, ParserRuleContext.STATEMENT);

                    // If the parser recovered by inserting a token, then try to re-parse the same
                    // rule with the inserted token token. This is done to pick the correct branch
                    // to continue the parsing.
                    if (solution.action == Action.INSERT) {
                        parseStatement(solution.tokenKind);
                    }
                }
                break;
        }
    }

    /**
     * <p>
     * Parse local variable declaration statement. A local variable declaration can take following format.
     * </p>
     * 
     * <code>
     * local-var-decl-stmt := local-init-var-decl-stmt | local-no-init-var-decl-stmt
     * <br/><br/>
     * local-init-var-decl-stmt := [annots] [final] typed-binding-pattern = action-or-expr ;
     * <br/><br/>
     * local-no-init-var-decl-stmt := [annots] [final] type-descriptor variable-name ;
     * </code>
     */
    private void parseVariableDeclStmt() {
        switchContext(ParserRuleContext.VAR_DECL_STMT);
        parseTypeDescriptor();
        parseVariableName();
        parseVarDeclRhs();
        revertContext();
    }

    /**
     * Parse the right hand side of a variable declaration statement.
     */
    private void parseVarDeclRhs() {
        Token token = peek();
        parseVarDeclRhs(token.kind);
    }

    /**
     * Parse the right hand side of a variable declaration statement, given the
     * next token kind.
     * 
     * @param tokenKind Next token kind
     */
    private void parseVarDeclRhs(TokenKind tokenKind) {
        switch (tokenKind) {
            case ASSIGN:
                parseAssignOp();
                parseExpression();
                parseStatementEnd();
                this.listner.exitVarDefStmt(true);
                break;
            case SEMICOLON:
                parseStatementEnd();
                this.listner.exitVarDefStmt(false);
                break;
            default:
                Token token = peek();
                Solution solution = recover(token, ParserRuleContext.VAR_DECL_STMT_RHS);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.INSERT) {
                    parseVarDeclRhs(solution.tokenKind);
                }
                break;
        }
    }

    /**
     * <p>
     * Parse assignment statement, which takes the following format.
     * </p>
     * <code>assignment-stmt := lvexpr = action-or-expr ;</code>
     */
    private void parseAssignmentStmt() {
        switchContext(ParserRuleContext.ASSIGNMENT_STMT);

        parseVariableName();
        parseAssignOp();
        parseExpression();
        parseStatementEnd();
        this.listner.exitAssignmentStmt();

        revertContext();
    }

    /*
     * Expressions
     */

    /**
     * Parse expression. This will start parsing expressions from the lowest level of precedence.
     */
    private void parseExpression() {
        parseExpression(OperatorPrecedence.BINARY_COMPARE);
    }

    /**
     * Parse an expression that has an equal or higher precedence than a given level.
     * 
     * @param precedenceLevel Precedence level of expression to be parsed
     */
    private void parseExpression(OperatorPrecedence precedenceLevel) {
        parseTerminalExpression();
        parseBinaryExprRhs(precedenceLevel);
    }

    /**
     * Parse terminal expressions. A terminal expression has the highest precedence level
     * out of all expressions, and will be at the leaves of an expression tree.
     */
    private void parseTerminalExpression() {
        Token token = peek();
        switch (token.kind) {
            case FLOAT_LITERAL:
            case INT_LITERAL:
            case HEX_LITERAL:
                parseLiteral();
                break;
            case IDENTIFIER:
                parseVariableName();
                break;
            case OPEN_PARENTHESIS:
                parseBracedExpression();
                break;
            default:
                recover(token, ParserRuleContext.EXPRESSION);
                break;
        }
    }

    /**
     * Parse the right-hand-side of a binary expression.
     */
    private void parseBinaryExprRhs() {
        parseBinaryExprRhs(OperatorPrecedence.BINARY_COMPARE);
    }

    /**
     * <p>
     * Parse the right-hand-side of a binary expression.
     * </p>
     * <code>binary-expr-rhs := (binary-op expression)*</code>
     */
    private void parseBinaryExprRhs(OperatorPrecedence precedenceLevel) {
        Token token = peek();
        if (isEndOfExpression(token)) {
            return;
        }

        TokenKind binaryOpKind;
        boolean operatorRecovered = false;
        if (isBinaryOperator(token.kind)) {
            binaryOpKind = token.kind;
        } else {
            Solution solution = recover(token, ParserRuleContext.BINARY_EXPR_RHS);

            // If the current rule was recovered by removing a token,
            // then this entire rule is already parsed while recovering.
            // so we done need to parse the remaining of this rule again.
            // Proceed only if the recovery action was an insertion.
            if (solution.action == Action.REMOVE) {
                return;
            }

            // We come here if the operator is missing. Treat this as injecting an operator
            // that matches to the current operator precedence level, and continue.
            binaryOpKind = getOperatorKindToInsert(precedenceLevel);
            operatorRecovered = true;
        }

        // If the precedence level of the operator that was being parsed is higher than
        // the newly found (next) operator, then return and finish the previous expr,
        // because it has a higher precedence.
        OperatorPrecedence operatorPrecedence = getOpPrecedence(binaryOpKind);
        if (precedenceLevel.isHigherThan(operatorPrecedence)) {
            return;
        }

        // Parse the operator
        if (!operatorRecovered) {
            Token binaryOp = consume();
            this.listner.exitOperator(binaryOp); // operator
        }

        // Parse expression that follows the binary operator, until a operator
        // with different precedence is encountered. If an operator with lower
        // precedence is reached, then come back here and finish the current
        // binary expr. If a an operator with higher precedence level is reached,
        // then complete that binary-expr, come back here and finish the current
        // expr.
        parseExpression(operatorPrecedence);
        this.listner.endBinaryExpression();

        // Then continue the operators with the same precedence level.
        parseBinaryExprRhs(precedenceLevel);
    }

    /**
     * Get the precedence of a given operator.
     * 
     * @param binaryOpKind Operator kind
     * @return Precedence of the given operator
     */
    private OperatorPrecedence getOpPrecedence(TokenKind binaryOpKind) {
        switch (binaryOpKind) {
            case MUL:
            case DIV:
                return OperatorPrecedence.MULTIPLICATIVE;
            case ADD:
            case SUB:
                return OperatorPrecedence.ADDITIVE;
            case GT:
            case LT:
                return OperatorPrecedence.BINARY_COMPARE;
            default:
                throw new UnsupportedOperationException("Unsupported binary operator '" + binaryOpKind + "'");
        }
    }

    /**
     * <p>
     * Get the operator kind to insert during recovery, given the precedence level.
     * </p>
     * 
     * @param opPrecedenceLevel Precedence of the given operator
     * @return
     */
    private TokenKind getOperatorKindToInsert(OperatorPrecedence opPrecedenceLevel) {
        switch (opPrecedenceLevel) {
            case MULTIPLICATIVE:
                return TokenKind.MUL;
            case ADDITIVE:
                return TokenKind.ADD;
            case BINARY_COMPARE:
                return TokenKind.LT;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported operator precedence level'" + opPrecedenceLevel + "'");
        }
    }

    /**
     * <p>
     * Parse braced expression.
     * </p>
     * <code>braced-expr := ( expression )</code>
     */
    private void parseBracedExpression() {
        parseOpenParenthesis();
        parseExpression();
        parseCloseParenthesis();
        this.listner.endBracedExpression();
    }

    /**
     * Check whether the given token is an end of a expression.
     * 
     * @param token Token to check
     * @return <code>true</code> if the token represents an end of a block. <code>false</code> otherwise
     */
    private boolean isEndOfExpression(Token token) {
        switch (token.kind) {
            case CLOSE_BRACE:
            case CLOSE_PARENTHESIS:
            case CLOSE_BRACKET:
            case SEMICOLON:
            case COMMA:
            case PUBLIC:
            case FUNCTION:
            case EOF:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse a literal expression.
     */
    private void parseLiteral() {
        this.listner.exitLiteral(consume()); // literal
    }
}
