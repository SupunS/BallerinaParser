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

    private static final int BUFFER_SIZE = 100;

    private BallerinaLexer lexer;
    private CircularBuffer tokensAhead = new CircularBuffer(BUFFER_SIZE);
    private Token currentToken = TokenGenerator.SOF;

    TokenReader(BallerinaLexer lexer) {
        this.lexer = lexer;
    }

    /**
     * Consumes the input and return the next token.
     * 
     * @return Next token in the input
     */
    public Token read() {
        if (this.tokensAhead.size > 0) {
            // cache the head
            this.currentToken = tokensAhead.consume();
            return this.currentToken;
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
        if (this.tokensAhead.size == 0) {
            this.tokensAhead.add(this.lexer.nextToken());
        }
        return this.tokensAhead.peek();
    }

    public Token consumeNonTrivia() {
        if (this.tokensAhead.size > 0) {
            this.currentToken = this.tokensAhead.consume();
            return this.currentToken;
        }

        this.currentToken = this.lexer.nextToken();
        while (this.currentToken.kind == TokenKind.WHITE_SPACE || this.currentToken.kind == TokenKind.NEWLINE ||
                this.currentToken.kind == TokenKind.COMMENT) {
            this.currentToken = this.lexer.nextToken();
        }

        return this.currentToken;
    }

    public Token peekNonTrivia() {
        if (this.tokensAhead.size > 0) {
            return this.tokensAhead.peek();
        }

        Token nextToken = this.lexer.nextToken();
        while (nextToken.kind == TokenKind.WHITE_SPACE || nextToken.kind == TokenKind.NEWLINE ||
                nextToken.kind == TokenKind.COMMENT) {
            nextToken = this.lexer.nextToken();
        }

        this.tokensAhead.add(nextToken);
        return nextToken;
    }

    /**
     * Returns the current token. i.e: last consumed token.
     * 
     * @return The current token.
     */
    public Token head() {
        return this.currentToken;
    }

    public Token peek(int k) {
        Token nextToken;
        while (this.tokensAhead.size < k) {
            nextToken = this.lexer.nextToken();
            while (nextToken.kind == TokenKind.WHITE_SPACE || nextToken.kind == TokenKind.NEWLINE ||
                    nextToken.kind == TokenKind.COMMENT) {
                nextToken = this.lexer.nextToken();
            }

            this.tokensAhead.add(nextToken);
        }

        return this.tokensAhead.peek(k);
    }

    private static class CircularBuffer {

        private final int capacity;
        private final Token[] tokens;
        private int endIndex = -1;
        private int startIndex = -1;
        private int size = 0;

        CircularBuffer(int size) {
            this.capacity = size;
            this.tokens = new Token[size];
        }

        public void add(Token token) {
            if (this.size == this.capacity) {
                throw new IndexOutOfBoundsException("buffer overflow");
            }
            
            if (this.endIndex == this.capacity - 1) {
                this.endIndex = 0;
            } else {
                this.endIndex++;
            }

            if (this.size == 0) {
                this.startIndex = this.endIndex;
            }

            this.tokens[this.endIndex] = token;
            this.size++;
        }

        public Token consume() {
            Token token = this.tokens[this.startIndex];
            this.size--;
            if (this.startIndex == this.capacity - 1) {
                this.startIndex = 0;
            } else {
                this.startIndex++;
            }

            return token;
        }

        public Token peek() {
            return this.tokens[this.startIndex];
        }

        public Token peek(int k) {
            if (k > this.size) {
                throw new IndexOutOfBoundsException("size: " + this.size + ", index: " + k);
            }

            int index = this.startIndex + k - 1;
            if (index >= this.capacity) {
                index = index - this.capacity;
            }

            return this.tokens[index];
        }
    }
}
