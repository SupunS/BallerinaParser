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

    TOP_LEVEL_NODE(null),
    COMP_UNIT(null),
    FUNC_DEFINITION(null),
    STATEMENT(null),
    PARAM_LIST("parameters"),
    PARAMETER("parameter"),
    ANNOTATION_ATTACHMENT("annotation"),
    FUNC_BODY("function body"),
    FUNC_SIGNATURE("function signature"),
    EXTERNAL_FUNC_BODY("external function body"),
    FUNC_BODY_BLOCK("function body"),
    EXPRESSION("expression"),
    RETURN_TYPE_DESCRIPTOR("return type desc"),
    
    // terminals

    FUNC_NAME("function name"),
    OPEN_PARANTHESIS("("),
    CLOSE_PARANTHESIS(")"),
    RETURNS("returns"),
    TYPE_DESCRIPTOR("type"),
    OPEN_BRACE("{"),
    CLOSE_BRACE("}"),
    ASSIGN_OP("="),
    SEMICOLON(";"),
    EXTERNAL_KEYWORD("external"), 
    VARIABLE_NAME("variable");

    private String value;

    ParserRuleContext(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

}
