/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyRareVariantPolicy;

final class StudyRareSupport {
    private StudyRareSupport() { }
    static Map<String, Object> hypothesis(Context context) { return StudyBioinformatics.hypothesis(context); }
    static Map<String, Object> policy(Context context) {
        var analysis = StudyResultTables.analysis(context);
        return StudyRareVariantPolicy.resolve(map(analysis.get("rare_variants")), map(analysis.get("association")), true);
    }
    static List<Map<String, Object>> datasets(Context context) { return StudyBioinformatics.datasets(context); }
    static List<String> strings(Object value) { return list(value).stream().map(Object::toString).toList(); }
    static List<String> outputs(Context context) throws IOException {
        try (var files = Files.walk(context.outputDirectory())) {
            return files.filter(Files::isRegularFile).map(context.outputDirectory()::relativize)
                .map(path -> path.toString().replace('\\', '/')).sorted().toList();
        }
    }
    static Result waiting(Context context, String key, String reason) throws IOException {
        var need = StudyNeed.review("rare-variants:" + context.operation().get("hypothesis") + ":" + key,
            "How should the rare-variant prerequisite be resolved?", reason, true);
        var details = Map.<String, Object>of("needs", List.of(need.document()), "reason", reason,
            "scientific_scope", "The independent single-variant branch may continue; no uncalibrated set-test fallback is chosen.");
        write(context.outputDirectory().resolve("request.yaml"), details);
        return new Result(State.WAITING_FOR_INPUT, reason, outputs(context), details);
    }
    static Path verified(Path directory, Map<String, Object> manifest, String relative) throws IOException {
        Path path = directory.resolve(relative).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)
                || !FollowupSupport.hash(path).equals(map(manifest.get("output_sha256")).get(relative)))
            throw new IOException("Missing or changed rare-variant dependency: " + path);
        return path;
    }
    static Result emptyScores(Context context) throws IOException {
        var details = Map.<String,Object>of("dataset", datasets(context).get(0).get("id"), "retained_variants", 0,
            "empty_mask_universe", true, "evidence_disposition", "not_estimable", "mask_membership_sha256",
            FollowupSupport.hash(StudyResultTables.artifact(context, "rare_variant_masks", "mask-membership.tsv")),
            "method_report", Map.of("method", "Rare score generation", "performed", false,
                "reason", "No alleles remain in the verified prespecified masks; no null model or score was fitted."));
        write(context.outputDirectory().resolve("scores.yaml"), details);
        return new Result(State.COMPLETE, "No eligible rare-mask alleles; score fitting was not performed", outputs(context), details);
    }
    record Variant(String chromosome, long position, String reference, String alternate) implements Comparable<Variant> {
        static Variant parse(String raw) throws IOException {
            String[] parts = raw.split(":", -1);
            if (parts.length != 4 || !parts[2].matches("[ACGT]+") || !parts[3].matches("[ACGT]+") || parts[2].equals(parts[3]))
                throw new IOException("Expected a normalized biallelic CHROM:POS:REF:ALT key: " + raw);
            String chr = org.jlinalg.raremetal.RareMetalStudy.chromosome(parts[0]);
            long position;
            try { position = Long.parseLong(parts[1]); } catch (NumberFormatException e) { throw new IOException("Invalid variant position: " + raw, e); }
            if (position < 1 || position > Integer.MAX_VALUE) throw new IOException("Unsupported genomic position: " + raw);
            return new Variant(chr, position, parts[2], parts[3]);
        }
        public int compareTo(Variant other) {
            int chr = org.jlinalg.raremetal.RareMetalStudy.compareChromosome(chromosome, other.chromosome);
            if (chr != 0) return chr;
            int positionOrder = Long.compare(position, other.position);
            return positionOrder != 0 ? positionOrder : toString().compareTo(other.toString());
        }
        public String toString() { return chromosome + ":" + position + ":" + reference + ":" + alternate; }
        boolean autosomal() { return chromosome.matches("[1-9]|1[0-9]|2[0-2]"); }
    }
    static Map<String, Object> roles(Context context) {
        Set<String> selected = new java.util.LinkedHashSet<>(strings(context.operation().get("datasets")));
        var roles = new LinkedHashMap<String, Object>();
        map(hypothesis(context).get("inputs")).forEach((role, raw) -> roles.put(role, strings(raw).stream().filter(selected::contains).toList()));
        return roles;
    }
}
