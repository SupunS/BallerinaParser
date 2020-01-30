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

public enum TokenKind {
    EOF,    // end of file
    SOF,    // start of file
    PUBLIC,
    PRIVATE,
    FUNCTION,
    IDENTIFIER,

    COLON,
    SEMICOLON,
    DOT,
    COMMA,
    LEFT_PARANTHESIS,
    RIGHT_PARANTHESIS,
    LEFT_BRACE,
    RIGHT_BRACE,
    LEFT_BRACKET,
    RIGHT_BRACKET,

    WHITE_SPACE,
    NEWLINE,
    INVALID,

    INT_LITERAL,
    FLOAT_LITERAL,
    HEX_LITERAL,

    // Arithmetic operators
    ASSIGN,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    GT,
    LT,

    EQUAL,
    REF_EQUAL,
    EQUAL_GT,

    TYPE, RETURN, RETURNS, EXTERNAL;
}
