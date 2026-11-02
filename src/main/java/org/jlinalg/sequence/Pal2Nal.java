/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded native codon back-translation using PAL2NAL v14 matching tables and
 * ten-residue anchors. This is a native API, not a byte-compatible PAL2NAL CLI.
 * Every pairing, relaxed-match, placement and filtering choice is explicit.
 * Standard peptide letters, X, stop '*', '-' and frame-shift digits 1..9 are
 * supported. Optional documented-symbol normalization fixes v14's '.'/'_'
 * re-gapping defect explicitly. U and other unknown residues are rejected.
 * No reverse-complement, splice alignment, genetic-code inference or partial
 * anchor chaining is performed. A complete result does not establish biological
 * correspondence. The caller must choose verification thresholds.
 */
public final class Pal2Nal {
    private Pal2Nal() { }

    /** Original v14 table IDs; table 10 retains the explicitly warned source defect. */
    public static Set<Integer> supportedCodonTables() { return Pal2NalCodes.tableIds(); }

    public enum Pairing { EXACT_ID, INPUT_ORDER }
    public enum Matching { EXACT_ONLY, ALLOW_V14_RELAXED }
    public enum Placement { REQUIRE_UNIQUE, SOURCE_LEFTMOST }
    public enum GapStopFilter { KEEP, V14_NOGAP, SELECTED_V14_TABLE_NOGAP }
    public enum State { GAP, FRAME_SHIFT, VERIFIED, WILDCARD, MISMATCH }
    public enum Drop { UNSELECTED_COLUMN, MISMATCH_COLUMN, GAP_TRIPLET, STOP_TRIPLET }

    public record Sequence(String id, String sequence) {
        public Sequence { Objects.requireNonNull(id); Objects.requireNonNull(sequence); }
    }

    /** A non-null selection contains exactly one '#' or space per input column. */
    public record Options(int codonTable, Pairing pairing, Matching matching,
                          Placement placement, GapStopFilter gapStopFilter,
                          boolean removeMismatchColumns, String selection,
                          boolean normalizeDocumentedSymbols, boolean normalizeTfastx,
                          int minimumVerifiedResidues, double minimumVerifiedFraction) {
        public Options {
            Pal2NalCodes.requireTable(codonTable);
            Objects.requireNonNull(pairing); Objects.requireNonNull(matching);
            Objects.requireNonNull(placement); Objects.requireNonNull(gapStopFilter);
            if (minimumVerifiedResidues < 0 || !Double.isFinite(minimumVerifiedFraction)
                    || minimumVerifiedFraction < 0 || minimumVerifiedFraction > 1)
                throw new IllegalArgumentException("Require a nonnegative count and a finite verification fraction in [0,1]");
        }
    }

    public record Limits(int sequences, int proteinColumns, int dnaCharactersPerRow,
                         long inputCells, long outputCells, long workSteps,
                         long estimatedRetainedBytes) {
        public Limits {
            if (sequences < 1 || proteinColumns < 1 || dnaCharactersPerRow < 1
                    || inputCells < 1 || outputCells < 1 || workSteps < 1 || estimatedRetainedBytes < 65536)
                throw new IllegalArgumentException("All complete-population capacities must be positive");
        }
        public static Limits defaults() {
            return new Limits(100_000, 1_000_000, 30_000_000, 30_000_000,
                    30_000_000, 200_000_000, 1_073_741_824);
        }
    }

