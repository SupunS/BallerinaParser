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

public class BallerinaParserV2 {

    private final BallerinaParserErrorHandler errorHandler = new BallerinaParserErrorHandler();
    private final BallerinaParserListener listner = new BallerinaParserListener();
    private ParserRuleContext currentContext = ParserRuleContext.COMP_UNIT;
    private final TokenReader tokenReader;

    public BallerinaParserV2(BallerinaLexer lexer) {
        this.tokenReader = new TokenReader(lexer);
    }

    public BallerinaParserV2(InputStream inputStream) {
        this.tokenReader = new TokenReader(new BallerinaLexer(inputStream));
    }

    public BallerinaParserV2(String source) {
        this.tokenReader = new TokenReader(new BallerinaLexer(source));
    }

    private Token consume() {
        Token token = this.tokenReader.read();
        while (token.kind == TokenKind.WHITE_SPACE || token.kind == TokenKind.NEWLINE) {
            token = this.tokenReader.read();
        }
        return token;
    }

    private Token peek() {
        Token token = this.tokenReader.peek();
        while (token.kind == TokenKind.WHITE_SPACE || token.kind == TokenKind.NEWLINE) {
            this.tokenReader.read();
            token = this.tokenReader.peek();
        }
        return token;
    }

    public void parse() {
        switch (this.currentContext) {
            case COMP_UNIT:
                parseCompUnit();
                break;
            case ANNOTATION_ATTACHMENT:
                parseAnnotationsAttachment();
                break;
            case EXTERNAL_FUNCTION_BODY_START:
                break;
            case FUNCTION_BODY:
                break;
            case FUNCTION_BODY_BLOCK_START:
                break;
            case FUNCTION_DEFINITION:
                break;
            case FUNCTION_NAME:
                parseFunctionName();
                break;
            case FUNCTION_SIGNATURE_START:
                parseFunctionSignature();
                break;
            case PARAMETER:
                break;
            case PARAMETER_LIST:
                parseParamList();
                break;
            case RETURN_TYPE_DESCRIPTOR:
                parseReturnTypeDescriptor();
                break;
            case TYPE_DESCRIPTOR:
                break;
            default:
                break;

        }
    }

    private void continueTo(ParserRuleContext context) {
        this.currentContext = context;
        parse();
    }

    /**
     * Parse a given input and returns the AST. Starts parsing from the top of a compilation unit.
     */
    private void parseCompUnit() {
        while (peek().kind != TokenKind.EOF) {
            parseTopLevelNode();
        }

        this.listner.exitCompUnit();
    }

    private void parseTopLevelNode() {
        parseModifiers();
        Token token = peek();
        switch (token.kind) {
            case FUNCTION:
                parseFunctionDefinition();
                break;
            default:
                this.errorHandler.reportError(token, "Invalid token: " + token.text);
                break;
        }
    }

    /**
     * Parse access modifiers.
     */
    private void parseModifiers() {
        switch (peek().kind) {
            case PUBLIC:
            case PRIVATE:
                consume();
                this.listner.exitModifier();
                break;
            default:
                this.listner.addEmptyNode();
                break;
        }
    }

    /**
     * <code>
     * function-defn := FUNCTION identifier function-signature function-body
     * </code>
     */
    private void parseFunctionDefinition() {
        consume(); // 'function' keyword. This is already verified
        continueTo(ParserRuleContext.FUNCTION_NAME);

        parseFunctionBody();

        this.listner.exitFunctionDefinition();
    }

    private void parseFunctionName() {
        Token token = peek();
        if (token.kind != TokenKind.IDENTIFIER) {
            this.errorHandler.reportError(token, "Invalid token: " + token.text);
            if (!recover(ParserRuleContext.FUNCTION_NAME)) {
                this.listner.exitErrorNode();
                return;
            }
        }

        this.listner.exitFunctionName(consume()); // function name

        continueTo(ParserRuleContext.FUNCTION_SIGNATURE_START);
    }

