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
            case FUNCTION_BODY_BLOCK_START:
                parseFunctionBodyBlockStart();
                break;
            case FUNCTION_BODY_BLOCK_END:
                parseFunctionBodyBlockEnd();
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
            case FUNCTION_SIGNATURE_START:
                parseFunctionSignatureStart();
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
            case EXTERNAL_FUNCTION_BODY_START:
                parseExternalFunctionBodyStart();
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
            case FUNCTION_SIGNATURE_END:
                parseFunctionSignatureEnd();
                break;
            default:
                break;

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

    private boolean recover(Token token, ParserRuleContext currentContext) {
        this.healer.recover(token, currentContext);
        return true;
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
        consume(); // 'function' keyword. This is already verified

        parseFunctionName();
        parseFunctionSignature();
        parseFunctionBody();

        this.listner.exitFunctionDefinition();
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
        parseFunctionSignatureStart();
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
            recover(token, ParserRuleContext.FUNCTION_SIGNATURE_END);
        }
    }

    /**
     * 
     */
    private void parseFunctionSignatureStart() {
        Token token = peek();
        if (token.kind == TokenKind.LEFT_PARANTHESIS) {
            consume(); // (
        } else {
            recover(token, ParserRuleContext.FUNCTION_SIGNATURE_START);
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
        parseFunctionBodyBlockStart();
        parseStatements();
        parseFunctionBodyBlockEnd();
        this.listner.exitFunctionBodyBlock();
    }

    /**
     * 
     */
    private void parseStatements() {
        // TODO: parse statements/worker declrs
        // Token token = this.tokenReader.peek();
        // while (token.kind == TokenKind.NEWLINE) {
        // token = this.tokenReader.consume();
        // }
    }

    /**
     * 
     */
    private void parseFunctionBodyBlockEnd() {
        Token token = peek();
        if (token.kind == TokenKind.RIGHT_BRACE) {
            consume(); // }
        } else {
            recover(token, ParserRuleContext.FUNCTION_BODY_BLOCK_END);
        }
    }

    /**
     * 
     */
    private void parseFunctionBodyBlockStart() {
        Token token = peek();
        if (token.kind == TokenKind.LEFT_BRACE) {
            consume(); // {
        } else {
            recover(token, ParserRuleContext.FUNCTION_BODY_BLOCK_START);
        }
    }

    /**
     * <code>
     * external-function-body := = annots external ;
     * </code>
     */
    private void parseExternalFunctionBody() {
        parseExternalFunctionBodyStart();
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
            consume(); // ';' keyword
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
    private void parseExternalFunctionBodyStart() {
        Token token = peek();
        if (token.kind == TokenKind.ASSIGN) {
            consume(); // =
        } else {
            recover(token, ParserRuleContext.EXTERNAL_FUNCTION_BODY_START);
        }
    }
}
