/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * Independent streaming implementation of the CADD1.6 consequence-tier selection contract.
 * The separately obtained original program is the behavioral reference. No model parameters
 * are included. This operation groups consecutive rows by their first five literal fields;
 * it neither annotates variants nor establishes pathogenicity. Source nonfinite comparisons,
 * first ties, GPS borrowing and score-suffix propagation remain explicit.
 */
public final class CaddConsequenceHierarchy {
    private CaddConsequenceHierarchy() { }

    public record Limits(long maximumInputCharacters, long maximumGroupCharacters,
                         int maximumLineCharacters, int maximumGroupRows) {
        public Limits {
            if (maximumInputCharacters < 1 || maximumGroupCharacters < 1 || maximumLineCharacters < 1 || maximumGroupRows < 1)
                throw new IllegalArgumentException("Positive complete-stream/group capacities are required");
        }
    }
    /** phredColumnPresent is the original --PHRED default-column convention; RawScore headers take precedence. */
    public record Options(boolean allAnnotationLines, boolean noHeader, boolean phredColumnPresent, Limits limits) {
        public Options { Objects.requireNonNull(limits, "limits"); }
    }
    public record Statistics(long inputRows, long outputRows, long consecutiveGroups, long headerLines,
                             long nonfiniteInputScores, long nonfiniteSelectedGroups, long inputCharacters) {
        public boolean hasNonfiniteComparisons() { return nonfiniteInputScores != 0; }
    }

    /**
     * Processes the entire character stream without closing caller-owned objects. Header line endings
     * are preserved; data rows have the original program's LF serialization. Capacities count UTF-16
     * code units. On error, any already-written prefix is incomplete: callers must retain diagnostics
     * and admit no complete result. Returned statistics certify only this operation's completion.
     */
    public static Statistics transform(Reader input, Appendable output, Options options) throws IOException {
        Objects.requireNonNull(input, "input"); Objects.requireNonNull(output, "output"); Objects.requireNonNull(options, "options");
        var state = new State(output, options); var line = new StringBuilder(); char[] buffer = new char[8192];
        try {
            for (int count; (count = input.read(buffer)) != -1;) {
                for (int i = 0; i < count; i++) {
                    if (line.length() >= options.limits().maximumLineCharacters()) throw new IOException("Complete CADD hierarchy line capacity exceeded; no truncated result");
                    line.append(buffer[i]);
                    if (buffer[i] == '\n') { state.accept(line.toString()); line.setLength(0); }
                }
            }
            if (!line.isEmpty()) state.accept(line.toString());
            state.flush();
            return new Statistics(state.inputRows, state.outputRows, state.groups, state.headerLines,
                    state.nonfiniteInputs, state.nonfiniteSelected, state.characters);
        } catch (ArithmeticException | IndexOutOfBoundsException invalid) {
            throw new IOException("Original CADD hierarchy column/counter cannot be assessed; no complete result", invalid);
        }
    }

    private static final Set<String> TIER_ZERO = Set.of("NON_SYNONYMOUS", "SYNONYMOUS", "STOP_GAINED", "STOP_LOST", "FRAME_SHIFT", "INFRAME", "CANONICAL_SPLICE", "SPLICE_SITE", "NONCODING_CHANGE");
    private static final Set<String> TIER_ONE = Set.of("REGULATORY", "5PRIME_UTR", "3PRIME_UTR");
    private static final Set<String> TIER_TWO = Set.of("INTRONIC", "UPSTREAM", "DOWNSTREAM");
    private static final Set<String> GPS_NAMES = Set.of("Grantham", "PolyPhenCat", "PolyPhenVal", "SIFTcat", "SIFTval");
    private record Choice(double score, String[] fields) { }

    private static final class State {
        final Appendable output; final Options options; final Choice[] best = new Choice[4];
        final List<String[]> all = new ArrayList<>();
        List<String> position;
        int scoreColumn, consequenceColumn = 7;
        int[] gps = {109, 110, 111, 112, 113, 114};
        String[] groupGps = {"NA"};
        long inputRows, outputRows, groups, headerLines, nonfiniteInputs, nonfiniteSelected, characters;
        long groupCharacters; int groupRows;
        State(Appendable output, Options options) { this.output = output; this.options = options; scoreColumn = options.phredColumnPresent() ? -2 : -1; }

