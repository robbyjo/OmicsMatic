/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.RsubreadCountMatrix;
import org.jlinalg.sequence.RsubreadCountMatrix.Limits;
import org.jlinalg.sequence.RsubreadCountMatrix.Name;
import org.jlinalg.sequence.RsubreadCountMatrix.RowNamesPolicy;
import org.jlinalg.sequence.RsubreadCountMatrix.Storage;

/** Complete original R count-matrix and cbind replay; no counting/adapter credit. */
public final class RsubreadMatrixAudit {
    private RsubreadMatrixAudit() { }
    private static int rejected;
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void reject(Checked action) throws Exception {
        try { action.run(); } catch (Exception expected) { rejected++; return; }
        throw new AssertionError("Invalid contract unexpectedly accepted");
    }
    private static RsubreadCountMatrix load(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.US_ASCII)) {
            return RsubreadCountMatrix.readWire(reader, Limits.defaults());
        }
    }
    private static String wire(RsubreadCountMatrix matrix) throws IOException {
        var writer = new StringWriter(); matrix.writeWire(writer); return writer.toString();
    }
    private static List<Map<String,String>> manifest(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file); String[] header = fields(lines.get(0)); var rows = new ArrayList<Map<String,String>>();
        for (String line : lines.subList(1, lines.size())) {
            String[] values = fields(line); if (values.length != header.length) throw new IOException("Manifest dimensions differ");
            var row = new LinkedHashMap<String,String>(); for (int i = 0; i < header.length; i++) row.put(header[i], values[i]); rows.add(row);
        }
        return rows;
    }
    private static String[] fields(String line) {
        return Arrays.stream(line.split("\t", -1)).map(s -> s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1).replace("\"\"", "\"") : s).toArray(String[]::new);
    }
    private static void sameWire(RsubreadCountMatrix matrix, Path expected) throws IOException {
        if (!wire(matrix).equals(Files.readString(expected, StandardCharsets.US_ASCII))) throw new AssertionError("Original matrix bits/names/storage/order differ: " + expected);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Require original fixture root and new audit output");
        Path root = Path.of(args[0]).toRealPath(), out = Path.of(args[1]); Files.createDirectory(out);
        List<Map<String,String>> counts = manifest(root.resolve("original-cases.tsv")), bindings = manifest(root.resolve("matrix-bind-cases.tsv"));
        if (counts.size() != 16 || bindings.size() != 10) throw new AssertionError("Complete original case population required");
        long values = 0, names = 0;
        for (var row : counts) {
            Path source = root.resolve(row.get("id")).resolve("counts.matrix"); RsubreadCountMatrix matrix = load(source); sameWire(matrix, source);
            if (matrix.rows() != Integer.parseInt(row.get("rows")) || matrix.columns() != Integer.parseInt(row.get("columns")) || !matrix.storage().name().equals(row.get("storage").toUpperCase(java.util.Locale.ROOT))) throw new AssertionError("Original dimensions/storage differ");
            values += (long) matrix.rows() * matrix.columns(); names += matrix.rows() + matrix.columns();
        }
        int positive = 0, originalErrors = 0, strictNameRejections = 0;
        for (var row : bindings) {
            Path home = root.resolve("matrix-bind").resolve(row.get("id")); var inputs = new ArrayList<RsubreadCountMatrix>();
            for (int i = 1; i <= Integer.parseInt(row.get("inputs")); i++) inputs.add(load(home.resolve("input" + i + ".matrix")));
            if (row.get("status").equals("original-error")) {
                if (!Files.isRegularFile(home.resolve("original-error.txt")) || Files.exists(home.resolve("expected.matrix"))) throw new AssertionError("Original error was repaired");
                for (RowNamesPolicy policy : RowNamesPolicy.values()) reject(() -> RsubreadCountMatrix.columnBind(inputs, policy)); originalErrors++; continue;
            }
            RsubreadCountMatrix.Bound bound = RsubreadCountMatrix.columnBind(inputs, RowNamesPolicy.RETAIN_FIRST_POSITIONAL); sameWire(bound.matrix(), home.resolve("expected.matrix"));
            List<Integer> differences = row.get("differing_inputs").isEmpty() ? List.of() : Arrays.stream(row.get("differing_inputs").split(",")).map(Integer::valueOf).toList();
            if (!bound.differingInputs().equals(differences) || bound.rowNamesDiffer() != !differences.isEmpty()) throw new AssertionError("Differing input provenance omitted");
            if (differences.isEmpty()) sameWire(RsubreadCountMatrix.columnBind(inputs, RowNamesPolicy.REQUIRE_IDENTICAL).matrix(), home.resolve("expected.matrix"));
            else { reject(() -> RsubreadCountMatrix.columnBind(inputs, RowNamesPolicy.REQUIRE_IDENTICAL)); strictNameRejections++; }
            positive++; values += (long) bound.matrix().rows() * bound.matrix().columns(); names += bound.matrix().rows() + bound.matrix().columns();
        }
        if (positive != 9 || originalErrors != 1 || strictNameRejections != 1) throw new AssertionError("Original binding/error family population differs");
        List<Name> rows = List.of(Name.literal("gene")), columns = List.of(Name.literal("library"));
        for (double value : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) reject(() -> new RsubreadCountMatrix(Storage.DOUBLE, rows, columns, new double[] {value}));
        for (double value : new double[] {0.5, 2147483648.0, -0.0}) reject(() -> new RsubreadCountMatrix(Storage.INTEGER, rows, columns, new double[] {value}));
        reject(() -> new RsubreadCountMatrix(Storage.DOUBLE, rows, columns, new double[0]));
        reject(() -> new RsubreadCountMatrix(Storage.DOUBLE, rows, List.of(), new double[0]));
        reject(() -> new Name(false, null)); reject(() -> new Name(true, "NA")); reject(() -> Name.literal("\ud800")); reject(() -> Name.literal("\0"));
        reject(() -> RsubreadCountMatrix.columnBind(List.of(), RowNamesPolicy.REQUIRE_IDENTICAL));
        reject(() -> new Limits(-1, 1, 1, 10, 10));
        var base = new RsubreadCountMatrix(Storage.DOUBLE, rows, columns, new double[] {1}); String original = wire(base);
        for (String bad : List.of("", original + "\n", original.substring(0, original.length() - 1), original.replace("MATRIX_V1", "MATRIX_V2"), original.replace("DOUBLE", "LOGICAL"), original.replace("\t1\t1\n", "\t01\t1\n"), original.replace("R\tS67656e65", "C\tS67656e65"), original.replace("S67656e65", "Sff"), original.replace("S67656e65", "S0"), original.replace("S67656e65", "SAB"), original.replace("\n", "\r\n"), original.replace("3ff0000000000000", "3ff00000000000000"))) reject(() -> RsubreadCountMatrix.readWire(new StringReader(bad), Limits.defaults()));
        for (String bits : List.of("bff0000000000000", "7ff8000000000000", "7ff0000000000000", "fff0000000000000")) {
            String bad = original.replace("3ff0000000000000", bits); reject(() -> RsubreadCountMatrix.readWire(new StringReader(bad), Limits.defaults()));
        }
        for (String bits : List.of("3fe0000000000000", "8000000000000000", "41e0000000000000")) {
            String bad = original.replace("DOUBLE", "INTEGER").replace("3ff0000000000000", bits); reject(() -> RsubreadCountMatrix.readWire(new StringReader(bad), Limits.defaults()));
        }
        for (Limits limits : List.of(new Limits(0, 1, 1, 20, 20), new Limits(1, 1, 0, 20, 20), new Limits(1, 1, 1, 1, 20), new Limits(1, 1, 1, 20, 1))) reject(() -> RsubreadCountMatrix.readWire(new StringReader(original), limits));
        double[] source = {1}; var mutableRows = new ArrayList<>(rows); var immutable = new RsubreadCountMatrix(Storage.INTEGER, mutableRows, columns, source); source[0] = 99; mutableRows.set(0, Name.literal("changed")); double[] returned = immutable.columnMajor(); returned[0] = 77;
        if (immutable.value(0,0) != 1 || !immutable.rowNames().equals(rows)) throw new AssertionError("Caller mutation changed a retained matrix");
        reject(() -> immutable.rowNames().set(0, Name.literal("changed")));
        var tiny = new RsubreadCountMatrix(Storage.DOUBLE, rows, columns, new double[] {Double.MIN_VALUE}); if (Double.doubleToRawLongBits(RsubreadCountMatrix.readWire(new StringReader(wire(tiny)), Limits.defaults()).value(0,0)) != 1) throw new AssertionError("Subnormal count bit pattern changed");
        Map<String,Object> report = new LinkedHashMap<>(); report.put("status", "passed-complete-original-R-count-matrices-and-positional-cbind-only"); report.put("original_count_matrices", counts.size()); report.put("original_cbind_positive", positive); report.put("original_cbind_errors", originalErrors); report.put("strict_name_mismatch_rejections", strictNameRejections); report.put("numeric_values_replayed", values); report.put("names_replayed", names); report.put("contract_rejections", rejected); report.put("analysis_admitted", false); report.put("implementation_credit", false); report.put("full_adapter_available", false);
        Files.writeString(out.resolve("audit.json"), RegenieRuntime.json(report) + "\n"); System.out.println(RegenieRuntime.json(report));
    }
}
