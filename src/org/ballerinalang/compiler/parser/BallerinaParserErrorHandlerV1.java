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

public class BallerinaParserErrorHandlerV1 {

    private final TokenReader tokenReader;
    private final BallerinaParserListener listner;
    private final BallerinaParserErrorHandlerV2 errorHandler;
    private final BallerinaParser parser;

    private ParserRuleContext parentContext = ParserRuleContext.COMP_UNIT;

    public BallerinaParserErrorHandlerV1(TokenReader tokenReader, BallerinaParserListener listner,
            BallerinaParserErrorHandlerV2 errorHandler, BallerinaParser parser) {
        this.tokenReader = tokenReader;
        this.listner = listner;
        this.errorHandler = errorHandler;
        this.parser = parser;
    }

    public void setParentContext(ParserRuleContext context) {
        this.parentContext = context;
    }

    public void recover(Token nextToken, ParserRuleContext currentContext) {
        switch (currentContext) {
            case ANNOTATION_ATTACHMENT:
                break;
            case FUNCTION_BODY:
                recoverFunctionBody(nextToken);
                break;
            case FUNCTION_DEFINITION:
                break;
            case FUNCTION_NAME:
                recoverFunctionName(nextToken);
                break;
            case LEFT_PARANTHESIS:
                recoverLeftParanthesis(nextToken);
                break;
            case RIGHT_PARANTHESIS:
                recoverRightParanthesis(nextToken);
                break;
            case PARAMETER:
                break;
            case PARAMETER_LIST:
                break;
            case RETURN_TYPE_DESCRIPTOR:
                recoverReturnTypeDescriptor(nextToken);
                break;
            case TYPE_DESCRIPTOR:
                recoverTypeDescriptor(nextToken);
                break;
            case COMP_UNIT:
                break;
            case LEFT_BRACE:
                recoverRightBrace(nextToken);
                break;
            case RIGHT_BRACE:
                recoverRightBrace();
                break;
            case EXTERNAL_FUNCTION_BODY:
                break;
            case ASSIGN_OP:
                recoverAssignOperator(nextToken);
                break;
            case STATEMENT_END:
                recoverSemicolon();
                break;
            case VARIABLE_NAME:
                recoverVariableName(nextToken);
                break;
            case EXPRESSION:
                recoverExpression(nextToken);
                break;
            case FUNCTION_BODY_BLOCK:
            case EXTERNAL_FUNCTION_BODY_END:
            case FUNCTION_SIGNATURE:
            case STATEMENT:
            case TOP_LEVEL_NODE:
            default:
                // Remove the token and continue, if we don't know to recover from it.
                // This is a fail-safe.
                removeInvalidToken(nextToken);
        }
    }

    public void removeInvalidToken(Token nextToken) {
        // This means no match is found for the current token.
        // Then consume it and return an error node
        this.errorHandler.reportInvalidToken(nextToken);
        this.tokenReader.consumeNonTrivia();

        // FIXME: add this error node to the tree
        // this.listner.exitErrorNode(nextToken.text);
    }

    private void reportMissingTokenError(String message) {
        Token currentToken = this.tokenReader.head();
        this.errorHandler.reportMissingTokenError(currentToken, message);
    }

    /**
     * Prune the given token from the tree to eliminate any unexpected tokens.
     * The given token is pruned only if this token is not expected in a upcoming rule.
     * This way it decides whether this token is an extraneous token or the expected
     * token at this current position is missing.
     * 
     * If the token is pruned, then this will re-attempt to parse the current rule.
     * 
     * @param nextToken Token to be pruned.
     * @param context Current parser rule context
     * @return <code>true</code> if the token was pruned. <code>false</code> otherwise.
     */
    private boolean prune(Token nextToken, ParserRuleContext context) {
        if (hasMatch(nextToken, context)) {
            return false;
        }

        // If the current token is not expected to be found in future, then
        // remove this additional token and re-attempt parsing the same rule again.
        removeInvalidToken(nextToken);
        this.parser.parse(context);
        return true;
    }

