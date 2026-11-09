/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.sequence.RsubreadCountMatrix;
import org.jlinalg.sequence.RsubreadCountMatrix.Name;
import org.jlinalg.sequence.RsubreadCountResult;
import org.jlinalg.sequence.RsubreadDataFrame;
import org.jlinalg.sequence.RsubreadDataFrame.Column;
import org.jlinalg.sequence.RsubreadDataFrame.Type;

/** Fresh source-counted edge cases; literal values are compared before admission. */
public final class RsubreadResultEdgeAudit {
    private RsubreadResultEdgeAudit() { }
    private static int frames, rejected;
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static RsubreadDataFrame frame(Path path, Path out) throws Exception {
        RsubreadDataFrame value;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            value = RsubreadDataFrame.readWire(reader, RsubreadDataFrame.Limits.defaults());
        }
        var writer = new StringWriter(); value.writeWire(writer);
        require(writer.toString().equals(Files.readString(path, StandardCharsets.US_ASCII)), "Fresh complete frame differs");
        Files.writeString(out, writer.toString(), StandardCharsets.US_ASCII); frames++; return value;
    }
    private static RsubreadDataFrame replace(RsubreadDataFrame f, int at, Column column) {
        var columns = new ArrayList<>(f.columnList()); columns.set(at, column);
        return new RsubreadDataFrame(f.rowNamesKind(), f.rowNames(), columns);
    }
    private static void reject(RsubreadCountResult r, RsubreadDataFrame annotation) {
        try { new RsubreadCountResult(r.counts(), annotation, r.targets(), r.stat(), r.junction().orElse(null)); }
        catch (IllegalArgumentException expected) { rejected++; return; }
        throw new AssertionError("Fresh annotation corruption/unsupported coercion was accepted");
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 2, "Require fresh original profile and NEW native output");
        Path root = Path.of(args[0]), out = Path.of(args[1]); require(!Files.exists(out), "Require NEW output"); Files.createDirectories(out);
        List<String> cases = List.of("union-strands", "numeric-ids", "logical-ids", "empty-junctions", "extra-attributes", "bam-single",
            "bam-paired-fragments", "bam-paired-reads", "bam-junctions");
        for (String id : cases) {
            Path source = root.resolve(id).resolve("values"), home = out.resolve(id); Files.createDirectory(home);
            RsubreadCountMatrix matrix;
            try (var reader = Files.newBufferedReader(source.resolve("counts.matrix"), StandardCharsets.US_ASCII)) {
                matrix = RsubreadCountMatrix.readWire(reader, RsubreadCountMatrix.Limits.defaults());
            }
            var writer = new StringWriter(); matrix.writeWire(writer);
            require(writer.toString().equals(Files.readString(source.resolve("counts.matrix"), StandardCharsets.US_ASCII)), "Fresh matrix differs");
            Files.writeString(home.resolve("counts.matrix"), writer.toString(), StandardCharsets.US_ASCII);
            var annotation = frame(source.resolve("annotation.frame"), home.resolve("annotation.frame"));
            var stat = frame(source.resolve("stat.frame"), home.resolve("stat.frame"));
            var targets = frame(source.resolve("targets.frame"), home.resolve("targets.frame"));
            var junction = Files.exists(source.resolve("counts_junction.frame"))
                ? frame(source.resolve("counts_junction.frame"), home.resolve("counts_junction.frame")) : null;
            var result = new RsubreadCountResult(matrix, annotation, targets.column(0).characters(), stat, junction);
            if (id.equals("union-strands")) {
                require(annotation.column(5).numeric(0) == 175, "Strand/chromosome union differs from original C control");
                reject(result, replace(annotation, 4, Column.characters(Name.literal("Strand"), List.of(Name.literal("+;+;+;+")))));
                reject(result, replace(annotation, 1, Column.characters(Name.literal("Chr"), List.of(Name.literal("chr1;chr1;chr1;chr1")))));
                reject(result, replace(annotation, 5, Column.integers(Name.literal("Length"), new int[]{125})));
            } else if (id.equals("numeric-ids")) {
                require(annotation.column(0).type() == Type.INTEGER && matrix.rowNames().equals(List.of(Name.literal("123"), Name.literal("456"))), "Integer identifiers changed");
                reject(result, replace(annotation, 0, Column.doubles(Name.literal("GeneID"), new long[]{Double.doubleToRawLongBits(123), Double.doubleToRawLongBits(456)})));
            } else if (id.equals("logical-ids")) {
                require(annotation.column(0).type() == Type.LOGICAL && matrix.rowNames().equals(List.of(Name.literal("TRUE"), Name.literal("FALSE"), Name.na())), "Logical/NA identifiers changed");
            } else if (id.equals("empty-junctions")) {
                require(junction != null && junction.rows() == 0 && junction.columns() == 15
                    && junction.column(14).type() == Type.LOGICAL, "Empty-junction shape/storage was repaired");
            } else if (id.equals("extra-attributes")) require(annotation.columns() == 7, "Extra attribute was dropped");
        }
        require(rejected == 4, "Fresh negative control population differs");
        String report = "{\"status\":\"passed-nine-fresh-original-Rsubread-result-edge-controls-only\",\"original_results\":9,\"frames\":" + frames
            + ",\"contract_rejections\":" + rejected + ",\"implementation_credit\":false,\"full_adapter_available\":false}\n";
        Files.writeString(out.resolve("audit.json"), report, StandardCharsets.US_ASCII); System.out.print(report);
    }
}
