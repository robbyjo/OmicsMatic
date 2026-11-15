/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.pipeline.DelimitedMatrixSource;
import org.jlinalg.pipeline.MappedOmicsAdjustment;

/** CLI input contract for mapped cross-omics association covariates. */
final class MappedOmicsInputs {
    private MappedOmicsInputs() { }

    static MappedOmicsAdjustment prepare(CliOptions options, List<String> sampleIds)
            throws IOException {
        Map<String, MappedOmicsAdjustment.Source> sources = new LinkedHashMap<>();
        for (var entry : options.adjustmentOmics.entrySet())
            sources.put(entry.getKey(), new MappedOmicsAdjustment.Source(
                DelimitedMatrixSource.open(entry.getValue()), TransformParser.parse(
                    options.adjustmentTransforms.getOrDefault(entry.getKey(), List.of()).stream()
                        .map(spec -> "<omics>=" + spec).toList(),
                    options.transformPlugins)));
        DelimitedData table = DelimitedData.read(options.adjustmentMap);
        int primary = table.column("feature_id"), source = table.column("source"),
            adjustment = table.column("adjustment_id");
        Map<String, List<MappedOmicsAdjustment.Feature>> mapping = new LinkedHashMap<>();
        for (String[] row : table.rows()) {
            String id = row[primary].trim();
            if (id.isBlank()) throw new IllegalArgumentException("mapping feature_id cannot be blank");
            var feature = new MappedOmicsAdjustment.Feature(row[source].trim(), row[adjustment].trim());
            List<MappedOmicsAdjustment.Feature> mapped = mapping.computeIfAbsent(id, ignored -> new ArrayList<>());
            if (mapped.contains(feature))
                throw new IllegalArgumentException("duplicate adjustment mapping: " + id + " -> " + feature.label());
            mapped.add(feature);
        }
        Path directory = options.output.toAbsolutePath().getParent();
        return MappedOmicsAdjustment.prepare(sources, mapping, sampleIds, directory);
    }

    static List<String> hashes(CliOptions options) throws IOException {
        List<String> hashes = new ArrayList<>();
        hashes.add("mapping=" + FollowupSupport.hash(options.adjustmentMap));
        for (var entry : options.adjustmentOmics.entrySet())
            hashes.add(entry.getKey() + "=" + FollowupSupport.hash(entry.getValue()));
        return List.copyOf(hashes);
    }
}