    private void recoverReturnTypeDescriptor(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.RETURN_TYPE_DESCRIPTOR)) {
            return;
        }

        this.listner.addEmptyNode();
    }

    private void recoverTypeDescriptor(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.TYPE_DESCRIPTOR)) {
            return;
        }

        reportMissingTokenError("missing type descriptor");
        this.listner.addMissingNode();
    }

    private void recoverSemicolon() {
        // Reaches here only if the ';' is missing. So no need to prune.
        // Simply log and continue;
        reportMissingTokenError("missing ';'");
    }

    private void recoverAssignOperator(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.ASSIGN_OP)) {
            return;
        }

        reportMissingTokenError("missing '='");
    }

    private void recoverRightBrace(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.LEFT_BRACE)) {
            return;
        }

        // We come here if theres a matching rule ahead.
        // No need to add a node for this
        reportMissingTokenError("missing '{'");

    }

    private void recoverRightBrace() {
        // Reaches here only if the '}' to end the function body block is missing.
        // So no need to prune. Simply log and continue;
        reportMissingTokenError("missing '}'");

    }

    private void recoverFunctionBody(Token nextToken) {
        if (hasMatchInFunction(nextToken, ParserRuleContext.EXTERNAL_FUNCTION_BODY)) {
            this.parser.parse(ParserRuleContext.EXTERNAL_FUNCTION_BODY);
            return;
        }

        if (hasMatchInFunction(nextToken, ParserRuleContext.FUNCTION_BODY_BLOCK)) {
            this.parser.parse(ParserRuleContext.FUNCTION_BODY_BLOCK);
            return;
        }

        removeInvalidToken(nextToken);
        this.parser.parse(ParserRuleContext.FUNCTION_BODY);
    }

    private void recoverFunctionName(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.FUNCTION_NAME)) {
            return;
        }

        // We come here if there's a matching rule ahead.
        // So fill the tree for the expected function name, and continue the parsing.
        reportMissingTokenError("missing function name");
        this.listner.addMissingNode();
    }

    private void recoverLeftParanthesis(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.LEFT_PARANTHESIS)) {
            return;
        }
        reportMissingTokenError("missing '('");
    }

    private void recoverRightParanthesis(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.RIGHT_PARANTHESIS)) {
            return;
        }
        reportMissingTokenError("missing ')'");
    }

    private void recoverVariableName(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.VARIABLE_NAME)) {
            return;
        }

        reportMissingTokenError("missing variable");
        this.listner.addMissingNode();
    }

    private void recoverExpression(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.EXPRESSION)) {
            return;
        }

        reportMissingTokenError("missing expression");
    }

    /*
     * hasMatch? methods
     */

    private boolean hasMatch(Token nextToken, ParserRuleContext currentContext) {

        // TODO: We may have to define the limit to the look-ahead (a tolerance level).
        // i.e: When to stop looking further ahead, and return.
        // Because we don't want to keep looking for eternity, whether this extraneous/mismatching
        // token is useful in future. If its not expected for the next x number of rules (or until
        // rule x), we can terminate.

        // TODO: Memoize - if the same token is already validated against the same rule,
        // then return the result of the previous attempt.

        switch (this.parentContext) {
            case FUNCTION_DEFINITION:
                return hasMatchInFunction(nextToken, currentContext);
            case STATEMENT:
                return hasMatchInStatement(nextToken, currentContext);
            default:
                break;
        }
        return false;
    }

    /**
     * @param nextToken
     * @param currentContext
     * @return
     */
    private boolean hasMatchInStatement(Token nextToken, ParserRuleContext currentContext) {
        ParserRuleContext nextContext;
        switch (currentContext) {
            case TYPE_DESCRIPTOR:
                if (nextToken.kind == TokenKind.TYPE) {
                    return true;
                }
                nextContext = ParserRuleContext.VARIABLE_NAME;
                break;
            case VARIABLE_NAME:
                if (nextToken.kind == TokenKind.IDENTIFIER) {
                    return true;
                }
                nextContext = ParserRuleContext.ASSIGN_OP;
                break;
            case ASSIGN_OP:
                if (nextToken.kind == TokenKind.ASSIGN) {
                    return true;
                }
                nextContext = ParserRuleContext.EXPRESSION;
                break;
            case EXPRESSION:
                if (hasMatchInExpression(nextToken)) {
                    return true;
                }
                nextContext = ParserRuleContext.STATEMENT_END;
                break;
            case STATEMENT_END:
                if (nextToken.kind == TokenKind.SEMICOLON) {
                    return true;
                }
                // else check whether a block has ended
                return isEndOfBlock(nextToken);
            default:
                return false;
        }
        // Try the next rule
        return hasMatchInStatement(nextToken, nextContext);
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

    private boolean hasMatchInFunction(Token nextToken, ParserRuleContext currentContext) {
        ParserRuleContext nextContext;
        switch (currentContext) {
            case LEFT_PARANTHESIS:
                if (nextToken.kind == TokenKind.LEFT_PARANTHESIS) {
                    return true;
                }

                nextContext = ParserRuleContext.PARAMETER_LIST;
                break;
            case PARAMETER_LIST:
                // TODO: if match, return the context
                nextContext = ParserRuleContext.PARAMETER;
            case PARAMETER:
                // TODO: if match, return the context
                nextContext = ParserRuleContext.RIGHT_PARANTHESIS;
                break;
            case RIGHT_PARANTHESIS:
                if (nextToken.kind == TokenKind.RIGHT_PARANTHESIS) {
                    return true;
                }
                nextContext = ParserRuleContext.RETURN_TYPE_DESCRIPTOR;
                break;
            case RETURN_TYPE_DESCRIPTOR:
                if (nextToken.kind == TokenKind.RETURNS) {
                    return true;
                }
                nextContext = ParserRuleContext.TYPE_DESCRIPTOR;
                break;
            case TYPE_DESCRIPTOR:
                if (nextToken.kind == TokenKind.TYPE) {
                    return true;
                }
                nextContext = ParserRuleContext.FUNCTION_BODY;
                break;
            case FUNCTION_BODY:
                if (hasMatchInFunction(nextToken, ParserRuleContext.EXTERNAL_FUNCTION_BODY)) {
                    return true;
                }
                nextContext = ParserRuleContext.LEFT_BRACE;
                break;
            case FUNCTION_BODY_BLOCK:
            case LEFT_BRACE:
                if (nextToken.kind == TokenKind.LEFT_BRACE) {
                    return true;
                }
                nextContext = ParserRuleContext.RIGHT_BRACE;
                break;
            case RIGHT_BRACE:
                return nextToken.kind == TokenKind.RIGHT_BRACE;
            case COMP_UNIT:
            case ANNOTATION_ATTACHMENT:
                nextContext = ParserRuleContext.EXTERNAL_FUNCTION_BODY_END;
                break;
            case EXTERNAL_FUNCTION_BODY:
            case ASSIGN_OP:
                if (nextToken.kind == TokenKind.ASSIGN) {
                    return true;
                }
                nextContext = ParserRuleContext.ANNOTATION_ATTACHMENT;
                break;
            case EXTERNAL_FUNCTION_BODY_END:
                if (nextToken.kind == TokenKind.EXTERNAL) {
                    return true;
                }
                nextContext = ParserRuleContext.STATEMENT_END;
                break;
            case STATEMENT_END:
                return nextToken.kind == TokenKind.SEMICOLON;
            case FUNCTION_NAME:
                if (nextToken.kind == TokenKind.IDENTIFIER) {
                    return true;
                }
                nextContext = ParserRuleContext.LEFT_PARANTHESIS;
                break;
            case TOP_LEVEL_NODE:
            case FUNCTION_DEFINITION:
            case FUNCTION_SIGNATURE:
            default:
                return false;
        }

        // Try the next rule
        return hasMatchInFunction(nextToken, nextContext);
    }
}
