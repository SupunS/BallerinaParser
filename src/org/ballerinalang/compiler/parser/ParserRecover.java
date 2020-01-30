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

public class ParserRecover {

    private final TokenReader tokenReader;
    private final BallerinaParserListener listner;
    private final BallerinaParserErrorHandler errorHandler;
    private final BallerinaParser parser;

    public ParserRecover(TokenReader tokenReader, BallerinaParserListener listner,
            BallerinaParserErrorHandler errorHandler, BallerinaParser parser) {
        this.tokenReader = tokenReader;
        this.listner = listner;
        this.errorHandler = errorHandler;
        this.parser = parser;
    }

    public void recover(Token nextToken, ParserRuleContext context) {
        if (nextToken.kind == TokenKind.EOF) {
            this.listner.addMissingNode();
            return;
        }

        switch (context) {
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
            case FUNCTION_SIGNATURE_START:
                recoverFunctionSignatureStart(nextToken);
                break;
            case FUNCTION_SIGNATURE_END:
                recoverFunctionSignatureEnd(nextToken);
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
            case FUNCTION_BODY_BLOCK_START:
                recoverFunctionBodyBlockStart(nextToken);
                break;
            case FUNCTION_BODY_BLOCK_END:
                recoverFunctionBodyBlockEnd(nextToken);
                break;
            case EXTERNAL_FUNCTION_BODY:
                break;
            case EXTERNAL_FUNCTION_BODY_START:
                recoverExternFunctionBodyStart(nextToken);
                break;
            case STATEMENT_END:
                recoverStatementEnd(nextToken);
            case FUNCTION_BODY_BLOCK:
            case EXTERNAL_FUNCTION_BODY_END:
            case FUNCTION_SIGNATURE:
            default:
                break;
        }
    }

    /**
     * 
     */
    private void recoverReturnTypeDescriptor(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.RETURN_TYPE_DESCRIPTOR)) {
            return;
        }

        this.listner.addEmptyNode();
    }

    /**
     * 
     */
    private void recoverTypeDescriptor(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.TYPE_DESCRIPTOR)) {
            return;
        }

        this.errorHandler.reportError(nextToken, "missing type descriptor");
        this.listner.addMissingNode();
    }

    /**
     * @param nextToken
     */
    private void recoverStatementEnd(Token nextToken) {
        // Reaches here only if the ';' is missing. So no need to prune.
        // Simply log and continue;
        this.errorHandler.reportError(nextToken, "missing ';'");
    }

    /**
     * @param nextToken
     */
    private void recoverExternFunctionBodyStart(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.EXTERNAL_FUNCTION_BODY_START)) {
            return;
        }

        this.errorHandler.reportError(nextToken, "missing '='");
    }

    private void recoverFunctionBodyBlockStart(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.FUNCTION_BODY_BLOCK_START)) {
            return;
        }

        // We come here if theres a matching rule ahead.
        // No need to add a node for this
        this.errorHandler.reportError(nextToken, "missing {");

    }

    private void recoverFunctionBodyBlockEnd(Token nextToken) {
        // Reaches here only if the '}' to end the function body block is missing.
        // So no need to prune. Simply log and continue;
        this.errorHandler.reportError(nextToken, "missing }");

    }

    /**
     * 
     */
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
        this.errorHandler.reportError(nextToken, "missing function name");
        this.listner.addMissingNode();
    }

    /**
     * @param nextToken
     */
    private boolean prune(Token nextToken, ParserRuleContext context) {
        if (hasMatchInFunction(nextToken, context)) {
            return false;
        }

        // If the current token is not expected to be found in future, then
        // remove this additional token and re-attempt parsing the same rule again.
        removeInvalidToken(nextToken);
        this.parser.parse(context);
        return true;
    }

    private void recoverFunctionSignatureStart(Token nextToken) {
        if (prune(nextToken, ParserRuleContext.FUNCTION_SIGNATURE_START)) {
            return;
        }
        this.errorHandler.reportError(nextToken, "missing '('");
    }

    private void recoverFunctionSignatureEnd(Token nextToken) {
        this.errorHandler.reportError(nextToken, "missing ')'");
    }

    /**
     * @param nextToken
     * @param currentContext
     * @return
     */
    private boolean hasMatchInFunction(Token nextToken, ParserRuleContext currentContext) {

        // TODO: Memoize - if the same token is already validated against the same rule,
        // then return the result of the previous attempt.

        ParserRuleContext nextContext;
        switch (currentContext) {
            case FUNCTION_SIGNATURE_START:
                if (nextToken.kind == TokenKind.LEFT_PARANTHESIS) {
                    return true;
                }

                nextContext = ParserRuleContext.PARAMETER_LIST;
                break;
            case PARAMETER_LIST:
                // TODO: if match, return the context
                return hasMatchInFunction(nextToken, ParserRuleContext.PARAMETER);
            case PARAMETER:
                // TODO: if match, return the context
                nextContext = ParserRuleContext.FUNCTION_SIGNATURE_END;
                break;
            case FUNCTION_SIGNATURE_END:
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
                nextContext = ParserRuleContext.FUNCTION_BODY_BLOCK_START;
                break;
            case FUNCTION_BODY_BLOCK:
            case FUNCTION_BODY_BLOCK_START:
                if (nextToken.kind == TokenKind.LEFT_BRACE) {
                    return true;
                }
                nextContext = ParserRuleContext.FUNCTION_BODY_BLOCK_END;
                break;
            case FUNCTION_BODY_BLOCK_END:
                return nextToken.kind == TokenKind.RIGHT_BRACE;
            case COMP_UNIT:
            case ANNOTATION_ATTACHMENT:
                nextContext = ParserRuleContext.EXTERNAL_FUNCTION_BODY_END;
                break;
            case EXTERNAL_FUNCTION_BODY:
            case EXTERNAL_FUNCTION_BODY_START:
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
                nextContext = ParserRuleContext.FUNCTION_SIGNATURE_START;
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

    /**
     * @param nextToken
     */
    public void removeInvalidToken(Token nextToken) {
        // This means no match is found for the current token.
        // Then consume it and return an error node
        this.errorHandler.reportError(nextToken, "invalid token '" + nextToken.text + "'");
        this.tokenReader.consume();

        // FIXME: add this error node to the tree
        // this.listner.exitErrorNode(nextToken.text);
    }
}
