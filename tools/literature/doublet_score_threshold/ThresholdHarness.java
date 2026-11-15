/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.singlecell.DoubletScoreThreshold;

/** Standalone cross-JVM consumer of the complete independent R fixtures. */
public final class ThresholdHarness {
    private static void require(boolean okay, String detail) {
        if (!okay) throw new AssertionError(detail);
    }
    private static double decode(String bits) {
        return Double.longBitsToDouble(Long.parseUnsignedLong(bits, 16));
    }
    private static int[] rows(String column) {
        return column.isEmpty() ? new int[0] : Arrays.stream(column.split(",")).mapToInt(Integer::parseInt).toArray();
    }
    public static void main(String[] args) throws Exception {
        Map<String,List<Double>> groups = new LinkedHashMap<>();
        int cases = 0;
        List<String> classificationLines = Files.readAllLines(Path.of(args[0]));
        for (String line : classificationLines.subList(1, classificationLines.size())) {
            String[] fields = line.split("\t", -1);
            double value = decode(fields[1]);
            require(DoubletScoreThreshold.classify(value, decode(fields[0])).name().equals(fields[2]), line);
            groups.computeIfAbsent(fields[0], ignored -> new ArrayList<>()).add(value);
            cases++;
        }
        List<String> selectionLines = Files.readAllLines(Path.of(args[1]));
        for (String line : selectionLines.subList(1, selectionLines.size())) {
            String[] fields = line.split("\t", -1);
            double[] values = groups.get(fields[0]).stream().mapToDouble(Double::doubleValue).toArray();
            long[] before = Arrays.stream(values).mapToLong(Double::doubleToRawLongBits).toArray();
            var actual = DoubletScoreThreshold.select(values, decode(fields[0]));
            require(Arrays.equals(actual.retainedRows(), rows(fields[1])), "Retained rows " + fields[0]);
            require(Arrays.equals(actual.removedRows(), rows(fields[2])), "Removed rows " + fields[0]);
            require(Arrays.equals(actual.unknownRows(), rows(fields[3])), "Unknown rows " + fields[0]);
            require(Arrays.equals(before, Arrays.stream(values).mapToLong(Double::doubleToRawLongBits).toArray()),
                "Input bits changed " + fields[0]);
            int[] expected = actual.retainedRows();
            if (expected.length > 0) {
                int[] exposed = actual.retainedRows(); exposed[0] = Integer.MIN_VALUE;
                require(Arrays.equals(expected, actual.retainedRows()), "Getter exposes mutable rows");
            }
            if (Double.doubleToRawLongBits(decode(fields[0])) == Double.doubleToRawLongBits(0.3)) {
                var convenience = DoubletScoreThreshold.hicat109ReportedCutoff(values);
                require(Arrays.equals(convenience.retainedRows(), actual.retainedRows()), "Convenience retained");
                require(Arrays.equals(convenience.removedRows(), actual.removedRows()), "Convenience removed");
                require(Arrays.equals(convenience.unknownRows(), actual.unknownRows()), "Convenience unknown");
            }
        }
        var empty = DoubletScoreThreshold.hicat109ReportedCutoff(new double[0]);
        require(empty.retainedRows().length + empty.removedRows().length + empty.unknownRows().length == 0,
            "Empty input adds rows");
        int[] supplied = {7, 8};
        var owned = new DoubletScoreThreshold.Selection(supplied, new int[0], new int[0]);
        supplied[0] = 99;
        require(owned.retainedRows()[0] == 7, "Constructor exposes mutable rows");
        System.out.println("PASS_R_ORACLE_" + cases + "_COMPARISONS_" + groups.size()
            + "_COMPLETE_SELECTIONS_INPUT_BITS_AND_DEFENSIVE_COPIES Java=" + System.getProperty("java.version"));
    }
}
