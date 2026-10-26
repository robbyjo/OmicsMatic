/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import static org.jlinalg.cli.StudyRareSupport.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Allele-resolved annotation is interpreted before any association P value. */
final class StudyRareMasks {
    Result execute(Context context) throws IOException {
        var config = policy(context); var masks = list(config.get("masks"));
        if (masks.isEmpty()) return waiting(context, "masks", "Declare analysis.rare_variants.masks with consequence labels and MAF limits before testing; no significant-result-derived masks are inferred.");
        Map<String, Map<String, TreeSet<Variant>>> membership = new TreeMap<>();
        Map<String, String> locusAlleles = new LinkedHashMap<>();
        List<Object> sources = new ArrayList<>(); String build = null, release = null;
        for (var dataset : datasets(context)) {
            var files = map(dataset.get("files"));
            Path path = files.containsKey("variant_annotation") ? context.project().resolve(text(files.get("variant_annotation"))) : null;
            String declaredRelease = text(dataset.get("annotation_release"));
            String sourceKind = "supplied_annotation";
            if (path == null) for (Path dependency : context.dependencyDirectories().values()) {
                Path manifestPath = dependency.resolve("manifest.yaml"); if (!Files.isRegularFile(manifestPath)) continue;
                var manifest = StudyYaml.loadArtifact(manifestPath); String name = "rare-annotation/" + dataset.get("id") + ".tsv";
                if (!"variant_annotation".equals(manifest.get("operation_type")) || !"complete".equals(manifest.get("state")) || !map(manifest.get("output_sha256")).containsKey(name)) continue;
                path = StudyFindingInventory.verified(dependency, name, text(map(manifest.get("output_sha256")).get(name)));
                String generatedRelease = text(map(manifest.get("details")).get("annotation_release"));
                if (!declaredRelease.isBlank() && !declaredRelease.equals(generatedRelease)) throw new IOException("Generated annotation release conflicts with dataset declaration");
                declaredRelease = generatedRelease; sourceKind = "verified_pre_association_consequences";
            }
            if (path == null || declaredRelease.isBlank() || text(dataset.get("genome_build")).isBlank())
                return waiting(context, "annotation:" + dataset.get("id"), "Supply files.variant_annotation (variant_id, set_id, consequence), annotation_release and genome_build for every score-producing cohort. Variant consequences and masks must precede testing.");
            String declaredBuild = text(dataset.get("genome_build"));
            if (!StudyVariantUniverse.build(declaredBuild).isBlank()) declaredBuild = StudyVariantUniverse.build(declaredBuild);
            if (build != null && !build.equals(declaredBuild)) return waiting(context, "genome-build", "Rare-set cohorts have different genome builds; normalize them before pooling. No automatic liftover is performed.");
            build = declaredBuild;
            if (dataset.containsKey("coordinate_system") && !"1-based-closed".equals(dataset.get("coordinate_system")))
                return waiting(context, "coordinates", "Normalize variant_annotation keys to 1-based CHROM:POS:REF:ALT before set testing; zero-based positions cannot be interpreted as VCF coordinates.");
            if (release != null && !release.equals(declaredRelease)) return waiting(context, "annotation-release", "Rare masks require one compatible annotation release across the score cohorts; align definitions before pooling.");
            release = declaredRelease;
            var table = DelimitedData.readResult(path);
            int variantColumn = table.column("variant_id"), setColumn = table.column("set_id"), annotationColumn = table.column("consequence");
            for (String[] row : table.rows()) {
                Variant variant = Variant.parse(row[variantColumn]);
                if (!variant.autosomal()) return waiting(context, "ploidy", "Native Gaussian rare scores currently require diploid autosomal variants; provide a calibrated ploidy-aware workflow for " + variant);
                if (table.header().contains("genome_build")) {
                    String rowBuild = row[table.column("genome_build")]; if (!StudyVariantUniverse.build(rowBuild).isBlank()) rowBuild = StudyVariantUniverse.build(rowBuild);
                    if (!build.equals(rowBuild)) throw new IOException("Annotation row build conflicts with dataset: " + variant);
                }
                String set = row[setColumn];
                if (!set.matches("[A-Za-z0-9][A-Za-z0-9_.:|+\\-]{0,255}")) throw new IOException("Set IDs must be nonblank stable identifiers without whitespace: " + set);
                String locus = variant.chromosome() + ":" + variant.position();
                String old = locusAlleles.putIfAbsent(locus, variant.toString());
                if (old != null && !old.equals(variant.toString())) throw new IOException("Conflicting allele/multiallelic annotation at " + locus + " in set " + set);
                Set<String> consequences = new TreeSet<>(java.util.Arrays.stream(row[annotationColumn].split("[;&|,]")).map(String::strip).filter(label -> !label.isBlank()).toList());
                for (Object raw : masks) {
                    var mask = map(raw);
                    if (strings(mask.get("consequences")).stream().noneMatch(consequences::contains)) continue;
                    membership.computeIfAbsent(text(mask.get("id")), ignored -> new TreeMap<>())
                        .computeIfAbsent(set, ignored -> new TreeSet<>()).add(variant);
                }
            }
            sources.add(Map.of("dataset", dataset.get("id"), "sha256", FollowupSupport.hash(path), "annotation_release", declaredRelease, "genome_build", build, "source_kind", sourceKind));
        }
        var external = new StudyRareExternalFrequency().apply(context, config, membership, build);
        if (external.hold() != null) return external.hold();
        List<String[]> rows = new ArrayList<>(); List<Object> summaries = new ArrayList<>(); long requiredWindow = 1;
        Files.createDirectories(context.outputDirectory().resolve("groups"));
        for (Object raw : masks) {
            var mask = map(raw); String id = text(mask.get("id"));
            var sets = membership.getOrDefault(id, Map.of()); StringBuilder groups = new StringBuilder();
            for (var entry : sets.entrySet()) {
                TreeSet<Variant> variants = entry.getValue();
                if (!variants.first().chromosome().equals(variants.last().chromosome())) throw new IOException("A rare set must lie on one chromosome: " + entry.getKey());
                requiredWindow = Math.max(requiredWindow, variants.last().position() - variants.first().position());
                groups.append(entry.getKey());
                for (Variant variant : variants) {
                    groups.append(' ').append(variant);
                    rows.add(new String[] {id, entry.getKey(), variant.toString(), text(mask.get("maximum_maf"))});
                }
                groups.append('\n');
            }
            Files.writeString(context.outputDirectory().resolve("groups/" + id + ".txt"), groups);
            summaries.add(Map.of("id", id, "sets", sets.size(), "definition", mask));
        }
        FollowupSupport.table(context.outputDirectory().resolve("mask-membership.tsv"), List.of("mask_id", "set_id", "variant_id", "maximum_maf"), rows);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("masks", summaries); details.put("sources", sources); details.put("genome_build", build == null ? "unknown" : build);
        details.put("required_covariance_window_bp", requiredWindow); details.put("membership_rows", rows.size());
        details.put("external_frequency", external.details());
        details.put("frequency_policy", "Consequence membership fixed before testing; each mask's MAF limit is evaluated from observed aligned cohort allele frequencies by the score-pooling backend.");
        details.put("mapping_limitations", "Supplied annotation determines set/gene membership; no nearest-gene inference, transcript collapsing or pathogenicity claim is made.");
        details.put("method_report", StudyMethods.record("Prespecified consequence masks and optional external ALT-frequency eligibility",
            Map.of("masks", masks, "external_frequency", external.details(), "observed_frequency_rule", details.get("frequency_policy")),
            list(config.get("references")), config.getOrDefault("rationale", "Scientist-declared masks and, when enabled, source/population/coverage policy fixed before association testing."),
            List.of("rare variant masks external alternate allele frequency population coverage allele number")));
        write(context.outputDirectory().resolve("masks.yaml"), details);
        return new Result(State.COMPLETE, "Constructed prespecified rare-variant masks", outputs(context), details);
    }
}
