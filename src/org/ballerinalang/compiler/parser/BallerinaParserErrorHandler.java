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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Responsible for recovering from a parser error.
 * 
 * When an unexpected token is reached, error handler will try inserting/removing a token from the current head, and see
 * how far the parser can successfully progress. After fixing the current head and trying to progress, if it encounters
 * more issues, then it will try to fix those as well. All possible combinations of insertions and deletions will be
 * tried out for such errors. Once all possible paths are discovered, pick the optimal combination that leads to the
 * best recovery. Finally, apply the best solution and continue the parsing.
 * <br/>
 * e.g.: If the best combination of fixes was <code>[insert, insert, remove, remove]</code>, then apply only the first
 * fix and continue.
 * <ul>
 * <li>If the fix was a ‘remove’ - then consume the token stream once, and continue from the same rule again.
 * </li>
 * <li>
 * If the fix was an ‘insert’ - then insert the missing node, and continue from the next rule, without consuming the
 * token stream.
 * </li>
 * </ul>
 * </p>
 * 
 * @since 1.2.0
 */
public class BallerinaParserErrorHandler {

    private final TokenReader tokenReader;
    private final BallerinaParserListener listner;
    private final BallerinaParserErrorListener errorListener;
    private final BallerinaParser parser;

    private ArrayDeque<ParserRuleContext> ctxStack = new ArrayDeque<>();

    private static final ParserRuleContext[] FUNC_BODIES =
            { ParserRuleContext.FUNC_BODY_BLOCK, ParserRuleContext.EXTERNAL_FUNC_BODY };

    private static final ParserRuleContext[] STATEMENTS =
            { ParserRuleContext.ASSIGNMENT_STMT, ParserRuleContext.VAR_DEF_STMT };

    /**
     * Limit for the distance to travel, to determine a successful lookahead.
     */
    private static final int LOOKAHEAD_LIMIT = 5;

    // private Map<Key, Solution> recoveryCache = new HashMap<>();

