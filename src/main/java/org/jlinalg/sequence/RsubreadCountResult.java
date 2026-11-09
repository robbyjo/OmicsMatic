/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jlinalg.sequence.RsubreadCountMatrix.Name;
import org.jlinalg.sequence.RsubreadDataFrame.Column;
import org.jlinalg.sequence.RsubreadDataFrame.Type;

/**
 * Count-list relationships for the two source-checked Rsubread reference editions.
 * Validates every stored annotation interval, strand-aware union length, sample
 * position and summary/junction count. Does not recount reads or validate a fit.
 */
public final class RsubreadCountResult {
    public static final List<String> ANNOTATION = List.of("GeneID", "Chr", "Start", "End", "Strand", "Length");
    public static final List<String> STATUSES = List.of("Assigned", "Unassigned_Unmapped", "Unassigned_Read_Type",
        "Unassigned_Singleton", "Unassigned_MappingQuality", "Unassigned_Chimera", "Unassigned_FragmentLength",
        "Unassigned_Duplicate", "Unassigned_MultiMapping", "Unassigned_Secondary", "Unassigned_NonSplit",
        "Unassigned_NoFeatures", "Unassigned_Overlapping_Length", "Unassigned_Ambiguity");
    public static final List<String> JUNCTION = List.of("Gene_SP1", "Gene_SP2", "Transcript", "Status", "Donor", "Acceptor",
        "Chr_SP1", "Location_SP1", "Strand_SP1", "Chr_SP2", "Location_SP2", "Strand_SP2",
        "NearestExonBoundary_SP1", "NearestExonBoundary_SP2");

    private final RsubreadCountMatrix counts;
    private final RsubreadDataFrame annotation;
    private final RsubreadDataFrame stat;
    private final RsubreadDataFrame junction;
    private final List<Name> targets;

    /**
     * GeneID storage supports CHARACTER, INTEGER and LOGICAL, including NA.
     * Other original storage fails explicitly rather than guessing R text coercion.
     * Additional annotation columns remain literal and are not interpreted here.
     */
    public RsubreadCountResult(RsubreadCountMatrix counts, RsubreadDataFrame annotation,
            List<Name> targets, RsubreadDataFrame stat, RsubreadDataFrame junction) {
        this.counts = Objects.requireNonNull(counts); this.annotation = Objects.requireNonNull(annotation);
        this.targets = List.copyOf(targets); this.stat = Objects.requireNonNull(stat); this.junction = junction;
        require(this.targets.equals(counts.columnNames()), "Targets and count columns differ by position");
        require(annotation.rows() == counts.rows() && annotation.columns() >= ANNOTATION.size(), "Annotation shape differs");
        headers(annotation, ANNOTATION);
        for (int row = 0; row < counts.rows(); row++) {
            require(geneId(annotation.column(0), row).equals(counts.rowNames().get(row)), "GeneID/count row identity differs");
            checkAnnotationRow(annotation, row);
        }
        require(stat.columns() == targets.size() + 1 && stat.rows() == STATUSES.size(), "Summary shape differs");
        require(stat.column(0).name().equals(Name.literal("Status")) && stat.column(0).type() == Type.CHARACTER,
            "Summary Status field differs");
        require(stat.column(0).characters().equals(STATUSES.stream().map(Name::literal).toList()), "Summary status population/order differs");
        samples(stat, 1);
        if (junction != null) {
            require(junction.columns() == JUNCTION.size() + targets.size(), "Junction shape differs from the 14-field layout");
            headers(junction, JUNCTION); samples(junction, JUNCTION.size());
            for (int row = 0; row < junction.rows(); row++) {
                for (int c : new int[]{6, 9}) require(!scalar(junction.column(c), row).isEmpty(), "Missing junction chromosome");
                for (int c : new int[]{7, 10}) positiveCoordinate(scalar(junction.column(c), row));
            }
        }
    }
    public RsubreadCountMatrix counts() { return counts; }
    public RsubreadDataFrame annotation() { return annotation; }
    public List<Name> targets() { return targets; }
    public RsubreadDataFrame stat() { return stat; }
    public Optional<RsubreadDataFrame> junction() { return Optional.ofNullable(junction); }

