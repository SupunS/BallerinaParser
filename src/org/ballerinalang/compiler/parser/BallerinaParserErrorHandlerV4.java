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
import java.util.Arrays;

public class BallerinaParserErrorHandlerV4 {

    private final TokenReader tokenReader;
    private final BallerinaParserListener listner;
    private final BallerinaParser parser;

    private ArrayDeque<ParserRuleContext> ctxStack = new ArrayDeque<>();

    /**
     * Limit for the distance to travel, to determine a successful lookahead.
     */
    private static final int LOOKAHEAD_LIMIT = 5;

    // private Map<Key, Solution> recoveryCache = new HashMap<>();

    public BallerinaParserErrorHandlerV4(TokenReader tokenReader, BallerinaParserListener listner,
            BallerinaParser parser) {
        this.tokenReader = tokenReader;
        this.listner = listner;
        this.parser = parser;
    }

    public void pushContext(ParserRuleContext context) {
        this.ctxStack.push(context);
    }

    public void popContext() {
        this.ctxStack.pop();
    }

    private ParserRuleContext getParentContext() {
        return this.ctxStack.peek();
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

        // Key key = new Key(nextToken, currentContext);
        // Solution fromCace = this.recoveryCache.get(key);
        // if (fromCace != null) {
        // applyFix(fromCace);
        // return;
        // }

        if (nextToken.kind == TokenKind.EOF) {
            reportMissingTokenError("missing " + currentContext);
            this.listner.addMissingNode(currentContext.toString());
            return;
        }

        Result bestMatch = seekMatch(currentContext);
        if (bestMatch.matches > 0) {

            // Add to cache
            // for (Solution item : bestMatch.fixes) {
            // this.recoveryCache.put(new Key(nextToken, item.ctx), item);
            // }

            Solution fix = bestMatch.fixes.pop();
            applyFix(fix, currentContext);
        } else {
            // fail safe
            // this means we can't find a path to recover
            removeInvalidToken();
        }
    }

    /**
     * @param fix
     * @param currentCtx
     */
    private void applyFix(Solution fix, ParserRuleContext currentCtx) {
        if (fix.action == Action.REMOVE) {
            removeInvalidToken();
            this.parser.parse(currentCtx);
        } else {
            if (isProductionWithAlternatives(currentCtx)) {
                // If the original issues was at the production where there are alternatives,
                // then try to re-parse the matched alternative without reporting errors.
                // Errors will be reported at the next try.

                // TODO: This will cause the parser to recover from the same issue twice.
                // Fix this redundant operation.
                this.parser.parse(fix.enclosingCtx);
            } else {
                reportMissingTokenError("missing " + fix.ctx);
                this.listner.addMissingNode(fix.ctx.toString());
            }
        }
    }

