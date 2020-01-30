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

import org.ballerinalang.compiler.parser.tree.ASTNode;
import org.ballerinalang.compiler.parser.tree.EmptyNode;
import org.ballerinalang.compiler.parser.tree.InvalidNode;
import org.ballerinalang.compiler.parser.tree.ExternFuncBodyNode;
import org.ballerinalang.compiler.parser.tree.FuncBodyNode;
import org.ballerinalang.compiler.parser.tree.FunctionNode;
import org.ballerinalang.compiler.parser.tree.IdentifierNode;
import org.ballerinalang.compiler.parser.tree.MissingNode;
import org.ballerinalang.compiler.parser.tree.ModifierNode;
import org.ballerinalang.compiler.parser.tree.ParametersNode;
import org.ballerinalang.compiler.parser.tree.ReturnTypeDescNode;
import org.ballerinalang.compiler.parser.tree.TypeNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class BallerinaParserListener {

    private final ArrayDeque<ASTNode> nodesStack = new ArrayDeque<>();

    // TODO: make this a stack of lists (nested modifiers are possible)
    private List<ASTNode> modifiersList = new ArrayList<>(2);

    public void exitCompUnit() {
        System.out.println("--------------------------------------");
        while (!this.nodesStack.isEmpty()) {
            System.out.println(this.nodesStack.removeLast());
            System.out.println();
        }
        System.out.println("--------------------------------------");
    }

    public void exitModifier(Token modifier) {
        this.modifiersList.add(new ModifierNode(modifier));
    }

    public void exitFunctionDefinition() {
        FunctionNode func = new FunctionNode();
        func.body = this.nodesStack.pop();
        func.returnType = this.nodesStack.pop();
        func.parameters = this.nodesStack.pop();
        func.name = this.nodesStack.pop();
        func.modifiers = this.modifiersList;
        this.nodesStack.push(func);

        this.modifiersList = new ArrayList<>(2);
    }

    public void exitFunctionSignature() {
        // do nothing
    }

    public void exitParamList(int paramCount) {
        ParametersNode params = new ParametersNode();
        for (int i = 0; i < paramCount; i++) {
            params.add(this.nodesStack.pop());
        }

        this.nodesStack.push(params);
    }

    public void exitReturnTypeDescriptor() {
        ReturnTypeDescNode returnTypeDesc = new ReturnTypeDescNode();
        returnTypeDesc.type = this.nodesStack.pop();
        returnTypeDesc.annot = this.nodesStack.pop();
        this.nodesStack.push(returnTypeDesc);
    }

    public void exitTypeDescriptor(Token type) {
        this.nodesStack.push(new TypeNode(type));
    }

    public void exitAnnotations() {
        // TODO:
        this.addEmptyNode();
    }

    public void exitFunctionBody() {
        // do nothing
        // exitFunctionBodyBlock() or exitExternalFunctionBody() method will add the relevant node
        // to the stack
    }

    public void exitFunctionBodyBlock() {
        this.nodesStack.push(new FuncBodyNode());
    }

    public void exitExternalFunctionBody() {
        ASTNode annot = this.nodesStack.pop();
        this.nodesStack.push(new ExternFuncBodyNode(annot));
    }

    public void exitFunctionName(Token name) {
        this.nodesStack.push(new IdentifierNode(name));
    }

    public void exitErrorNode() {
        this.nodesStack.push(new InvalidNode());
    }

    public void exitErrorNode(String content) {
        this.nodesStack.push(new InvalidNode(content));
    }

    public void exitParameter() {
    }

    public void addEmptyNode() {
        this.nodesStack.push(new EmptyNode());
    }

    public void addMissingNode() {
        this.nodesStack.push(new MissingNode());
    }

    public ASTNode getLastNode() {
        return this.nodesStack.peek();
    }
}
