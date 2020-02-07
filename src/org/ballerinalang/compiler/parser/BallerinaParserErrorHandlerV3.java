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

    private ArrayDeque<ActionItem> actionStack = new ArrayDeque<>();

    private ParserRuleContext enclosingContext = ParserRuleContext.COMP_UNIT;

    /**
     * Limit for the distance to travel, to determine a successful lookahead.
     */
    private static final int LOOKAHEAD_LIMIT = 5;

    /**
     * Maximum number of tokens to remove to recover from an invalid syntax.
     */
    private static final int REMOVE_LIMIT = 3;

    public BallerinaParserErrorHandlerV3(TokenReader tokenReader, BallerinaParserListener listner,
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
//        System.out.println("xxx.bal:" + line + ":" + col + ":" + message);
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
            ActionItem actionItem = bestMatch.actionItems.pop();
            if (actionItem.action == Action.REMOVE) {
                removeInvalidToken();
                this.parser.parse(actionItem.ctx);
            } else {
                reportMissingTokenError("missing " + actionItem.ctx);
                this.listner.addMissingNode(actionItem.ctx.toString());
                // this.parser.parse(actionItem.nextCtx);
            }
        } else {
            // fail safe
            // this means we can't find a path to recover
            removeInvalidToken();
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
        if (insertionResult.matches == 0 && deletionResult.matches == 0) {
            return insertionResult;
        }

        if (insertionResult.matches >= deletionResult.matches) { // weigh more for insertion
            // insertToken(currentContext, nextContext[0]);
            insertionResult.actionItems
                    .push(new ActionItem(Action.INSERT, currentContext, nextContext[0], currentContext.toString()));
            return insertionResult;
        } else {
            // removeToken(currentContext, nextContext[0]);
            deletionResult.actionItems
                    .push(new ActionItem(Action.REMOVE, currentContext, nextContext[0], this.tokenReader.peek(k).text));
            return deletionResult;
        }
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

        // TODO: We may have to define the limit to the look-ahead (a tolerance level).
        // i.e: When to stop looking further ahead, and return.
        // Because we don't want to keep looking for eternity, whether this extraneous/mismatching
        // token is useful in future. If its not expected for the next x number of rules (or until
        // rule x), we can terminate.

        // TODO: Memoize - if the same token is already validated against the same rule,
        // then return the result of the previous attempt.

        switch (this.enclosingContext) {
            case FUNCTION_DEFINITION:
                return seekMatchInFunction(k, currentDepth, currentContext);
            case STATEMENT:
                return seekMatchInStatement(k, currentDepth, currentContext);
            default:
                return new Result();
        }
    }

    private Result seekMatchInStatement(int k, ParserRuleContext currentContext) {
        return seekMatchInStatement(k, 0, currentContext);
    }

    private Result seekMatchInStatement(int k, int currentDepth, ParserRuleContext currentContext) {
        ArrayDeque<ActionItem> actionItems = new ArrayDeque<>();
        int matchingRulesCount = 0;

        ParserRuleContext nextContext = currentContext;
        boolean mismatchFound = false;
        while (matchingRulesCount <= LOOKAHEAD_LIMIT) {
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
                    nextContext = ParserRuleContext.STATEMENT_END;
                    if (!hasMatchInExpression(nextToken)) {
                        mismatchFound = true;
                    }
                    break;
                case STATEMENT_END:
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
                for (ActionItem item : fixedPathResult.actionItems) {
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

    private Result seekMatchInFunction(int k, ParserRuleContext context) {
        return seekMatchInFunction(k, 0, context);
    }

    private Result seekMatchInFunction(int k, int currentDepth, ParserRuleContext currentContext) {
        boolean mismatchFound = false;
        ArrayDeque<ActionItem> actionItems = new ArrayDeque<>();
        int matchingRulesCount = 0;

        ParserRuleContext nextContext = currentContext;
        while (currentDepth <= LOOKAHEAD_LIMIT) {
            Token nextToken = this.tokenReader.peek(k);
            if (nextToken.kind == TokenKind.EOF) {
                break;
            }

            currentDepth++;
            switch (nextContext) {
                case FUNCTION_DEFINITION:
                    nextContext = ParserRuleContext.FUNCTION_NAME;
                    if (nextToken.kind != TokenKind.FUNCTION) {
                        mismatchFound = true;
                    }
                    break;
                case FUNCTION_NAME:
                    nextContext = ParserRuleContext.LEFT_PARANTHESIS;
                    if (nextToken.kind != TokenKind.IDENTIFIER) {
                        mismatchFound = true;
                    }
                    break;
                case FUNCTION_SIGNATURE:
                case LEFT_PARANTHESIS:
                    nextContext = ParserRuleContext.RIGHT_PARANTHESIS;
                    if (nextToken.kind != TokenKind.LEFT_PARANTHESIS) {
                        mismatchFound = true;
                    }
                    break;
                case PARAMETER_LIST:
                    // TODO: if match, return the context
                    nextContext = ParserRuleContext.PARAMETER;
                case PARAMETER:
                    // TODO: if match, return the context
                    k--; // stay at the same place
                    matchingRulesCount--;
                    currentDepth--;
                    nextContext = ParserRuleContext.RIGHT_PARANTHESIS;
                    break;
                case RIGHT_PARANTHESIS:
                    nextContext = ParserRuleContext.RETURN_TYPE_DESCRIPTOR;
                    if (nextToken.kind != TokenKind.RIGHT_PARANTHESIS) {
                        mismatchFound = true;
                    }
                    break;
                case RETURN_TYPE_DESCRIPTOR:
                    // TODO: this is optional. handle optional rules
                    if (nextToken.kind != TokenKind.RETURNS) {
                        // if there are no matches in the optional rule, then continue from the
                        // next immediate rule without changing the state

                        k--; // stay at the same place
                        matchingRulesCount--;
                        currentDepth--;
                        nextContext = ParserRuleContext.FUNCTION_BODY;
                    } else {
                        nextContext = ParserRuleContext.TYPE_DESCRIPTOR;
                    }
                    break;
                case TYPE_DESCRIPTOR:
                    nextContext = ParserRuleContext.FUNCTION_BODY;
                    if (nextToken.kind != TokenKind.TYPE) {
                        mismatchFound = true;
                    }
                    break;
                case FUNCTION_BODY:
                    Result funcBodyBlockResult = seekMatchInFunction(k, currentDepth, ParserRuleContext.LEFT_BRACE);
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
                        for (ActionItem item : funcBodyBlockResult.actionItems) {
                            actionItems.add(item); // add() here instead of push() to maintain the same order
                        }
                    } else { // matches to external function
                        matchingRulesCount += externFuncResult.matches + 1;
                        for (ActionItem item : externFuncResult.actionItems) {
                            actionItems.add(item); // add() here instead of push() to maintain the same order
                        }
                    }

                    return new Result(actionItems, matchingRulesCount);
                case FUNCTION_BODY_BLOCK:
                case LEFT_BRACE:
                    nextContext = ParserRuleContext.RIGHT_BRACE;
                    if (nextToken.kind != TokenKind.LEFT_BRACE) {
                        mismatchFound = true;
                        break;
                    }
                    break;
                case RIGHT_BRACE:
                    nextContext = ParserRuleContext.TOP_LEVEL_NODE;
                    if (nextToken.kind != TokenKind.RIGHT_BRACE) {
                        mismatchFound = true;
                    }
                    break;
                case EXTERNAL_FUNCTION_BODY:// shouldn't reach
                case ASSIGN_OP:
                    nextContext = ParserRuleContext.ANNOTATION_ATTACHMENT;
                    if (nextToken.kind != TokenKind.ASSIGN) {
                        mismatchFound = true;
                    }
                    break;
                case ANNOTATION_ATTACHMENT:
                case EXTERNAL_FUNCTION_BODY_END:
                    nextContext = ParserRuleContext.STATEMENT_END;
                    if (nextToken.kind != TokenKind.EXTERNAL) {
                        mismatchFound = true;
                    }
                    break;
                case STATEMENT_END:
                    nextContext = ParserRuleContext.TOP_LEVEL_NODE;
                    if (nextToken.kind != TokenKind.SEMICOLON) {
                        mismatchFound = true;
                    }
                    break;
                case TOP_LEVEL_NODE:
                    nextContext = ParserRuleContext.FUNCTION_DEFINITION;
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
                for (ActionItem item : fixedPathResult.actionItems) {
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
        private ArrayDeque<ActionItem> actionItems;

        public Result(ArrayDeque<ActionItem> actionItem, int matches) {
            this.actionItems = actionItem;
            this.matches = matches;
        }

        public Result(int matches) {
            this.actionItems = new ArrayDeque<>();
            this.matches = matches;
        }

        public Result() {
            this.actionItems = new ArrayDeque<>();
            this.matches = 0;
        }
    }

    private class ActionItem {

        private ParserRuleContext ctx;
        private ParserRuleContext nextCtx;
        private Action action;
        private String token;

        public ActionItem(Action action, ParserRuleContext ctx, ParserRuleContext nextCtx, String token) {
            this.action = action;
            this.ctx = ctx;
            this.nextCtx = nextCtx;
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
