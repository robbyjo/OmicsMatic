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
import java.util.List;
import org.jlinalg.sequence.RsubreadCountMatrix;
import org.jlinalg.sequence.RsubreadCountMatrix.Name;
import org.jlinalg.sequence.RsubreadCountResult;
import org.jlinalg.sequence.RsubreadDataFrame;
import org.jlinalg.sequence.RsubreadDataFrame.Column;
import org.jlinalg.sequence.RsubreadDataFrame.RowNames;
import org.jlinalg.sequence.RsubreadDataFrame.Type;

/** Standalone oracle replay: no R, canonical executable or other project library. */
public final class RsubreadResultValueAudit {
    private RsubreadResultValueAudit() { }
    private static int frames, cells, results, rejected;
    @FunctionalInterface private interface Attempt { void run() throws Exception; }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void reject(Attempt attempt) throws Exception {
        try { attempt.run(); } catch (IOException | IllegalArgumentException expected) { rejected++; return; }
        throw new AssertionError("Invalid complete result/frame was accepted");
    }
    private static RsubreadDataFrame frame(Path path, Path copy) throws Exception {
        String original = Files.readString(path, StandardCharsets.US_ASCII); RsubreadDataFrame value;
        try (var in = new StringReader(original)) { value = RsubreadDataFrame.readWire(in, RsubreadDataFrame.Limits.defaults()); }
        var out = new StringWriter(); value.writeWire(out);
        require(original.equals(out.toString()), "Raw values/types/order/row names differ: " + path);
        Files.writeString(copy, out.toString(), StandardCharsets.US_ASCII); frames++; cells += value.rows() * value.columns(); return value;
    }
    private static RsubreadCountResult result(Path source, Path out) throws Exception {
        Files.createDirectory(out); RsubreadCountMatrix counts;
        try (var in = Files.newBufferedReader(source.resolve("counts.matrix"), StandardCharsets.US_ASCII)) {
            counts = RsubreadCountMatrix.readWire(in, RsubreadCountMatrix.Limits.defaults());
        }
        var wire = new StringWriter(); counts.writeWire(wire);
        require(wire.toString().equals(Files.readString(source.resolve("counts.matrix"))), "Count matrix differs");
        Files.writeString(out.resolve("counts.matrix"), wire.toString(), StandardCharsets.US_ASCII);
        var annotation = frame(source.resolve("annotation.frame"), out.resolve("annotation.frame"));
        var stat = frame(source.resolve("stat.frame"), out.resolve("stat.frame"));
        var targets = frame(source.resolve("targets.frame"), out.resolve("targets.frame"));
        require(targets.columns() == 1 && targets.column(0).type() == Type.CHARACTER, "Invalid literal target vector");
        RsubreadDataFrame junction = Files.exists(source.resolve("counts_junction.frame"))
            ? frame(source.resolve("counts_junction.frame"), out.resolve("counts_junction.frame")) : null;
        List<String> members = Files.readAllLines(source.resolve("members.txt"));
        require(members.equals(junction == null ? List.of("counts", "annotation", "targets", "stat")
            : List.of("counts", "counts_junction", "annotation", "targets", "stat")), "Original list members/order differ");
        Files.write(out.resolve("members.txt"), members, StandardCharsets.US_ASCII);
        var result = new RsubreadCountResult(counts, annotation, targets.column(0).characters(), stat, junction); results++; return result;
    }
    private static RsubreadDataFrame replace(RsubreadDataFrame frame, int index, Column replacement) {
        var columns = new ArrayList<>(frame.columnList()); columns.set(index, replacement);
        return new RsubreadDataFrame(frame.rowNamesKind(), frame.rowNames(), columns);
    }
    private static void construct(RsubreadCountResult r, RsubreadDataFrame annotation, List<Name> targets,
            RsubreadDataFrame stat, RsubreadDataFrame junction) {
        new RsubreadCountResult(r.counts(), annotation, targets, stat, junction);
    }
    private static void corruptions(RsubreadCountResult r, RsubreadCountResult j) throws Exception {
        var annotation = r.annotation(); var stat = r.stat(); var targets = r.targets();
        reject(() -> construct(r, annotation, List.of(Name.literal("wrong sample")), stat, null));
        reject(() -> construct(r, replace(annotation, 0, Column.characters(Name.literal("GeneID"),
            java.util.Collections.nCopies(annotation.rows(), Name.literal("wrong gene")))), targets, stat, null));
        reject(() -> construct(r, replace(annotation, 0, Column.characters(Name.literal("wrong field"),
            annotation.column(0).characters())), targets, stat, null));
        int[] wrongLength = new int[annotation.rows()]; Arrays.fill(wrongLength, 1);
        reject(() -> construct(r, replace(annotation, 5, Column.integers(Name.literal("Length"), wrongLength)), targets, stat, null));
        reject(() -> construct(r, replace(annotation, 2, Column.characters(Name.literal("Start"),
            java.util.Collections.nCopies(annotation.rows(), Name.literal("0")))), targets, stat, null));
        reject(() -> construct(r, replace(annotation, 1, Column.characters(Name.literal("Chr"),
            java.util.Collections.nCopies(annotation.rows(), Name.literal("")))), targets, stat, null));
        reject(() -> construct(r, replace(annotation, 4, Column.characters(Name.literal("Strand"),
            java.util.Collections.nCopies(annotation.rows(), Name.literal("?")))), targets, stat, null));
        reject(() -> construct(r, annotation, targets, replace(stat, 0,
            Column.characters(Name.literal("Status"), java.util.Collections.nCopies(stat.rows(), Name.literal("Assigned")))), null));
        reject(() -> construct(r, annotation, targets, replace(stat, 1,
            Column.integers(Name.literal("wrong sample"), new int[stat.rows()])), null));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, .5}) {
            long[] values = new long[stat.rows()]; values[0] = Double.doubleToRawLongBits(invalid);
            reject(() -> construct(r, annotation, targets, replace(stat, 1, Column.doubles(targets.get(0), values)), null));
        }
        int[] missing = new int[stat.rows()]; missing[0] = Integer.MIN_VALUE;
        reject(() -> construct(r, annotation, targets, replace(stat, 1, Column.integers(targets.get(0), missing)), null));
        var junction = j.junction().orElseThrow();
        reject(() -> construct(j, j.annotation(), j.targets(), j.stat(), replace(junction, 7,
            Column.integers(Name.literal("Location_SP1"), new int[junction.rows()]))));
        reject(() -> construct(j, j.annotation(), j.targets(), j.stat(), replace(junction, 14,
            Column.integers(Name.literal("wrong sample"), new int[junction.rows()]))));
        reject(() -> construct(j, j.annotation(), j.targets(), j.stat(), replace(junction, 6,
            Column.characters(Name.literal("Chr_SP1"), java.util.Collections.nCopies(junction.rows(), Name.na())))));
    }
    private static void frameContract(RsubreadDataFrame edge) throws Exception {
        var out = new StringWriter(); edge.writeWire(out); String valid = out.toString();
        var cap = RsubreadDataFrame.Limits.defaults();
        for (String bad : List.of(valid + "extra\n", valid.substring(0, valid.length() - 1), valid.replace("\n", "\r\n"),
                valid.replaceFirst("RSUBREAD_FRAME_V1", "RSUBREAD_FRAME_V2"), valid.replaceFirst("EXPLICIT", "AUTOMATIC"),
                valid.replaceFirst("INTEGER", "FACTOR"), valid.replaceFirst("V\\t80000000", "V\t0000000g"),
                valid.replaceFirst("LOGICAL", "INTEGER\tEXTRA"), valid.replaceFirst("7\\t4", "07\t4"),
                valid.replaceFirst("C\\tCHARACTER\\tS", "C\tCHARACTER\tSc0af"))) {
            reject(() -> RsubreadDataFrame.readWire(new StringReader(bad), cap));
        }
        for (var limit : List.of(new RsubreadDataFrame.Limits(6, 4, 28, 1024, 10000),
                new RsubreadDataFrame.Limits(7, 3, 28, 1024, 10000), new RsubreadDataFrame.Limits(7, 4, 27, 1024, 10000),
                new RsubreadDataFrame.Limits(7, 4, 28, 1, 10000), new RsubreadDataFrame.Limits(7, 4, 28, 1024, 1)))
            reject(() -> RsubreadDataFrame.readWire(new StringReader(valid), limit));
        reject(() -> Column.logicals(Name.literal("x"), new int[]{2}));
        reject(() -> new RsubreadDataFrame(RowNames.EXPLICIT, List.of(Name.literal("a"), Name.literal("a")), List.of()));
        reject(() -> new RsubreadDataFrame(RowNames.EXPLICIT, List.of(Name.na()), List.of()));
        reject(() -> new RsubreadDataFrame(RowNames.AUTOMATIC, List.of(Name.literal("2")), List.of()));
        reject(() -> new RsubreadDataFrame(RowNames.EXPLICIT, List.of(Name.literal("a")), List.of(Column.integers(Name.literal("x"), new int[0]))));
        long[] input = edge.column(2).rawWords(); long original = edge.column(2).rawWord(0); input[0] = 0;
        require(edge.column(2).rawWord(0) == original, "Returned numeric array aliases internal state");
        require(edge.column(0).characters().get(0).equals(Name.literal("NA")) && edge.column(0).characters().get(1).missing()
            && edge.column(0).characters().get(2).equals(Name.literal("")), "NA/literal NA/empty cells conflated");
        require(edge.column(2).rawWord(0) == 0x7ff00000000007a2L && edge.column(2).rawWord(4) == Long.MIN_VALUE,
            "R NA payload or negative zero changed");
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 2, "Require source and NEW output directories"); Path source = Path.of(args[0]), out = Path.of(args[1]);
        require(!Files.exists(out), "Output must be NEW"); Files.createDirectories(out);
        var all = new ArrayList<RsubreadCountResult>();
        try (var paths = Files.list(source)) {
            for (Path path : paths.filter(Files::isDirectory).sorted().toList())
                if (Files.exists(path.resolve("counts.matrix"))) all.add(result(path, out.resolve(path.getFileName())));
        }
        require(results == 16, "Expected the complete original sixteen-case family");
        var bySingle = all.stream().filter(x -> x.junction().isEmpty() && x.counts().columns() == 1
            && x.counts().rows() == 5).findFirst().orElseThrow();
        var byJunction = all.stream().filter(x -> x.junction().isPresent() && x.junction().orElseThrow().rows() > 0).findFirst().orElseThrow();
        corruptions(bySingle, byJunction);
        var edge = frame(source.resolve("scalar-edge.frame"), out.resolve("scalar-edge.frame")); frameContract(edge);
        frame(source.resolve("zero-rows.frame"), out.resolve("zero-rows.frame")); frame(source.resolve("zero-columns.frame"), out.resolve("zero-columns.frame"));
        String json = "{\"status\":\"passed-complete-original-Rsubread-result-values-and-relationships-only\",\"original_results\":" + results
            + ",\"frames\":" + frames + ",\"scalar_cells\":" + cells + ",\"contract_rejections\":" + rejected
            + ",\"implementation_credit\":false,\"full_adapter_available\":false,\"independent_read_counting\":false}\n";
        Files.writeString(out.resolve("audit.json"), json, StandardCharsets.US_ASCII); System.out.print(json);
    }
}
