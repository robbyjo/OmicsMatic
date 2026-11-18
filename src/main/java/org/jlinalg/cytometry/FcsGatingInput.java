/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit bridge from stored FCS channels to Gating-ML amplifier scale and file compensation. */
public final class FcsGatingInput {
    private final List<String> names, warnings;
    private final double[] decades, minimum, range, gain;
    private final GatingMl20.FcsCompensation compensation;
    private final String compensationKeyword;
    private FcsGatingInput(FcsReader reader, String selection, boolean legacyZeroMinimumAsOne, double cutoff) throws IOException {
        Objects.requireNonNull(reader); Objects.requireNonNull(selection);
        if (!Double.isFinite(cutoff) || cutoff < 0 || cutoff >= 1) throw new IllegalArgumentException("Declare an SVD cutoff in [0,1)");
        var parameters = reader.info().parameters(); names = parameters.stream().map(FcsReader.Parameter::name).toList();
        if (new java.util.HashSet<>(names).size() != names.size()) throw new IOException("Gating requires unique source parameter names; no identifier repair");
        int n = names.size(); decades = new double[n]; minimum = new double[n]; range = new double[n]; gain = new double[n]; var notes = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            var p = parameters.get(i); String[] exponent = p.amplification().split(",",-1);
            if (exponent.length != 2) throw new IOException("Parameter "+p.name()+": require the complete PnE pair");
            decades[i] = decimal(reader.numericMetadataText(exponent[0])); minimum[i] = decimal(reader.numericMetadataText(exponent[1])); range[i] = decimal(reader.numericMetadataText(p.range())); gain[i] = p.gain() == null ? 1 : decimal(reader.numericMetadataText(p.gain()));
            if (decades[i] < 0 || minimum[i] < 0 || range[i] <= 0 || gain[i] <= 0) throw new IOException("Parameter "+p.name()+": invalid amplifier/range/gain metadata");
            if (reader.info().version().equals("3.1") && !reader.info().dataType().equals("I") && (decades[i] != 0 || minimum[i] != 0)) throw new IOException("Floating FCS3.1 data requires PnE=0,0; no incompatible transform is inferred");
            if (decades[i] > 0 && minimum[i] == 0) {
                if (!legacyZeroMinimumAsOne || reader.info().version().equals("3.1")) throw new FcsReader.UnsupportedFormatException("Parameter "+p.name()+": log minimum zero is not defined. A supported export or an explicitly selected legacy interpretation is required; dependent analysis skipped");
                minimum[i] = 1; notes.add("Explicit legacy policy interprets PnE log minimum zero as one for "+p.name()+". Original metadata bytes remain unchanged; this is a declared interpretation, not an inferred paper setting.");
            }
            if (decades[i] == 0 && minimum[i] != 0) throw new IOException("Parameter "+p.name()+": linear amplification requires PnE=0,0");
        }
        List<String> known = List.of("$SPILLOVER","SPILL","$COMP","COMP").stream().filter(k -> reader.keyword(k) != null).toList();
        String selected = selection;
        GatingMl20.FcsCompensation parsed;
        if (selection.equals("AUTO")) {
            if (known.isEmpty()) selected = "NONE";
            else if (known.size() == 1 && List.of("$SPILLOVER","SPILL").contains(known.get(0))) selected = known.get(0);
            else selected = "UNRESOLVED";
        }
        if (selected.equals("NONE")) {
            if (!known.isEmpty()) throw new IOException("Declared absent compensation conflicts with original metadata; select the intended exact description or use explicitly uncompensated gates");
            parsed = GatingMl20.FcsCompensation.absent(); compensationKeyword = null;
        } else if (selected.equals("UNRESOLVED") || selected.equals("$COMP") || selected.equals("COMP")) {
            parsed = GatingMl20.FcsCompensation.unavailable("Multiple or legacy compensation descriptions require an exact supported selection/export; no first-description or inversion convention is guessed: "+known);
            compensationKeyword = null;
            notes.add("Original-file compensation is unresolved. Uncompensated gates may run; any FCS-compensation-dependent gate causes the complete requested analysis to be skipped.");
        } else {
            if (!List.of("$SPILLOVER","SPILL").contains(selected)) throw new IllegalArgumentException("Select AUTO, NONE, $SPILLOVER or SPILL");
            String value = reader.keyword(selected);
            if (value == null) throw new FcsReader.UnsupportedFormatException("Selected original compensation keyword is absent: "+selected+"; dependent analysis skipped");
            parsed = GatingMl20.FcsCompensation.available(spillover(value, selected, names, cutoff, reader)); compensationKeyword = selected;
        }
        compensation = parsed;
        notes.add("Channel-to-scale uses the declared PnE/PnR antilog or PnG division (unit gain when absent). It applies no compensation by itself, no calibration, no time-in-seconds conversion and no display/gating transform. Gating-ML must select further operations explicitly.");
        warnings = List.copyOf(notes);
    }
    /** AUTO selects only one unambiguous supported description; legacy zero-log-minimum is never implicit. */
    public static FcsGatingInput prepare(FcsReader reader, String compensationKeywordSelection,
                                         boolean legacyZeroMinimumAsOne, double relativeSvdCutoff) throws IOException {
        return new FcsGatingInput(reader, compensationKeywordSelection, legacyZeroMinimumAsOne, relativeSvdCutoff);
    }
    public List<String> names() { return names; }
    public List<String> warnings() { return warnings; }
    public GatingMl20.FcsCompensation compensation() { return compensation; }
    public String compensationKeyword() { return compensationKeyword; }
    /** Converts a complete stored-channel vector, preserving the caller's original array. */
    public double[] scale(double[] stored) {
        if (stored == null || stored.length != names.size()) throw new IllegalArgumentException("Require the complete source parameter vector");
        double[] result = new double[stored.length];
        for (int i = 0; i < stored.length; i++) {
            if (!Double.isFinite(stored[i])) throw new IllegalArgumentException("Nonfinite stored value for "+names.get(i)+"; no dropped event or inferred scale value");
            result[i] = decades[i] > 0 ? Math.pow(10,decades[i]*stored[i]/range[i])*minimum[i] : stored[i]/gain[i];
            if (!Double.isFinite(result[i])) throw new ArithmeticException("Nonfinite channel-to-scale output for "+names.get(i)+" at these units/precision");
        }
        return result;
    }
    private static CytometryCompensation spillover(String text, String keyword, List<String> sourceNames, double cutoff, FcsReader reader) throws IOException {
        String[] cells = text.split(",",-1); int n;
        cells[0] = reader.numericMetadataText(cells[0]);
        try { if (!cells[0].matches("[0-9]+")) throw new NumberFormatException(); n = Integer.parseInt(cells[0]); }
        catch (NumberFormatException error) { throw new IOException("Invalid complete spillover dimension count",error); }
        if (n < 1 || n > 512 || cells.length != 1+n+n*n) throw new IOException("Complete spillover shape is invalid or outside 1..512 channels");
        List<String> names = List.of(java.util.Arrays.copyOfRange(cells,1,n+1));
        if (names.stream().anyMatch(s -> !sourceNames.contains(s)) || new java.util.HashSet<>(names).size() != n) throw new IOException("Spillover names are duplicate or absent from the exact original source axis");
        double[][] values = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) values[i][j] = decimal(reader.numericMetadataText(cells[1+n+i*n+j]));
        if (keyword.equals("$SPILLOVER")) for (int i = 0; i < n; i++) if (values[i][i] != 1) throw new IOException("Standard $SPILLOVER diagonal must be one; no normalization or repair");
        return CytometryCompensation.spectrum(names,names,values,cutoff);
    }
    private static double decimal(String value) throws IOException {
        if (!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new IOException("Require finite explicit FCS scale/compensation decimals; no trimming or nonfinite replacement");
        double result = Double.parseDouble(value); if (!Double.isFinite(result)) throw new IOException("Nonfinite FCS scale/compensation value"); return result;
    }
}
