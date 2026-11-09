/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.SnifflesVcf;

/** Native full-record VCF inspection; does not execute Sniffles or admit an SV-calling workflow. */
public final class SnifflesVcfCli {
    private SnifflesVcfCli() { }
    public static void main(String[] args) { System.exit(run(args)); }
    public static int run(String[] args) {
        Path out = null; int code = 2;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", "FAIL2_SNIFFLES_NATIVE_VCF_INSPECTION");
        state.put("inspection_admitted", false); state.put("SV_calling_executed", false);
        state.put("biological_or_paper_reproduction_certified", false);
        try {
            require(args.length == 6 && args[0].equals("--edition") && args[2].equals("--vcf") && args[4].equals("--out"),
                    "Use --edition 1.0.7|2.0.2|2.0.7|2.3.2|2.4 --vcf ABSOLUTE.vcf --out NEW_ABSOLUTE_DIRECTORY");
            Path requested = Path.of(args[5]);
            require(requested.isAbsolute() && requested.normalize().equals(requested) && requested.getParent() != null
                    && requested.getParent().toRealPath().equals(requested.getParent()), "Require canonical absolute parent and a NEW output directory");
            out = Files.createDirectory(requested);
            warnings(out, state);
            SnifflesVcf.Edition edition = null;
            for (var candidate : SnifflesVcf.Edition.values()) if (candidate.version().equals(args[1])) edition = candidate;
            require(edition != null, "Unsupported exact Sniffles edition");
            var limits = SnifflesVcf.Limits.bounded();
            write(out.resolve("effective-limits.json"), Map.of("maximum_bytes", limits.maximumBytes(), "maximum_records", limits.maximumRecords(),
                    "maximum_line_characters", limits.maximumLineCharacters(), "maximum_header_lines", limits.maximumHeaderLines(), "maximum_samples", limits.maximumSamples()));
            Path input = Path.of(args[3]);
            require(input.isAbsolute() && input.normalize().equals(input) && Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)
                    && input.toRealPath().equals(input), "Require a canonical absolute regular VCF input");
            var before = pin(input, limits.maximumBytes());
            Path captured = out.resolve("original.vcf"); Files.copy(input, captured);
            require(before.equals(pin(captured, limits.maximumBytes())), "Captured VCF differs from original input");
            write(out.resolve("input.json"), Map.of("original_path", input.toString(), "pin", before, "edition", edition.version()));
            Path variants = out.resolve("variants.jsonl");
            SnifflesVcf.Summary summary;
            final SnifflesVcf.Edition selected = edition;
            try (var rows = Files.newBufferedWriter(variants, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                summary = SnifflesVcf.read(Files.newInputStream(captured), edition, limits, header ->
                    write(captured.getParent().resolve("header.json"), Map.of("metadata_lines", header.metadataLines(),
                            "column_line", header.columnLine(), "samples", header.samples())), variant -> {
                        var record = new LinkedHashMap<String, Object>();
                        record.put("row", variant.row()); record.put("raw_line", variant.rawLine()); record.put("columns", variant.columns());
                        var info = new ArrayList<Object>();
                        for (var field : variant.info()) { var value = new LinkedHashMap<String, Object>(); value.put("key", field.key()); value.put("value", field.value()); info.add(value); }
                        record.put("info", info); record.put("format", variant.format()); record.put("samples", variant.samples());
                        record.put("read_support_info_field", selected.readSupportInfoField());
                        var support = variant.readSupport(selected);
                        record.put("read_support", support.isPresent() ? support.getAsLong() : null);
                        rows.write(SnifflesJson.encode(record)); rows.write('\n');
                    });
            }
            var report = new LinkedHashMap<String, Object>();
            report.put("records", summary.records()); report.put("variant_types", summary.variantTypes()); report.put("pass_records", summary.passRecords());
            report.put("support_present", summary.supportPresent()); report.put("support_missing", summary.supportMissing());
            report.put("phased_genotypes", summary.phasedGenotypes()); report.put("phase_info_records", summary.phaseInfoRecords());
            report.put("samples", summary.header().samples()); report.put("read_support_info_field", edition.readSupportInfoField());
            write(out.resolve("summary.json"), report);
            require(before.equals(pin(input, limits.maximumBytes())) && before.equals(pin(captured, limits.maximumBytes())), "Original or captured VCF changed during inspection");
            Map<String, Object> outputs = new LinkedHashMap<>();
            for (String name : List.of("original.vcf", "effective-limits.json", "input.json", "header.json", "variants.jsonl", "summary.json"))
                outputs.put(name, pin(out.resolve(name), limits.maximumBytes() * 8));
            state.put("outputs", outputs); state.put("edition", edition.version());
            state.put("status", "PASS_COMPLETE_NATIVE_SNIFFLES_VCF_INSPECTION_ONLY"); state.put("inspection_admitted", true);
            code = 0;
        } catch (Exception failure) {
            state.put("error", failure.toString());
            System.err.println("FAIL2: " + failure + "\nACTION: Preserve this attempt, inspect the named input/limits error, and retry with a NEW output directory. No SV-calling result was admitted.");
        } finally {
            if (out != null) try { warnings(out, state); write(out.resolve("COMPLETED.json"), state); }
            catch (IOException failure) { System.err.println("FAIL2: final inspection receipt unavailable: " + failure); code = 2; }
        }
        if (code == 0) System.out.println(state.get("status") + ": " + out);
        return code;
    }
    static Map<String, Object> pin(Path file, long maximum) throws IOException {
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && file.toRealPath().equals(file), "Missing/linked regular file: " + file);
        long bytes = 0; MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) != -1;) { bytes = Math.addExact(bytes, read); require(bytes <= maximum, "File exceeds declared complete byte capacity: " + file); digest.update(buffer, 0, read); }
        }
        return Map.of("bytes", bytes, "sha256", HexFormat.of().formatHex(digest.digest()));
    }
    static void write(Path file, Object value) throws IOException { Files.writeString(file, SnifflesJson.encode(value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
    static void require(boolean condition, String detail) throws IOException { if (!condition) throw new IOException(detail); }
    private static void warnings(Path out, Map<String, Object> state) throws IOException {
        String text = "# " + state.get("status") + "\n\n" +
            "This is native inspection of a complete, plain UTF-8 Sniffles VCF. It does not run the caller, merge variants, establish phase accuracy or reproduce a paper. Original headers, records, sample order, symbolic/breakend alleles and missing/trailing-omitted genotype values are retained. Supporting reads use RE in 1.0.7 and SUPPORT in Sniffles2; this is not an inferred read-depth filter. Textual checks do not certify all metadata declarations against the VCF standard.\n\n" +
            "**Only a PASS_COMPLETE_NATIVE_SNIFFLES_VCF_INSPECTION_ONLY receipt admits this complete inspection. Partial ledgers from any failure must not be used.** Limits are printed in effective-limits.json.\n\n";
        if (state.containsKey("error")) text += "**ACTION REQUIRED: " + state.get("error") + "**\n\nPreserve this attempt, fix the named input or capacity issue and retry into a NEW output directory. No SV-calling result is admitted.\n";
        Files.writeString(out.resolve("WARNINGS.md"), text, StandardCharsets.UTF_8);
    }
}
