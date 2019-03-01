/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

#ifdef _WIN64
  #include <winsock2.h>
  #define EXPORT __declspec(dllexport)
#else
  #include <stdint.h>
  #include <arpa/inet.h>
  #define EXPORT
#endif

// Linux not have ntohll, use be64toh
#ifdef be64toh
  #define ntohll be64toh
  #define htonll htobe64
#endif

struct HostNetworkValues {
  union {
    uint16_t hs;
    uint32_t hl;
    uint64_t hll;
  };
  uint16_t ns;
  uint32_t nl;
  uint64_t nll;
};

static void calc(struct HostNetworkValues *p) {
  p->ns = htons(p->hs);
  p->nl = htonl(p->hl);
  p->nll = htonll(p->hll);
}

EXPORT long initBE(struct HostNetworkValues *p, uint64_t seed) {
  p->hll = ntohll(seed);
  calc(p);
  return p->hll;
}

EXPORT long initHost(struct HostNetworkValues *p, uint64_t seed) {
  p->hll = seed;
  calc(p);
  return p->hll;
}

EXPORT long isSameValue(struct HostNetworkValues *p, uint64_t seed) {
    struct HostNetworkValues v;
    initHost(&v, seed);
    // Not use memcmp because padding
    if (p->hll != v.hll) {
        return (p->hll);
    }
    if (p->ns != v.ns) {
        return p->ns;
    }
    if (p->nl != v.nl) {
        return p->nl;
    }
    if (p->nll != v.nll) {
        return p->nll;
    }
    return 0;
}