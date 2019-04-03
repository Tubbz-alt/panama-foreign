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

#include "precompiled.hpp"
#include "prims/directUpcallHandler.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"

JVM_ENTRY(jlong, AllocateUpcallStub_Specialized(JNIEnv *env, jobject obj, jint nlongs, jint ndoubles, jint rettag))
  ShouldNotReachHere();
  return 0; // removed
JVM_END

JVM_ENTRY(jlong, AllocateUpcallStub_LinkToNative(JNIEnv *env, jobject _unused, jobject mh))
  Handle mh_h(THREAD, JNIHandles::resolve(mh));
  jobject mh_j = JNIHandles::make_weak_global(mh_h);

  oop lform = java_lang_invoke_MethodHandle::form(mh_h());
  oop vmentry = java_lang_invoke_LambdaForm::vmentry(lform);
  Method* entry = java_lang_invoke_MemberName::vmtarget(vmentry);

  assert(entry->method_holder()->is_initialized(), "no clinit barrier");
  CompilationPolicy::compile_if_required(entry, CHECK_0);

  return (jlong)DirectUpcallHandler::generate_linkToNative_upcall_stub(mh_j, entry, CHECK_0);
 JVM_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)
#define JLI "Ljava/lang/invoke/"
#define MH JLI "MethodHandle;"

// These are the native methods on jdk.internal.foreign.abi.DirectUpcallHandler.
static JNINativeMethod DUH_methods[] = {
  {CC "allocateUpcallStub", CC "(III)J",      FN_PTR(AllocateUpcallStub_Specialized)},
};

// Native methods on jdk.internal.foreign.invokers.LinkToNativeUpcallHandler.
static JNINativeMethod L2NUH_methods[] = {
  {CC "allocateUpcallStub",  CC "(" MH ")J", FN_PTR(AllocateUpcallStub_LinkToNative)},
};
/**
 * This one function is exported, used by NativeLookup.
 */
JVM_ENTRY(void, JVM_RegisterDirectUpcallHandlerMethods(JNIEnv *env, jclass DUH_class)) {
  ThreadToNativeFromVM ttnfv(thread);

  int status = env->RegisterNatives(DUH_class, DUH_methods, sizeof(DUH_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionOccurred(),
            "register jdk.internal.foreign.abi.DirectUpcallHandler natives");
}
JVM_END

JVM_ENTRY(void, JVM_RegisterLinkToNativeUpcallHandlerMethods(JNIEnv *env, jclass L2NUH_class)) {
  ThreadToNativeFromVM ttnfv(thread);

  int status = env->RegisterNatives(L2NUH_class, L2NUH_methods, sizeof(L2NUH_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionOccurred(),
            "register jdk.internal.foreign.abi.LinkToNativeUpcallHandler natives");
}
JVM_END