    /** Coordinates are one-based; zero DNA start denotes a peptide gap. */
    public record Cell(int proteinColumn, int dnaStart, String bases, State state) { }
    /** Every expanded triplet is retained with all of its drop reasons. */
    public record Triplet(int expandedStart, int proteinColumn, int part,
                          int outputStart, Set<Drop> drops) {
        public Triplet { drops = Set.copyOf(drops); }
    }
    public record Row(String proteinId, String dnaId, String originalProtein,
                      String normalizedProtein, String originalDna,
                      int dnaStart, int dnaEnd, boolean relaxed,
                      int placementsAtLeast, int verifiedResidues, int wildcardResidues,
                      int mismatchedResidues, List<Cell> cells, String expandedAlignment,
                      String alignment) {
        public Row { cells = List.copyOf(cells); }
    }
    public record Result(Options options, List<Row> rows, List<Triplet> triplets,
                         List<String> warnings, int proteinColumns, int expandedColumns,
                         int outputColumns, long workSteps) {
        public Result {
            rows = List.copyOf(rows); triplets = List.copyOf(triplets); warnings = List.copyOf(warnings);
        }
        public Map<String,String> alignedSequences() {
            var values = new LinkedHashMap<String,String>();
            for (Row row : rows) values.put(row.proteinId(), row.alignment());
            return Collections.unmodifiableMap(values);
        }
    }

