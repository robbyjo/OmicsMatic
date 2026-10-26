/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.pipeline.DataFormat;
import org.jlinalg.pipeline.VariantSources;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Allele-resolved catalog and aliases; annotation scope never depends on P values. */
final class StudyVariantUniverse {
    record Key(String build, String chromosome, long position, String reference, String alternate) {
        String id() { return build + ":" + chromosome + ":" + position + ":" + reference + ":" + alternate; }
        String locus() { return chromosome + ":" + position + ":" + reference + ":" + alternate; }
        boolean snp() { return reference.length() == 1 && alternate.length() == 1; }
    }
    static final class Item {
        final String dataset, feature, source; Key key; boolean eligible; String disposition;
        Item(String dataset, String feature, Key key, boolean eligible, String disposition, String source) {
            this.dataset = dataset; this.feature = feature; this.key = key; this.eligible = eligible; this.disposition = disposition; this.source = source;
        }
    }
    final List<Item> items = new ArrayList<>();
    final Map<String, Key> queries = new LinkedHashMap<>();
    final Map<String, Object> sourceHashes = new LinkedHashMap<>();
    private final Set<String> aliases = new LinkedHashSet<>();
    private StudyVariantUniverse() { }

    static StudyVariantUniverse load(Context context, boolean beforeAssociation) throws IOException {
        var catalog = new StudyVariantUniverse();
        Set<String> selected = new LinkedHashSet<>(list(context.operation().get("datasets")).stream().map(Object::toString).toList());
        Map<String, Map<String, String[]>> primary = new LinkedHashMap<>();
        Map<String, DelimitedData> primaryTables = new LinkedHashMap<>();
        Map<String, Set<String>> rareQc = new LinkedHashMap<>();
        for (Path directory : context.dependencyDirectories().values()) {
            Path manifest = directory.resolve("manifest.yaml"); if (!Files.isRegularFile(manifest)) continue;
            var declared = StudyYaml.loadArtifact(manifest);
            if (!text(context.operation().get("hypothesis")).equals(text(declared.get("hypothesis")))) continue;
            if ("complete".equals(declared.get("state")) && "rare_variant_scores".equals(declared.get("operation_type")) && map(declared.get("output_sha256")).containsKey("variant-qc.tsv")) {
                Path qc = StudyFindingInventory.verified(directory, "variant-qc.tsv", text(map(declared.get("output_sha256")).get("variant-qc.tsv")));
                String dataset = text(map(declared.get("details")).get("dataset")); var table = DelimitedData.readResult(qc);
                Set<String> retained = rareQc.computeIfAbsent(dataset, ignored -> new LinkedHashSet<>());
                for (String[] row : table.rows()) if (row[table.column("retained")].equals("true")) retained.add(row[table.column("variant_id")]);
                catalog.sourceHashes.put(dataset + ":rare_qc", FollowupSupport.hash(qc));
            }
        }
        for (var entry : StudyPrimaryResults.cohorts(context).entrySet()) {
            String dataset = entry.getKey(); Path path = entry.getValue().path();
            var table = DelimitedData.readResult(path); primaryTables.put(dataset, table);
            Map<String, String[]> rows = new LinkedHashMap<>();
            for (String[] row : table.rows()) if (rows.putIfAbsent(row[table.column("feature_id")], row) != null)
                throw new IOException("Ambiguous variant/contrast in cohort annotation universe: " + dataset);
            primary.put(dataset, rows); catalog.sourceHashes.put(dataset + ":tested_cohort_results", entry.getValue().sha256());
        }
        for (var dataset : StudyBioinformatics.datasets(context)) {
            String id = text(dataset.get("id")); if (!selected.contains(id) || !Set.of("gwas", "genomics").contains(dataset.get("type"))) continue;
            String build = build(text(dataset.get("genome_build"))); var files = map(dataset.get("files"));
            if (!beforeAssociation && !primaryTables.containsKey(id) && !rareQc.containsKey(id)) throw new IOException("Verified primary GWAS result producer is unavailable for " + id);
            Map<String, Key> identities = new LinkedHashMap<>();
            if (files.containsKey("variants")) {
                Path path = context.project().resolve(text(files.get("variants"))); var table = DelimitedData.readResult(path);
                catalog.sourceHashes.put(id + ":variants", FollowupSupport.hash(path));
                for (String[] row : table.rows()) {
                    String feature = field(table, row, "feature_id", "id", "variant_id");
                    Key key = from(table, row, build); if (feature.isBlank() || key == null) throw new IOException("files.variants requires unambiguous feature IDs and canonical variant coordinates/alleles");
                    Key previous = identities.putIfAbsent(feature, key); if (previous != null && !previous.equals(key)) throw new IOException("Conflicting variant identities: " + feature);
                }
            }
            String genotypeRole = files.containsKey("genotype") ? "genotype" : files.containsKey("matrix") && !text(dataset.get("stage")).endsWith("_results") ? "matrix" : "";
            if (!genotypeRole.isEmpty()) {
                Path path = context.project().resolve(text(files.get(genotypeRole))); catalog.sourceHashes.put(id + ":genotype", FollowupSupport.hash(path));
                List<String> external = null;
                String samples = text(files.getOrDefault("sample", files.get("samples")));
                if (!samples.isBlank()) {
                    Path samplePath = context.project().resolve(samples); external = SampleFiles.read(samplePath);
                    catalog.sourceHashes.put(id + ":sample", FollowupSupport.hash(samplePath));
                }
                var source = VariantSources.open(path, DataFormat.infer(path), external);
                try (var reader = source.open(null)) {
                    for (var block = reader.read(128); block != null; block = reader.read(128)) for (var variant : block.variants()) {
                        Key key = key(build, variant.chromosome(), variant.position(), variant.referenceAllele(), variant.alternateAllele());
                        Key named = parseId(variant.id(), build);
                        if (key != null && named != null && !key.equals(named)) throw new IOException("Variant ID conflicts with genotype coordinates/alleles: " + variant.id());
                        if (key == null) key = named;
                        Key declared = identities.get(variant.id());
                        if (declared != null && key != null && !declared.equals(key)) throw new IOException("Variant source conflicts with supplied identity: " + variant.id());
                        if (declared != null) key = declared;
                        boolean primaryEligible = primary.getOrDefault(id, Map.of()).containsKey(variant.id());
                        boolean rareEligible = key != null && rareQc.getOrDefault(id, Set.of()).contains(key.locus());
                        boolean eligible = beforeAssociation || primaryEligible || rareEligible;
                        catalog.add(id, variant.id(), key, eligible, beforeAssociation ? "pre_association_catalog" : primaryEligible ? "primary_eligible" : rareEligible ? "rare_set_QC_candidate" : "excluded_from_primary", genotypeRole);
                    }
                }
            } else {
                DelimitedData table = primaryTables.get(id);
                if (beforeAssociation && !files.containsKey("variants")) throw new IOException("Pre-association annotation needs genotype inputs or files.variants");
                if (beforeAssociation) for (var entry : identities.entrySet()) catalog.add(id, entry.getKey(), entry.getValue(), true, "pre_association_catalog", "variants");
                else {
                    if (table == null) throw new IOException("Verified GWAS result producer is unavailable for " + id);
                    for (String[] row : table.rows()) {
                        String feature = row[table.column("feature_id")]; Key key = from(table, row, build), declared = identities.get(feature);
                        if (key != null && declared != null && !key.equals(declared)) throw new IOException("Result alleles conflict with files.variants: " + feature);
                        catalog.add(id, feature, declared == null ? key : declared, true, "primary_eligible", "primary_results");
                    }
                    // Excluded imported rows remain visible even when their identity is unusable.
                    if (files.containsKey("results")) {
                        Path path = context.project().resolve(text(files.get("results"))); catalog.sourceHashes.put(id + ":results", FollowupSupport.hash(path));
                        var raw = DelimitedData.readResult(path); String configured = text(map(dataset.get("columns")).get("feature_id"));
                        for (String[] row : raw.rows()) {
                            String feature = configured.isBlank() ? field(raw, row, "feature_id", "id", "probe_id", "variant", "__row_name__") : row[raw.column(configured)];
                            if (primary.getOrDefault(id, Map.of()).containsKey(feature)) continue;
                            catalog.add(id, feature, identities.containsKey(feature) ? identities.get(feature) : from(raw, row, build), false, "excluded_from_primary", "raw_results");
                        }
                    }
                }
            }
        }
        return catalog;
    }
    private void add(String dataset, String feature, Key key, boolean eligible, String disposition, String source) throws IOException {
        if (!aliases.add(dataset + "\u001f" + feature)) throw new IOException("Duplicate variant feature identity: " + dataset + "/" + feature);
        if (key == null && eligible) disposition = "identity_unresolved";
        items.add(new Item(dataset, feature, key, eligible, disposition, source)); if (key != null && eligible) queries.putIfAbsent(key.id(), key);
    }
    static Key key(String build, String chromosome, long position, String reference, String alternate) {
        if (build.isBlank() || chromosome == null || reference == null || alternate == null) return null;
        chromosome = chromosome.replaceFirst("(?i)^chr", ""); reference = reference.toUpperCase(Locale.ROOT); alternate = alternate.toUpperCase(Locale.ROOT);
        if (position < 1 || !chromosome.matches("[A-Za-z0-9_.-]+") || !reference.matches("[ACGT]+") || !alternate.matches("[ACGT]+") || reference.equals(alternate)) return null;
        return new Key(build, chromosome, position, reference, alternate);
    }
    private static Key from(DelimitedData table, String[] row, String fallback) throws IOException {
        String declared = field(table, row, "genome_build"), build = build(declared);
        if (!declared.isBlank() && build.isBlank()) throw new IOException("Unsupported annotation row build: " + declared);
        if (!fallback.isBlank() && !build.isBlank() && !fallback.equals(build)) throw new IOException("Variant row build conflicts with dataset build");
        if (build.isBlank()) build = fallback;
        String position = field(table, row, "position", "pos", "POS", "BP"), chr = field(table, row, "chromosome", "chrom", "CHROM", "CHR");
        String ref = field(table, row, "reference_allele", "ref", "REF"), alt = field(table, row, "alternate_allele", "alt", "ALT");
        Key coordinate = null;
        try { coordinate = key(build, chr, Long.parseLong(position), ref, alt); } catch (NumberFormatException invalid) { /* canonical ID fallback */ }
        Key named = parseId(field(table, row, "variant_id", "feature_id", "id", "variant"), build);
        if (coordinate != null && named != null && !coordinate.equals(named)) throw new IOException("Variant ID conflicts with row coordinates/alleles");
        return coordinate == null ? named : coordinate;
    }
    static Key parseId(String id, String build) throws IOException {
        String[] parts = id.split(":", -1);
        int offset = parts.length == 5 ? 1 : 0;
        if (parts.length - offset == 4) try {
            String namedBuild = offset == 1 ? build(parts[0]) : build;
            if (offset == 1 && !build.isBlank() && !namedBuild.equals(build)) throw new IOException("Canonical variant build conflicts with declared build");
            return key(namedBuild, parts[offset], Long.parseLong(parts[offset + 1]), parts[offset + 2], parts[offset + 3]);
        } catch (NumberFormatException invalid) { return null; }
        return null;
    }
    private static String field(DelimitedData table, String[] row, String... names) { for (String name : names) if (table.header().contains(name)) return row[table.column(name)]; return ""; }
    static String build(String build) { return switch (build) { case "GRCh38", "hg38" -> "GRCh38"; case "GRCh37", "hg19" -> "GRCh37"; default -> ""; }; }
}