        void accept(String physical) throws IOException {
            characters = Math.addExact(characters, physical.length());
            if (characters > options.limits().maximumInputCharacters()) throw new IOException("Complete CADD hierarchy input-character capacity exceeded; no subset");
            String[] fields = stripTrailing(physical).split("\t", -1);
            if (physical.startsWith("#")) {
                headerLines = Math.addExact(headerLines, 1);
                var indices = new ArrayList<Integer>();
                for (int i = 0; i < fields.length; i++) if (GPS_NAMES.contains(fields[i])) indices.add(i);
                gps = indices.stream().mapToInt(Integer::intValue).toArray();
                // The original header scan stops at RawScore, even if Consequence occurs later.
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i].equals("RawScore")) { scoreColumn = i; break; }
                    if (fields[i].equals("Consequence")) consequenceColumn = i;
                }
                if (!options.noHeader()) output.append(physical);
                return;
            }
            double score = CaddCalibration.parse(fields[index(scoreColumn, fields.length)]);
            inputRows = Math.addExact(inputRows, 1);
            if (!Double.isFinite(score)) nonfiniteInputs = Math.addExact(nonfiniteInputs, 1);
            List<String> next = Arrays.asList(Arrays.copyOf(fields, Math.min(5, fields.length)));
            if (!next.equals(position)) {
                flush(); position = List.copyOf(next); Arrays.fill(best, null); all.clear();
                groups = Math.addExact(groups, 1); groupRows = 0; groupCharacters = 0;
                groupGps = gpsValues(fields);
            }
            groupRows = Math.addExact(groupRows, 1); groupCharacters = Math.addExact(groupCharacters, physical.length());
            if (groupRows > options.limits().maximumGroupRows() || groupCharacters > options.limits().maximumGroupCharacters()) throw new IOException("Complete CADD hierarchy group capacity exceeded; no annotation-row subset");
            String consequence = fields[index(consequenceColumn, fields.length)];
            int tier = TIER_ZERO.contains(consequence) ? 0 : TIER_ONE.contains(consequence) ? 1 : TIER_TWO.contains(consequence) ? 2 : 3;
            // Primitive > intentionally preserves the original NaN, signed-zero and first-tie behavior.
            if (best[tier] == null || score > best[tier].score()) best[tier] = new Choice(score, fields);
            if (firstGps(groupGps).equals("NA") && !fields[firstGpsIndex()].equals("NA")) groupGps = gpsValues(fields);
            if (options.allAnnotationLines()) all.add(fields);
        }

        void flush() throws IOException {
            if (position == null) return;
            Choice selected = null; for (Choice candidate : best) if (candidate != null) { selected = candidate; break; }
            if (selected == null) throw new IOException("No original CADD hierarchy choice for a nonempty group");
            if (!Double.isFinite(selected.score())) nonfiniteSelected = Math.addExact(nonfiniteSelected, 1);
            if (options.allAnnotationLines()) {
                int from = slice(scoreColumn, selected.fields().length);
                String[] suffix = Arrays.copyOfRange(selected.fields(), from, selected.fields().length);
                for (String[] row : all) {
                    int prefix = slice(scoreColumn, row.length);
                    String[] result = Arrays.copyOf(row, prefix + suffix.length);
                    System.arraycopy(suffix, 0, result, prefix, suffix.length); emit(result);
                }
            } else {
                String[] row = selected.fields();
                if (row[firstGpsIndex()].equals("NA") && !firstGps(groupGps).equals("NA"))
                    for (int i = 0; i < Math.min(gps.length, groupGps.length); i++) row[gps[i]] = groupGps[i];
                emit(row);
            }
        }
        private void emit(String[] row) throws IOException { output.append(String.join("\t", row)).append('\n'); outputRows = Math.addExact(outputRows, 1); }
        private int firstGpsIndex() throws IOException { if (gps.length == 0) throw new IOException("Original CADD hierarchy has no GPS columns; no complete result"); return gps[0]; }
        private String firstGps(String[] values) throws IOException { if (values.length == 0) throw new IOException("Original CADD hierarchy has an empty GPS field group; no complete result"); return values[0]; }
        private String[] gpsValues(String[] row) { var values = new String[gps.length]; for (int i = 0; i < gps.length; i++) values[i] = row[gps[i]]; return values; }
    }
    private static int index(int column, int length) throws IOException { int result = column < 0 ? length + column : column; if (result < 0 || result >= length) throw new IOException("Original CADD hierarchy column is absent"); return result; }
    private static int slice(int column, int length) { return Math.max(0, Math.min(length, column < 0 ? length + column : column)); }
    private static String stripTrailing(String value) { int end = value.length(); while (end > 0 && " \t\r\n\u000b\f".indexOf(value.charAt(end - 1)) >= 0) end--; return value.substring(0, end); }
}
