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
    COMP_UNIT,
    FUNCTION_DEFINITION,
    FUNCTION_NAME,
    LEFT_PARANTHESIS,
    RIGHT_PARANTHESIS,
    PARAMETER_LIST,
    PARAMETER,
    RETURN_TYPE_DESCRIPTOR,
    TYPE_DESCRIPTOR,
    ANNOTATION_ATTACHMENT,
    FUNCTION_BODY,
    LEFT_BRACE,
    RIGHT_BRACE,
    EXTERNAL_FUNCTION_BODY,
    ASSIGN_OP,
    FUNCTION_SIGNATURE,
    STATEMENT_END,
    EXTERNAL_FUNCTION_BODY_END, 
    FUNCTION_BODY_BLOCK,
    TOP_LEVEL_NODE, STATEMENT, VARIABLE_NAME, EXPRESSION;
}
