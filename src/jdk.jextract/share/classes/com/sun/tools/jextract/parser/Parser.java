/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tools.jextract.parser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.sun.tools.jextract.Context;
import jdk.internal.clang.Cursor;
import jdk.internal.clang.CursorKind;
import jdk.internal.clang.Diagnostic;
import jdk.internal.clang.Index;
import jdk.internal.clang.LibClang;
import jdk.internal.clang.SourceLocation;
import jdk.internal.clang.SourceRange;
import jdk.internal.clang.TranslationUnit;
import com.sun.tools.jextract.tree.HeaderTree;
import com.sun.tools.jextract.tree.MacroTree;
import com.sun.tools.jextract.tree.SimpleTreeVisitor;
import com.sun.tools.jextract.tree.Tree;
import com.sun.tools.jextract.tree.TreeMaker;
import com.sun.tools.jextract.tree.TreePrinter;

public class Parser {
    private final PrintWriter out;
    private final PrintWriter err;
    private final TreeMaker treeMaker;
    private final boolean supportMacros;
    private final Logger logger = Logger.getLogger(getClass().getPackage().getName());

    public Parser(Context context, boolean supportMacros) {
        this.out = context.out;
        this.err = context.err;
        this.treeMaker = new TreeMaker();
        this.supportMacros = supportMacros;
    }

    public HeaderTree parse(Path path, Collection<String> args) {
        final Index index = LibClang.createIndex(false);
        logger.info(() -> {
            StringBuilder sb = new StringBuilder(
                    "Parsing header file " + path + " with following args:\n");
            int i = 0;
            for (String arg : args) {
                sb.append("arg[");
                sb.append(i++);
                sb.append("] = ");
                sb.append(arg);
                sb.append("\n");
            }
            return sb.toString();
        });

        Cursor tuCursor = index.parse(path.toString(),
            d -> {
                err.println(d);
                if (d.severity() >  Diagnostic.CXDiagnostic_Warning) {
                    throw new RuntimeException(d.toString());
                }
            },
            supportMacros, args.toArray(new String[0]));

        MacroParser macroParser = new MacroParser(tuCursor.getTranslationUnit());
        List<Tree> decls = new ArrayList<>();
        tuCursor.children().
            peek(c -> logger.finest(
                () -> "Cursor: " + c.spelling() + "@" + c.USR() + "?" + c.isDeclaration())).
            forEach(c -> {
                SourceLocation loc = c.getSourceLocation();
                if (loc == null) {
                    logger.info(() -> "Ignore Cursor " + c.spelling() + "@" + c.USR() + " has no SourceLocation");
                    return;
                }

                SourceLocation.Location src = loc.getFileLocation();
                if (src == null) {
                    logger.info(() -> "Cursor " + c.spelling() + "@" + c.USR() + " has no FileLocation");
                    return;
                }

                logger.fine(() -> "Do cursor: " + c.spelling() + "@" + c.USR());

                if (c.isDeclaration()) {
                    if (c.kind() == CursorKind.UnexposedDecl ||
                        c.kind() == CursorKind.Namespace) {
                        c.children().forEach(cu -> decls.add(treeMaker.createTree(cu)));
                    } else {
                        decls.add(treeMaker.createTree(c));
                    }
                } else if (supportMacros && isMacro(c) && src.path() != null) {
                    String macroName = c.spelling();
                    if (c.isMacroFunctionLike()) {
                        logger.fine(() -> "Skipping function-like macro " + macroName);
                    } else {
                        logger.fine(() -> "Defining macro " + macroName);

                        TranslationUnit tu = c.getTranslationUnit();
                        SourceRange range = c.getExtent();
                        String[] tokens = tu.tokens(range);
                        decls.add(treeMaker.createMacro(c, macroParser.eval(c, tokens)));
                    }
                }
            });

        return treeMaker.createHeader(tuCursor, path, decls);
    }

    private boolean isMacro(Cursor c) {
        return c.isPreprocessing() && c.kind() == CursorKind.MacroDefinition;
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Expected a header file");
            return;
        }

        Context context = new Context();
        Parser p = new Parser(context,true);
        Path builtinInc = Paths.get(System.getProperty("java.home"), "conf", "jextract");
        List<String> clangArgs = List.of("-I" + builtinInc);
        HeaderTree header = p.parse(Paths.get(args[0]), clangArgs);
        TreePrinter printer = new TreePrinter();
        header.accept(printer, null);
    }
}