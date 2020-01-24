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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

public class BallerinaLexer {

    private static final byte EOF = -1;

    private InputReader reader;
    private StringBuilder sb = new StringBuilder(30);
    private BallerinaParserErrorHandler errorHandler = new BallerinaParserErrorHandler();
    private PositionTracer tracer = new PositionTracer();
    private TokenGenerator tokenGenerator = new TokenGenerator(this.tracer);

    public BallerinaLexer(InputStream inputStream) {
        this.reader = new InputReader(inputStream);
    }

    public BallerinaLexer(String source) {
        this.reader = new InputReader(source);
    }

    /**
     * Get the next lexical token.
     * 
     * @return Next lexical token.
     */
    public Token nextToken() {
        Token token = readToken();
        this.tracer.markTokenEnd();
        return token;
    }

    private Token readToken() {
        Token token;
        int startChar = consume();
        switch (startChar) {
            case EOF:
                return TokenGenerator.EOF;

            // Separators
            case LexerTerminals.COLON:
                token = this.tokenGenerator.getColon();
                break;
            case LexerTerminals.SEMICOLON:
                token = this.tokenGenerator.getSemicolon();
                break;
            case LexerTerminals.DOT:
                token = this.tokenGenerator.getDot();
                break;
            case LexerTerminals.COMMA:
                token = this.tokenGenerator.getComma();
                break;
            case LexerTerminals.LEFT_PARANTHESIS:
                token = this.tokenGenerator.getLeftParanthesis();
                break;
            case LexerTerminals.RIGHT_PARANTHESIS:
                token = this.tokenGenerator.getRigthtParanthesis();
                break;
            case LexerTerminals.LEFT_BRACE:
                token = this.tokenGenerator.getLeftBrace();
                break;
            case LexerTerminals.RIGHT_BRACE:
                token = this.tokenGenerator.getRightBrace();
                break;
            case LexerTerminals.LEFT_BRACKET:
                token = this.tokenGenerator.getLeftBracket();
                break;
            case LexerTerminals.RIGHT_BRACKET:
                token = this.tokenGenerator.getRightBracket();
                break;

            // Arithmetic operators
            case LexerTerminals.ASSIGN:
                return processEqualOperator();
            case LexerTerminals.ADD:
                token = this.tokenGenerator.getPlus();
                break;
            case LexerTerminals.SUB:
                token = this.tokenGenerator.getMinus();
                break;
            case LexerTerminals.MUL:
                token = this.tokenGenerator.getMutiplication();
                break;
            case LexerTerminals.DIV:
                token = this.tokenGenerator.getDivision();
                break;
            case LexerTerminals.MOD:
                token = this.tokenGenerator.getModulus();
                break;

            // Numbers
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return processNumericLiteral(startChar);

            // Other
            case 0x9:
            case 0xD:
            case 0x20:
                return processWhiteSpace(startChar);
            case LexerTerminals.NEWLINE:
                token = this.tokenGenerator.getNewline();
                this.tracer.markNewLine();
                break;

            // Identifiers and keywords
            default:
                append(startChar);
                if (isIdentifierInitialChar(startChar)) {
                    return processIdentifierOrKeyword();
                }
                return processInvalidToken();
        }

        return token;
    }

    /**
     * Process any token that starts with '='.
     * 
     * @return One of the tokens: <code>'=', '==', '=>', '==='</code>
     */
    private Token processEqualOperator() {
        switch (peek()) { // check for the second char
            case LexerTerminals.ASSIGN:
                consume();
                if (peek() == LexerTerminals.ASSIGN) {
                    // this is '==='
                    consume();
                    return this.tokenGenerator.REF_EQUAL();
                } else {
                    // this is '=='
                    return this.tokenGenerator.EQUAL();
                }
            case LexerTerminals.GT:
                // this is '==='
                consume();
                return this.tokenGenerator.EQUAL_GT();
            default:
                // this is '=='
                return this.tokenGenerator.ASSIGN();
        }
    }

