/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @run testng SmokeTest
 */

import java.io.File;

import jdk.incubator.jextract.Declaration;
import jdk.incubator.jextract.JextractTask;
import jdk.incubator.jextract.Type;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class SmokeTest {

    @Test
    public void testParser() {
        var task = JextractTask.newTask(false, new File(System.getProperty("test.src.path"), "smoke.h").toPath());
        Declaration.Scoped d = task.parse("");
        Declaration.Scoped pointDecl = checkStruct(d, "Point", "x", "y");
        Type intType = ((Declaration.Variable)pointDecl.members().get(0)).type();
        checkGlobal(d, "p", Type.declared(pointDecl));
        checkFunction(d, "distance", intType, Type.declared(pointDecl), Type.declared(pointDecl));
        Declaration.Variable ch_ptr_ptr = findDecl(d, "ch_ptr_ptr", Declaration.Variable.class);
        checkFunction(d, "pointers", ch_ptr_ptr.type(), ch_ptr_ptr.type(), ch_ptr_ptr.type());
        checkConstant(d, "ZERO", intType, 0L);
    }

    Declaration.Scoped checkStruct(Declaration.Scoped toplevel, String name, String... fields) {
        Declaration.Scoped struct = findDecl(toplevel, name, Declaration.Scoped.class);
        assertEquals(struct.members().size(), fields.length);
        for (int i = 0 ; i < fields.length ; i++) {
            assertEquals(struct.members().get(i).name(), fields[i]);
        }
        return struct;
    }

    Declaration.Variable checkGlobal(Declaration.Scoped toplevel, String name, Type type) {
        Declaration.Variable global = findDecl(toplevel, name, Declaration.Variable.class);
        assertTypeEquals(type, global.type());
        return global;
    }

    Declaration.Function checkFunction(Declaration.Scoped toplevel, String name, Type ret, Type... params) {
        Declaration.Function function = findDecl(toplevel, name, Declaration.Function.class);
        assertTypeEquals(ret, function.type().returnType());
        assertEquals(function.parameters().size(), params.length);
        for (int i = 0 ; i < params.length ; i++) {
            assertTypeEquals(params[i], function.type().argumentTypes().get(i));
            Type paramType = function.parameters().get(i).type();
            if (paramType instanceof Type.Array) {
                assertTypeEquals(params[i], Type.pointer(((Type.Array) paramType).elementType()));
            } else {
                assertTypeEquals(params[i], function.parameters().get(i).type());
            }
        }
        return function;
    }

    Declaration.Constant checkConstant(Declaration.Scoped toplevel, String name, Type type, Object value) {
        Declaration.Constant constant = findDecl(toplevel, name, Declaration.Constant.class);
        assertTypeEquals(type, constant.type());
        assertEquals(value, constant.value());
        return constant;
    }

    @SuppressWarnings("unchecked")
    <D extends Declaration> D findDecl(Declaration.Scoped toplevel, String name, Class<D> declType) {
        for (Declaration d : toplevel.members()) {
            if (declType.isAssignableFrom(d.getClass()) && d.name().equals(name)) {
                return (D)d;
            }
        }
        fail("No declaration with name " + name + " found in " + toplevel);
        return null;
    }

    void assertTypeEquals(Type expected, Type found) {
        assertEquals(expected.getClass(), found.getClass());
        if (expected instanceof Type.Primitive) {
            assertEquals(((Type.Primitive)expected).kind(), ((Type.Primitive)found).kind());
            assertEquals(((Type.Primitive)expected).layout(), ((Type.Primitive)found).layout());
        } else if (expected instanceof Type.Delegated) {
            assertEquals(((Type.Delegated)expected).kind(), ((Type.Delegated)found).kind());
            assertTypeEquals(((Type.Delegated)expected).type(), ((Type.Delegated)found).type());
        } else if (expected instanceof Type.Array) {
            assertEquals(((Type.Array)expected).kind(), ((Type.Array)found).kind());
            assertEquals(((Type.Array)expected).elementCount(), ((Type.Array)found).elementCount());
            assertTypeEquals(((Type.Array)expected).elementType(), ((Type.Array)found).elementType());
        } else if (expected instanceof Type.Declared) {
            assertEquals(((Type.Declared)expected).tree(), ((Type.Declared)found).tree());
        } else if (expected instanceof Type.Function) {
            assertTypeEquals(((Type.Function)expected).returnType(), ((Type.Function)found).returnType());
            assertEquals(((Type.Function)expected).argumentTypes().size(), ((Type.Function)found).argumentTypes().size());
            assertEquals(((Type.Function)expected).varargs(), ((Type.Function)found).varargs());
            for (int i = 0 ; i < ((Type.Function)expected).argumentTypes().size() ; i++) {
                assertTypeEquals(((Type.Function)expected).argumentTypes().get(i), ((Type.Function)found).argumentTypes().get(i));
            }
        }
    }
}
