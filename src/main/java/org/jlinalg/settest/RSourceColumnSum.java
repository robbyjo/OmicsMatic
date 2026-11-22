/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

/** Streaming access to the existing selected x86 R4.6.1 64-bit-significand colSums accumulator.
 * Inputs remain rounded doubles and preserve their original row order. This is a selected
 * source arithmetic profile, not a general R runtime or a replacement association test.
 */
public final class RSourceColumnSum {
    private final RLongDoubleSum delegate=new RLongDoubleSum();
    public RSourceColumnSum() { }
    public void add(double value){delegate.add(value);}
    public double value(){return delegate.value();}
}
