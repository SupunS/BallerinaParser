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
import java.util.HashMap;
import java.util.Map;

public class BallerinaParserErrorHandlerV5 {

    private final TokenReader tokenReader;
    private final BallerinaParserListener listner;
    private final BallerinaParser parser;

    private ArrayDeque<ParserRuleContext> enclosingContext = new ArrayDeque<>();

    private ActionItem lastFix = null;
    private Map<Token, ActionItem> recoveryCache = new HashMap<>();

    /**
     * Limit for the distance to travel, to determine a successful lookahead.
     */
    private static final int LOOKAHEAD_LIMIT = 5;

    public BallerinaParserErrorHandlerV5(TokenReader tokenReader, BallerinaParserListener listner,
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

        ActionItem cachedSol = this.recoveryCache.get(nextToken);
        if (cachedSol != null) {
            applyFix(cachedSol, currentContext);
            return;
        }

        if (nextToken.kind == TokenKind.EOF) {
            reportMissingTokenError("missing " + currentContext);
            this.listner.addMissingNode(currentContext.toString());
            return;
        }

        int bestMatch = seekMatch(currentContext);
        if (bestMatch > 0) {
            applyFix(this.lastFix, currentContext);
        } else {
            // fail safe
            // this means we can't find a path to recover
            removeInvalidToken();
        }
    }

    /**
     * 
     */
    private void applyFix(ActionItem action, ParserRuleContext currentCtx) {
        if (action.action == Action.REMOVE) {
            removeInvalidToken();
            this.parser.parse(currentCtx);
        } else {
            reportMissingTokenError("missing " + currentCtx);
            this.listner.addMissingNode(currentCtx.toString());
            // this.parser.parse(actionItem.nextCtx);
        }
    }

    private int tryToFixAndContinue(int k, int depth, ParserRuleContext currentContext,
                                    ParserRuleContext... nextContext) {
        // insert the missing token. That means continue the CURRENT token, with the NEXT Context
        int insertionResult = 0;
        if (currentContext != nextContext[0]) {
            insertionResult = seekMatch(k, depth, nextContext[0]);
        }

        int match;
        // remove current token. That means continue with the NEXT token, with the CURRENT context
        int deletionResult = seekMatch(k + 1, depth, currentContext);
        if (insertionResult == 0 && deletionResult == 0) {
            this.lastFix = new ActionItem(Action.REMOVE, currentContext, this.tokenReader.peek(k).text);
            match = insertionResult;
        } else if (insertionResult >= deletionResult) { // weigh more for insertion
            this.lastFix = new ActionItem(Action.INSERT, currentContext, currentContext.toString());
            match = insertionResult;
        } else {
            this.lastFix = new ActionItem(Action.REMOVE, currentContext, this.tokenReader.peek(k).text);
            match = deletionResult;
        }

        this.recoveryCache.put(this.tokenReader.peek(k), this.lastFix);
        return match;
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

    private int seekMatch(ParserRuleContext currentContext) {
        // start a fresh seek with the next immediate token (peek(1), and the current context)
        return seekMatch(1, 0, currentContext);
    }

    private int seekMatch(int k, int currentDepth, ParserRuleContext currentContext) {

        // TODO: We may have to define the limit to the look-ahead (a tolerance level).
        // i.e: When to stop looking further ahead, and return.
        // Because we don't want to keep looking for eternity, whether this extraneous/mismatching
        // token is useful in future. If its not expected for the next x number of rules (or until
        // rule x), we can terminate.

        // TODO: Memoize - if the same token is already validated against the same rule,
        // then return the result of the previous attempt.

        switch (this.enclosingContext.peek()) {
            case FUNC_DEFINITION:
                return seekMatchInFunction(k, currentDepth, currentContext);
            case STATEMENT:
                return seekMatchInStatement(k, currentDepth, currentContext);
            default:
                return 0;
        }
    }

    private int seekMatchInStatement(int k, int currentDepth, ParserRuleContext currentContext) {
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
                    return matchingRulesCount;
                default:
                    return matchingRulesCount;
            }

            if (mismatchFound) {
                matchingRulesCount += tryToFixAndContinue(k, currentDepth, currentContext, nextContext);

                // Do not consider the current rule as match, since we had to fix it.
                // i.e: do not increment the match count by 1;
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

    private int seekMatchInFunction(int k, int currentDepth, ParserRuleContext currentContext) {
        boolean mismatchFound = false;
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
                    nextContext = ParserRuleContext.OPEN_PARANTHESIS;
                    if (nextToken.kind != TokenKind.IDENTIFIER) {
                        mismatchFound = true;
                    }
                    break;
                case FUNC_SIGNATURE:
                case OPEN_PARANTHESIS:
                    nextContext = ParserRuleContext.CLOSE_PARANTHESIS;
                    if (nextToken.kind != TokenKind.LEFT_PARANTHESIS) {
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
                    nextContext = ParserRuleContext.CLOSE_PARANTHESIS;
                    break;
                case CLOSE_PARANTHESIS:
                    nextContext = ParserRuleContext.RETURNS;
                    if (nextToken.kind != TokenKind.RIGHT_PARANTHESIS) {
                        mismatchFound = true;
                    }
                    break;
                case RETURNS:
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
                    int funcBodyBlockResult =
                            seekMatchInFunction(k, currentDepth, ParserRuleContext.OPEN_BRACE);
                    int externFuncResult;
                    if (funcBodyBlockResult != LOOKAHEAD_LIMIT) {
                        externFuncResult = seekMatchInFunction(k, currentDepth, ParserRuleContext.ASSIGN_OP);
                    } else {
                        externFuncResult = 0;
                    }

                    if (funcBodyBlockResult == 0 && externFuncResult == 0) {
                        mismatchFound = true;
                        break;
                    }

                    if (funcBodyBlockResult >= externFuncResult) { // matches to function body block
                        matchingRulesCount += funcBodyBlockResult;
                    } else { // matches to external function
                        matchingRulesCount += externFuncResult + 1;
                    }

                    return matchingRulesCount;
                case FUNC_BODY_BLOCK:
                case OPEN_BRACE:
                    nextContext = ParserRuleContext.CLOSE_BRACE;
                    if (nextToken.kind != TokenKind.LEFT_BRACE) {
                        mismatchFound = true;
                        break;
                    }
                    break;
                case CLOSE_BRACE:
                    nextContext = ParserRuleContext.TOP_LEVEL_NODE;
                    if (nextToken.kind != TokenKind.RIGHT_BRACE) {
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
                    return matchingRulesCount;
            }

            if (mismatchFound) {
                matchingRulesCount += tryToFixAndContinue(k, currentDepth, currentContext, nextContext);

                // Do not consider the current rule as match, since we had to fix it.
                // i.e: do not increment the match count by 1;
                break;
            }

            // Try the next token with the next rule
            matchingRulesCount++;
            k++;
        }

        return matchingRulesCount;
    }

    private class ActionItem {

        private ParserRuleContext ctx;
        private Action action;
        private String token;

        public ActionItem(Action action, ParserRuleContext ctx, String token) {
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
