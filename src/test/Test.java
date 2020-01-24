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
package test;

import org.ballerinalang.compiler.parser.BallerinaLexer;
import org.ballerinalang.compiler.parser.Token;
import org.ballerinalang.compiler.parser.TokenKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Test {

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
//         String path = "/Users/supun/eclipse-workspace-2019-09/BallerinaParser/src/test/test1.bal";
        String path = "/Users/supun/eclipse-workspace-2019-09/BallerinaParser/src/test/test2.bal";

        byte[] encoded = Files.readAllBytes(Paths.get(path));
        String content = new String(encoded);

//        while (true) {
            BallerinaLexer lexer = new BallerinaLexer(content);
            
            Token token = lexer.nextToken();
            long sTime = System.currentTimeMillis();
            while (token.kind != TokenKind.EOF) {
//            System.out.println(
//                    token + " " + "[" + token.getLine() + ", " + token.getStartCol() + ", " + token.getEndCol() + "]");
                // System.out.print(token.text);
                token = lexer.nextToken();
            }
//        }

        System.out.println("Time: " + (System.currentTimeMillis() - sTime) / 1000.0);
    }

}
