/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.sequence.StringTieGtf;

/** Complete captured input parsing with no record filtering, conversion or chromosome aliases. */
final class StringTieInputs {
    private StringTieInputs() { }
    record Data(Map<String, Long> reference, StringTieGtf.Document guide, Map<String, Object> audit) { }
    static Map<String, String> members(StringTieRecipe.Request request) {
        var result = new LinkedHashMap<String, String>(); result.put("inputs/reference.sizes", request.sizes());
        if (request.guide() != null) result.put("inputs/guide.gtf", request.guide());
        if (request.bam() != null) result.put("inputs/reads.bam", request.bam());
        for (int i = 0; i < request.merge().size(); i++) result.put(request.mergeMember(i), request.merge().get(i));
        return Map.copyOf(result);
    }
    static Data audit(Path root, StringTieRecipe.Request request, String producerRoot, Runnable check) throws IOException {
        var reference = new LinkedHashMap<String, Long>();
        String sizes = NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("inputs/reference.sizes"), Math.toIntExact(request.capacity("metadata_bytes"))));
        String[] lines = sizes.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i]; check.run(); if (raw.isEmpty() && i == lines.length - 1 && sizes.endsWith("\n")) continue;
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw; String[] fields = line.split("\t", -1);
            require(fields.length == 2 && fields[0].matches("[^\\s\\p{Cntrl}]+") && fields[1].matches("[1-9][0-9]{0,9}"), "Require complete name<TAB>positive-signed32 chromosome sizes without aliases or blank rows");
            long length = Long.parseLong(fields[1]); require(length <= Integer.MAX_VALUE && reference.size() < request.capacity("references") && reference.putIfAbsent(fields[0], length) == null, "Duplicate/invalid/excessive chromosome-size dictionary");
        }
        require(!reference.isEmpty(), "Empty chromosome-size dictionary");
        var audit = new LinkedHashMap<String, Object>(); audit.put("genome_build", request.genomeBuild()); audit.put("reference_assembly_authenticated", false); audit.put("reference", reference);
        StringTieGtf.Document guide = request.guide() == null ? null : gtf(root.resolve("inputs/guide.gtf"), reference, StringTieGtf.Profile.ANNOTATION, request, check);
        audit.put("guide_transcripts", guide == null ? 0 : guide.transcripts().size());
        if (request.bam() != null) {
            var bam = StringTieBam.inspect(root.resolve("inputs/reads.bam"), reference, request.bamPolicies(), StringTieRecipe.bam(request.limits()), row -> { }, check);
            var report = new LinkedHashMap<>(bam.document()); report.put("source", producerRoot + "/inputs/reads.bam"); audit.put("BAM", report);
        } else {
            var counts = new LinkedHashMap<String, Object>();
            for (int i = 0; i < request.merge().size(); i++) {
                String member = request.mergeMember(i); StringTieGtf.Document document;
                try { document = gtf(root.resolve(member), reference, StringTieGtf.Profile.ANNOTATION, request, check); }
                catch (IOException error) {
                    if (!"Reference annotation has no complete exon-bearing transcripts".equals(error.getMessage())) throw error;
                    // A genuinely empty source assembly has comments/blank lines
                    // only. Recheck that complete empty-output grammar explicitly.
                    document = gtf(root.resolve(member), reference, StringTieGtf.Profile.MERGE, request, check);
                }
                counts.put(member, document.transcripts().size());
            }
            audit.put("merge_input_transcripts", counts);
        }
        return new Data(Map.copyOf(reference), guide, Map.copyOf(audit));
    }
    static StringTieGtf.Document gtf(Path file, Map<String, Long> reference, StringTieGtf.Profile profile, StringTieRecipe.Request request, Runnable check) throws IOException {
        try (Reader reader = reader(file)) { return StringTieGtf.read(reader, reference, profile, StringTieRecipe.gtf(request.limits()), check); }
    }
    static Reader reader(Path file) throws IOException { return new java.io.BufferedReader(new java.io.InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)), 65536); }
    private static void require(boolean value, String reason) throws IOException { if (!value) throw new IOException(reason); }
}
