/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source behaviour derived from GNU libstdc++ (C) 2007-2017 Free Software
 * Foundation, Inc., GPL-3.0-or-later with GCC Runtime Library Exception 3.1.
 * MurmurHash64A is based on Austin Appleby's public-domain MurmurHash2.
 */
package org.jlinalg.sequence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Original comparison profile's GNU 64-bit string-map iteration. This ordering
 * affects which tied gene is found by lower_bound; it is not cosmetic sorting.
 * Source: GCC releases/gcc-7.5.0, libstdc++-v3/include/bits/hashtable.h,
 * src/shared/hashtable-aux.cc and libsupc++/hash_bytes.cc. The default prime
 * growth sequence below covers every possible Java Map size without allocating
 * empty buckets. This is not a general implementation of std::unordered_map.
 */
final class TpmCalculatorOrder {
    private TpmCalculatorOrder() { }
    private static final long[] GROWTH={13,29,59,127,257,541,1109,2357,5087,10273,20753,42043,85229,
        172933,351061,712697,1447153,2938679,5967347,12117689,24607243,49969847,101473717,
        206062531,418451333,849749479,1725587117,3504151727L};
    private static final class Node<V> {
        final Map.Entry<String,V> entry; final long hash;
        Node<V> previous,next;
        Node(Map.Entry<String,V> entry) { this.entry=entry; hash=hash(entry.getKey()); }
    }
    private static final class Chain<V> {
        final Map<Long,Node<V>> buckets=new HashMap<>();
        Node<V> head; long count=1;
        void insert(Node<V> node) {
            long bucket=Long.remainderUnsigned(node.hash,count);
            Node<V> first=buckets.get(bucket);
            if (first==null) { node.previous=null; node.next=head; }
            else { node.previous=first.previous; node.next=first; }
            if (node.previous==null) head=node; else node.previous.next=node;
            if (node.next!=null) node.next.previous=node;
            buckets.put(bucket,node);
        }
        void rehash(long count) {
            Node<V> old=head; head=null; buckets.clear(); this.count=count;
            while (old!=null) { Node<V> next=old.next; old.next=null; old.previous=null; insert(old); old=next; }
        }
    }
    static <V> List<Map.Entry<String,V>> entries(Map<String,V> insertionOrder) {
        var chain=new Chain<V>(); int size=0,growth=0;
        for (var e:insertionOrder.entrySet()) {
            if (size==0 || size+1L>chain.count) chain.rehash(GROWTH[growth++]);
            chain.insert(new Node<>(e)); size++;
        }
        var result=new ArrayList<Map.Entry<String,V>>(size);
        for (Node<V> node=chain.head;node!=null;node=node.next) result.add(node.entry);
        return result;
    }
    static long hash(String value) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8); long multiplier=0xc6a4a7935bd1e995L;
        long hash=0xc70f6907L ^ (bytes.length*multiplier); int p=0;
        for (;p+8<=bytes.length;p+=8) {
            long block=0; for (int n=0;n<8;n++) block|=(bytes[p+n]&255L)<<(8*n);
            block*=multiplier; block^=block>>>47; block*=multiplier;
            hash^=block; hash*=multiplier;
        }
        if (p<bytes.length) {
            long tail=0; for (int n=0;p+n<bytes.length;n++) tail|=(bytes[p+n]&255L)<<(8*n);
            hash^=tail; hash*=multiplier;
        }
        hash^=hash>>>47; hash*=multiplier; hash^=hash>>>47;
        return hash;
    }
}