    /**
     * <code>
     * function-signature := ( param-list ) return-type-descriptor
     * </code>
     */
    private void parseFunctionSignature() {
        Token token = peek();
        if (token.kind != TokenKind.LEFT_PARANTHESIS) {
            this.errorHandler.reportError(token, "missing '('");
            if (!recover(ParserRuleContext.FUNCTION_SIGNATURE_START)) {
                this.listner.exitErrorNode();
                return;
            }
        }

        consume(); // (
        continueTo(ParserRuleContext.PARAMETER_LIST);

        token = peek();
        if (token.kind != TokenKind.RIGHT_PARANTHESIS) {
            this.errorHandler.reportError(token, "missing ')'");
            if (!recover(ParserRuleContext.FUNCTION_SIGNATURE_START)) {
                this.listner.exitErrorNode();
                return;
            }
        }
        consume(); // )

        continueTo(ParserRuleContext.RETURN_TYPE_DESCRIPTOR);

        this.listner.exitFunctionSignature();
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
        Token token = peek();

        // if the return type is not present, simply return
        if (token.kind != TokenKind.RETURNS) {
            this.listner.addEmptyNode();
            return;
        }

        consume(); // 'returns' keyword

        // TODO:
        
        continueTo(ParserRuleContext.ANNOTATION_ATTACHMENT);
        continueTo(ParserRuleContext.TYPE_DESCRIPTOR);
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
        if (token.kind != TokenKind.TYPE) {
            this.errorHandler.reportError(token, "missing the type");
            if (!recover(ParserRuleContext.TYPE_DESCRIPTOR)) {
                this.listner.exitErrorNode();
                return;
            }
        }

        Token type = consume(); // type descriptor

        // TODO: only supports builtin types. Add others

        this.listner.exitTypeDescriptor(type);
    }

    /**
     * 
     */
    private void parseAnnotationsAttachment() {
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
                this.errorHandler.reportError(token, "missing function definition");
                if (!recover(ParserRuleContext.FUNCTION_BODY)) {
                    this.listner.exitErrorNode();
                    return;
                }
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
        consume(); // { Already verified

        // TODO: parse statements/worker declrs

        Token token = peek();
        if (token.kind != TokenKind.RIGHT_BRACE) {
            this.errorHandler.reportError(token, "missing '}'");
            if (recover(ParserRuleContext.FUNCTION_BODY_BLOCK_START)) {
                // if recovered, an error node is added consuming whatever that is wrong.
                // continue from the next context.
            } else {
                // Quite unfortunate!
                // Consume everything and return an error node
                return;
            }
        }

        consume(); // }

        this.listner.exitFunctionBodyBlock();
    }

    /**
     * <code>
     * external-function-body := = annots external ;
     * </code>
     */
    private void parseExternalFunctionBody() {
        consume(); // '=' This is already verified

        parseAnnotationsAttachment();
        Token token = peek();
        if (token.kind != TokenKind.EXTERNAL) {
            this.errorHandler.reportError(token, "missing 'external'");
            if (!recover(ParserRuleContext.EXTERNAL_FUNCTION_BODY_START)) {
                this.listner.exitErrorNode();
                return;
            }
        }

        consume(); // 'external' keyword
        consume(); // ';' keyword

        this.listner.exitExternalFunctionBody();
    }

    private boolean recover(ParserRuleContext currentContext) {
        // TODO
        return true;
    }

    /**
     * Reader that can read tokens from a given lexer.
     * 
     * @since 1.2.0
     */
    private static class TokenReader {

        private BallerinaLexer lexer;
        private Token nextToken;
        private boolean peeked = false;

        TokenReader(BallerinaLexer lexer) {
            this.lexer = lexer;
        }

        /**
         * Consumes the input and return the next token.
         * 
         * @return Next token in the input
         */
        public Token read() {
            if (this.peeked) {
                this.peeked = false;
                return this.nextToken;
            }
            return this.lexer.nextToken();
        }

        /**
         * Lookahead in the input and returns the next token. This will not consume the input.
         * That means calling this method multiple times will return the same result.
         * 
         * @return Next token in the input
         */
        public Token peek() {
            if (!this.peeked) {
                this.nextToken = this.lexer.nextToken();
                this.peeked = true;
            }
            return this.nextToken;
        }
    }
}