    /**
     * <p>
     * Process and returns a numeric literal.
     * </p>
     * <code>
     * numeric-literal := int-literal | floating-point-literal
     * </br>
     * int-literal := DecimalNumber | HexIntLiteral
     * </br>
     * DecimalNumber := 0 | NonZeroDigit Digit*
     * </br>
     * Digit := 0 .. 9
     * </br>
     * NonZeroDigit := 1 .. 9
     * </code>
     * 
     * @return The numeric literal.
     */
    private Token processNumericLiteral(int startChar) {
        append(startChar);

        if (isHexIndicator(startChar)) {
            return processHexIntLiteral();
        }

        TokenKind kind = TokenKind.INT_LITERAL;
        int len = 1;
        while (true) {
            switch (peek()) {
                case '.':
                case 'e':
                case 'E':
                    // TODO: handle float
                    Token token = processInvalidToken();
                    this.errorHandler.reportError(token, "I dont't know how to handle floats yet");
                    return token;
                default:
                    if (!isDigit(peek())) {
                        break;
                    }
                    consumeAndAppend();
                    len++;
                    continue;
            }
            break;
        }

        if (startChar == '0' && len > 1 && kind == TokenKind.INT_LITERAL) {
            Token token = processInvalidToken();
            this.errorHandler.reportError(token, "invalid int literal. cannot start with '0'.");
            return token;
        }

        return this.tokenGenerator.getLiteral(getCurrentTokenText(), kind);
    }

    /**
     * <p>
     * Process and returns a hex integer literal.
     * </p>
     * <code>
     * HexIntLiteral := HexIndicator HexNumber
     * </br>
     * HexNumber := HexDigit+
     * </br>
     * HexIndicator := 0x | 0X
     * </br>
     * HexDigit := Digit | a .. f | A .. F
     * </br>
     * </code>
     * 
     * @return
     */
    private Token processHexIntLiteral() {
        consumeAndAppend();
        while (isHexDigit(peek())) {
            consumeAndAppend();
        }
        return this.tokenGenerator.getLiteral(getCurrentTokenText(), TokenKind.HEX_LITERAL);
    }

    /**
     * Process and returns an identifier or a keyword.
     * 
     * @return An identifier or a keyword.
     */
    private Token processIdentifierOrKeyword() {
        while (isIdentifierFollowingChar(peek())) {
            consumeAndAppend();
        }

        String tokenText = getCurrentTokenText();
        switch (tokenText) {
            case LexerTerminals.PUBLIC:
                return this.tokenGenerator.getPublic();
            case LexerTerminals.FUNCTION:
                return this.tokenGenerator.getFunction();
            default:
                return this.tokenGenerator.getIdentifier(tokenText);
        }
    }

    /**
     * Process and returns an invalid token. Consumes the input until {@link #isEndOfInvalidToken()}
     * is reached.
     * 
     * @return The invalid token.
     */
    private Token processInvalidToken() {
        while (!isEndOfInvalidToken()) {
            consumeAndAppend();
        }

        return this.tokenGenerator.getInvalidToken(getCurrentTokenText());
    }

    /**
     * Process whitespace.
     * <code>WhiteSpaceChar := 0x9 | 0xA | 0xD | 0x20</code>
     * 
     * @param startChar Starting character of the whitespace.
     * @return A whitespace {@link Token}
     */
    private Token processWhiteSpace(int startChar) {
        append(startChar);
        while (isWhiteSpace(peek())) {
            consumeAndAppend();
        }

        return this.tokenGenerator.getWhiteSpaces(getCurrentTokenText());
    }

    /**
     * Check whether the current index is pointing to an end of an invalid lexer-token.
     * An invalid token is considered to end if one of the below is reached:
     * <ul>
     * <li>a whitespace</li>
     * <li>semicolon</li>
     * <li>newline</li>
     * </ul>
     * 
     * @return <code>true</code>, if the end of an invalid token is reached, <code>false</code> otherwise
     */
    private boolean isEndOfInvalidToken() {
        int currentChar = peek();
        switch (currentChar) {
            case LexerTerminals.NEWLINE:
            case LexerTerminals.SEMICOLON:
                return true;
            default:
                return isWhiteSpace(currentChar);
        }
    }

    /**
     * <p>
     * Check whether a given char is an identifier start char.
     * </p>
     * <code>IdentifierInitialChar := A .. Z | a .. z | _ | UnicodeIdentifierChar</code>
     * 
     * @param c character to check
     * @return <code>true</code>, if the character is an identifier start char. <code>false</code> otherwise.
     */
    private boolean isIdentifierInitialChar(int c) {
        // TODO: pre-mark all possible characters, using a mask. And use that mask here to check
        if ('A' <= c && c <= 'Z')
            return true;
        if ('a' <= c && c <= 'z')
            return true;
        if (c == '_')
            return true;
        // TODO: if (UnicodeIdentifierChar) return false;
        return false;
    }

