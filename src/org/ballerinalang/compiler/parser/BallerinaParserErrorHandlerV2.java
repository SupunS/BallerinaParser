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

public class BallerinaParserErrorHandlerV2 {

    private final TokenReader tokenReader;
    private final BallerinaParserListener listner;
    private final BallerinaParser parser;

    private ParserRuleContext enclosingContext = ParserRuleContext.COMP_UNIT;

    /**
     * Limit for the distance to travel, to determine a successful lookahead.
     */
    private static final int LOOKAHEAD_LIMIT = 5;

    /**
     * Maximum number of tokens to remove to recover from an invalid syntax.
     */
    private static final int REMOVE_LIMIT = 3;

    public BallerinaParserErrorHandlerV2(TokenReader tokenReader, BallerinaParserListener listner,
            BallerinaParser parser) {
        this.tokenReader = tokenReader;
        this.listner = listner;
        this.parser = parser;
    }

    public void setEnclosingContext(ParserRuleContext context) {
        this.enclosingContext = context;
    }

    /*
     * -------------- Error reporting --------------
     */

    public void reportInvalidToken(Token token) {
        logError(token.line, token.startCol, "invalid token '" + token.text + "'");
    }

    public void reportMissingTokenError(Token token, String message) {
        logError(token.line, token.endCol, message);
    }

    private void logError(int line, int col, String message) {
        System.out.println("xxx.bal:" + line + ":" + col + ":" + message);
    }

    /*
     * -------------- Error recovering --------------
     */

    public void recover(Token nextToken, ParserRuleContext currentContext) {
        // Assumption: always comes here after a peek()

        // Try removing peek(1). This is equivalent to continuing from peek(2) from the current role onwards.
        // If that didn't work out, try removing peek(2) as well, and see how far we can go.
        // If that didn't work out, keep removing until peek(REMOVE_LIMIT) and see how far we can go.

        int peekIndex = 2;
        for (int i = 1; i <= REMOVE_LIMIT; i++) {
            int progress = seekMatch(peekIndex++, currentContext);
            if (progress > 0) {
                // we come here if removing some tokens has caused the parser to progress further.
                for (int j = 0; j < i; j++) {
                    removeInvalidToken(); // remove the invalid tokens
                }

                // re-parse the current rule
                this.parser.parse(currentContext);
                return;
            }
        }

        int progress = seekMatch(1, currentContext);

        reportMissingTokenError("missing " + currentContext);
        this.listner.addMissingNode(currentContext.toString());

        // try inserting expected token at nextRule(1)
        // this is equivalent to continuing from peek(1), from the next rule onwards

        // try inserting expected token at nextRule(2)
    }

    public void removeInvalidToken() {
        Token invalidToken = this.tokenReader.consumeNonTrivia();
        // This means no match is found for the current token.
        // Then consume it and return an error node
        reportInvalidToken(invalidToken);

        // FIXME: add this error node to the tree
        // this.listner.exitErrorNode(nextToken.text);
    }

    private void reportMissingTokenError(String message) {
        Token currentToken = this.tokenReader.head();
        reportMissingTokenError(currentToken, message);
    }

    /*
     * seekMatch methods
     */

    private int seekMatch(int k, ParserRuleContext currentContext) {

        // TODO: We may have to define the limit to the look-ahead (a tolerance level).
        // i.e: When to stop looking further ahead, and return.
        // Because we don't want to keep looking for eternity, whether this extraneous/mismatching
        // token is useful in future. If its not expected for the next x number of rules (or until
        // rule x), we can terminate.

        // TODO: Memoize - if the same token is already validated against the same rule,
        // then return the result of the previous attempt.

        switch (this.enclosingContext) {
            case FUNCTION_DEFINITION:
                return seekMatchInFunction(k, currentContext);
            case STATEMENT:
                return seekMatchInStatement(k, currentContext);
            default:
                return 0;
        }
    }

    private int seekMatchInStatement(int k, ParserRuleContext context) {
        int matchingRulesCount = 0;

        boolean terminate = false;
        while (matchingRulesCount != LOOKAHEAD_LIMIT) {
            Token nextToken = this.tokenReader.peek(k);
            switch (context) {
                case TYPE_DESCRIPTOR:
                    if (nextToken.kind != TokenKind.TYPE) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.VARIABLE_NAME;
                    break;
                case VARIABLE_NAME:
                    if (nextToken.kind != TokenKind.IDENTIFIER) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.ASSIGN_OP;
                    break;
                case ASSIGN_OP:
                    if (nextToken.kind != TokenKind.ASSIGN) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.EXPRESSION;
                    break;
                case EXPRESSION:
                    // FIXME: alternative paths
                    if (!hasMatchInExpression(nextToken)) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.STATEMENT_END;
                    break;
                case STATEMENT_END:
                    // FIXME: alternative paths
                    if (nextToken.kind != TokenKind.SEMICOLON) {
                        terminate = true;
                        break;
                    }
                    // else check whether a block has ended
                    if (!isEndOfBlock(nextToken)) {
                        terminate = true;
                        break;
                    }
                default:
                    terminate = true;
                    break;
            }

            if (terminate) {
                break;
            }

            // Try the next token against the next rule
            matchingRulesCount++;
            k++;
        }

        return matchingRulesCount;
    }