    private ArrayDeque<ParserRuleContext> cloneCtxStack() {
        ArrayDeque<ParserRuleContext> stackCopy = new ArrayDeque<>();
        for (ParserRuleContext ctx : ctxStack) {
            stackCopy.add(ctx);
        }

        return stackCopy;
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

    private boolean isProductionWithAlternatives(ParserRuleContext currentCtx) {
        return currentCtx == ParserRuleContext.FUNC_BODY;
    }

    /*
     * seekMatch methods
     */

    private Result seekMatch(ParserRuleContext currentContext) {
        // start a fresh seek with the next immediate token (peek(1), and the current context)
        return seekMatchInSubTree(1, 0, currentContext);
    }

    private Result seekMatchInSubTree(int lookahead, int currentDepth, ParserRuleContext currentContext) {
        ArrayDeque<ParserRuleContext> tempCtxStack = this.ctxStack;
        this.ctxStack = cloneCtxStack();
        Result result = seekMatch(lookahead, currentDepth, currentContext);
        this.ctxStack = tempCtxStack;
        return result;
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

    private Result seekMatch(int lookahead, int currentDepth, ParserRuleContext currentContext) {
        boolean hasMatch;
        boolean skipRule;
        ArrayDeque<Solution> fixes = new ArrayDeque<>();
        int matchingRulesCount = 0;

        while (currentDepth <= LOOKAHEAD_LIMIT) {
            hasMatch = true;
            skipRule = false;

            Token nextToken = this.tokenReader.peek(lookahead);
            if (nextToken.kind == TokenKind.EOF) {
                break;
            }

            switch (currentContext) {
                case FUNCTION_KEYWORD:
                    hasMatch = nextToken.kind == TokenKind.FUNCTION;
                    break;
                case FUNC_NAME:
                    hasMatch = nextToken.kind == TokenKind.IDENTIFIER;
                    break;
                case OPEN_PARANTHESIS:
                    hasMatch = nextToken.kind == TokenKind.OPEN_PARANTHESIS;
                    break;
                case PARAM_LIST:
                    // TODO: if match, return the context
                case PARAMETER:
                    // TODO: if match, return the context
                    skipRule = true;
                    break;
                case CLOSE_PARANTHESIS:
                    hasMatch = nextToken.kind == TokenKind.CLOSE_PARANTHESIS;
                    break;
                case RETURNS_KEYWORD:
                    hasMatch = nextToken.kind == TokenKind.RETURNS;
                    if (!hasMatch) {
                        // If there are no matches in the optional rule, then continue from the
                        // next immediate rule without changing the state
                        skipRule = true;
                    }
                    break;
                case TYPE_DESCRIPTOR:
                    hasMatch = nextToken.kind == TokenKind.TYPE;
                    break;
                case FUNC_BODY:
                    Result funcBodyBlockResult =
                            seekMatchInSubTree(lookahead, currentDepth, ParserRuleContext.FUNC_BODY_BLOCK);
                    Result externFuncResult =
                            seekMatchInSubTree(lookahead, currentDepth, ParserRuleContext.EXTERNAL_FUNC_BODY);

                    // This means there are no matches for the function body
                    if (funcBodyBlockResult.matches == 0 && externFuncResult.matches == 0) {
                        hasMatch = true;
                        break;
                    }

                    if (funcBodyBlockResult.matches >= externFuncResult.matches) { // matches to function body block
                        matchingRulesCount += funcBodyBlockResult.matches;
                        for (Solution item : funcBodyBlockResult.fixes) {
                            fixes.add(item); // add() here instead of push() to maintain the same order
                        }
                    } else { // matches to external function
                        matchingRulesCount += externFuncResult.matches + 1;
                        for (Solution item : externFuncResult.fixes) {
                            fixes.add(item); // add() here instead of push() to maintain the same order
                        }
                    }

                    return new Result(fixes, matchingRulesCount);
                case OPEN_BRACE:
                    hasMatch = nextToken.kind == TokenKind.OPEN_BRACE;
                    break;
                case CLOSE_BRACE:
                    hasMatch = nextToken.kind == TokenKind.CLOSE_BRACE;
                    break;
                case ASSIGN_OP:
                    hasMatch = nextToken.kind == TokenKind.ASSIGN;
                    break;
                case ANNOTATION_ATTACHMENT:
                case EXTERNAL_KEYWORD:
                    hasMatch = nextToken.kind == TokenKind.EXTERNAL;
                    break;
                case SEMICOLON:
                    hasMatch = nextToken.kind == TokenKind.SEMICOLON;
                    break;
                case TOP_LEVEL_NODE:
                    hasMatch = nextToken.kind == TokenKind.PUBLIC;
                    // skip the optional rule if no match is found
                    if (!hasMatch) {
                        skipRule = true;
                    }
                    break;
                case VARIABLE_NAME:
                    hasMatch = nextToken.kind == TokenKind.IDENTIFIER;
                    break;

                // productions
                case EXPRESSION:
                    hasMatch = hasMatchInExpression(nextToken);
                    break;
                case COMP_UNIT:
                case FUNC_DEFINITION:
                case FUNC_SIGNATURE:
                case RETURN_TYPE_DESCRIPTOR:
                case EXTERNAL_FUNC_BODY:
                case FUNC_BODY_BLOCK:
                case STATEMENT:
                    // stay at the same place
                    skipRule = true;
                    hasMatch = true;
                    break;
                default:
                    return new Result(matchingRulesCount);
            }

            if (!hasMatch) {
                Result fixedPathResult = fixAndContinue(lookahead, currentDepth + 1, currentContext);
                for (Solution item : fixedPathResult.fixes) {
                    fixes.add(item); // add() here instead of push() to maintain the same order
                }

                matchingRulesCount += fixedPathResult.matches;

                // Do not consider the current rule as match, since we had to fix it.
                // i.e: do not increment the match count by 1;
                return new Result(fixes, matchingRulesCount);
            }

            currentContext = getNextRule(currentContext, lookahead + 1);

            if (!skipRule) {
                // Try the next token with the next rule
                currentDepth++;
                matchingRulesCount++;
                lookahead++;
            }

        }

        return new Result(fixes, matchingRulesCount);
    }

    private Result fixAndContinue(int lookahead, int currentDepth, ParserRuleContext currentContext) {
        // NOTE: Below order is important. We have to visit the current context first, before
        // getting and visiting the nextContext. Because getting the next context is a stateful
        // operation, as it could update (push/pop) the current context stack.

        // Remove current token. That means continue with the NEXT token, with the CURRENT context
        Result deletionResult = seekMatchInSubTree(lookahead + 1, currentDepth, currentContext);

        // Insert the missing token. That means continue the CURRENT token, with the NEXT Context
        ParserRuleContext nextContext = getNextRule(currentContext, lookahead);
        Result insertionResult = seekMatchInSubTree(lookahead, currentDepth, nextContext);

        Result fixedPathResult;
        Solution action;
        if (insertionResult.matches == 0 && deletionResult.matches == 0) {
            fixedPathResult = insertionResult;
        } else if (insertionResult.matches >= deletionResult.matches) {
            action = new Solution(Action.INSERT, currentContext, getParentContext(), currentContext.toString());
            insertionResult.fixes.push(action);
            fixedPathResult = insertionResult;
        } else {
            action = new Solution(Action.REMOVE, currentContext, getParentContext(),
                    this.tokenReader.peek(lookahead).text);
            deletionResult.fixes.push(action);
            fixedPathResult = deletionResult;
        }
        return fixedPathResult;
    }

    private ParserRuleContext getNextRule(ParserRuleContext currentContext, int nextLookahead) {
        // If this is a production then push the context to the stack.
        // We can do this within the below switch-case. But doing it separately
        // for the sake of maintainability.
        switch (currentContext) {
            case COMP_UNIT:
            case FUNC_DEFINITION:
            case FUNC_SIGNATURE:
            case RETURN_TYPE_DESCRIPTOR:
            case EXTERNAL_FUNC_BODY:
            case FUNC_BODY_BLOCK:
            case STATEMENT:
                pushContext(currentContext);
            default:
                break;
        }

        ParserRuleContext parentCtx;
        switch (currentContext) {
            case COMP_UNIT:
                return ParserRuleContext.TOP_LEVEL_NODE;
            case FUNC_DEFINITION:
                return ParserRuleContext.FUNCTION_KEYWORD;
            case FUNC_SIGNATURE:
                return ParserRuleContext.OPEN_PARANTHESIS;
            case RETURN_TYPE_DESCRIPTOR:
                return ParserRuleContext.RETURNS_KEYWORD;
            case EXTERNAL_FUNC_BODY:
                return ParserRuleContext.EXTERNAL_KEYWORD;
            case FUNC_BODY_BLOCK:
                return ParserRuleContext.OPEN_BRACE;
            case STATEMENT:
                return ParserRuleContext.TYPE_DESCRIPTOR;
            case ASSIGN_OP:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext.EXTERNAL_FUNC_BODY) {
                    return ParserRuleContext.EXTERNAL_KEYWORD;
                } else if (parentCtx == ParserRuleContext.STATEMENT) {
                    return ParserRuleContext.EXPRESSION;
                } else {
                    throw new IllegalStateException();
                }
            case CLOSE_BRACE:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext.FUNC_BODY_BLOCK) {
                    popContext(); // end func body block
                    return ParserRuleContext.TOP_LEVEL_NODE;
                } else {
                    throw new IllegalStateException();
                }
            case CLOSE_PARANTHESIS:
                popContext(); // end func signature
                return ParserRuleContext.FUNC_BODY;
            case EXPRESSION:
                return ParserRuleContext.SEMICOLON;
            case EXTERNAL_KEYWORD:
                return ParserRuleContext.SEMICOLON;
            case FUNCTION_KEYWORD:
                return ParserRuleContext.FUNC_NAME;
            case FUNC_NAME:
                return ParserRuleContext.FUNC_SIGNATURE;
            case OPEN_BRACE:
                if (isEndOfBlock(this.tokenReader.peek(nextLookahead))) {
                    return ParserRuleContext.CLOSE_BRACE;
                }
                return ParserRuleContext.STATEMENT;
            case OPEN_PARANTHESIS:
                return ParserRuleContext.PARAM_LIST;
            case PARAM_LIST:
                return ParserRuleContext.PARAMETER;
            case RETURNS_KEYWORD:
                if (this.tokenReader.peek(nextLookahead).kind != TokenKind.RETURNS) {
                    // If there are no matches in the optional rule, then continue from the
                    // next immediate rule without changing the state
                    return ParserRuleContext.FUNC_BODY;
                }
                return ParserRuleContext.TYPE_DESCRIPTOR;
            case SEMICOLON:
                parentCtx = getParentContext();
                switch (parentCtx) {
                    case EXTERNAL_FUNC_BODY:
                        popContext(); // end external func
                        return ParserRuleContext.TOP_LEVEL_NODE;
                    case EXPRESSION:
                        popContext(); // end expression
                        // A semicolon after an expression also means its an end 
                        // of a statement, Hence fall through.
                    case STATEMENT:
                        popContext(); // end statement
                        if (isEndOfBlock(this.tokenReader.peek(nextLookahead))) {
                            return ParserRuleContext.CLOSE_BRACE;
                        }
                        return ParserRuleContext.STATEMENT;
                    default:
                        throw new IllegalStateException();
                }
            case TYPE_DESCRIPTOR:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext.STATEMENT) {
                    return ParserRuleContext.VARIABLE_NAME;
                } else if (parentCtx == ParserRuleContext.RETURN_TYPE_DESCRIPTOR) {
                    return ParserRuleContext.FUNC_BODY;
                } else {
                    throw new IllegalStateException();
                }
            case VARIABLE_NAME:
                return ParserRuleContext.ASSIGN_OP;
            case TOP_LEVEL_NODE:
                return ParserRuleContext.FUNC_DEFINITION;
            case FUNC_BODY:
                return ParserRuleContext.TOP_LEVEL_NODE;
            case PARAMETER:
                return ParserRuleContext.CLOSE_PARANTHESIS;
            case ANNOTATION_ATTACHMENT:
            default:
                throw new IllegalStateException();
        }
    }

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
        private ParserRuleContext enclosingCtx;
        private Action action;
        private String token;

        public Solution(Action action, ParserRuleContext ctx, ParserRuleContext enclosingCtx, String token) {
            this.action = action;
            this.ctx = ctx;
            this.token = token;
            this.enclosingCtx = enclosingCtx;
        }

        @Override
        public String toString() {
            return action.toString() + "'" + token + "'";
        }
    }

    private class Key {
        Token token;
        ParserRuleContext ctx;
        int hash;

        Key(Token token, ParserRuleContext ctx) {
            this.token = token;
            this.ctx = ctx;
            this.hash = Arrays.deepHashCode(new Object[] { this.token, this.ctx });
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (!(obj instanceof Key)) {
                return false;
            }
            return ((Key) obj).ctx.equals(ctx) && ((Key) obj).token.equals(token);
        }
    }

    private enum Action {
        INSERT, REMOVE;
    }
}
