/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import static org.jlinalg.genetics.PlinkAssociations.*;

/**
 * Imports original additive GLM confidence limits without recomputing bounds.
 * The requested level must come from the original invocation, not a rounded
 * header: PLINK1 truncates percentage labels, while PLINK2 prints six digits.
 * These original intervals use a normal critical value, including linear GLM
 * reports whose P-values use a Student t distribution. This class is a reader,
 * not a confidence-interval estimator or a cross-version equivalence claim.
 */
public final class PlinkConfidenceIntervals {
    private PlinkConfidenceIntervals() { }
    /** Scale of the reported limits; binary bounds are not log-transformed. */
    public enum Scale { BETA, ODDS_RATIO }
    /** Reportability of the interval independently of the core effect fields. */
    public enum Status { REPORTED, UNREPORTABLE_ESTIMATE, UNREPORTABLE_BOUNDS }
    /** Original limits and the separately declared invocation-level confidence. */
    public record Interval(double requestedLevel,Scale scale,double lower,double upper,
            String lowerColumn,String upperColumn,Status status) {
        /** Constructs an interval; unavailable bounds must both be NaN. */
        public Interval {
            validateLevel(requestedLevel);Objects.requireNonNull(scale);Objects.requireNonNull(status);
            Objects.requireNonNull(lowerColumn);Objects.requireNonNull(upperColumn);
            if (status==Status.REPORTED) {
                if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower>upper || scale==Scale.ODDS_RATIO && lower<=0) throw new IllegalArgumentException("Invalid reported confidence limits");
            } else if (!Double.isNaN(lower) || !Double.isNaN(upper)) throw new IllegalArgumentException("Unreportable confidence limits must both be NaN");
        }
        /** True when both original bounds can be reported on their declared scale. */
        public boolean reportable() { return status==Status.REPORTED; }
    }
    /** Validates the shared original PLINK1/2 --ci domain, [0.01,1). */
    public static void validateLevel(double level) {
        if (!Double.isFinite(level) || level<.01 || level>=1) throw new IllegalArgumentException("--confidence-level must be a finite original PLINK --ci level in [0.01,1)");
    }
    /**
     * Reads one complete imported row with exactly one original Lxx/Uxx pair.
     * Header consistency is checked against the selected original profile; it
     * cannot prove the invocation level, especially for truncated PLINK1 labels.
     * Caller provenance must establish that level. Any upstream ERR/NA effect
     * suppresses both bounds, including finite provisional Firth limits.
     *
     * @throws IOException on missing, ambiguous, mismatched or malformed columns
     */
    public static Interval read(Row row,Engine engine,Model model,double requestedLevel) throws IOException {
        Objects.requireNonNull(row);Objects.requireNonNull(engine);Objects.requireNonNull(model);validateLevel(requestedLevel);
        var raw=row.rawFields();boolean linear=model==Model.LINEAR;
        if (!raw.containsKey(engine==Engine.PLINK1?"SNP":"ID") || !raw.containsKey(linear?"BETA":"OR")
                || engine==Engine.PLINK1 && !Set.of(Model.LINEAR,Model.LOGISTIC).contains(model)) throw new IOException("Confidence report engine/model differs from the imported row");
        String se=engine==Engine.PLINK2 && !linear?"LOG(OR)_SE":"SE";
        if (!raw.containsKey(se)) throw new IOException("Original confidence report must include its reported standard error");
        String lower=null,upper=null;
        for (String key:raw.keySet()) {
            if (key.matches("L[0-9]+(?:\\.[0-9]+)?")) { if (lower!=null) throw new IOException("Ambiguous original lower confidence columns");lower=key; }
            if (key.matches("U[0-9]+(?:\\.[0-9]+)?")) { if (upper!=null) throw new IOException("Ambiguous original upper confidence columns");upper=key; }
        }
        if (lower==null || upper==null || !lower.substring(1).equals(upper.substring(1))) throw new IOException("Require one matching original Lxx/Uxx confidence pair");
        double displayed=Double.parseDouble(lower.substring(1)),percent=requestedLevel*100;
        if (engine==Engine.PLINK1) {
            // Original plink_glm.c uses (int)(ci_size*(100+2^-30)).
            if (!lower.substring(1).equals(Integer.toString((int)(requestedLevel*(100+0x1.0p-30))))) throw new IOException("PLINK1 truncated confidence label differs from the declared invocation level");
        } else {
            double unit=Math.pow(10,Math.floor(Math.log10(percent))-5);
            if (!Double.isFinite(displayed) || Math.abs(displayed-percent)>.5001*unit+4*Math.ulp(percent)) throw new IOException("PLINK2 confidence label differs from the declared invocation level");
        }
        Scale scale=linear?Scale.BETA:Scale.ODDS_RATIO;
        if (!row.effectReportable()) return new Interval(requestedLevel,scale,Double.NaN,Double.NaN,lower,upper,Status.UNREPORTABLE_ESTIMATE);
        double lo=value(raw.get(lower)),hi=value(raw.get(upper));
        if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo>hi || scale==Scale.ODDS_RATIO && lo<=0) return new Interval(requestedLevel,scale,Double.NaN,Double.NaN,lower,upper,Status.UNREPORTABLE_BOUNDS);
        return new Interval(requestedLevel,scale,lo,hi,lower,upper,Status.REPORTED);
    }
    private static double value(String text) throws IOException {
        if (Set.of("NA","NaN","nan",".","inf","Inf","+inf","-inf","Infinity","+Infinity","-Infinity").contains(text)) return Double.NaN;
        try { return Double.parseDouble(text); } catch (NumberFormatException failure) { throw new IOException("Malformed original confidence limit: "+text,failure); }
    }
}
