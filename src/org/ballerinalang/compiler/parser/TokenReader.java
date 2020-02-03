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

/**
 * Reader that can read tokens from a given lexer.
 * 
 * @since 1.2.0
 */
public class TokenReader {

    private BallerinaLexer lexer;
    private Token nextToken;
    private Token currentToken = TokenGenerator.SOF;
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

            // cache the head
            this.currentToken = this.nextToken;

            return this.nextToken;
        }

        // cache the head
        this.currentToken = this.lexer.nextToken();

        return this.currentToken;
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

    public Token consumeNonTrivia() {
        if (this.peeked) {
            this.peeked = false;
            this.currentToken = this.nextToken;
        } else {
            this.currentToken = this.lexer.nextToken();
        }

        while (this.currentToken.kind == TokenKind.WHITE_SPACE ||
                this.currentToken.kind == TokenKind.NEWLINE ||
                this.nextToken.kind == TokenKind.COMMENT) {
            this.currentToken = this.lexer.nextToken();
        }

        return this.currentToken;
    }

    public Token peekNonTrivia() {
        if (this.peeked) {
            return this.nextToken;
        }

        this.nextToken = this.lexer.nextToken();
        while (this.nextToken.kind == TokenKind.WHITE_SPACE ||
                this.nextToken.kind == TokenKind.NEWLINE ||
                this.nextToken.kind == TokenKind.COMMENT) {
            this.nextToken = this.lexer.nextToken();
        }

        this.peeked = true;
        return this.nextToken;
    }

    /**
     * Returns the current token. i.e: last consumed token.
     * 
     * @return The current token.
     */
    public Token head() {
        return this.currentToken;
    }
}
