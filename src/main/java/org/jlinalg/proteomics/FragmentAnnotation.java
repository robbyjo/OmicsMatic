/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.proteomics;

import java.util.Objects;
import java.util.regex.Pattern;

/** Literal b/y backbone-ion annotation. No mass matching, charge guessing or loss inference. */
public record FragmentAnnotation(char series, int number, int charge) {
    private static final Pattern SYNTAX = Pattern.compile("([by])([1-9][0-9]*)\\^([1-9][0-9]*)");

    public FragmentAnnotation {
        if ((series != 'b' && series != 'y') || number < 1 || charge < 1)
            throw new IllegalArgumentException("Require explicit b/y series, positive fragment number and positive charge");
    }

    /** Accepts the entire literal notation, e.g. y12^3. Unsupported or missing information fails. */
    public static FragmentAnnotation parse(String value) {
        Objects.requireNonNull(value, "annotation");
        if (value.length() > 64) throw new IllegalArgumentException("Fragment annotation exceeds 64 characters");
        var match = SYNTAX.matcher(value);
        if (!match.matches())
            throw new IllegalArgumentException("Unsupported explicit fragment annotation: require bN^Z or yN^Z without whitespace, leading zeros or losses");
        try {
            return new FragmentAnnotation(match.group(1).charAt(0), Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Fragment number or charge exceeds signed integer capacity", error);
        }
    }

    /** This grammar describes an unshifted backbone ion; loss-bearing syntax is refused by parse. */
    public String lossType() { return "noloss"; }
}
