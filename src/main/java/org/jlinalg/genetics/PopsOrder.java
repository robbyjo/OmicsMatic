/*
 * Adapted from NumPy 1.19.5 aquicksort_double and aheapsort_double.
 * Copyright (c) 2005-2020, NumPy Developers. All rights reserved.
 * Copyright (C) 2026 JLinAlg contributors (Java translation).
 * SPDX-License-Identifier: BSD-3-Clause
 * See docs/literature/pops-native-development/NUMPY-LICENSE.txt.
 */
package org.jlinalg.genetics;

/** NumPy 1.19.5 indirect quicksort, retaining the original behavior for tied p-values. */
final class PopsOrder {
    private PopsOrder() { }

    static int[] argsort(double[] values) {
        int finite = 0;
        for (double value : values) if (!Double.isNaN(value)) finite++;
        int[] order = new int[values.length];
        for (int i = 0, j = 0, k = finite; i < values.length; i++) {
            if (Double.isNaN(values[i])) order[k++] = i; else order[j++] = i;
        }
        if (finite < 2) return order;
        int[] lefts = new int[128], rights = new int[128], depths = new int[128];
        int stack = 0, left = 0, right = finite - 1, depth = (31 - Integer.numberOfLeadingZeros(finite)) * 2;
        for (;;) {
            if (depth < 0) heap(values, order, left, right - left + 1);
            else {
                while (right - left > 15) {
                    int middle = left + ((right - left) >> 1);
                    if (values[order[middle]] < values[order[left]]) swap(order, middle, left);
                    if (values[order[right]] < values[order[middle]]) swap(order, right, middle);
                    if (values[order[middle]] < values[order[left]]) swap(order, middle, left);
                    double pivot = values[order[middle]];
                    int i = left, j = right - 1;
                    swap(order, middle, j);
                    for (;;) {
                        do i++; while (values[order[i]] < pivot);
                        do j--; while (pivot < values[order[j]]);
                        if (i >= j) break;
                        swap(order, i, j);
                    }
                    swap(order, i, right - 1);
                    if (i - left < right - i) {
                        lefts[stack] = i + 1; rights[stack] = right; right = i - 1;
                    } else {
                        lefts[stack] = left; rights[stack] = i - 1; left = i + 1;
                    }
                    depths[stack++] = --depth;
                }
                for (int i = left + 1; i <= right; i++) {
                    int value = order[i], j = i;
                    while (j > left && values[value] < values[order[j - 1]]) { order[j] = order[j - 1]; j--; }
                    order[j] = value;
                }
            }
            if (stack == 0) return order;
            right = rights[--stack]; left = lefts[stack]; depth = depths[stack];
        }
    }

    private static void heap(double[] v, int[] a, int offset, int count) {
        for (int l = count >> 1; l > 0; l--) sift(v, a, offset, l, count, a[offset + l - 1]);
        while (count > 1) {
            int tmp = a[offset + count - 1]; a[offset + count - 1] = a[offset]; count--;
            sift(v, a, offset, 1, count, tmp);
        }
    }

    private static void sift(double[] v, int[] a, int offset, int i, int count, int tmp) {
        for (int j = i * 2; j <= count; j *= 2) {
            if (j < count && v[a[offset + j - 1]] < v[a[offset + j]]) j++;
            if (!(v[tmp] < v[a[offset + j - 1]])) break;
            a[offset + i - 1] = a[offset + j - 1]; i = j;
        }
        a[offset + i - 1] = tmp;
    }

    private static void swap(int[] x, int a, int b) { int t = x[a]; x[a] = x[b]; x[b] = t; }
}
