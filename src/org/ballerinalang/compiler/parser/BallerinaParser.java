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

import java.io.InputStream;

public class BallerinaParser {

    private final BallerinaParserErrorHandler errorHandler = new BallerinaParserErrorHandler();
    private final BallerinaParserListener listner = new BallerinaParserListener();
    private final ParserRecover healer;
    private final TokenReader tokenReader;

    private ParserRuleContext parentContext = ParserRuleContext.COMP_UNIT;

    public BallerinaParser(BallerinaLexer lexer) {
        this.tokenReader = new TokenReader(lexer);
        this.healer = new ParserRecover(tokenReader, listner, errorHandler, this);
    }

    public BallerinaParser(InputStream inputStream) {
        this.tokenReader = new TokenReader(new BallerinaLexer(inputStream));
        this.healer = new ParserRecover(tokenReader, listner, errorHandler, this);
    }

    public BallerinaParser(String source) {
        this.tokenReader = new TokenReader(new BallerinaLexer(source));
        this.healer = new ParserRecover(tokenReader, listner, errorHandler, this);
    }

    public void parse() {
        parse(ParserRuleContext.COMP_UNIT);
    }

    public void parse(ParserRuleContext context) {
        switch (context) {
            case COMP_UNIT:
                parseCompUnit();
                break;
            case ANNOTATION_ATTACHMENT:
                break;
            case EXTERNAL_FUNCTION_BODY:
                parseExternalFunctionBody();
                break;
            case FUNCTION_BODY:
                parseFunctionBody();
                break;
            case LEFT_BRACE:
                parseLeftBrace();
                break;
            case RIGHT_BRACE:
                parseRightBrace();
                break;
            case FUNCTION_DEFINITION:
                parseFunctionDefinition();
                break;
            case FUNCTION_NAME:
                parseFunctionName();
                break;
            case FUNCTION_SIGNATURE:
                parseFunctionSignature();
                break;
            case LEFT_PARANTHESIS:
                parseLeftParanthesis();
                break;
            case PARAMETER:
                // TODO
                break;
            case PARAMETER_LIST:
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
            case EXTERNAL_FUNCTION_BODY_END:
                parseExternalFunctionBodyEnd();
                break;
            case FUNCTION_BODY_BLOCK:
                parseFunctionBodyBlock();
                break;
            case STATEMENT_END:
                parseStatementEnd();
                break;
            case RIGHT_PARANTHESIS:
                parseFunctionSignatureEnd();
                break;
            case VARIABLE_NAME:
                parseVariableName();
                break;
            case EXPRESSION:
            case STATEMENT:
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

    private void recover(Token token, ParserRuleContext currentContext) {
        this.healer.recover(token, currentContext);
    }

    /**
     * Parse a given input and returns the AST. Starts parsing from the top of a compilation unit.
     */
    private void parseCompUnit() {
        Token token = peek();
        while (token.kind != TokenKind.EOF) {
            parseTopLevelNode();
            token = peek();
        }

        this.listner.exitCompUnit();
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
                this.healer.removeInvalidToken(token);
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
        ParserRuleContext prevContext = this.parentContext;
        switchMode(ParserRuleContext.FUNCTION_DEFINITION);

        consume(); // 'function' keyword. This is already verified

        parseFunctionName();
        parseFunctionSignature();
        parseFunctionBody();

        this.listner.exitFunctionDefinition();

        switchMode(prevContext);
    }

    private void parseFunctionName() {
        Token token = peek();
        if (token.kind == TokenKind.IDENTIFIER) {
            this.listner.exitFunctionName(consume()); // function name
            return;
        } else {
            recover(token, ParserRuleContext.FUNCTION_NAME);
        }
    }

    /**
     * <code>
     * function-signature := ( param-list ) return-type-descriptor
     * </code>
     */
    private void parseFunctionSignature() {
        parseLeftParanthesis();
        parseParamList();
        parseFunctionSignatureEnd();
        parseReturnTypeDescriptor();
        this.listner.exitFunctionSignature();
    }

    /**
     * 
     */
    private void parseFunctionSignatureEnd() {
        Token token = peek();
        if (token.kind == TokenKind.RIGHT_PARANTHESIS) {
            consume(); // )
        } else {
            recover(token, ParserRuleContext.RIGHT_PARANTHESIS);
        }
    }

    /**
     * 
     */
    private void parseLeftParanthesis() {
        Token token = peek();
        if (token.kind == TokenKind.LEFT_PARANTHESIS) {
            consume(); // (
        } else {
            recover(token, ParserRuleContext.LEFT_PARANTHESIS);
        }
    }

    /**
     * 
     */
    private void parseParamList() {
        boolean b = false;
        int paramCount = 0;
        while (b) {
            this.listner.exitParameter();
            paramCount++;
        }

        this.listner.exitParamList(paramCount);
    }

    /**
     * <code>return-type-descriptor := [ returns annots type-descriptor ]</code>
     */
    private void parseReturnTypeDescriptor() {
        // If the return type is not present, simply return
        Token token = peek();
        if (token.kind == TokenKind.RETURNS) {
            consume(); // 'returns' keyword
        } else {
            recover(token, ParserRuleContext.RETURN_TYPE_DESCRIPTOR);
            return;
        }

        parseAnnotations();

        parseTypeDescriptor();

        this.listner.exitReturnTypeDescriptor();
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
            Token type = consume(); // type descriptor
            this.listner.exitTypeDescriptor(type);
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
            case LEFT_BRACE:
                parseFunctionBodyBlock();
                break;
            default:
                recover(token, ParserRuleContext.FUNCTION_BODY);
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
        parseLeftBrace();
        parseStatements();
        parseRightBrace();
        this.listner.exitFunctionBodyBlock();
    }

    /**
     * 
     */
    private void parseStatements() {
        ParserRuleContext prevContext = this.parentContext;
        switchMode(ParserRuleContext.STATEMENT);

        // TODO: parse statements/worker declrs
        Token token = peek();
        while (!isEndOfBlock(token)) {
            parseStatement();
            token = peek();
        }

        switchMode(prevContext);
    }

    /**
     * @param token
     * @return
     */
    private boolean isEndOfBlock(Token token) {
        switch (token.kind) {
            case RIGHT_BRACE:
            case PUBLIC:
            case FUNCTION:
            case EOF:
                return true;
            default:
                return false;
        }
    }

    /**
     * 
     */
    private void parseStatement() {
        Token token = peek();
        switch (token.kind) {
            case TYPE:
                // TODO: add other statements that starts with a type
                parseVariableDefStmt();
                break;
            default:
                this.healer.removeInvalidToken(token);
                break;
        }
    }

    /**
     * 
     */
    private void parseVariableDefStmt() {
        parseTypeDescriptor();
        parseVariableName();

        Token token = peek();
        if (token.kind != TokenKind.SEMICOLON) {
            parseAssignOp();
            parseExpression();
        }

        parseStatementEnd();
        this.listner.exitVarDefStmt();
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
    private void parseExpression() {
        Token token = peek();
        switch (token.kind) {
            case FLOAT_LITERAL:
            case INT_LITERAL:
            case HEX_LITERAL:
                parseLiteral();
            default:

        }

    }

    /**
     * 
     */
    private void parseLiteral() {
        this.listner.exitLiteral(consume()); // literal
    }

    /**
     * 
     */
    private void parseRightBrace() {
        Token token = peek();
        if (token.kind == TokenKind.RIGHT_BRACE) {
            consume(); // }
        } else {
            recover(token, ParserRuleContext.RIGHT_BRACE);
        }
    }

    /**
     * 
     */
    private void parseLeftBrace() {
        Token token = peek();
        if (token.kind == TokenKind.LEFT_BRACE) {
            consume(); // {
        } else {
            recover(token, ParserRuleContext.LEFT_BRACE);
        }
    }

    /**
     * <code>
     * external-function-body := = annots external ;
     * </code>
     */
    private void parseExternalFunctionBody() {
        parseAssignOp();
        parseAnnotations();
        parseExternalFunctionBodyEnd();
        parseStatementEnd();
        this.listner.exitExternalFunctionBody();
    }

    /**
     * 
     */
    private void parseStatementEnd() {
        Token token = peek();
        if (token.kind == TokenKind.SEMICOLON) {
            consume(); // ';'
        } else {
            recover(token, ParserRuleContext.STATEMENT_END);
        }
    }

    /**
     * 
     */
    private void parseExternalFunctionBodyEnd() {
        Token token = peek();
        if (token.kind == TokenKind.EXTERNAL) {
            consume(); // 'external' keyword
        } else {
            recover(token, ParserRuleContext.EXTERNAL_FUNCTION_BODY_END);
        }
    }

    /**
     * 
     */
    private void parseAssignOp() {
        Token token = peek();
        if (token.kind == TokenKind.ASSIGN) {
            consume(); // =
        } else {
            recover(token, ParserRuleContext.ASSIGN_OP);
        }
    }

    private void switchMode(ParserRuleContext context) {
        this.parentContext = context;
        this.healer.setParentContext(context);
    }
}
