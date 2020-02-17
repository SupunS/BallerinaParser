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
                parseLeftBrace();
                break;
            case CLOSE_BRACE:
                parseRightBrace();
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
            case OPEN_PARANTHESIS:
                parseOpenParanthesis();
                break;
            case PARAMETER:
                // TODO
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
                parseExternalFunctionBodyEnd();
                break;
            case FUNC_BODY_BLOCK:
                parseFunctionBodyBlock();
                break;
            case SEMICOLON:
                parseStatementEnd();
                break;
            case CLOSE_PARANTHESIS:
                parseCloseParanthesis();
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
            case VAR_DEF_STMT:
                parseVariableDefStmt();
                break;
            case ASSIGNMENT_STMT:
                parseAssignmentStmt();
                break;
            case BINARY_EXPR_RHS:
                parseBinaryExprRhs();
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

    private Action recover(Token token, ParserRuleContext currentCtx) {
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
     * <code>
     * function-signature := ( param-list ) return-type-descriptor
     * </code>
     */
    private void parseFunctionSignature() {
        switchContext(ParserRuleContext.FUNC_SIGNATURE);
        parseOpenParanthesis();
        parseParamList();
        parseCloseParanthesis();
        parseReturnTypeDescriptor();
        this.listner.exitFunctionSignature();
        revertContext();
    }

    /**
     * 
     */
    private void parseCloseParanthesis() {
        Token token = peek();
        if (token.kind == TokenKind.CLOSE_PARANTHESIS) {
            this.listner.exitSyntaxNode(consume()); // )
        } else {
            recover(token, ParserRuleContext.CLOSE_PARANTHESIS);
        }
    }

    /**
     * 
     */
    private void parseOpenParanthesis() {
        Token token = peek();
        if (token.kind == TokenKind.OPEN_PARANTHESIS) {
            this.listner.exitSyntaxNode(consume()); // (
        } else {
            recover(token, ParserRuleContext.OPEN_PARANTHESIS);
        }
    }

    /**
     * 
     */
    private void parseParamList() {
        switchContext(ParserRuleContext.PARAM_LIST);
        boolean b = false;
        int paramCount = 0;
        while (b) {
            this.listner.exitParameter();
            paramCount++;
        }

        this.listner.exitParamList(paramCount);
        revertContext();
    }

    /**
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
     * <code>type-descriptor :=
     *      &nbsp;simple-type-descriptor</br>
     *      &nbsp;| structured-type-descriptor</br>
     *      &nbsp;| behavioral-type-descriptor</br>
     *      &nbsp;| singleton-type-descriptor</br>
     *      &nbsp;| union-type-descriptor</br>
     *      &nbsp;| optional-type-descriptor</br>
     *      &nbsp;| any-type-descriptor</br>
     *      &nbsp;| anydata-type-descriptor</br>
     *      &nbsp;| byte-type-descriptor</br>
     *      &nbsp;| json-type-descriptor</br>
     *      &nbsp;| type-descriptor-reference</br>
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
     * <code>
     * function-body := function-body-block | external-function-body
     * external-function-body := = annots external ;
     * function-body-block := { [default-worker-init, named-worker-decl+] default-worker }
     * </code>
     */
    private void parseFunctionBody() {
        Token token = peek();
        switch (token.kind) {
            case ASSIGN:
                parseExternalFunctionBody();
                break;
            case OPEN_BRACE:
                parseFunctionBodyBlock();
                break;
            default:
                recover(token, ParserRuleContext.FUNC_BODY);
                break;
        }

        this.listner.exitFunctionBody();
    }

    /**
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
        parseLeftBrace();
        parseStatements(); // TODO: allow workers
        parseRightBrace();
        this.listner.exitFunctionBodyBlock();
        revertContext();
    }

    /**
     * @param token
     * @return
     */
    private boolean isEndOfBlock(Token token) {
        switch (token.kind) {
            case CLOSE_BRACE:
            case PUBLIC:
            case FUNCTION:
            case EOF:
                return true;
            default:
                return false;
        }
    }

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
     * 
     */
    private void parseRightBrace() {
        Token token = peek();
        if (token.kind == TokenKind.CLOSE_BRACE) {
            this.listner.exitSyntaxNode(consume()); // }
        } else {
            recover(token, ParserRuleContext.CLOSE_BRACE);
        }
    }

    /**
     * 
     */
    private void parseLeftBrace() {
        Token token = peek();
        if (token.kind == TokenKind.OPEN_BRACE) {
            this.listner.exitSyntaxNode(consume()); // {
        } else {
            recover(token, ParserRuleContext.OPEN_BRACE);
        }
    }

    /**
     * <code>
     * external-function-body := = annots external ;
     * </code>
     */
    private void parseExternalFunctionBody() {
        switchContext(ParserRuleContext.EXTERNAL_FUNC_BODY);
        parseAssignOp();
        parseAnnotations();
        parseExternalFunctionBodyEnd();
        parseStatementEnd();
        this.listner.exitExternalFunctionBody();
        revertContext();
    }

    /**
     * 
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
     * 
     */
    private void parseExternalFunctionBodyEnd() {
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

    private void parseAssignOp() {
        Token token = peek();
        if (token.kind == TokenKind.ASSIGN) {
            this.listner.exitOperator(consume()); // =
        } else {
            recover(token, ParserRuleContext.ASSIGN_OP);
        }
    }

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
     * 
     */
    private void parseStatements() {
        // TODO: parse statements/worker declrs
        Token token = peek();
        while (!isEndOfBlock(token)) {
            parseStatement();
            token = peek();
        }
    }

    private void parseStatement() {
        Token token = peek();
        switch (token.kind) {
            case TYPE:
                // TODO: add other statements that starts with a type
                parseVariableDefStmt();
                break;
            case IDENTIFIER:
                parseAssignmentStmt();
                break;
            default:
                // If the next token in the token stream does not match to any of the statements and
                // if it is not the end of statement, then try to fix it and continue.
                if (!isEndOfBlock(token)) {
                    recover(token, ParserRuleContext.STATEMENT);
                }
                break;
        }
    }

    private void parseVariableDefStmt() {
        switchContext(ParserRuleContext.VAR_DEF_STMT);

        parseTypeDescriptor();
        parseVariableName();

        Token token = peek();
        boolean hasExpr = false;
        if (token.kind != TokenKind.SEMICOLON) {
            parseAssignOp();
            parseExpression();
            hasExpr = true;
        }

        parseStatementEnd();
        this.listner.exitVarDefStmt(hasExpr);

        revertContext();
    }

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

    private void parseExpression() {
        // switchContext(ParserRuleContext.EXPRESSION);
        parseExpressionStart();
        parseBinaryExprRhs();
        // revertContext();
    }

    private void parseExpressionStart() {
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
            case OPEN_PARANTHESIS:
                parseBracedExpression();
                break;
            default:
                recover(token, ParserRuleContext.EXPRESSION);
                break;
        }
    }

    private void parseBinaryExprRhs() {
        Token token = peek();
        if (isEndOfExpression(token)) {
            return;
        }

        TokenKind binaryOpKind;
        if (isBinaryOperator(token.kind)) {
            Token binaryOp = consume();
            this.listner.exitOperator(binaryOp); // operator
            binaryOpKind = binaryOp.kind;
        } else {
            Action action = recover(token, ParserRuleContext.BINARY_EXPR_RHS);

            // If the current rule was recovered by removing a token,
            // then this entire rule is already parsed while recovering.
            // so we done need to parse the remaining of this rule again.
            // Proceed only if the recovery action was an insertion.
            if (action == Action.REMOVE) {
                return;
            }

            // We come here if the operator is missing. Hence default it to '+', and continue.
            binaryOpKind = TokenKind.ADD;
        }

        switch (binaryOpKind) {
            case MUL:
            case DIV:
                parseExpressionStart();
                break;
            case ADD:
            case SUB:
                parseExpression();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported binary operator '" + binaryOpKind + "'");
        }

        this.listner.endBinaryExpression();
        parseBinaryExprRhs();
    }

    private void parseBracedExpression() {
        parseOpenParanthesis();
        parseExpression();
        parseCloseParanthesis();
        this.listner.endBracedExpression();
    }

    private boolean isEndOfExpression(Token token) {
        switch (token.kind) {
            case CLOSE_BRACE:
            case CLOSE_PARANTHESIS:
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

    private void parseLiteral() {
        this.listner.exitLiteral(consume()); // literal
    }
}
