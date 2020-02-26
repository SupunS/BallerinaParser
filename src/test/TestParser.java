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
import org.ballerinalang.compiler.parser.BallerinaParser;
import org.ballerinalang.compiler.parser.Token;
import org.ballerinalang.compiler.parser.TokenKind;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestParser {

    public static void main(String[] args) throws IOException {
        String path = "/Users/supun/eclipse-workspace-2019-09/BallerinaParser/src/test/test1.bal";
//         String path = "/Users/supun/eclipse-workspace-2019-09/BallerinaParser/src/test/test2.bal";

//        FileInputStream is = new FileInputStream(path);
        String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
//        while (true) {
            testParser(content);
//        }
    }

    private static void testParser(String content) throws FileNotFoundException {
        long sTime = System.currentTimeMillis();
        BallerinaParser parser = new BallerinaParser(content);
        parser.parse();
        System.out.println("Time: " + (System.currentTimeMillis() - sTime) / 1000.0);
    }
    
    private static void testParser(FileInputStream is) throws FileNotFoundException {
        BallerinaLexer lexer = new BallerinaLexer(is);
        BallerinaParser parser = new BallerinaParser(lexer);
        long sTime = System.currentTimeMillis();
        parser.parse();
        System.out.println("Time: " + (System.currentTimeMillis() - sTime) / 1000.0);
        try {
            is.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void testLexer(FileInputStream is) throws FileNotFoundException {
        BallerinaLexer lexer = new BallerinaLexer(is);
        Token token = lexer.nextToken();
        long sTime = System.currentTimeMillis();
        while (token.kind != TokenKind.EOF) {
            System.out.println(
                    token + " " + "[" + token.getLine() + ", " + token.getStartCol() + ", " + token.getEndCol() + "]");
            // System.out.print(token.text);
            token = lexer.nextToken();
        }
        System.out.println("Time: " + (System.currentTimeMillis() - sTime) / 1000.0);
    }

    private static void testANTLR4Parser(FileInputStream is) throws IOException {
        // ANTLRInputStream ais = new ANTLRInputStream(is);
        // ais.name = "xxx.bal";
        // org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaLexer lexer = new
        // org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaLexer(ais);
        // lexer.removeErrorListeners();
        //// lexer.addErrorListener(new BallerinaParserErrorListener(context, diagnosticSrc));
        // CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        // org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaParser parser = new
        // org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaParser(tokenStream);
        //// parser.setErrorHandler(getErrorStrategy(diagnosticSrc));
        //// parser.addParseListener(newListener(tokenStream, compUnit, diagnosticSrc));
        // parser.compilationUnit();
    }
}
