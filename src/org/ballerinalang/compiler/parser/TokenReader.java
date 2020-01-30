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
    private boolean peeked = false;

    TokenReader(BallerinaLexer lexer) {
        this.lexer = lexer;
    }

    /**
     * Consumes the input and return the next token.
     * 
     * @return Next token in the input
     */
    public Token readToken() {
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
    public Token peekToken() {
        if (!this.peeked) {
            this.nextToken = this.lexer.nextToken();
            this.peeked = true;
        }
        return this.nextToken;
    }

    public Token consume() {
        Token token = this.readToken();
        while (token.kind == TokenKind.WHITE_SPACE || token.kind == TokenKind.NEWLINE) {
            token = this.readToken();
        }
        return token;
    }

    public Token peek() {
        Token token = this.peekToken();
        while (token.kind == TokenKind.WHITE_SPACE || token.kind == TokenKind.NEWLINE) {
            this.readToken();
            token = this.peekToken();
        }
        return token;
    }
}
