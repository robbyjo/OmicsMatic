/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.util.function.DoubleUnaryOperator;
import org.jlinalg.cytometry.internal.stanford.Arcsinh;
import org.jlinalg.cytometry.internal.stanford.Hyperlog;
import org.jlinalg.cytometry.internal.stanford.Linear;
import org.jlinalg.cytometry.internal.stanford.Logarithmic;
import org.jlinalg.cytometry.internal.stanford.Logicle;
import org.jlinalg.cytometry.internal.stanford.Transform;

/** Explicit Gating-ML display transforms on already decoded/scaled measurements. */
public final class CytometryTransforms {
    private CytometryTransforms() { }
    /** Finite-input/finite-result transform. No clipping to the nominal [0,1] display interval. */
    public interface Scale {
        double apply(double value);
        double inverse(double value);
    }
    private record Checked(String name, DoubleUnaryOperator forward, DoubleUnaryOperator backward) implements Scale {
        private double evaluate(double value, DoubleUnaryOperator operator) {
            finite(value, "transform input");
            double result = operator.applyAsDouble(value);
            if (!Double.isFinite(result)) throw new ArithmeticException("Undefined/nonfinite "+name+" transform at value "+value+"; no clipping or value replacement");
            return result;
        }
        @Override public double apply(double value) { return evaluate(value, forward); }
        @Override public double inverse(double value) { return evaluate(value, backward); }
    }
    private static Scale checked(String name, Transform original) {
        return new Checked(name, original::scale, original::inverse);
    }
    public static Scale linear(double top, double additionalNegative) {
        finite(top, "T"); finite(additionalNegative, "A");
        if (!(top > 0) || additionalNegative < 0 || additionalNegative > top) throw new IllegalArgumentException("Linear transform requires T>0 and 0<=A<=T");
        // Normative validity permits A=T although the reference constructor is stricter.
        if (top == additionalNegative) return new Checked("flin(A=T)", x -> (x/top+1)/2, x -> top*(2*x-1));
        return checked("flin", new Linear(top, additionalNegative));
    }
    public static Scale logarithmic(double top, double decades) {
        finite(top, "T"); finite(decades, "M");
        return checked("flog", new Logarithmic(top, decades));
    }
    public static Scale arcsinh(double top, double decades, double additionalNegative) {
        finite(top, "T"); finite(decades, "M"); finite(additionalNegative, "A");
        if (additionalNegative > decades) throw new IllegalArgumentException("Gating-ML arcsinh requires A<=M");
        return checked("fasinh", new Arcsinh(top, decades, additionalNegative));
    }
    public static Scale logicle(double top, double linearWidth, double decades, double additionalNegative) {
        finite(top, "T"); finite(linearWidth, "W"); finite(decades, "M"); finite(additionalNegative, "A");
        return checked("logicle", new Logicle(top, linearWidth, decades, additionalNegative));
    }
    public static Scale hyperlog(double top, double linearWidth, double decades, double additionalNegative) {
        finite(top, "T"); finite(linearWidth, "W"); finite(decades, "M"); finite(additionalNegative, "A");
        return checked("hyperlog", new Hyperlog(top, linearWidth, decades, additionalNegative));
    }
    /** Gating-ML fratio = A*(x-B)/(y-C); undefined denominators are explicit failures. */
    public static double ratio(double x, double y, double a, double b, double c) {
        finite(x, "ratio x"); finite(y, "ratio y"); finite(a, "ratio A"); finite(b, "ratio B"); finite(c, "ratio C");
        if (y == c) throw new ArithmeticException("Undefined ratio: y=C; no event drop or denominator repair");
        double value = a*((x-b)/(y-c));
        if (!Double.isFinite(value)) throw new ArithmeticException("Ratio is not finite at these units/precision");
        return value;
    }
    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name+" must be finite");
    }
}
