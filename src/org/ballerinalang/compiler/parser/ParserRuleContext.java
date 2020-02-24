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

public enum ParserRuleContext {

    // productions
    COMP_UNIT("comp-unit"),
    TOP_LEVEL_NODE("top-level-node"),
    FUNC_DEFINITION("func-definition"),
    STATEMENT("statement"),
    PARAM_LIST("parameters"),
    PARAMETER("parameter"),
    FOLLOW_UP_PARAM("follow-up-param"),
    PARAMETER_RHS("parameter-rhs"),
    ANNOTATION_ATTACHMENT("annotation"),
    FUNC_BODY("func-body"),
    FUNC_SIGNATURE("func-signature"),
    EXTERNAL_FUNC_BODY("external-func-body"),
    FUNC_BODY_BLOCK("func-body-block"),
    RETURN_TYPE_DESCRIPTOR("return-type-desc"),
    ASSIGNMENT_STMT("assignment-stmt"),
    VAR_DECL_STMT("var-decl-stmt"),
    VAR_DECL_STMT_RHS("var-decl-rhs"),
    VAR_DECL_STMT_RHS_VALUE("var-decl-rhs-value"),

    // terminals
    FUNCTION_KEYWORD("function"),
    FUNC_NAME("function-name"),
    OPEN_PARENTHESIS("("),
    CLOSE_PARENTHESIS(")"),
    RETURNS_KEYWORD("returns"),
    TYPE_DESCRIPTOR("type"),
    OPEN_BRACE("{"),
    CLOSE_BRACE("}"),
    ASSIGN_OP("="),
    SEMICOLON(";"),
    EXTERNAL_KEYWORD("external"), 
    VARIABLE_NAME("variable"),
    BINARY_OPERATOR("binary-operator"),
    COMMA(","),

    // expressions
    EXPRESSION("expression"),
    BINARY_EXPR_RHS("expression-rhs"),
    //LITERAL_EXPR("literal"),
    // CAST_EXPR("cast-expr")
    // BINARY_EXPR("binary-expr"),
    ;

    private String value;

    ParserRuleContext(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

}
