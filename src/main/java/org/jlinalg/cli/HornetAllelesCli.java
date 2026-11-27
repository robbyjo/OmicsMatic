/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.sequence.HornetAlleles;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Portable, explicitly scoped Hornet sequence component and complete saved replay. */
public final class HornetAllelesCli {
    private HornetAllelesCli() { }
    static final String PROFILE = "hornet-e642ac0e-alleles-v1";
    private static final int MAXIMUM_BYTES = 33_554_432;
    static final String ACTION = "WARNINGS AND REQUIRED ACTION: this command generates Hornet allele sequences only. Mapping, remapping, FASTQ quality/orientation handling, BAM filtering and paper-specific references/variants remain separate work. Do not interpret component success as a completed WASP, Hornet or paper analysis. Read WARNINGS.md; require COMPLETED.json and a successful --verify with the exact producing JAR. Missing input or producer gives SKIP3 and skips dependent calculation. Invalid/changed input, capacity exhaustion, unsupported domain or cancellation gives FAIL2 with no complete result. Preserve failures, correct the named cause and retry NEW paths. If a full analysis needs unavailable software or installation permission, skip it and install the named prerequisite before retrying.";

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0 || Arrays.equals(args, new String[]{"--help"})) {
                emit(out, "hornet-alleles --example\n--request REQUEST.json --out NEW_DIRECTORY\n--verify COMPLETE_CAPTURE\n\nNative Hornet single-read/unphased-pair/phased-pair allele-sequence component. Explicit zero-based coordinates and BAM-orientation sequences; this command does not read or write BAM/FASTQ. See docs/hornet-alleles.md.\n\n" + ACTION); return 0;
            }
            if (Arrays.equals(args, new String[]{"--example"})) { emit(out, NextflowJson.json(example())); return 0; }
            if (args.length == 4 && args[0].equals("--request") && args[2].equals("--out")) {
                Path root = capture(Path.of(args[1]).toAbsolutePath().normalize(), Path.of(args[3]));
                emit(err, ACTION); emit(out, "COMPLETE_NATIVE_HORNET_ALLELE_COMPONENT " + root); return 0;
            }
            if (args.length == 2 && args[0].equals("--verify")) {
                verify(Path.of(args[1]), false); emit(err, ACTION); emit(out, "PASS_COMPLETE_NATIVE_HORNET_ALLELE_REPLAY"); return 0;
            }
            throw new IllegalArgumentException("Use hornet-alleles --help");
        } catch (Unavailable e) { emit(err, "SKIP3 DEPENDENT HORNET COMPONENT SKIPPED: " + e.getMessage() + "\n" + ACTION); return 3; }
        catch (IOException | RuntimeException | OutOfMemoryError e) { emit(err, "FAIL2 NO COMPLETE HORNET COMPONENT: " + e.getMessage() + "\n" + ACTION); return 2; }
    }

    static Object example() {
        return map("schema_version", 1, "profile", PROFILE, "input_domain", "explicit-zero-based-BAM-orientation-reads",
                "preparation", "Synthetic illustration; supply the actual allele extraction, coordinate/build and read provenance. No paper settings are inferred.",
                "phased", true,
                "reads", List.of(readExample(100), readExample(110)),
                "variants", List.of(map("chromosome", "chr1", "position", 101, "reference", "A", "alternate", "C")));
    }
    private static Object readExample(int start) { return map("chromosome", "chr1", "start", start, "sequence", "AAAAAA", "cigar", "6M", "unmapped", false); }

    static Map<String,Object> compute(byte[] raw) throws IOException {
        check(); var doc = object(SimpleJson.parse(utf8(raw)));
        keys(doc, "schema_version", "profile", "input_domain", "preparation", "phased", "reads", "variants"); integer(doc.get("schema_version"), 1, 1);
        require(PROFILE.equals(doc.get("profile")) && "explicit-zero-based-BAM-orientation-reads".equals(doc.get("input_domain")), "Require the explicit Hornet allele-component input domain");
        String preparation = text(doc.get("preparation"));
        require(!preparation.isBlank() && preparation.length() <= 8192 && preparation.chars().noneMatch(c -> c < 32 || c == 127), "Supply bounded actual preparation provenance");
        var reads = new ArrayList<HornetAlleles.Read>(); var rows = list(doc.get("reads"));
        require(rows.size() >= 1 && rows.size() <= 2, "Supply one read or one paired-read unit");
        for (Object item : rows) {
            var r = object(item); keys(r, "chromosome", "start", "sequence", "cigar", "unmapped");
            reads.add(new HornetAlleles.Read(text(r.get("chromosome")), (int)integer(r.get("start"), 0, Integer.MAX_VALUE), text(r.get("sequence")), text(r.get("cigar")), bool(r.get("unmapped"))));
        }
        var variants = new ArrayList<HornetAlleles.Variant>(); rows = list(doc.get("variants"));
        require(rows.size() <= HornetAlleles.NATIVE_MAX_VARIANTS, "Native Hornet variant capacity exceeded");
        for (Object item : rows) {
            check(); var v = object(item); keys(v, "chromosome", "position", "reference", "alternate");
            variants.add(new HornetAlleles.Variant(text(v.get("chromosome")), (int)integer(v.get("position"), 0, Integer.MAX_VALUE), text(v.get("reference")), text(v.get("alternate"))));
        }
        var data = new HornetAlleles.Variants(variants); boolean phased = bool(doc.get("phased"));
        var result = reads.size() == 1 ? HornetAlleles.single(reads.get(0), data, phased) : HornetAlleles.paired(reads.get(0), reads.get(1), data, phased);
        check();
        return map("profile", PROFILE, "source_revision", HornetAlleles.REVISION, "sequences", result.sequences(), "dispositions", result.dispositions(),
                "input_read_units", 1, "input_reads", reads.size(), "input_variant_rows", variants.size(), "allele_component_admitted", true,
                "mapping_workflow_admitted", false, "paper_recipe_admitted", false, "original_runtime_executed", false, "native_family_credit", 0, "upstream_family_credit", 0);
    }
    private static boolean bool(Object value) throws IOException { require(value instanceof Boolean, "Require an explicit JSON boolean"); return (Boolean)value; }

    private static Path capture(Path request, Path supplied) throws IOException {
        if (!Files.isReadable(request)) throw new Unavailable("Request unavailable: " + request);
        byte[] raw = bytes(request, MAXIMUM_BYTES); Pin original = pin(request, MAXIMUM_BYTES, HornetAllelesCli::check);
        require(original.equals(new Pin(raw.length, sha(raw))), "Request changed during capture");
        Pin producing = producer();
        Path root = supplied.toAbsolutePath().normalize();
        require(root.getParent() != null && supplied.toAbsolutePath().equals(root), "Require a canonical NEW capture path");
        directory(root.getParent()); require(!Files.exists(root, LinkOption.NOFOLLOW_LINKS), "Capture exists; use a NEW path"); Files.createDirectory(root);
        try {
            writeJson(root.resolve("INCOMPLETE.json"), map("allele_component_admitted", false, "mapping_workflow_admitted", false));
            Files.write(root.resolve("request.json"), raw, StandardOpenOption.CREATE_NEW);
            Files.writeString(root.resolve("WARNINGS.md"), warnings(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            writeJson(root.resolve("result.json"), compute(raw));
            require(original.equals(pin(request, MAXIMUM_BYTES, HornetAllelesCli::check)) && producing.equals(producer()), "Request or producer changed before admission");
            var population = files(root); population.remove("INCOMPLETE.json");
            writeJson(root.resolve("manifest.json"), map("schema_version", 1, "profile", PROFILE, "producer", producing, "files", population));
            writeJson(root.resolve("COMPLETED.json"), map("status", "COMPLETE_NATIVE_HORNET_ALLELE_COMPONENT", "manifest", pin(root.resolve("manifest.json"), 1048576, HornetAllelesCli::check), "mapping_workflow_admitted", false));
            verify(root, true);
            require(original.equals(pin(request, MAXIMUM_BYTES, HornetAllelesCli::check)) && producing.equals(producer()), "Original input or JAR changed during final admission");
            Files.delete(root.resolve("INCOMPLETE.json")); return root;
        } catch (IOException | RuntimeException | Error error) {
            try {
                Files.deleteIfExists(root.resolve("COMPLETED.json"));
                writeJson(root.resolve("FAILURE.json"), map("status", "FAIL2_NO_COMPLETE_HORNET_COMPONENT", "error", error.toString(), "required_action", ACTION));
            } catch (IOException suppressed) { error.addSuppressed(suppressed); }
            throw error;
        }
    }

    private static void verify(Path supplied, boolean precommit) throws IOException {
        Path root = supplied.toAbsolutePath().normalize(); directory(root);
        require(!Files.exists(root.resolve("FAILURE.json"), LinkOption.NOFOLLOW_LINKS) && Files.exists(root.resolve("INCOMPLETE.json"), LinkOption.NOFOLLOW_LINKS) == precommit, "Failed/incomplete Hornet component cannot be admitted");
        var done = object(json(root.resolve("COMPLETED.json"), 1048576)); keys(done, "status", "manifest", "mapping_workflow_admitted");
        require("COMPLETE_NATIVE_HORNET_ALLELE_COMPONENT".equals(done.get("status")) && Boolean.FALSE.equals(done.get("mapping_workflow_admitted")), "Missing or expanded Hornet admission");
        require(pin(root.resolve("manifest.json"), 1048576, HornetAllelesCli::check).equals(fingerprint(done.get("manifest"), 1048576)), "Saved Hornet manifest changed");
        var manifest = object(json(root.resolve("manifest.json"), 1048576)); keys(manifest, "schema_version", "profile", "producer", "files"); integer(manifest.get("schema_version"), 1, 1);
        require(PROFILE.equals(manifest.get("profile")) && producer().equals(fingerprint(manifest.get("producer"), 1000000000)), "Restore the exact producing JAR and profile; do not refresh old seals");
        var actual = files(root); var expected = new java.util.HashSet<>(Set.of("request.json", "WARNINGS.md", "result.json", "manifest.json", "COMPLETED.json"));
        if (precommit) expected.add("INCOMPLETE.json"); require(actual.keySet().equals(expected), "Extra or missing Hornet capture file");
        actual.remove("manifest.json"); actual.remove("COMPLETED.json"); actual.remove("INCOMPLETE.json");
        require(NextflowJson.json(actual).equals(NextflowJson.json(manifest.get("files"))), "Complete Hornet capture byte population changed");
        require(Files.readString(root.resolve("WARNINGS.md"), StandardCharsets.UTF_8).equals(warnings()), "Saved Hornet warnings changed");
        require(NextflowJson.json(compute(bytes(root.resolve("request.json"), MAXIMUM_BYTES))).equals(NextflowJson.json(json(root.resolve("result.json"), MAXIMUM_BYTES))), "Fresh Hornet sequence/counter replay differs");
        check();
    }

    private static TreeMap<String,Object> files(Path root) throws IOException {
        var result = new TreeMap<String,Object>(); long total = 0;
        try (var paths = Files.newDirectoryStream(root)) {
            for (Path path : paths) {
                check(); require(result.size() < 8, "Extra Hornet capture files"); Pin value = pin(path, MAXIMUM_BYTES, HornetAllelesCli::check);
                total += value.bytes(); require(total <= 2L * MAXIMUM_BYTES + 1048576, "Hornet capture byte capacity exceeded"); result.put(path.getFileName().toString(), value);
            }
        }
        return result;
    }
    private static Pin producer() throws IOException {
        try {
            Path path = Path.of(HornetAllelesCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (!path.getFileName().toString().endsWith(".jar")) throw new Unavailable("Build and retain the exact executable JAR; class directories are not a producer identity");
            return pin(path, 1000000000, HornetAllelesCli::check);
        } catch (java.net.URISyntaxException e) { throw new Unavailable("Exact producing JAR unavailable"); }
    }
    private static final class Unavailable extends IOException { private static final long serialVersionUID = 1L; Unavailable(String message) { super(message); } }
    private static void check() { if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Hornet component cancelled; no complete result"); }
    private static void emit(PrintStream stream, String value) { stream.writeBytes((value + "\n").getBytes(StandardCharsets.UTF_8)); }
    private static String warnings() {
        return "HORNET ALLELE COMPONENT: WARNINGS AND REQUIRED ACTION\n\n" + ACTION + "\n\n"
                + "The source is TheFraserLab/Hornet revision " + HornetAlleles.REVISION + ". The exact revision used by P007 is unestablished. This component implements get_read_seqs/get_dual_read_seqs and reverse_complement, not assign_reads, FASTQ writing, mapper execution, remapped-read filtering, duplicate removal or variant preparation. It does not establish standalone WASP or STARsolo coverage.\n\n"
                + "Coordinates are zero based, read sequences already in BAM alignment orientation, variants explicit ref/alt rows. Duplicate positions use the last row. Input is one single read or one same-chromosome pair; cross-chromosome pairs and CIGAR P/B are outside the native admitted domain and fail. CIGAR M/= /X align query/reference; I/S consume query; D/N consume reference; H consumes neither. No strand/quality transformation occurs.\n\n"
                + "Source quirks are preserved: ref+alt are concatenated into a string; only a hyphen triggers its effective indel test. Multi-base alleles can therefore be iterated as individual characters. This is source behavior, not an endorsement of such inputs. Unphased single reads retain other-base positions, whereas pairs can substitute them. Phased other-base pairs are discarded without incrementing the source's unused non_refalt_base counter. Phased single-read mode is unsupported. Shared mate SNPs receive concordant substitutions; discordant overlaps are discarded. Duplicate generated sequences are retained. Paired mode checks the product of allele-string lengths against32 before phased processing; phased mode can therefore discard more than five biallelic SNPs even though only two sequences would be emitted. Single mode tests its32 threshold before each aligned base; a sixth SNP at the last aligned base can yield64 sequences, while a following base triggers discard. No source counters or retained sequences are silently repaired.\n\n"
                + "Native protective capacities are distinct from source dispositions: request/result32MiB each, one million variant rows, one million bases per read,4096 generated sequences and16MiB generated sequence characters. Exceeding a capacity fails; it never becomes a source biological discard. Complete request bytes, ordered sequences, counters, warnings and producer identity are retained. Verification checks the entire five-file population and recomputes every sequence/counter with the exact JAR. No downloads or installations occur in this command.\n";
    }
}