    /** Strict, complete in-memory input. No row, symbol, DNA tail or filter is inferred. */
    public static Result align(List<Sequence> proteins, List<Sequence> dna,
                               Options options, Limits limits, Runnable cancellation) throws IOException {
        Objects.requireNonNull(proteins); Objects.requireNonNull(dna); Objects.requireNonNull(options);
        Objects.requireNonNull(limits); Objects.requireNonNull(cancellation);
        var budget = new Budget(limits.workSteps(), cancellation);
        budget.check();
        if (proteins.isEmpty() || proteins.size() != dna.size() || proteins.size() > limits.sequences())
            throw new IOException("Require equal, nonempty complete peptide/DNA populations within the sequence capacity");
        // Reject capacity before copying populations or allocating per-cell ledgers.
        int columns = proteins.get(0).sequence().length();
        if (columns < 1 || columns > limits.proteinColumns()) throw new IOException("Protein column capacity exceeded or empty alignment");
        long inputCells = 0, retained = 65536;
        var proteinIds = new HashSet<String>();
        var dnaIds = new HashSet<String>();
        for (Sequence row : proteins) {
            budget.step(); validateId(row.id());
            if (!proteinIds.add(row.id())) throw new IOException("Duplicate peptide ID: " + row.id());
            if (row.sequence().length() != columns) throw new IOException("Ragged peptide alignment: " + row.id());
            inputCells = plus(inputCells, row.sequence().length());
            retained = plus(retained, plus(times(row.sequence().length(), 176), times(row.id().length(), 8)));
        }
        for (Sequence row : dna) {
            budget.step(); validateId(row.id());
            if (!dnaIds.add(row.id())) throw new IOException("Duplicate DNA ID: " + row.id());
            if (row.sequence().isEmpty() || row.sequence().length() > limits.dnaCharactersPerRow())
                throw new IOException("DNA is empty or exceeds its row capacity: " + row.id());
            inputCells = plus(inputCells, row.sequence().length());
            retained = plus(retained, plus(times(row.sequence().length(), 8), times(row.id().length(), 8)));
        }
        if (inputCells > limits.inputCells() || retained > limits.estimatedRetainedBytes())
            throw new IOException("Complete input/retained-storage capacity exceeded; no prefix is admitted");
        proteins = List.copyOf(proteins); dna = List.copyOf(dna);
        if (options.pairing() == Pairing.EXACT_ID && !proteinIds.equals(dnaIds))
            throw new IOException("Exact peptide/DNA ID sets differ; positional fallback is never implicit");
        String selection = options.selection();
        if (selection != null && (selection.length() != columns || selection.indexOf('#') < 0
                || selection.chars().anyMatch(c -> c != '#' && c != ' ')))
            throw new IOException("A selection must contain '#' and have one '#' or space per complete peptide column");
        var dnaById = new LinkedHashMap<String,Sequence>();
        for (Sequence row : dna) dnaById.put(row.id(), row);
        var warnings = new ArrayList<String>();
        if (options.codonTable() == 10)
            warnings.add("SOURCE_V14_TABLE_10: original v14 uses universal-code TGA=stop, not the NCBI Euplotid TGA=Cys definition");
        String missingStarts = switch (options.codonTable()) {
            case 3 -> "GTG"; case 11 -> "ATT, ATC, ATA"; case 13 -> "ATA"; default -> null;
        };
        if (missingStarts != null)
            warnings.add("SOURCE_V14_INITIATORS: original table " + options.codonTable() + " lacks current NCBI start codons "
                    + missingStarts + "; retain the source definition and inspect any start mismatch instead of silently substituting a newer code");
        if (options.pairing() == Pairing.INPUT_ORDER)
            warnings.add("EXPLICIT_POSITIONAL_PAIRING: DNA IDs are not used to establish correspondence; review every recorded pair");
        if (options.gapStopFilter() == GapStopFilter.V14_NOGAP)
            warnings.add("SOURCE_V14_NOGAP: stop filtering uses the uppercase universal-code pattern, regardless of the selected code; lowercase stops survive");
        if (options.gapStopFilter() == GapStopFilter.SELECTED_V14_TABLE_NOGAP)
            warnings.add("NATIVE_STOP_FILTER: case-insensitive selected-v14-table filtering intentionally differs from original v14 -nogap; the v14 table-10 defect remains");
        String[] normalized = new String[proteins.size()];
        int[] widths = new int[columns]; java.util.Arrays.fill(widths, 3);
        for (int i = 0; i < proteins.size(); i++) {
            budget.step();
            normalized[i] = normalize(proteins.get(i).sequence(), options, warnings, proteins.get(i).id(), budget);
            for (int c = 0; c < columns; c++) {
                budget.step(); char aa = normalized[i].charAt(c);
                if (aa >= '1' && aa <= '9') widths[c] = Math.max(widths[c], ((aa - '1') / 3 + 1) * 3);
            }
        }
        int expanded = 0;
        for (int width : widths) {
            if (expanded > Integer.MAX_VALUE - width) throw new IOException("Expanded alignment width exceeds integer capacity");
            expanded += width;
        }
        long outputCells = times(expanded, proteins.size());
        retained = plus(retained, plus(times(outputCells, 8), times(expanded, 96)));
        if (outputCells > limits.outputCells() || retained > limits.estimatedRetainedBytes())
            throw new IOException("Complete expanded-output/retained-storage capacity exceeded");
        var staged = new ArrayList<Row>();
        boolean[] mismatchColumns = new boolean[columns];
        for (int i = 0; i < proteins.size(); i++) {
            budget.check();
            Sequence protein = proteins.get(i);
            Sequence nucleotides = options.pairing() == Pairing.EXACT_ID ? dnaById.get(protein.id()) : dna.get(i);
            for (int c = 0; c < nucleotides.sequence().length(); c++) {
                budget.step();
                if ("ACGTURYSWKMBDHVNacgturyswkmbdhvn".indexOf(nucleotides.sequence().charAt(c)) < 0)
                    throw new IOException("Non-IUPAC DNA character in " + nucleotides.id() + " at " + (c + 1));
            }
            Match match = match(normalized[i], nucleotides.sequence(), options, budget);
            if (match.offset < 0) throw new IOException("No complete PAL2NAL v14 matching path for peptide " + protein.id());
            if (options.placement() == Placement.REQUIRE_UNIQUE && match.count > 1)
                throw new IOException("Multiple DNA placements for " + protein.id() + "; require a reviewed explicit leftmost choice");
            if (match.count > 1) warnings.add("MULTIPLE_PLACEMENTS: " + protein.id() + " uses the first of at least two complete matches");
            if (match.relaxed) warnings.add("RELAXED_V14_ANCHORS: " + protein.id() + " uses wildcard anchors; inspect verification counts and every mismatch");
            var cells = new ArrayList<Cell>(columns);
            var alignment = new StringBuilder(expanded);
            int pos = match.offset, verified = 0, wildcard = 0, mismatched = 0, residues = 0;
            boolean seenLetter = false;
            for (int c = 0; c < columns; c++) {
                budget.step(); char aa = normalized[i].charAt(c);
                int length = aa == '-' ? 0 : aa >= '1' && aa <= '9' ? aa - '0' : 3;
                if (pos > nucleotides.sequence().length() - length)
                    throw new IOException("Native DNA coordinate audit failed for " + protein.id());
                String bases = nucleotides.sequence().substring(pos, pos + length);
                State state;
                if (aa == '-') state = State.GAP;
                else if (aa >= '1' && aa <= '9') state = State.FRAME_SHIFT;
                else {
                    residues++;
                    if (aa == 'X') { state = State.WILDCARD; wildcard++; }
                    else {
                        boolean initiation = (match.relaxed ? residues == 1 : !seenLetter) && aa == 'M';
                        boolean okay = match.relaxed && initiation
                                ? Pal2NalCodes.matchesFallbackStart(bases)
                                : Pal2NalCodes.matches(options.codonTable(), initiation ? 'B' : aa, bases);
                        if (okay) { state = State.VERIFIED; verified++; }
                        else { state = State.MISMATCH; mismatched++; mismatchColumns[c] = true; }
                        seenLetter = true;
                    }
                }
                cells.add(new Cell(c + 1, length == 0 ? 0 : pos + 1, bases, state));
                alignment.append(bases).append("-".repeat(widths[c] - length));
                pos += length;
            }
            if (pos != match.offset + match.width) throw new IOException("Native complete DNA-consumption audit failed");
            if (verified < options.minimumVerifiedResidues() || (residues == 0 ? 0.0 : (double)verified / residues) < options.minimumVerifiedFraction())
                throw new IOException("Verification thresholds not met for " + protein.id() + ": " + verified + "/" + residues + " residues; no result admitted");
            if (wildcard > 0) warnings.add("UNVERIFIED_X: " + protein.id() + " has " + wildcard + " wildcard residues");
            if (mismatched > 0) warnings.add("MISMATCHED_CODONS: " + protein.id() + " has " + mismatched + " disagreeing residues; inspect every recorded cell before downstream analysis");
            if (verified == 0) warnings.add("NO_VERIFIED_RESIDUES: " + protein.id() + " satisfies only the explicitly permissive thresholds; conversion is not evidence of biological correspondence");
            if (match.offset > 0 || pos < nucleotides.sequence().length())
                warnings.add("UNPLACED_DNA_FLANKS: " + protein.id() + " retains " + match.offset + " leading and "
                        + (nucleotides.sequence().length() - pos) + " trailing input bases outside the codon alignment; no biological role is inferred");
            staged.add(new Row(protein.id(), nucleotides.id(), protein.sequence(), normalized[i], nucleotides.sequence(),
                    match.offset + 1, pos, match.relaxed, match.count, verified, wildcard, mismatched, cells,
                    alignment.toString(), ""));
        }
        var triplets = new ArrayList<Triplet>(expanded / 3);
        int originalStart = 0, output = 0;
        for (int c = 0; c < columns; c++) {
            for (int part = 0; part < widths[c] / 3; part++) {
                budget.step(); int position = originalStart + 3 * part;
                var drops = java.util.EnumSet.noneOf(Drop.class);
                if (selection != null && selection.charAt(c) != '#') drops.add(Drop.UNSELECTED_COLUMN);
                if (options.removeMismatchColumns() && mismatchColumns[c]) drops.add(Drop.MISMATCH_COLUMN);
                if (options.gapStopFilter() != GapStopFilter.KEEP) {
                    for (Row row : staged) {
                        budget.step(); String triplet = row.expandedAlignment().substring(position, position + 3);
                        if (triplet.indexOf('-') >= 0) drops.add(Drop.GAP_TRIPLET);
                        boolean stop = options.gapStopFilter() == GapStopFilter.V14_NOGAP
                                ? Pal2NalCodes.matchesUniversalUppercaseStop(triplet)
                                : Pal2NalCodes.matches(options.codonTable(), '*', triplet);
                        if (stop) drops.add(Drop.STOP_TRIPLET);
                    }
                }
                triplets.add(new Triplet(position + 1, c + 1, part + 1, drops.isEmpty() ? output + 1 : 0, drops));
                if (drops.isEmpty()) output += 3;
            }
            originalStart += widths[c];
        }
        var result = new ArrayList<Row>();
        for (Row row : staged) {
            var filtered = new StringBuilder(output);
            for (Triplet triplet : triplets) {
                budget.step();
                if (triplet.drops().isEmpty()) filtered.append(row.expandedAlignment(), triplet.expandedStart() - 1, triplet.expandedStart() + 2);
            }
            if (filtered.length() != output) throw new IOException("Native output-width audit failed");
            result.add(new Row(row.proteinId(), row.dnaId(), row.originalProtein(), row.normalizedProtein(), row.originalDna(),
                    row.dnaStart(), row.dnaEnd(), row.relaxed(), row.placementsAtLeast(), row.verifiedResidues(),
                    row.wildcardResidues(), row.mismatchedResidues(), row.cells(), row.expandedAlignment(), filtered.toString()));
        }
        if (output == 0) warnings.add("EMPTY_FILTERED_ALIGNMENT: all expanded triplets were explicitly removed; no downstream fit is justified");
        budget.check();
        return new Result(options, result, triplets, warnings, columns, expanded, output, budget.used);
    }