    public BallerinaParserErrorHandler(TokenReader tokenReader, BallerinaParserListener listner,
            BallerinaParser parser) {
        this.tokenReader = tokenReader;
        this.listner = listner;
        this.parser = parser;
        this.errorListener = new BallerinaParserErrorListener();
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
     * -------------- Error recovering --------------
     */

    /**
     * Recover from current context. Returns the action needs to be taken with respect
     * to the next token, in order to recover. This method will search for the most
     * optimal action, that will result the parser to proceed the farthest distance.
     * 
     * @param nextToken Next token of the input where the error occurred
     * @param currentCtx Current parser context
     * @return The action needs to be taken for the next token, in order to recover
     */
    public Action recover(ParserRuleContext currentCtx, Token nextToken) {
        // Assumption: always comes here after a peek()

        // Key key = new Key(nextToken, currentContext);
        // Solution fromCace = this.recoveryCache.get(key);
        // if (fromCace != null) {
        // applyFix(fromCace);
        // return;
        // }

        if (nextToken.kind == TokenKind.EOF) {
            reportMissingTokenError("missing " + currentCtx);
            this.listner.addMissingNode(currentCtx.toString());
            return Action.INSERT;
        }

        Result bestMatch = seekMatch(currentCtx);
        if (bestMatch.matches > 0) {

            // Add to cache
            // for (Solution item : bestMatch.fixes) {
            // this.recoveryCache.put(new Key(nextToken, item.ctx), item);
            // }

            Solution fix = bestMatch.fixes.pop();
            applyFix(currentCtx, fix);
            return fix.action;
        } else {
            // fail safe
            // this means we can't find a path to recover
            removeInvalidToken();
            return Action.REMOVE;
        }

    }

    /**
     * Remove the invalid token. This method assumes that the next immediate token
     * of the token input stream is the culprit.
     */
    public void removeInvalidToken() {
        Token invalidToken = this.tokenReader.consumeNonTrivia();
        // This means no match is found for the current token.
        // Then consume it and return an error node
        this.errorListener.reportInvalidToken(invalidToken);

        // FIXME: add this error node to the tree
        // this.listner.exitErrorNode(nextToken.text);
    }

    /**
     * Apply the fix to the current context.
     * 
     * @param currentCtx Current context
     * @param fix Fix to apply
     */
    private void applyFix(ParserRuleContext currentCtx, Solution fix) {
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

    /**
     * Get a snapshot of the current context stack.
     * 
     * @return Snapshot of the current context stack
     */
    private ArrayDeque<ParserRuleContext> getCtxStackSnapshot() {
        ArrayDeque<ParserRuleContext> stackCopy = new ArrayDeque<>();
        for (ParserRuleContext ctx : ctxStack) {
            stackCopy.add(ctx);
        }

        return stackCopy;
    }

    private void reportMissingTokenError(String message) {
        Token currentToken = this.tokenReader.head();
        this.errorListener.reportMissingTokenError(currentToken, message);
    }

    private boolean isProductionWithAlternatives(ParserRuleContext currentCtx) {
        switch (currentCtx) {
            case STATEMENT:
            case FUNC_BODY:
                return true;
            default:
                return false;
        }
    }

    /*
     * seekMatch methods
     */

    /**
     * Start a fresh search for a way to recover with the next immediate token (peek(1), and the current context).
     * 
     * @param currentCtx Current parser context
     * @return Recovery result
     */
    private Result seekMatch(ParserRuleContext currentCtx) {
        return seekMatchInSubTree(currentCtx, 1, 0);
    }

    /**
     * Search for a solution in a sub-tree/sub-path. This will take a snapshot of the current context stack
     * and will operate on top of it, so that the original state of the parser will not be disturbed. On return
     * the previous state of the parser contexts will be restored.
     * 
     * @param currentCtx Current context
     * @param lookahead Position of the next token to consider, from the position of the original error.
     * @param currentDepth Amount of distance traveled so far.
     * @return Recovery result
     */
    private Result seekMatchInSubTree(ParserRuleContext currentCtx, int lookahead, int currentDepth) {
        ArrayDeque<ParserRuleContext> tempCtxStack = this.ctxStack;
        this.ctxStack = getCtxStackSnapshot();
        Result result = seekMatch(currentCtx, lookahead, currentDepth);
        this.ctxStack = tempCtxStack;
        return result;
    }

    /**
     * TODO: This is a duplicate method. Same as {@link BallerinaParser#isEndOfBlock}.
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
     * TODO: This is a duplicate method. Same as {@link BallerinaParser#isEndOfBlock}.
     * 
     * @param token
     * @return
     */
    private boolean isEndOfExpression(Token token) {
        switch (token.kind) {
            case CLOSE_BRACE:
            case CLOSE_PARANTHESIS:
            case CLOSE_BRACKET:
            case PUBLIC:
            case FUNCTION:
            case EOF:
            case SEMICOLON:
            case COMMA:
                return true;
            default:
                return false;
        }
    }

    /**
     * Search for a solution.
     * 
     * @param currentCtx Current context
     * @param lookahead Position of the next token to consider, relative to the position of the original error.
     * @param currentDepth Amount of distance traveled so far.
     * @return Recovery result
     */
    private Result seekMatch(ParserRuleContext currentCtx, int lookahead, int currentDepth) {
        boolean hasMatch;
        boolean skipRule;
        ArrayDeque<Solution> fixes = new ArrayDeque<>();
        int matchingRulesCount = 0;

        while (currentDepth < LOOKAHEAD_LIMIT) {
            hasMatch = true;
            skipRule = false;

            Token nextToken = this.tokenReader.peek(lookahead);
            if (nextToken.kind == TokenKind.EOF) {
                break;
            }

            switch (currentCtx) {
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
                    // TODO: handle parameters list
                case PARAMETER:
                    // TODO: handle parameters
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
                    return seekInFuncBodies(lookahead, currentDepth, matchingRulesCount, fixes);
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
                    // Skip the optional rule if no match is found
                    if (!hasMatch) {
                        skipRule = true;
                    }
                    break;
                case VARIABLE_NAME:
                    hasMatch = nextToken.kind == TokenKind.IDENTIFIER;
                    break;
                case STATEMENT:
                    if (isEndOfBlock(nextToken)) {
                        // If we reach end of statements, then skip processing statements anymore,
                        // and move on to the next rule. This is done to avoid getting stuck on
                        // processing statements forever.
                        skipRule = true;
                        break;
                    }
                    return seekInStatements(currentCtx, nextToken, lookahead, currentDepth, matchingRulesCount, fixes);
                case BINARY_OPERATOR:
                    hasMatch = isBinaryOperator(nextToken);
                    break;
                case EXPRESSION:
                    return seekInExpression(currentCtx, lookahead, currentDepth, matchingRulesCount, fixes);

                // productions
                case BINARY_EXPR_RHS:
                case COMP_UNIT:
                case FUNC_DEFINITION:
                case FUNC_SIGNATURE:
                case RETURN_TYPE_DESCRIPTOR:
                case EXTERNAL_FUNC_BODY:
                case FUNC_BODY_BLOCK:
                case ASSIGNMENT_STMT:
                case VAR_DEF_STMT:
                default:
                    // Stay at the same place
                    skipRule = true;
                    hasMatch = true;
                    break;
            }

            if (!hasMatch) {
                Result fixedPathResult = fixAndContinue(currentCtx, lookahead, currentDepth + 1);
                // Do not consider the current rule as match, since we had to fix it.
                // i.e: do not increment the match count by 1;
                return getFinalResult(matchingRulesCount, fixes, fixedPathResult);
            }

            currentCtx = getNextRule(currentCtx, lookahead + 1);
            if (!skipRule) {
                // Try the next token with the next rule
                currentDepth++;
                matchingRulesCount++;
                lookahead++;
            }

        }

        return new Result(fixes, matchingRulesCount);
    }

    /**
     * Search for matching token sequences within the function body signatures and returns the most optimal solution.
     * This will check whether the token stream best matches to a 'function-body-block' or a 'external-function-body'.
     * 
     * @param lookahead Position of the next token to consider, relative to the position of the original error
     * @param currentDepth Amount of distance traveled so far
     * @param currentMatches Matching tokens found so far
     * @param fixes Fixes made so far
     * @return Recovery result
     */
    private Result seekInFuncBodies(int lookahead, int currentDepth, int currentMatches, ArrayDeque<Solution> fixes) {
        return seekInAlternativesPaths(lookahead, currentDepth, currentMatches, fixes, FUNC_BODIES);
    }

    /**
     * Search for matching token sequences within different kinds of statements and returns the most optimal solution.
     * 
     * @param currentCtx Current context
     * @param nextToken Next token in the token stream
     * @param lookahead Position of the next token to consider, relative to the position of the original error
     * @param currentDepth Amount of distance traveled so far
     * @param currentMatches Matching tokens found so far
     * @param fixes Fixes made so far
     * @return Recovery result
     */
    private Result seekInStatements(ParserRuleContext currentCtx, Token nextToken, int lookahead, int currentDepth,
                                    int currentMatches, ArrayDeque<Solution> fixes) {
        if (nextToken.kind == TokenKind.SEMICOLON) {
            // Semicolon at the start of a statement is a special case. This is equivalent to an empty
            // statement. So assume the fix for this is a REMOVE operation and continue from the next token.
            Result result = seekMatchInSubTree(ParserRuleContext.STATEMENT, lookahead + 1, currentDepth);
            fixes.add(new Solution(Action.REMOVE, currentCtx, getParentContext(), nextToken.toString()));
            fixes.addAll(result.fixes);
            currentMatches += result.matches;
            return new Result(fixes, currentMatches);
        }

        return seekInAlternativesPaths(lookahead, currentDepth, currentMatches, fixes, STATEMENTS);
    }

    /**
     * Search for matching token sequences within expressions and returns the most optimal solution.
     * 
     * @param currentCtx Current context
     * @param lookahead Position of the next token to consider, relative to the position of the original error
     * @param currentDepth Amount of distance traveled so far
     * @param currentMatches Matching tokens found so far
     * @param fixes Fixes made so far
     * @return Recovery result
     */
    private Result seekInExpression(ParserRuleContext currentCtx, int lookahead, int currentDepth, int currentMatches,
                                    ArrayDeque<Solution> fixes) {
        Token nextToken = this.tokenReader.peek(lookahead);
        boolean hasMatch = false;
        switch (nextToken.kind) {
            case INT_LITERAL:
            case HEX_LITERAL:
            case FLOAT_LITERAL:
            case IDENTIFIER:
                hasMatch = true;
                break;
            default:
                break;
        }

        currentDepth++;
        if (!hasMatch) {
            Result fixedPathResult = fixAndContinue(currentCtx, lookahead, currentDepth);
            return getFinalResult(currentMatches, fixes, fixedPathResult);
        } else {
            lookahead++;
            currentMatches++;
            nextToken = this.tokenReader.peek(lookahead);
            ParserRuleContext nextContext;
            if (isEndOfExpression(nextToken)) {
                // Here we assume the end of an expression is always a semicolon
                // TODO: add other types of expression-end
                nextContext = ParserRuleContext.SEMICOLON;
            } else {
                nextContext = ParserRuleContext.BINARY_EXPR_RHS;
            }
            Result result = seekMatch(nextContext, lookahead, currentDepth);
            return getFinalResult(currentMatches, fixes, result);
        }
    }

    /**
     * Search for matching token sequences within the given alternative paths, and find the most optimal solution.
     * 
     * @param lookahead Position of the next token to consider, relative to the position of the original error
     * @param currentDepth Amount of distance traveled so far
     * @param currentMatches Matching tokens found so far
     * @param fixes Fixes made so far
     * @return Recovery result
     */
    private Result seekInAlternativesPaths(int lookahead, int currentDepth, int currentMatches,
                                           ArrayDeque<Solution> fixes, ParserRuleContext[] alternativeRules) {

        @SuppressWarnings("unchecked")
        List<Result>[] results = new List[LOOKAHEAD_LIMIT];
        int bestMatchIndex = 0;

        // Visit all the alternative rules and get their results. Arrange them in way
        // such that results with the same number of matches are put together. This is
        // done so that we can easily pick the best, without iterating through them.
        for (ParserRuleContext rule : alternativeRules) {
            Result result = seekMatchInSubTree(rule, lookahead, currentDepth);
            List<Result> similarResutls = results[result.matches];
            if (similarResutls == null) {
                similarResutls = new ArrayList<>(LOOKAHEAD_LIMIT);
                results[result.matches] = similarResutls;
                if (bestMatchIndex < result.matches) {
                    bestMatchIndex = result.matches;
                }
            }
            similarResutls.add(result);
        }

        // This means there are no matches for any of the statements
        if (bestMatchIndex == 0) {
            return new Result(fixes, currentMatches);
        }

        // If there is only one 'best' match,
        List<Result> bestMatches = results[bestMatchIndex];
        Result bestMatch = bestMatches.get(0);
        if (bestMatches.size() == 1) {
            return getFinalResult(currentMatches, fixes, bestMatch);
        }

        // If there are more than one 'best' match, then we need to do a tie-break.
        // For that, pick the path with the lowest number of fixes.
        // If it again results in more than one match, then return the based on the
        // precedence (order of occurrence).
        for (Result match : bestMatches) {
            if (match.fixes.size() < bestMatch.fixes.size()) {
                bestMatch = match;
            }
        }

        return getFinalResult(currentMatches, fixes, bestMatch);
    }

    /**
     * Combine a given result with the current results, and get the final result.
     * 
     * @param currentMatches Matches found so far
     * @param fixes FIxes found so far
     * @param bestMatch Result found in the sub-tree, that requires to be merged with the current results
     * @return Final result
     */
    private Result getFinalResult(int currentMatches, ArrayDeque<Solution> fixes, Result bestMatch) {
        currentMatches += bestMatch.matches;
        fixes.addAll(bestMatch.fixes);
        return new Result(fixes, currentMatches);
    }

    /**
     * <p>
     * Fix the error at the current position and continue forward to find the best path. This method
     * tries to fix the parser error using following steps:
     * <ol>
     * <li>
     * Insert a token and see how far the parser can proceed.
     * </li>
     * <li>
     * Delete a token and see how far the parser can proceed.
     * </li>
     * </ol>
     * 
     * Then decides the best action to perform (whether to insert or remove a token), using the result
     * of the above two steps, based on the following criteria:
     * <ol>
     * <li>
     * Pick the solution with the longest matching sequence.
     * </li>
     * <li>
     * If there's a tie, then check for the solution which requires lowest number of 'fixes'.
     * </li>
     * </li>
     * <li>
     * If there's a tie, then give priority for the 'insertion' as that doesn't require to remove
     * an input a user has given.
     * </li>
     * </ol>
     * </p>
     * 
     * @param currentCtx Current parser context
     * @param lookahead Position of the next token to consider, relative to the position of the original error
     * @param currentDepth Amount of distance traveled so far
     * @return Recovery result
     */
    private Result fixAndContinue(ParserRuleContext currentCtx, int lookahead, int currentDepth) {
        // NOTE: Below order is important. We have to visit the current context first, before
        // getting and visiting the nextContext. Because getting the next context is a stateful
        // operation, as it could update (push/pop) the current context stack.

        // Remove current token. That means continue with the NEXT token, with the CURRENT context
        Result deletionResult = seekMatchInSubTree(currentCtx, lookahead + 1, currentDepth);

        // Insert the missing token. That means continue the CURRENT token, with the NEXT context.
        // At this point 'lookahead' refers to the next token position, since there is a missing
        // token at the current position. Hence we don't need to increment the 'lookahead' when
        // calling 'getNextRule'.
        ParserRuleContext nextCtx = getNextRule(currentCtx, lookahead);
        Result insertionResult = seekMatchInSubTree(nextCtx, lookahead, currentDepth);

        // TODO: Add tie-break. i.e: "insertionResult.matches == deletionResult.matches" scenario.

        Result fixedPathResult;
        Solution action;
        if (insertionResult.matches == 0 && deletionResult.matches == 0) {
            fixedPathResult = insertionResult;
        } else if (insertionResult.matches >= deletionResult.matches) {
            action = new Solution(Action.INSERT, currentCtx, getParentContext(), currentCtx.toString());
            insertionResult.fixes.push(action);
            fixedPathResult = insertionResult;
        } else {
            action = new Solution(Action.REMOVE, currentCtx, getParentContext(), this.tokenReader.peek(lookahead).text);
            deletionResult.fixes.push(action);
            fixedPathResult = deletionResult;
        }
        return fixedPathResult;
    }

    /**
     * Get the next parser rule/context given the current parser context.
     * 
     * @param currentCtx Current parser context
     * @param nextLookahead Position of the next token to consider, relative to the position of the original error
     * @return Next parser context
     */
    private ParserRuleContext getNextRule(ParserRuleContext currentCtx, int nextLookahead) {
        // If this is a production, then push the context to the stack.
        // We can do this within the same switch-case that follows afters this one.
        // But doing it separately for the sake of readability/maintainability.
        switch (currentCtx) {
            case COMP_UNIT:
            case FUNC_DEFINITION:
            case FUNC_SIGNATURE:
            case RETURN_TYPE_DESCRIPTOR:
            case EXTERNAL_FUNC_BODY:
            case FUNC_BODY_BLOCK:
            case STATEMENT:
            case VAR_DEF_STMT:
            case ASSIGNMENT_STMT:
                // case EXPRESSION:
                pushContext(currentCtx);
            default:
                break;
        }

        ParserRuleContext parentCtx;
        switch (currentCtx) {
            case COMP_UNIT:
                return ParserRuleContext.TOP_LEVEL_NODE;
            case FUNC_DEFINITION:
                return ParserRuleContext.FUNCTION_KEYWORD;
            case FUNC_SIGNATURE:
                return ParserRuleContext.OPEN_PARANTHESIS;
            case RETURN_TYPE_DESCRIPTOR:
                return ParserRuleContext.RETURNS_KEYWORD;
            case EXTERNAL_FUNC_BODY:
                return ParserRuleContext.ASSIGN_OP;
            case FUNC_BODY_BLOCK:
                return ParserRuleContext.OPEN_BRACE;
            case STATEMENT:
                Token nextToken = this.tokenReader.peek(nextLookahead);
                if (isEndOfBlock(nextToken)) {
                    popContext(); // end statement
                    return ParserRuleContext.CLOSE_BRACE;
                } else {
                    throw new IllegalStateException();
                }
            case ASSIGN_OP:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext.EXTERNAL_FUNC_BODY) {
                    return ParserRuleContext.EXTERNAL_KEYWORD;
                } else if (isStatement(parentCtx)) {
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
                nextToken = this.tokenReader.peek(nextLookahead);
                if (isEndOfExpression(nextToken)) {
                    // Here we assume the end of an expression is always a semicolon
                    // TODO: add other types of expression-end
                    return ParserRuleContext.SEMICOLON;
                } else {
                    return ParserRuleContext.BINARY_EXPR_RHS;
                }

            case EXTERNAL_KEYWORD:
                return ParserRuleContext.SEMICOLON;
            case FUNCTION_KEYWORD:
                return ParserRuleContext.FUNC_NAME;
            case FUNC_NAME:
                return ParserRuleContext.FUNC_SIGNATURE;
            case OPEN_BRACE:
                // If an error occurs in the function definition signature, then only search
                // within the function signature. Do not search within the function body.
                // This is done to avoid the parser misinterpreting tokens in the signature
                // as part of the body, and vice-versa.
                return ParserRuleContext.CLOSE_BRACE;

            // TODO:
            // if (isEndOfBlock(this.tokenReader.peek(nextLookahead))) {
            // return ParserRuleContext.CLOSE_BRACE;
            // }

            // return ParserRuleContext.STATEMENT;
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
                if (parentCtx == ParserRuleContext.EXTERNAL_FUNC_BODY) {
                    popContext(); // end external func
                    return ParserRuleContext.TOP_LEVEL_NODE;
                } else if (isExpression(parentCtx)) {
                    // popContext(); // end expression
                    // A semicolon after an expression also means its an end of a statement, Hence pop the ctx.
                    popContext(); // end statement
                    if (isEndOfBlock(this.tokenReader.peek(nextLookahead))) {
                        return ParserRuleContext.CLOSE_BRACE;
                    }
                    return ParserRuleContext.STATEMENT;
                } else if (isStatement(parentCtx)) {
                    popContext(); // end statement
                    if (isEndOfBlock(this.tokenReader.peek(nextLookahead))) {
                        return ParserRuleContext.CLOSE_BRACE;
                    }
                    return ParserRuleContext.STATEMENT;
                } else {
                    throw new IllegalStateException();
                }
            case TYPE_DESCRIPTOR:
                parentCtx = getParentContext();
                if (isStatement(parentCtx)) {
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
            case ASSIGNMENT_STMT:
                return ParserRuleContext.VARIABLE_NAME;
            case VAR_DEF_STMT:
                return ParserRuleContext.TYPE_DESCRIPTOR;
            case BINARY_EXPR_RHS:
                return ParserRuleContext.BINARY_OPERATOR;
            case BINARY_OPERATOR:
                return ParserRuleContext.EXPRESSION;
            case ANNOTATION_ATTACHMENT:
            default:
                throw new IllegalStateException("cannot find the next rule for: " + currentCtx);
        }
    }

    /**
     * Check whether the given context is a statement.
     * 
     * @param ctx Parser context to check
     * @return <code>true</code> if the given context is a statement. <code>false</code> otherwise
     */
    private boolean isStatement(ParserRuleContext parentCtx) {
        return parentCtx == ParserRuleContext.STATEMENT || parentCtx == ParserRuleContext.VAR_DEF_STMT ||
                parentCtx == ParserRuleContext.ASSIGNMENT_STMT;
    }

    /**
     * Check whether the given context is an expression.
     * 
     * @param ctx Parser context to check
     * @return <code>true</code> if the given context is an expression. <code>false</code> otherwise
     */
    private boolean isExpression(ParserRuleContext ctx) {
        return ctx == ParserRuleContext.EXPRESSION;
    }

    /**
     * Check whether the given token refers to a binary operator.
     * 
     * @param token Token to check
     * @return <code>true</code> if the given token refers to a binary operator. <code>false</code> otherwise
     */
    private boolean isBinaryOperator(Token token) {
        switch (token.kind) {
            case ADD:
            case SUB:
            case DIV:
            case MUL:
            case GT:
            case LT:
            case EQUAL_GT:
            case EQUAL:
            case REF_EQUAL:
                return true;
            default:
                return false;
        }
    }

    /**
     * Represent a result of a token-sequence-search in a sub-tree. The result will contain the fixes required to
     * traverse in that sub-tree, and the number of matching tokens it found, without the fixed tokens.
     */
    private class Result {
        private int matches;
        private ArrayDeque<Solution> fixes;

        public Result(ArrayDeque<Solution> fixes, int matches) {
            this.fixes = fixes;
            this.matches = matches;
        }
    }

    /**
     * Represents a solution/fix for a parser error. A {@link Solution} consists of the parser context where the error
     * was encountered, the enclosing parser context, the token with the error, and the {@link Action} required to
     * recover.
     * 
     * @since 1.2.0
     */
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

    /**
     * Represents the actions that can be taken to recover from a parser error.
     * 
     * @since 1.2.0
     */
    enum Action {
        INSERT, REMOVE;
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
}