    /**
     * TODO: This is a duplicate method. Same as {@link BallerinaParser#isEndOfBlock}
     * 
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
     * @param nextToken
     * @return
     */
    private boolean hasMatchInExpression(Token nextToken) {
        return nextToken.kind == TokenKind.INT_LITERAL || nextToken.kind == TokenKind.FLOAT_LITERAL ||
                nextToken.kind == TokenKind.HEX_LITERAL;
    }

    private int seekMatchInFunction(int k, ParserRuleContext context) {
        int matchingRulesCount = 0;
        boolean terminate = false;

        while (matchingRulesCount < LOOKAHEAD_LIMIT) {
            Token nextToken = this.tokenReader.peek(k);
            switch (context) {
                case LEFT_PARANTHESIS:
                    if (nextToken.kind != TokenKind.LEFT_PARANTHESIS) {
                        terminate = true;
                        break;
                    }

                    context = ParserRuleContext.PARAMETER_LIST;
                    break;
                case PARAMETER_LIST:
                    // TODO: if match, return the context
                    context = ParserRuleContext.PARAMETER;
                case PARAMETER:
                    // TODO: if match, return the context
                    k--; // stay at the same place
                    context = ParserRuleContext.RIGHT_PARANTHESIS;
                    break;
                case RIGHT_PARANTHESIS:
                    if (nextToken.kind != TokenKind.RIGHT_PARANTHESIS) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.RETURN_TYPE_DESCRIPTOR;
                    break;
                case RETURN_TYPE_DESCRIPTOR:
                    if (nextToken.kind != TokenKind.RETURNS) {
                        context = ParserRuleContext.FUNCTION_BODY;
                        break;
                    }
                    context = ParserRuleContext.TYPE_DESCRIPTOR;
                    break;
                case TYPE_DESCRIPTOR:
                    if (nextToken.kind != TokenKind.TYPE) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.FUNCTION_BODY;
                    break;
                case FUNCTION_BODY:
                    int funcBodyBlock = seekMatch(k, ParserRuleContext.LEFT_BRACE);
                    if (funcBodyBlock == LOOKAHEAD_LIMIT) {
                        terminate = true;
                        break;
                    }

                    int externFunc = seekMatch(k, ParserRuleContext.EXTERNAL_FUNCTION_BODY);
                    if (externFunc == LOOKAHEAD_LIMIT) {
                        terminate = true;
                        break;
                    }

                    matchingRulesCount += Math.max(funcBodyBlock, externFunc);
                    terminate = true;
                    break;
                case FUNCTION_BODY_BLOCK:
                case LEFT_BRACE:
                    if (nextToken.kind != TokenKind.LEFT_BRACE) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.RIGHT_BRACE;
                    break;
                case RIGHT_BRACE:
                    if (nextToken.kind != TokenKind.RIGHT_BRACE) {
                        terminate = true;
                        break;
                    }

                    // Left brace is the end of the function definition. Therefore return.
                    terminate = true;
                    break;
                case COMP_UNIT:
                case ANNOTATION_ATTACHMENT:
                    context = ParserRuleContext.EXTERNAL_FUNCTION_BODY_END;
                    break;
                case EXTERNAL_FUNCTION_BODY:
                case ASSIGN_OP:
                    if (nextToken.kind != TokenKind.ASSIGN) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.ANNOTATION_ATTACHMENT;
                    break;
                case EXTERNAL_FUNCTION_BODY_END:
                    if (nextToken.kind != TokenKind.EXTERNAL) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.STATEMENT_END;
                    break;
                case STATEMENT_END:
                    if (nextToken.kind == TokenKind.SEMICOLON) {
                        terminate = true;
                        break;
                    }

                    // Semicolon is end of the function definition. Therefore return.
                    terminate = true;
                    break;
                case FUNCTION_NAME:
                    if (nextToken.kind != TokenKind.IDENTIFIER) {
                        terminate = true;
                        break;
                    }
                    context = ParserRuleContext.LEFT_PARANTHESIS;
                    break;
                case TOP_LEVEL_NODE:
                case FUNCTION_DEFINITION:
                case FUNCTION_SIGNATURE:
                default:
                    return matchingRulesCount;
            }

            if (terminate) {
                break;
            }

            // Try the next token with the next rule
            matchingRulesCount++;
            k++;
        }

        return matchingRulesCount;
    }
}