    /**
     * <p>
     * Check whether a given char is an identifier following char.
     * </p>
     * <code>IdentifierFollowingChar := IdentifierInitialChar | Digit</code>
     * 
     * @param c character to check
     * @return <code>true</code>, if the character is an identifier following char. <code>false</code> otherwise.
     */
    private boolean isIdentifierFollowingChar(int c) {
        return isIdentifierInitialChar(c) || isDigit(c);
    }

    /**
     * <p>
     * Check whether a given char is a digit.
     * </p>
     * <code>Digit := 0..9</code>
     * 
     * @param c character to check
     * @return <code>true</code>, if the character represents a digit. <code>false</code> otherwise.
     */
    private boolean isDigit(int c) {
        return ('0' <= c && c <= '9');
    }

    /**
     * <p>
     * Check whether a given char is a hexa digit.
     * </p>
     * <code>HexDigit := Digit | a .. f | A .. F</code>
     * 
     * @param c character to check
     * @return <code>true</code>, if the character represents a hex digit. <code>false</code> otherwise.
     */
    private boolean isHexDigit(int c) {
        if ('a' <= c && c <= 'f')
            return true;
        if ('A' <= c && c <= 'f')
            return true;
        return isDigit(c);
    }

    /**
     * <p>
     * Check whether a given char is a whitespace.
     * </p>
     * <code>WhiteSpaceChar := 0x9 | 0xD | 0x20</code>
     * 
     * @param c character to check
     * @return <code>true</code>, if the character represents a whitespace. <code>false</code> otherwise.
     */
    private boolean isWhiteSpace(int c) {
        return c == 0x9 || c == 0xD || c == 0x20;
    }

    /**
     * <p>
     * Check whether current input index points to a start of a hex-numeric literal.
     * </p>
     * <code>HexIndicator := 0x | 0X</code>
     * 
     * @param startChar starting character of the literal
     * @return <code>true</code>, if the current input points to a start of a hex-numeric literal.
     *         <code>false</code> otherwise.
     */
    private boolean isHexIndicator(int startChar) {
        return startChar == '0' && (peek() == 'x') || peek() == 'X';
    }

    /**
     * Consumes and returns the next character from the reader.
     * 
     * @return Next character
     */
    private int consume() {
        try {
            this.tracer.length++;
            return reader.read();
        } catch (IOException e) {
            // FIXME
            e.printStackTrace();
            this.tracer.length--;
            throw new IllegalStateException();
        }
    }

    /**
     * Returns the next character from the reader, without consuming the stream.
     * 
     * @return Next character
     */
    private int peek() {
        try {
            return this.reader.peek();
        } catch (IOException e) {
            // FIXME
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    /**
     * Append a given character to the currently processing token.
     * 
     * @param c Character to append
     */
    private void append(int c) {
        this.sb.append((char) c);
    }

    /**
     * Consume the next character and append it to the currently processing token.
     */
    private void consumeAndAppend() {
        this.sb.append((char) consume());
    }

    /**
     * Get the text associated with the current token.
     * 
     * @return Text associated with the current token.
     */
    private String getCurrentTokenText() {
        String text = this.sb.toString();
        // reset the string builder
        this.sb = new StringBuilder();
        return text;
    }

    /**
     * Reader that can reads from a given input.
     * 
     * @since 1.2.0
     */
    private static class InputReader {

        private Reader reader;
        private int nextChar;
        private boolean peaked = false;

        InputReader(InputStream inputStream) {
            this.reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        }

        InputReader(String source) {
            this.reader = new StringReader(source);
        }

        /**
         * Consumes the input and return the next character.
         * 
         * @return Next character in the input
         * @throws IOException
         */
        public int read() throws IOException {
            if (this.peaked) {
                this.peaked = false;
                return this.nextChar;
            }
            return this.reader.read();
        }

        /**
         * Lookup ahead in the input and returns the next character. This will not consume the input.
         * That means calling this method multiple times will return the same result.
         * 
         * @return Next character in the input
         * @throws IOException
         */
        public int peek() throws IOException {
            if (!this.peaked) {
                this.nextChar = this.reader.read();
                this.peaked = true;
            }
            return this.nextChar;
        }
    }
}
