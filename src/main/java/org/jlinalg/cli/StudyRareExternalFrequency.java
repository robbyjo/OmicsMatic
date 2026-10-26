/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import static org.jlinalg.cli.StudyRareSupport.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Fixed-panel external ALT-frequency screen; no P values or phenotype rows are read. */
final class StudyRareExternalFrequency {
    record Assessment(Map<String,Object> details, Result hold) { }
    private record Frequency(Long ac, Long an, String filter) {
        double af() { return ac == null || an == null || an == 0 ? Double.NaN : (double) ac / an; }
    }

    Assessment apply(Context context, Map<String,Object> config,
            Map<String,Map<String,TreeSet<Variant>>> membership, String annotationBuild) throws IOException {
        var policy = map(config.get("external_frequency"));
        if (!Boolean.TRUE.equals(policy.get("enabled")))
            return new Assessment(Map.of("status", "not_requested"), null);
        for (String field : List.of("file", "source", "release", "genome_build", "populations", "minimum_allele_number", "rationale"))
            if (!policy.containsKey(field)) return hold(context, "metadata", "Declare external_frequency." + field
                + " before testing. Population labels, coverage and the scientific panel rationale are prespecified; they are not inferred from phenotype associations.", Map.of());
        String build = StudyVariantUniverse.build(text(policy.get("genome_build")));
        if (build.isBlank() || annotationBuild == null || !build.equals(StudyVariantUniverse.build(annotationBuild)))
            return hold(context, "genome-build", "External frequencies and annotation require the same explicitly supported GRCh37/GRCh38 build. No liftover, strand flip or allele swap is inferred.", Map.of());
        Path original = context.project().resolve(text(policy.get("file"))).normalize();
        if (!Files.isRegularFile(original)) return hold(context, "source", "Supply the declared external allele-count table before computing rare masks: " + original, Map.of());
        String sha = FollowupSupport.hash(original);
        Path snapshot = context.outputDirectory().resolve("external-frequency-source.tsv");
        Files.copy(original, snapshot, StandardCopyOption.REPLACE_EXISTING);
        if (!sha.equals(FollowupSupport.hash(snapshot)) || !sha.equals(FollowupSupport.hash(original)))
            throw new IOException("External frequency source changed while it was being snapshotted");
        var candidates = new TreeSet<Variant>();
        var candidateRows = new ArrayList<String[]>();
        for (var mask : membership.entrySet()) for (var set : mask.getValue().entrySet()) for (var variant : set.getValue()) {
            candidates.add(variant); candidateRows.add(new String[] {mask.getKey(), set.getKey(), variant.toString()});
        }
        FollowupSupport.table(context.outputDirectory().resolve("external-frequency-candidates.tsv"), List.of("mask_id", "set_id", "variant_id"), candidateRows);
        var populations = strings(policy.get("populations"));
        long minAn = ((Number) policy.get("minimum_allele_number")).longValue();
        int maximumRows = ((Number) policy.get("maximum_rows")).intValue();
        var values = new TreeMap<Variant,Map<String,Frequency>>();
        var loci = new TreeSet<String>();
        Set<String> seen = new java.util.HashSet<>();
        long sourceRows = 0;
        try (var reader = Files.newBufferedReader(snapshot)) {
            String first = reader.readLine();
            if (first == null) throw new IOException("Empty external frequency source");
            var columns = List.of(first.split("\t", -1));
            if (new java.util.HashSet<>(columns).size() != columns.size()) throw new IOException("Duplicate external frequency column");
            for (String field : List.of("variant_id", "population", "allele_count", "allele_number", "filter", "genome_build"))
                if (!columns.contains(field)) throw new IOException("Missing external frequency column: " + field);
            String line;
            while ((line = reader.readLine()) != null) {
                if (++sourceRows > maximumRows) return hold(context, "row-budget", "The external frequency table exceeds maximum_rows; no prefix or partial mask is tested. Supply a complete allele-resolved candidate subset or increase the prespecified budget.", Map.of("source_sha256", sha));
                String[] row = line.split("\t", -1);
                if (row.length != columns.size()) throw new IOException("Ragged external frequency row " + sourceRows);
                Variant variant = Variant.parse(row[columns.indexOf("variant_id")]);
                String population = row[columns.indexOf("population")];
                if (!population.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) throw new IOException("Invalid external population label");
                if (!seen.add(variant + "\t" + population)) throw new IOException("Duplicate external variant/population: " + variant + "/" + population);
                String rowBuild = StudyVariantUniverse.build(row[columns.indexOf("genome_build")]);
                if (!build.equals(rowBuild)) throw new IOException("External frequency row build conflicts with declaration: " + variant);
                Long ac = count(row[columns.indexOf("allele_count")]), an = count(row[columns.indexOf("allele_number")]);
                if (ac != null && an != null && ac > an) throw new IOException("External allele_count exceeds allele_number: " + variant);
                String filter = row[columns.indexOf("filter")];
                if (filter.isBlank() || filter.length() > 500 || filter.chars().anyMatch(Character::isISOControl)) throw new IOException("External frequency filter must be explicit bounded text");
                if (!populations.contains(population)) continue;
                loci.add(variant.chromosome() + ":" + variant.position());
                if (candidates.contains(variant)) values.computeIfAbsent(variant, ignored -> new LinkedHashMap<>()).put(population, new Frequency(ac, an, filter));
            }
        }
        var audit = new ArrayList<String[]>();
        var unavailable = new TreeMap<Variant,List<String>>();
        var rejected = new TreeSet<Variant>();
        var maximumAf = new TreeMap<Variant,Double>();
        for (Variant variant : candidates) for (String population : populations) {
            Frequency frequency = values.getOrDefault(variant, Map.of()).get(population);
            String reason;
            if (frequency == null) reason = loci.contains(variant.chromosome() + ":" + variant.position()) && !values.containsKey(variant)
                ? "ALLELE_KEY_MISMATCH" : "MISSING_POPULATION";
            else if (Set.of("NA", ".").contains(frequency.filter)) reason = "UNAVAILABLE_SOURCE_FILTER";
            else if (!frequency.filter.equals("PASS")) { reason = "SOURCE_FILTER_FAILED"; rejected.add(variant); }
            else if (frequency.ac == null || frequency.an == null) reason = "UNAVAILABLE_COUNTS";
            else if (frequency.an < minAn) reason = "INSUFFICIENT_ALLELE_NUMBER";
            else { reason = "ASSESSED"; maximumAf.merge(variant, frequency.af(), Math::max); }
            if (!Set.of("ASSESSED", "SOURCE_FILTER_FAILED").contains(reason)) unavailable.computeIfAbsent(variant, ignored -> new ArrayList<>()).add(population + ":" + reason);
            audit.add(new String[] {variant.toString(), population, frequency == null || frequency.ac == null ? "NA" : frequency.ac.toString(),
                frequency == null || frequency.an == null ? "NA" : frequency.an.toString(), frequency == null ? "NA" : numeric(frequency.af()),
                frequency == null ? "NA" : frequency.filter, reason});
        }
        FollowupSupport.table(context.outputDirectory().resolve("external-frequency-qc.tsv"),
            List.of("variant_id", "population", "allele_count", "allele_number", "alt_allele_frequency", "source_filter", "disposition"), audit);
        var decisions = new ArrayList<String[]>(); int excluded = 0;
        for (Object raw : list(config.get("masks"))) {
            var mask = map(raw); String id = text(mask.get("id"));
            double threshold = ((Number) mask.get("maximum_external_af")).doubleValue();
            var variants = new TreeSet<Variant>(); membership.getOrDefault(id, Map.of()).values().forEach(variants::addAll);
            var keep = new TreeSet<Variant>();
            for (Variant variant : variants) {
                String reason = unavailable.containsKey(variant) ? String.join(";", unavailable.get(variant)) : rejected.contains(variant)
                    ? "SOURCE_FILTER_FAILED" : maximumAf.getOrDefault(variant, Double.NaN) > threshold ? "MAXIMUM_POPULATION_ALT_AF_EXCEEDED" : "ELIGIBLE";
                boolean eligible = reason.equals("ELIGIBLE");
                if (eligible) keep.add(variant); else if (!unavailable.containsKey(variant)) excluded++;
                decisions.add(new String[] {id, variant.toString(), numeric(maximumAf.getOrDefault(variant, Double.NaN)),
                    Double.toString(threshold), unavailable.containsKey(variant) ? "NA" : Boolean.toString(eligible), reason});
            }
            // No partial family is published if any required external observation is unresolved.
            if (unavailable.isEmpty()) {
                var sets = membership.getOrDefault(id, Map.of());
                sets.values().forEach(set -> set.retainAll(keep));
                sets.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            }
        }
        FollowupSupport.table(context.outputDirectory().resolve("external-frequency-mask-qc.tsv"),
            List.of("mask_id", "variant_id", "maximum_population_alt_af", "maximum_external_af", "retained", "reason"), decisions);
        var details = new LinkedHashMap<String,Object>();
        details.put("status", unavailable.isEmpty() ? "complete" : "waiting_for_coverage");
        details.put("source", policy.get("source")); details.put("release", policy.get("release"));
        details.put("source_sha256", sha); details.put("source_snapshot", snapshot.getFileName().toString()); details.put("genome_build", build);
        details.put("populations", populations); details.put("minimum_allele_number", minAn); details.put("source_rows", sourceRows);
        details.put("candidate_variants", candidates.size()); details.put("unresolved_variants", unavailable.size());
        details.put("excluded_mask_variant_pairs", excluded);
        details.put("rule", "Every prespecified population must have an exact allele/build row with PASS and adequate AN; any non-PASS row excludes the allele. Maximum selected-population ALT AC/AN must be <= each mask's maximum_external_af. Missing observations hold the entire mask stage.");
        details.put("limitations", "Point-frequency eligibility only: not gnomAD FAF/grpmax, a confidence bound, pathogenicity evidence, ancestry inference or a guarantee of population representativeness. AC=0 needs explicit adequate AN; an absent row is not zero. Cohort observed MAF, genotype QC and model calibration remain separate prerequisites.");
        write(context.outputDirectory().resolve("external-frequency.yaml"), details);
        if (!unavailable.isEmpty()) return hold(context, "coverage", "External-frequency observations are unresolved for " + unavailable.size()
            + " candidate alleles. Complete every declared population with exact alleles and adequate AN; absent/zero-coverage observations are not assumed rare. No partial mask family is tested.", details);
        return new Assessment(details, null);
    }

    private static Long count(String value) throws IOException {
        if (Set.of("NA", ".").contains(value)) return null;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            long result = Long.parseLong(value);
            if (result > 9007199254740991L) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException error) { throw new IOException("External allele counts must be nonnegative exact integers or NA: " + value, error); }
    }

    private static String numeric(double value) { return Double.isFinite(value) ? Double.toString(value) : "NA"; }

    private static Assessment hold(Context context, String key, String reason, Map<String,Object> details) throws IOException {
        return new Assessment(details, waiting(context, "external-frequency:" + key, reason));
    }
}