    private static void validateId(String id) throws IOException {
        if (id.isBlank() || id.length() > 1_048_576 || id.codePoints().anyMatch(Character::isISOControl))
            throw new IOException("IDs must be nonblank Unicode text without control characters or unpaired surrogates");
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == id.length() || !Character.isLowSurrogate(id.charAt(i))) throw new IOException("Unpaired surrogate in ID");
            } else if (Character.isLowSurrogate(c)) throw new IOException("Unpaired surrogate in ID");
        }
    }
    private static String normalize(String text, Options options, List<String> warnings, String id, Budget budget) throws IOException {
        // ASCII symbols only, so uppercasing can never change the alignment width.
        for (int i = 0; i < text.length(); i++) { budget.step(); if (text.charAt(i) > 127) throw new IOException("Non-ASCII peptide symbol at " + (i + 1) + " in " + id); }
        String value = text.toUpperCase(Locale.ROOT);
        if (options.normalizeDocumentedSymbols() && (value.indexOf('.') >= 0 || value.indexOf('_') >= 0)) {
            value = value.replace('.', '-').replace('_', '*');
            warnings.add("NATIVE_DOCUMENTED_SYMBOL_NORMALIZATION: " + id + " maps '.' to gap and '_' to stop; fixes the original v14 re-gapping defect");
        }
        if (options.normalizeTfastx()) {
            String old = value;
            value = value.replace('\\', '1').replaceAll("/(-*)[A-Z*]", "-$12");
            if (!value.equals(old)) warnings.add("TFASTX_NORMALIZATION: " + id + " uses the original v14 frame-shift rewrite");
        }
        for (int i = 0; i < value.length(); i++) {
            budget.step();
            if ("ACDEFGHIKLMNPQRSTVWYX*-123456789".indexOf(value.charAt(i)) < 0)
                throw new IOException("Unsupported peptide symbol at " + (i + 1) + " in " + id + "; no unknown residue is silently taken as X");
        }
        if (value.length() != text.length()) throw new IOException("Peptide normalization changed the alignment width");
        return value;
    }

    private record Step(int width, char residue, boolean start, boolean wildcard) { }
    private record Match(int offset, int count, int width, boolean relaxed) { }
    private static List<Step> steps(String peptide, int table, boolean fallback) {
        Pal2NalCodes.requireTable(table);
        var result = new ArrayList<Step>(); boolean word = false;
        for (int i = 0; i < peptide.length(); i++) {
            char c = peptide.charAt(i);
            if (c == '-') continue;
            if (c >= '1' && c <= '9') result.add(new Step(c - '0', c, false, true));
            else {
                result.add(new Step(3, c, !word && c == 'M', c == 'X'));
                if (c != 'X') word = true;
            }
        }
        return result;
    }
    private static Match match(String peptide, String dna, Options options, Budget budget) throws IOException {
        List<Step> full = steps(peptide, options.codonTable(), false);
        int width = 0;
        for (Step step : full) {
            budget.step();
            if (width > Integer.MAX_VALUE - step.width) throw new IOException("Matcher width exceeds integer capacity");
            width += step.width;
        }
        if (width == 0) throw new IOException("An all-gap peptide row has no nucleotide correspondence");
        Match exact = search(full, dna, options.codonTable(), false, budget);
        if (exact.offset >= 0 || options.matching() == Matching.EXACT_ONLY) return exact;
        // Match the original anchor partition, including its raw-character tail-length test.
        var anchors = new ArrayList<String>(); int start = 0, count = 0;
        for (int i = 0; i < peptide.length(); i++) {
            budget.step(); if (peptide.charAt(i) != '-') count++;
            if (count == 10 || i == peptide.length() - 1) {
                anchors.add(peptide.substring(start, i + 1)); start = i + 1; count = 0;
            }
        }
        if (anchors.get(anchors.size() - 1).length() < 10) {
            // Original v14 has an undefined-pattern short-peptide fallback. Refuse it explicitly.
            if (anchors.size() == 1) return new Match(-1, 0, width, true);
            String tail = anchors.remove(anchors.size() - 1);
            int last = anchors.size() - 1; anchors.set(last, anchors.get(last) + tail);
        }
        var relaxed = new ArrayList<Step>();
        for (int i = 0; i < anchors.size(); i++) {
            List<Step> anchor = steps(anchors.get(i), options.codonTable(), true);
            if (i != 0) anchor = anchor.stream().map(step -> new Step(step.width, step.residue, false, step.wildcard)).toList();
            Match found = search(anchor, dna, options.codonTable(), true, budget);
            if (found.offset < 0) anchor = anchor.stream().map(step -> new Step(step.width, step.residue, false, true)).toList();
            relaxed.addAll(anchor);
        }
        return search(relaxed, dna, options.codonTable(), true, budget);
    }
    private static Match search(List<Step> steps, String dna, int table, boolean fallback, Budget budget) throws IOException {
        int width = 0;
        for (Step step : steps) { budget.step(); if (width > Integer.MAX_VALUE - step.width) throw new IOException("Matcher width exceeds integer capacity"); width += step.width; }
        if (width > dna.length()) return new Match(-1, 0, width, fallback);
        int first = -1, count = 0;
        for (int offset = 0; offset <= dna.length() - width; offset++) {
            budget.step(); int position = offset; boolean okay = true;
            for (Step step : steps) {
                budget.step();
                if (!step.wildcard) {
                    String triplet = dna.substring(position, position + step.width);
                    boolean matches = fallback && step.start ? Pal2NalCodes.matchesFallbackStart(triplet)
                            : Pal2NalCodes.matches(table, step.start ? 'B' : step.residue, triplet);
                    if (!matches) { okay = false; break; }
                }
                position += step.width;
            }
            if (okay) { if (first < 0) first = offset; if (++count == 2) break; }
        }
        return new Match(first, count, width, fallback);
    }
    private static long plus(long a, long b) throws IOException { try { return Math.addExact(a, b); } catch (ArithmeticException e) { throw new IOException("Capacity arithmetic overflow", e); } }
    private static long times(long a, long b) throws IOException { try { return Math.multiplyExact(a, b); } catch (ArithmeticException e) { throw new IOException("Capacity arithmetic overflow", e); } }
    private static final class Budget {
        private final long maximum; private final Runnable cancellation; private long used;
        Budget(long maximum, Runnable cancellation) { this.maximum = maximum; this.cancellation = cancellation; }
        void check() { cancellation.run(); }
        void step() throws IOException {
            if (used == maximum) throw new IOException("Complete matching/work capacity exceeded; no partial result is admitted");
            used++; if ((used & 1023) == 0) check();
        }
    }
}
