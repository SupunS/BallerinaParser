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

import java.util.ArrayDeque;

public class BallerinaParserErrorHandlerV3 {

    private final TokenReader tokenReader;
    private final BallerinaParserListener listner;
    private final BallerinaParser parser;

    private ArrayDeque<ParserRuleContext> enclosingContext = new ArrayDeque<>();

    /**
     * Limit for the distance to travel, to determine a successful lookahead.
     */
    private static final int LOOKAHEAD_LIMIT = 5;

    public BallerinaParserErrorHandlerV3(TokenReader tokenReader, BallerinaParserListener listner,
            BallerinaParser parser) {
        this.tokenReader = tokenReader;
        this.listner = listner;
        this.parser = parser;
        this.enclosingContext.push(ParserRuleContext.COMP_UNIT);
    }

    public void setEnclosingContext(ParserRuleContext context) {
        this.enclosingContext.push(context);
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
        if (nextToken.kind == TokenKind.EOF) {
            reportMissingTokenError("missing " + currentContext);
            this.listner.addMissingNode(currentContext.toString());
            return;
        }

        Result bestMatch = seekMatch(currentContext);
        if (bestMatch.matches > 0) {
            Solution actionItem = bestMatch.fixes.pop();
            applyFix(actionItem);
        } else {
            // fail safe
            // this means we can't find a path to recover
            removeInvalidToken();
        }
    }

    /**
     * @param actionItem
     */
    private void applyFix(Solution actionItem) {
        if (actionItem.action == Action.REMOVE) {
            removeInvalidToken();
            this.parser.parse(actionItem.ctx);
        } else {
            reportMissingTokenError("missing " + actionItem.ctx);
            this.listner.addMissingNode(actionItem.ctx.toString());
            // this.parser.parse(actionItem.nextCtx);
        }
    }