    private void samples(RsubreadDataFrame frame, int offset) {
        for (int c = 0; c < targets.size(); c++) {
            Column column = frame.column(c + offset);
            require(column.name().equals(targets.get(c)), "Sample header differs by position");
            require(column.type() == Type.INTEGER || column.type() == Type.DOUBLE || column.size() == 0,
                "Nonempty count column is not numeric");
            for (int row = 0; row < column.size(); row++) {
                double value = column.numeric(row);
                require(Double.isFinite(value) && value >= 0 && value == Math.rint(value), "Invalid summary/junction count");
            }
        }
    }
    private static void headers(RsubreadDataFrame frame, List<String> expected) {
        for (int i = 0; i < expected.size(); i++) require(frame.column(i).name().equals(Name.literal(expected.get(i))), "Original field order/name differs");
    }
    private static Name geneId(Column column, int row) {
        if (column.type() == Type.CHARACTER) return column.characters().get(row);
        if (column.type() == Type.INTEGER) return column.rawWord(row) == Integer.MIN_VALUE ? Name.na() : Name.literal(Long.toString(column.rawWord(row)));
        if (column.type() == Type.LOGICAL) return column.rawWord(row) == Integer.MIN_VALUE ? Name.na() : Name.literal(column.rawWord(row) == 0 ? "FALSE" : "TRUE");
        throw new IllegalArgumentException("Unsupported original GeneID storage; retain original R object and skip dependent interpretation");
    }
    private record Interval(long start, long end) { }
    private record Group(String chromosome, String strand) { }
    private static void checkAnnotationRow(RsubreadDataFrame frame, int row) {
        String[] chr = scalar(frame.column(1), row).split(";", -1), start = scalar(frame.column(2), row).split(";", -1),
            end = scalar(frame.column(3), row).split(";", -1), strand = scalar(frame.column(4), row).split(";", -1);
        require(chr.length == start.length && chr.length == end.length && chr.length == strand.length, "Annotation interval populations differ");
        Map<Group, List<Interval>> groups = new HashMap<>();
        for (int i = 0; i < chr.length; i++) {
            require(!chr[i].isEmpty() && Set.of("+", "-", ".").contains(strand[i]), "Invalid annotation chromosome/strand");
            long a = positiveCoordinate(start[i]), b = positiveCoordinate(end[i]);
            require(a <= b, "Reversed original annotation interval");
            groups.computeIfAbsent(new Group(chr[i], strand[i]), k -> new ArrayList<>()).add(new Interval(a, b));
        }
        long length = 0;
        for (List<Interval> intervals : groups.values()) {
            intervals.sort(Comparator.comparingLong(Interval::start).thenComparingLong(Interval::end));
            long a = intervals.get(0).start, b = intervals.get(0).end;
            for (int i = 1; i < intervals.size(); i++) {
                Interval interval = intervals.get(i);
                if (interval.start <= b + 1) b = Math.max(b, interval.end);
                else { length = Math.addExact(length, b - a + 1); a = interval.start; b = interval.end; }
            }
            length = Math.addExact(length, b - a + 1);
        }
        require(length == positiveCoordinate(scalar(frame.column(5), row)), "Original annotation length differs from chromosome-and-strand union");
    }
    private static long positiveCoordinate(String text) {
        require(text.matches("[1-9][0-9]*"), "Original coordinate/length is not a positive decimal integer");
        long value = Long.parseLong(text); require(value <= 4294967295L, "Original coordinate/length exceeds uint32"); return value;
    }
    private static String scalar(Column column, int row) {
        if (column.type() == Type.CHARACTER) {
            Name name = column.characters().get(row); require(!name.missing(), "Missing annotation/junction coordinate"); return name.value();
        }
        require(column.type() == Type.INTEGER || column.type() == Type.DOUBLE, "Unsupported coordinate/length storage");
        double value = column.numeric(row);
        require(Double.isFinite(value) && value >= 1 && value <= 4294967295L && value == Math.rint(value), "Invalid numeric coordinate/length");
        return Long.toString((long) value);
    }
    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalArgumentException(detail); }
}
