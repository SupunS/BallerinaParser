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
package org.ballerinalang.compiler.parser.tree;

import java.util.ArrayList;
import java.util.List;

public class FunctionNode extends ASTNode {
    public ASTNode functionKeyword;
    public ASTNode name;
    public ASTNode leftParenthesis;
    public ASTNode parameters;
    public ASTNode rightParenthesis;
    public ASTNode returnType;
    public ASTNode body;
    public List<ASTNode> modifiers = new ArrayList<>();

    public FunctionNode() {
        this.kind = NodeKind.FUNCTION;
    }

    @Override
    public String toString() {
        // TODO: use whitespace information
        StringBuilder sj = new StringBuilder();
        for (ASTNode modifier : modifiers) {
            sj.append(modifier.toString() + " ");
        }
        sj.append("function ");
        sj.append(this.name.toString());
        sj.append(this.parameters.toString() + " ");

        if (this.returnType.kind != NodeKind.EMPTY) {
            sj.append(this.returnType.toString() + " ");
        }

        sj.append(this.body.toString());
        return sj.toString();
    }
}
