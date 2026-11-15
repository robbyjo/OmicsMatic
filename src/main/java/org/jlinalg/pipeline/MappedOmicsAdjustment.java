/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit, many-to-many cross-omics covariates. Only mapped rows are staged
 * in a temporary binary file; expression matrices need not fit in memory.
 * Sample IDs and feature IDs are matched exactly, including ID versions. */
public final class MappedOmicsAdjustment implements AutoCloseable {
    /** A named adjustment matrix and its independent row transformation. */
    public record Source(NumericMatrixSource matrix, OmicsTransform transform) {
        public Source {
            if (matrix == null || transform == null)
                throw new IllegalArgumentException("adjustment matrix and transform are required");
        }
    }
    /** One adjustment feature, qualified by its source name. */
    public record Feature(String source, String id) {
        public Feature {
            if (source == null || !source.matches("[A-Za-z][A-Za-z0-9_-]*")
                    || id == null || id.isBlank())
                throw new IllegalArgumentException("adjustment source and feature ID are required");
        }
        public String label() { return source + ":" + id; }
    }

    private final List<String> sampleIds;
    private final Map<String, List<Feature>> mapping;
    private final Map<Feature, Long> offsets = new HashMap<>();
    private final Path cache;
    private final FileChannel channel;

    private MappedOmicsAdjustment(List<String> sampleIds,
            Map<String, List<Feature>> mapping, Path cache, FileChannel channel) {
        this.sampleIds = List.copyOf(sampleIds);
        this.mapping = Map.copyOf(mapping);
        this.cache = cache;
        this.channel = channel;
    }

    /** Stages mapped features in analysis-sample order. Absent samples remain
     * missing and are omitted only in models using that adjustment source. */
    public static MappedOmicsAdjustment prepare(Map<String, Source> sources,
            Map<String, List<Feature>> mapping, List<String> sampleIds,
            Path temporaryDirectory) throws IOException {
        SampleAlignment.requireOrder(sampleIds, sampleIds);
        if (sources == null || sources.isEmpty() || mapping == null || mapping.isEmpty())
            throw new IllegalArgumentException("adjustment sources and nonempty mapping are required");
        Map<String, List<Feature>> retained = new LinkedHashMap<>();
        Set<Feature> required = new HashSet<>();
        for (var entry : mapping.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue().isEmpty())
                throw new IllegalArgumentException("every mapped feature needs adjustment IDs");
            List<Feature> features = List.copyOf(entry.getValue());
            if (new HashSet<>(features).size() != features.size())
                throw new IllegalArgumentException("duplicate adjustment mapping: " + entry.getKey());
            for (Feature feature : features)
                if (!sources.containsKey(feature.source()))
                    throw new IllegalArgumentException("unknown adjustment source: " + feature.source());
            retained.put(entry.getKey(), features);
            required.addAll(features);
        }
        Files.createDirectories(temporaryDirectory);
        Path cache = Files.createTempFile(temporaryDirectory, "omics-adjustment-", ".bin");
        MappedOmicsAdjustment result;
        try {
            result = new MappedOmicsAdjustment(sampleIds, retained, cache,
                FileChannel.open(cache, StandardOpenOption.READ, StandardOpenOption.WRITE));
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(cache);
            throw error;
        }
        try {
            long position = 0;
            for (var entry : sources.entrySet()) {
                String name = entry.getKey();
                if (required.stream().noneMatch(f -> f.source().equals(name)))
                    throw new IllegalArgumentException("adjustment source is unused in mapping: " + name);
                Source source = entry.getValue();
                List<String> sourceIds = source.matrix().metadata().sampleIds();
                SampleAlignment.requireOrder(sourceIds, sourceIds);
                Map<String, Integer> analysisIndex = new HashMap<>();
                for (int i = 0; i < sampleIds.size(); i++) analysisIndex.put(sampleIds.get(i), i);
                List<Integer> sourceRows = new ArrayList<>(), destinations = new ArrayList<>();
                for (int i = 0; i < sourceIds.size(); i++) {
                    Integer destination = analysisIndex.get(sourceIds.get(i));
                    if (destination != null) { sourceRows.add(i); destinations.add(destination); }
                }
                if (sourceRows.isEmpty())
                    throw new IllegalArgumentException("adjustment source has no analysis samples: " + name);
                Set<String> seen = new HashSet<>();
                try (NumericBlockReader reader = source.matrix().open(
                        sourceRows.stream().mapToInt(Integer::intValue).toArray())) {
                    for (NumericBlock block; (block = reader.read(64)) != null;)
                        for (NumericRow row : block.rows()) {
                            if (!seen.add(row.id()))
                                throw new IllegalArgumentException("duplicate adjustment feature: " + name + ":" + row.id());
                            Feature feature = new Feature(name, row.id());
                            if (!required.contains(feature)) continue;
                            double[] values = source.transform().apply(row.values());
                            if (values == null || values.length != destinations.size())
                                throw new IllegalArgumentException("adjustment transform must preserve sample count");
                            double[] aligned = new double[sampleIds.size()];
                            Arrays.fill(aligned, Double.NaN);
                            for (int i = 0; i < values.length; i++) aligned[destinations.get(i)] = values[i];
                            ByteBuffer buffer = ByteBuffer.allocate(Math.multiplyExact(aligned.length, Double.BYTES));
                            for (double value : aligned) buffer.putDouble(value);
                            buffer.flip();
                            result.offsets.put(feature, position);
                            while (buffer.hasRemaining()) position += result.channel.write(buffer, position);
                        }
                }
            }
            return result;
        } catch (IOException | RuntimeException error) {
            try { result.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public List<String> sampleIds() { return sampleIds; }
    public List<Feature> features(String primaryId) {
        return mapping.getOrDefault(primaryId, List.of());
    }

    /** Thread-safe positional read; missing mappings never become unadjusted fits. */
    public List<double[]> values(String primaryId) throws IOException {
        List<Feature> features = features(primaryId);
        if (features.isEmpty())
            throw new IllegalArgumentException("no adjustment mapping for feature: " + primaryId);
        List<double[]> result = new ArrayList<>();
        for (Feature feature : features) {
            Long offset = offsets.get(feature);
            if (offset == null)
                throw new IllegalArgumentException("adjustment feature is absent: " + feature.label());
            ByteBuffer buffer = ByteBuffer.allocate(Math.multiplyExact(sampleIds.size(), Double.BYTES));
            long position = offset;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read < 0) throw new IOException("truncated adjustment cache");
                position += read;
            }
            buffer.flip();
            double[] values = new double[sampleIds.size()];
            for (int i = 0; i < values.length; i++) values[i] = buffer.getDouble();
            result.add(values);
        }
        return result;
    }

    @Override public void close() throws IOException {
        try { channel.close(); } finally { Files.deleteIfExists(cache); }
    }
}
