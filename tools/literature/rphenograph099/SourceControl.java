/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.singlecell.RphenographJaccard099;

/** Compares every returned entry with independently captured original C++ output. */
public final class SourceControl {
    private SourceControl() { }
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void refuses(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Invalid complete input was accepted");
    }
    public static void main(String[] args) throws Exception {
        int cases = 0;
        long completeRows = 0;
        try (var input = Files.newBufferedReader(Path.of(args[0], "coefficient-inputs.tsv"));
             var original = Files.newBufferedReader(Path.of(args[0], "coefficient-original.tsv"))) {
            check(input.readLine().equals("case_id\trows\tcolumns\trow_major_one_based_neighbors"), "input header");
            check(original.readLine().equals("case_id\toutput_row\tfrom\tto\tweight"), "original header");
            String line;
            while ((line = input.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                int rows = Integer.parseInt(fields[1]), columns = Integer.parseInt(fields[2]);
                String[] values = fields[3].isEmpty() ? new String[0] : fields[3].split(",", -1);
                int[] indices = new int[values.length];
                for (int i = 0; i < values.length; i++) indices[i] = Integer.parseInt(values[i]);
                var actual = RphenographJaccard099.compute(rows, columns, indices, Long.MAX_VALUE);
                check(actual.inputRows() == rows && actual.neighborColumns() == columns, fields[0] + " shape");
                check(actual.rawRows() == (long) rows * columns, fields[0] + " full output population");
                int positive = 0;
                boolean padding = false;
                for (int i = 0; i < actual.rawRows(); i++) {
                    String saved = original.readLine();
                    check(saved != null, fields[0] + " missing original row");
                    String[] expected = saved.split("\t", -1);
                    check(expected[0].equals(fields[0]) && Integer.parseInt(expected[1]) == i + 1, "original complete order");
                    check(actual.fromOneBased(i) == Integer.parseInt(expected[2]), fields[0] + " from");
                    check(actual.toOneBased(i) == Integer.parseInt(expected[3]), fields[0] + " to");
                    check(Double.doubleToLongBits(actual.weight(i)) == Double.doubleToLongBits(Double.parseDouble(expected[4])), fields[0] + " exact coefficient");
                    if (actual.fromOneBased(i) == 0) padding = true;
                    else { check(!padding, "positive row after zero padding"); positive++; }
                }
                check(actual.positiveRows() == positive && actual.zeroPaddedRows() == actual.rawRows() - positive, "full event census");
                cases++;
                completeRows += actual.rawRows();
            }
            check(original.readLine() == null, "excess original rows");
        }
        refuses(() -> RphenographJaccard099.compute(-1, 0, new int[0], 1));
        refuses(() -> RphenographJaccard099.compute(1, 1, new int[] {0}, 2));
        refuses(() -> RphenographJaccard099.compute(1, 1, new int[] {2}, 2));
        refuses(() -> RphenographJaccard099.compute(2, 1, new int[] {1}, 4));
        refuses(() -> RphenographJaccard099.compute(1, 1, new int[] {1}, 1));
        refuses(() -> RphenographJaccard099.compute(1, 1, new int[] {1}, -1));
        refuses(() -> RphenographJaccard099.compute(Integer.MAX_VALUE, 2, new int[0], Long.MAX_VALUE));
        refuses(() -> RphenographJaccard099.compute(new int[][] {{1}, {}}, 100));
        refuses(() -> RphenographJaccard099.compute(new int[][] {null}, 100));
        var empty = RphenographJaccard099.compute(new int[0][], 0);
        check(empty.rawRows() == 0, "empty convenience matrix");
        check(cases == 863 && completeRows == 105921, "complete original bank population");
        System.out.println("PASS_COMPLETE_ORIGINAL_RPHENOGRAPH099_CPP_PARITY cases=" + cases + " rows=" + completeRows + " assertions=" + assertions);
    }
}