    private Result tryToFixAndContinue(int k, int depth, ParserRuleContext currentContext,
                                       ParserRuleContext... nextContext) {
        // insert the missing token. That means continue the CURRENT token, with the NEXT Context
        Result insertionResult;
        if (currentContext != nextContext[0]) {
            insertionResult = seekMatch(k, depth, nextContext[0]);
        } else {
            insertionResult = new Result();
        }

        // remove current token. That means continue with the NEXT token, with the CURRENT context
        Result deletionResult = seekMatch(k + 1, depth, currentContext);
        // Result y = new Result(0);

        Result result;
        Solution action;
        if (insertionResult.matches == 0 && deletionResult.matches == 0) {
            result = insertionResult;
        } else if (insertionResult.matches >= deletionResult.matches) {
            // insertToken(currentContext, nextContext[0]);
            action = new Solution(Action.INSERT, currentContext, currentContext.toString());
            insertionResult.fixes.push(action);
            result = insertionResult;
        } else {
            // removeToken(currentContext, nextContext[0]);
            action = new Solution(Action.REMOVE, currentContext, this.tokenReader.peek(k).text);
            deletionResult.fixes.push(action);
            result = deletionResult;
        }

        return result;
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

    private Result seekMatch(ParserRuleContext currentContext) {
        // start a fresh seek with the next immediate token (peek(1), and the current context)
        return seekMatch(1, 0, currentContext);
    }

    private Result seekMatch(int k, int currentDepth, ParserRuleContext currentContext) {
        switch (this.enclosingContext.peek()) {
            case FUNC_DEFINITION:
                return seekMatchInFunction(k, currentDepth, currentContext);
            case STATEMENT:
                return seekMatchInStatement(k, currentDepth, currentContext);
            default:
                return new Result();
        }
    }

    private Result seekMatchInStatement(int k, int currentDepth, ParserRuleContext currentContext) {
        ArrayDeque<Solution> actionItems = new ArrayDeque<>();
        int matchingRulesCount = 0;

        ParserRuleContext nextContext = currentContext;
        boolean mismatchFound = false;
        while (currentDepth <= LOOKAHEAD_LIMIT) {
            Token nextToken = this.tokenReader.peek(k);
            if (nextToken.kind == TokenKind.EOF) {
                break;
            }

            currentDepth++;
            switch (currentContext) {
                case STATEMENT:
                case TYPE_DESCRIPTOR:
                    nextContext = ParserRuleContext.VARIABLE_NAME;
                    if (nextToken.kind != TokenKind.TYPE) {
                        mismatchFound = true;
                    }
                    break;
                case VARIABLE_NAME:
                    nextContext = ParserRuleContext.ASSIGN_OP;
                    if (nextToken.kind != TokenKind.IDENTIFIER) {
                        mismatchFound = true;
                    }
                    break;
                case ASSIGN_OP:
                    nextContext = ParserRuleContext.EXPRESSION;
                    if (nextToken.kind != TokenKind.ASSIGN) {
                        mismatchFound = true;
                    }
                    break;
                case EXPRESSION:
                    // FIXME: alternative paths
                    nextContext = ParserRuleContext.SEMICOLON;
                    if (!hasMatchInExpression(nextToken)) {
                        mismatchFound = true;
                    }
                    break;
                case SEMICOLON:
                    // FIXME: alternative paths
                    if (nextToken.kind != TokenKind.SEMICOLON) {
                        if (isEndOfBlock(nextToken)) {
                            nextContext = ParserRuleContext.TOP_LEVEL_NODE;
                        } else {
                            nextContext = ParserRuleContext.STATEMENT;
                        }
                        mismatchFound = true;
                        break;
                    }

                    matchingRulesCount++;
                    return new Result(actionItems, matchingRulesCount);
                default:
                    return new Result(matchingRulesCount);
            }

            if (mismatchFound) {
                Result fixedPathResult = tryToFixAndContinue(k, currentDepth, currentContext, nextContext);
                for (Solution item : fixedPathResult.fixes) {
                    actionItems.add(item); // add() here instead of push() to maintain the same order
                }

                matchingRulesCount += fixedPathResult.matches;

                // Do not consider the current rule as match, since we had to fix it.
                // i.e: do not increment the match count by 1;
                // if (fixedPathResult.matches > 0) {
                //// matchingRulesCount++;
                // }
                break;
            }

            // Try the next token against the next rule
            matchingRulesCount++;
            k++;
        }

        return new Result(actionItems, matchingRulesCount);
    }

    /**
     * TODO: This is a duplicate method. Same as {@link BallerinaParser#isEndOfBlock}
     * 
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

    /**
     * @param nextToken
     * @return
     */
    private boolean hasMatchInExpression(Token nextToken) {
        switch (nextToken.kind) {
            case INT_LITERAL:
            case FLOAT_LITERAL:
            case HEX_LITERAL:
            case IDENTIFIER:
                return true;
            default:
                return false;
        }
    }

    private Result seekMatchInFunction(int k, int currentDepth, ParserRuleContext currentContext) {
        boolean mismatchFound = false;
        ArrayDeque<Solution> actionItems = new ArrayDeque<>();
        int matchingRulesCount = 0;

        ParserRuleContext nextContext = currentContext;
        while (currentDepth <= LOOKAHEAD_LIMIT) {
            Token nextToken = this.tokenReader.peek(k);
            if (nextToken.kind == TokenKind.EOF) {
                break;
            }

            currentDepth++;
            switch (nextContext) {
                case FUNC_DEFINITION:
                    nextContext = ParserRuleContext.FUNC_NAME;
                    if (nextToken.kind != TokenKind.FUNCTION) {
                        mismatchFound = true;
                    }
                    break;
                case FUNC_NAME:
                    nextContext = ParserRuleContext.OPEN_PARENTHESIS;
                    if (nextToken.kind != TokenKind.IDENTIFIER) {
                        mismatchFound = true;
                    }
                    break;
                case FUNC_SIGNATURE:
                case OPEN_PARENTHESIS:
                    nextContext = ParserRuleContext.CLOSE_PARENTHESIS;
                    if (nextToken.kind != TokenKind.OPEN_PARENTHESIS) {
                        mismatchFound = true;
                    }
                    break;
                case PARAM_LIST:
                    // TODO: if match, return the context
                    nextContext = ParserRuleContext.PARAMETER;
                case PARAMETER:
                    // TODO: if match, return the context
                    k--; // stay at the same place
                    matchingRulesCount--;
                    currentDepth--;
                    nextContext = ParserRuleContext.CLOSE_PARENTHESIS;
                    break;
                case CLOSE_PARENTHESIS:
                    nextContext = ParserRuleContext.RETURNS_KEYWORD;
                    if (nextToken.kind != TokenKind.CLOSE_PARENTHESIS) {
                        mismatchFound = true;
                    }
                    break;
                case RETURNS_KEYWORD:
                    // TODO: this is optional. handle optional rules
                    if (nextToken.kind != TokenKind.RETURNS) {
                        // if there are no matches in the optional rule, then continue from the
                        // next immediate rule without changing the state

                        k--; // stay at the same place
                        matchingRulesCount--;
                        currentDepth--;
                        nextContext = ParserRuleContext.FUNC_BODY;
                    } else {
                        nextContext = ParserRuleContext.TYPE_DESCRIPTOR;
                    }
                    break;
                case TYPE_DESCRIPTOR:
                    nextContext = ParserRuleContext.FUNC_BODY;
                    if (nextToken.kind != TokenKind.TYPE) {
                        mismatchFound = true;
                    }
                    break;
                case FUNC_BODY:
                    Result funcBodyBlockResult =
                            seekMatchInFunction(k, currentDepth, ParserRuleContext.OPEN_BRACE);
                    Result externFuncResult;
                    if (funcBodyBlockResult.matches != LOOKAHEAD_LIMIT) {
                        externFuncResult = seekMatchInFunction(k, currentDepth, ParserRuleContext.ASSIGN_OP);
                    } else {
                        externFuncResult = new Result(currentDepth);
                    }

                    if (funcBodyBlockResult.matches == 0 && externFuncResult.matches == 0) {
                        mismatchFound = true;
                        break;
                    }

                    if (funcBodyBlockResult.matches >= externFuncResult.matches) { // matches to function body block
                        matchingRulesCount += funcBodyBlockResult.matches;
                        for (Solution item : funcBodyBlockResult.fixes) {
                            actionItems.add(item); // add() here instead of push() to maintain the same order
                        }
                    } else { // matches to external function
                        matchingRulesCount += externFuncResult.matches + 1;
                        for (Solution item : externFuncResult.fixes) {
                            actionItems.add(item); // add() here instead of push() to maintain the same order
                        }
                    }

                    return new Result(actionItems, matchingRulesCount);
                case FUNC_BODY_BLOCK:
                case OPEN_BRACE:
                    nextContext = ParserRuleContext.CLOSE_BRACE;
                    if (nextToken.kind != TokenKind.OPEN_BRACE) {
                        mismatchFound = true;
                        break;
                    }
                    break;
                case CLOSE_BRACE:
                    nextContext = ParserRuleContext.TOP_LEVEL_NODE;
                    if (nextToken.kind != TokenKind.CLOSE_BRACE) {
                        mismatchFound = true;
                    }
                    break;
                case EXTERNAL_FUNC_BODY:// shouldn't reach
                case ASSIGN_OP:
                    nextContext = ParserRuleContext.ANNOTATION_ATTACHMENT;
                    if (nextToken.kind != TokenKind.ASSIGN) {
                        mismatchFound = true;
                    }
                    break;
                case ANNOTATION_ATTACHMENT:
                case EXTERNAL_KEYWORD:
                    nextContext = ParserRuleContext.SEMICOLON;
                    if (nextToken.kind != TokenKind.EXTERNAL) {
                        mismatchFound = true;
                    }
                    break;
                case SEMICOLON:
                    nextContext = ParserRuleContext.TOP_LEVEL_NODE;
                    if (nextToken.kind != TokenKind.SEMICOLON) {
                        mismatchFound = true;
                    }
                    break;
                case TOP_LEVEL_NODE:
                    nextContext = ParserRuleContext.FUNC_DEFINITION;
                    if (nextToken.kind == TokenKind.PUBLIC) {
                        break;
                    }

                    // stay at the same place
                    k--;
                    matchingRulesCount--;
                    currentDepth--;
                    break;
                case COMP_UNIT:
                default:
                    return new Result(matchingRulesCount);
            }

            if (mismatchFound) {
                Result fixedPathResult = tryToFixAndContinue(k, currentDepth, currentContext, nextContext);
                for (Solution item : fixedPathResult.fixes) {
                    actionItems.add(item); // add() here instead of push() to maintain the same order
                }

                matchingRulesCount += fixedPathResult.matches;

                // Do not consider the current rule as match, since we had to fix it.
                // i.e: do not increment the match count by 1;
                break;
            }

            // Try the next token with the next rule
            matchingRulesCount++;
            k++;
        }

        return new Result(actionItems, matchingRulesCount);
    }

    private class Result {

        private int matches;
        private ArrayDeque<Solution> fixes;

        public Result(ArrayDeque<Solution> actionItem, int matches) {
            this.fixes = actionItem;
            this.matches = matches;
        }

        public Result(int matches) {
            this.fixes = new ArrayDeque<>();
            this.matches = matches;
        }

        public Result() {
            this.fixes = new ArrayDeque<>();
            this.matches = 0;
        }
    }

    private class Solution {

        private ParserRuleContext ctx;
        private Action action;
        private String token;

        public Solution(Action action, ParserRuleContext ctx, String token) {
            this.action = action;
            this.ctx = ctx;
            this.token = token;
        }

        @Override
        public String toString() {
            return action.toString() + "'" + token + "'";
        }
    }

    private enum Action {
        INSERT, REMOVE;
    }
}
