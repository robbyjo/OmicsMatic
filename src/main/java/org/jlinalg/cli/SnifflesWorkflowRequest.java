/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.SnifflesVcf;
import static org.jlinalg.cli.SnifflesWorkflowFiles.*;

/** Explicit typed caller options; omission/null of an optional scalar leaves the source default. */
final class SnifflesWorkflowRequest {
    private SnifflesWorkflowRequest() { }
    record Input(String id, String kind, Path path, Pin pin, String reference, String originalSpelling) { }
    record Analysis(String id, SnifflesVcf.Edition edition, String input, String tandem, String genotype,
                    boolean phase, boolean legacyGenotype, Integer threads, Integer minSvlen,
                    String minSupport, Integer mapq, Integer minAlignmentLength, String sampleId) { }
    record Limits(long inputBytes, long alignmentRecords, int readBases, long referenceBases,
                  SnifflesVcf.Limits vcf, SnifflesProcess.Limits process) {
        Map<String, Object> worker() {
            return Map.of("maximum_alignment_records", alignmentRecords, "maximum_read_bases", readBases, "maximum_reference_bases", referenceBases);
        }
        Map<String, Object> document() {
            var result = new LinkedHashMap<String, Object>(worker()); result.put("maximum_input_bytes", inputBytes);
            result.put("maximum_vcf_bytes", vcf.maximumBytes()); result.put("maximum_vcf_records", vcf.maximumRecords());
            result.put("maximum_line_characters", vcf.maximumLineCharacters()); result.put("maximum_header_lines", vcf.maximumHeaderLines()); result.put("maximum_samples", vcf.maximumSamples());
            result.put("process", process); result.put("process_limit_scope", "Per original/preparation command. fileBytes also bounds each native view; workspace bytes/files cover the complete batch. Input copies are bounded by their declared pins. RSS is sampled, not a hard aggregate reservation.");
            return result;
        }
    }
    record Request(Path source, Pin sourcePin, Path out, Path runtime, Pin runtimePin, List<Input> inputs, List<Analysis> analyses, Limits limits) {
        Request { inputs = List.copyOf(inputs); analyses = List.copyOf(analyses); }
        Input input(String id) throws IOException { for (var value : inputs) if (value.id().equals(id)) return value; throw new IOException("Unknown input id: " + id); }
    }
    static Request read(Path source) throws IOException {
        canonical(source); Pin sourcePin = pin(source, 16777216, () -> {}); var raw = SnifflesWorkflowFiles.read(source, 16777216);
        keys(raw, Set.of("schema_version", "out", "runtime", "inputs", "analyses", "limits")); integer(raw.get("schema_version"), 1, 1);
        Path out = absolute(raw.get("out")); originalPath(out); require(out.getParent() != null, "Output requires an existing canonical parent"); canonical(out.getParent());
        var runtime = object(raw.get("runtime")); keys(runtime, Set.of("path", "pin")); Path manifest = absolute(runtime.get("path")); var runtimePin = fingerprint(runtime.get("pin"), 67108864);
        var limits = limits(raw.containsKey("limits") ? object(raw.get("limits")) : Map.of());
        List<?> inputList = list(raw.get("inputs")); require(!inputList.isEmpty() && inputList.size() <= 10000, "Require 1..10000 declared inputs");
        var inputs = new ArrayList<Input>(); var identifiers = new java.util.HashSet<String>();
        for (Object item : inputList) {
            var value = object(item); keys(value, Set.of("id", "kind", "path", "pin", "reference")); String id = id(value.get("id")); require(identifiers.add(id), "Duplicate input id");
            String kind = text(value.get("kind")); require(Set.of("bam", "cram", "fasta", "bed", "vcf").contains(kind), "Unsupported input kind");
            String reference = optionalId(value, "reference"); require(reference == null || Set.of("bam", "cram").contains(kind), "Only alignments declare a reference");
            require(!kind.equals("cram") || reference != null, "CRAM requires an explicit reference input");
            inputs.add(new Input(id, kind, absolute(value.get("path")), fingerprint(value.get("pin"), limits.inputBytes()), reference, text(value.get("path"))));
        }
        for (var input : inputs) if (input.reference() != null) require(find(inputs, input.reference()).kind().equals("fasta"), "Reference id must select FASTA");
        List<?> analysisList = list(raw.get("analyses")); require(!analysisList.isEmpty() && analysisList.size() <= 1000, "Require 1..1000 declared analyses");
        var analyses = new ArrayList<Analysis>(); identifiers.clear(); var used = new java.util.HashSet<String>();
        for (Object item : analysisList) {
            var value = object(item); keys(value, Set.of("id", "edition", "input", "tandem_repeats", "genotype_vcf", "phase", "legacy_genotype", "threads", "minsvlen", "minsupport", "mapq", "minimum_alignment_length", "sample_id"));
            String id = id(value.get("id")); require(identifiers.add(id), "Duplicate analysis id"); String editionText = text(value.get("edition")); SnifflesVcf.Edition edition = null;
            for (var candidate : SnifflesVcf.Edition.values()) if (candidate.version().equals(editionText)) edition = candidate;
            require(edition != null, "Unsupported exact source edition"); String inputId = id(value.get("input")); Input input = find(inputs, inputId);
            require(Set.of("bam", "cram").contains(input.kind()), "Caller input must select BAM or CRAM"); used.add(inputId); if (input.reference() != null) used.add(input.reference());
            String tandem = optionalId(value, "tandem_repeats"), genotype = optionalId(value, "genotype_vcf");
            if (tandem != null) { require(find(inputs, tandem).kind().equals("bed"), "Tandem-repeat input must select BED"); used.add(tandem); }
            if (genotype != null) { require(find(inputs, genotype).kind().equals("vcf"), "Force-genotyping input must select VCF"); used.add(genotype); }
            boolean phase = optionalBool(value, "phase"), legacyGenotype = optionalBool(value, "legacy_genotype");
            String support = null;
            if (value.get("minsupport") != null) { Object setting = value.get("minsupport"); support = setting instanceof String ? text(setting) : Long.toString(integer(setting, 1, 1000000000)); require(support.equals("auto") || support.matches("[1-9][0-9]{0,9}") && Long.parseLong(support) <= 1000000000, "minsupport must be auto or a positive bounded integer"); }
            String sample = value.get("sample_id") == null ? null : text(value.get("sample_id"));
            require(sample == null || !sample.isEmpty() && sample.length() <= 4096 && sample.chars().noneMatch(c -> c < 32 || c == 127), "Sample id must be nonempty and contain no delimiters/control characters; no repair performed");
            if (edition == SnifflesVcf.Edition.V1_0_7 && (phase || tandem != null || sample != null || input.reference() != null || input.kind().equals("cram") || "auto".equals(support)))
                throw new SnifflesProcess.UnavailableDependency("Requested legacy mode/flag is not supported by source 1.0.7. Its --cluster is not read-assignment --phase; no edition or option is substituted. " + SnifflesRuntimeProfile.ACTION);
            require(edition == SnifflesVcf.Edition.V1_0_7 || !legacyGenotype, "legacy_genotype is only a legacy source option");
            analyses.add(new Analysis(id, edition, inputId, tandem, genotype, phase, legacyGenotype,
                optionalInt(value, "threads", 1, 1024), optionalInt(value, "minsvlen", 1, Integer.MAX_VALUE), support,
                optionalInt(value, "mapq", 0, 255), optionalInt(value, "minimum_alignment_length", 1, 100000000), sample));
        }
        require(used.equals(inputs.stream().map(Input::id).collect(java.util.stream.Collectors.toSet())), "Every declared input must be used by a requested analysis or its reference");
        require(sourcePin.equals(pin(source, 16777216, () -> {})), "Request changed while parsing"); return new Request(source, sourcePin, out, manifest, runtimePin, inputs, analyses, limits);
    }
    private static Limits limits(Map<?, ?> map) throws IOException {
        keys(map, Set.of("maximum_input_bytes", "maximum_alignment_records", "maximum_read_bases", "maximum_reference_bases", "maximum_vcf_bytes", "maximum_vcf_records", "maximum_line_characters", "maximum_header_lines", "maximum_samples", "wall_millis", "address_space_bytes", "cpu_seconds", "file_bytes", "open_files", "workspace_bytes", "file_count", "processes", "threads", "sampled_rss_bytes", "monitor_millis"));
        var vcf = new SnifflesVcf.Limits(number(map,"maximum_vcf_bytes",268435456,1,1000000000000L), number(map,"maximum_vcf_records",1000000,1,1000000000),
            (int)number(map,"maximum_line_characters",16777216,128,100000000), (int)number(map,"maximum_header_lines",100000,1,1000000), (int)number(map,"maximum_samples",10000,1,100000));
        SnifflesProcess.Limits process;
        try { process = new SnifflesProcess.Limits(number(map,"wall_millis",3600000,1,604800000), number(map,"address_space_bytes",17179869184L,16777216,1099511627776L),
            number(map,"cpu_seconds",3600,1,604800), number(map,"file_bytes",34359738368L,1,1000000000000L), (int)number(map,"open_files",512,8,1048576),
            number(map,"workspace_bytes",68719476736L,1,1000000000000L), (int)number(map,"file_count",50000,4,1000000), (int)number(map,"processes",256,1,4096),
            (int)number(map,"threads",1024,1,1048576), number(map,"sampled_rss_bytes",17179869184L,1,1099511627776L), (int)number(map,"monitor_millis",100,1,1000)); }
        catch (IllegalArgumentException failure) { throw new IOException("Invalid explicit original-process capacity", failure); }
        return new Limits(number(map,"maximum_input_bytes",8589934592L,1,1000000000000L), number(map,"maximum_alignment_records",10000000,1,1000000000),
            (int)number(map,"maximum_read_bases",1000000,1,100000000), number(map,"maximum_reference_bases",8589934592L,1,1000000000000L), vcf, process);
    }
    private static long number(Map<?, ?> map, String key, long defaultValue, long low, long high) throws IOException { return map.containsKey(key) ? integer(map.get(key), low, high) : defaultValue; }
    private static Integer optionalInt(Map<?, ?> map, String key, int low, int high) throws IOException { return map.get(key) == null ? null : (int)integer(map.get(key), low, high); }
    private static boolean optionalBool(Map<?, ?> map, String key) throws IOException { return map.containsKey(key) ? bool(map.get(key)) : false; }
    private static String optionalId(Map<?, ?> map, String key) throws IOException { return map.get(key) == null ? null : id(map.get(key)); }
    private static String id(Object value) throws IOException { String text = text(value); require(text.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"), "Require a bounded ASCII input/analysis id"); return text; }
    private static Path absolute(Object value) throws IOException { Path path = Path.of(text(value)); require(path.isAbsolute() && path.normalize().equals(path), "Require an absolute normalized path"); return path; }
    private static Input find(List<Input> values, String id) throws IOException { for (var value : values) if (value.id().equals(id)) return value; throw new IOException("Unknown input id: " + id); }
    private static void keys(Map<?, ?> map, Set<String> allowed) throws IOException { for (Object key : map.keySet()) require(allowed.contains(key), "Unknown request key: " + key); }
}
