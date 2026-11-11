/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.sequence.SalmonQuantification;

/** Complete captured bulk inputs; portable replay never opens recorded producer paths. */
final class SalmonQuantInputs {
    private SalmonQuantInputs() { }
    record Source(Path path, NextflowFiles.Pin pin) { }
    record Captured(Context context, List<Source> originals, Path originalIndex, Map<String, Object> originalIndexTree) {
        Captured { originals = List.copyOf(originals); originalIndexTree = Map.copyOf(originalIndexTree); }
        void verify(SalmonQuantRecipe.Request request, Runnable check) throws IOException {
            context.verify(request, check);
            for (Source source : originals) require(source.pin().equals(NextflowFiles.pin(source.path(), source.pin().bytes(), check)), "Original Salmon quantification input changed: " + source.path());
            if (originalIndex != null) require(originalIndexTree.equals(SalmonIndexWorkflow.inventory(originalIndex, SalmonQuantRecipe.process(request.capacities()), check)), "Complete original Salmon index bundle changed");
        }
    }
    record Context(Path root, SalmonReads.Context reads, SalmonReferences.Context reference, SalmonAlignments.Context alignments,
                   SalmonIndexWorkflow.Result index, List<SalmonQuantification.Target> targets, long processedFragments, long sourceDecoys,
                   boolean declaredRetainedPopulationMatchesSource, Map<String, Object> protectedInputs, Map<String, Object> protectedIndex,
                   Map<String, Object> document) {
        Context { targets = List.copyOf(targets); protectedInputs = Map.copyOf(protectedInputs); protectedIndex = Map.copyOf(protectedIndex); document = Map.copyOf(document); }
        void verify(SalmonQuantRecipe.Request request, Runnable check) throws IOException {
            var process = SalmonQuantRecipe.process(request.capacities());
            require(protectedInputs.equals(SalmonIndexWorkflow.inventory(root.resolve("inputs"), process, check)), "Complete captured Salmon quantification inputs changed");
            if (index != null) require(protectedIndex.equals(SalmonIndexWorkflow.inventory(root.resolve("index-bundle"), process, check)), "Complete captured Salmon index bundle changed");
        }
        void requireSupportedPopulation(SalmonQuantRecipe.Request request) throws IOException {
            if (index != null && index.audit().ledger().decoys().indexDecoys() > 0 && !index.audit().ledger().decoys().activeBoundaryHasContigId())
                throw new SalmonRuntime.Unavailable("This original index has an active decoy boundary without a contig reference ID; its user-fit behavior has no admitted contract. Inspect the complete index ledger, revise references explicitly and build a NEW index before dependent analysis");
            if (!declaredRetainedPopulationMatchesSource && request.population() == SalmonQuantRecipe.Population.REQUIRE_DECLARED_NONDECOYS)
                throw new SalmonRuntime.Unavailable("Exact original Salmon runtime cannot supply every declared retained nondecoy in this index: " + index.audit().ledger().decoys().disagreements().size()
                    + " source/declared assignment disagreements. No user fit was launched. Inspect index-bundle/REFERENCE-LEDGER.jsonl; explicitly revise/build a NEW reference/index or select ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER in a NEW recipe after reviewing its exclusions. Never impute missing rows");
        }
    }
    static Captured capture(Path root, SalmonQuantRecipe.Request request, Runnable check) throws IOException {
        NextflowFiles.directory(root); Files.createDirectory(root.resolve("inputs")); var originals = new ArrayList<Source>(); Path originalIndex = null; Map<String, Object> originalTree = Map.of();
        if (request.mode() == SalmonQuantRecipe.Mode.TRANSCRIPTOME_ALIGNMENTS) {
            Path fasta = Path.of(request.reference().path()), alignment = Path.of(request.alignments().path());
            var reference = SalmonReferences.inspect(fasta, request.reference().encoding(), request.reference().headers(), null, referenceLimits(request), check);
            var targets = reference.references().stream().map(r -> new SalmonQuantification.Target(r.indexName(), r.raw().length())).toList();
            var aligned = SalmonAlignments.inspect(alignment, request.alignments().format(), request.alignments().layout(), targets, alignmentLimits(request, reference.census().estimatedRetainedBytes()), check);
            require(Math.addExact(reference.fastaPin().bytes(), aligned.pin().bytes()) <= request.capacity("input_bytes"), "Complete Salmon alignment/FASTA combined input-byte capacity exceeded");
            originals.add(new Source(fasta, reference.fastaPin())); originals.add(new Source(alignment, aligned.pin()));
            SalmonIndexWorkflow.copy(fasta, root.resolve("inputs/" + referenceName(request)), reference.fastaPin(), check);
            SalmonIndexWorkflow.copy(alignment, root.resolve("inputs/" + alignmentName(request)), aligned.pin(), check);
        } else {
            originalIndex = Path.of(request.indexBundle()); var index = SalmonIndexWorkflow.verify(originalIndex, check);
            require(index.recipe().profile().equals(request.profile()), "Salmon mapping index and quantification exact editions differ; cross-edition index reuse has no admitted contract");
            originalTree = SalmonIndexWorkflow.inventory(originalIndex, SalmonQuantRecipe.process(request.capacities()), check);
            copyIndex(originalIndex, root.resolve("index-bundle"), originalTree, check);
            require(originalTree.equals(SalmonIndexWorkflow.inventory(originalIndex, SalmonQuantRecipe.process(request.capacities()), check)), "Original Salmon index changed during complete capture");
            var raw = request.reads(); Path first = Path.of(raw.first()), second = raw.second() == null ? null : Path.of(raw.second());
            var reads = SalmonReads.inspect(first, second, readLimits(request), raw.alphabet(), raw.pairing(), check);
            originals.add(new Source(first, reads.firstPin())); SalmonIndexWorkflow.copy(first, root.resolve("inputs/read1.fastq"), reads.firstPin(), check);
            if (second != null) { originals.add(new Source(second, reads.secondPin())); SalmonIndexWorkflow.copy(second, root.resolve("inputs/read2.fastq"), reads.secondPin(), check); }
        }
        var result = new Captured(readCaptured(root, request, check), originals, originalIndex, originalTree); result.verify(request, check); return result;
    }
    static Context readCaptured(Path root, SalmonQuantRecipe.Request request, Runnable check) throws IOException {
        NextflowFiles.directory(root); SalmonReads.Context reads = null; SalmonReferences.Context reference = null; SalmonAlignments.Context alignments = null; SalmonIndexWorkflow.Result index = null;
        List<SalmonQuantification.Target> targets; long processed, decoys = 0, retained; boolean matches = true; var document = new LinkedHashMap<String, Object>();
        document.put("input_mode", request.mode().name()); document.put("population_policy", request.population().name());
        if (request.mode() == SalmonQuantRecipe.Mode.TRANSCRIPTOME_ALIGNMENTS) {
            reference = SalmonReferences.inspect(root.resolve("inputs/" + referenceName(request)), request.reference().encoding(), request.reference().headers(), null, referenceLimits(request), check);
            targets = reference.references().stream().map(r -> new SalmonQuantification.Target(r.indexName(), r.raw().length())).toList();
            alignments = SalmonAlignments.inspect(root.resolve("inputs/" + alignmentName(request)), request.alignments().format(), request.alignments().layout(), targets, alignmentLimits(request, reference.census().estimatedRetainedBytes()), check);
            require(Math.addExact(reference.fastaPin().bytes(), alignments.pin().bytes()) <= request.capacity("input_bytes"), "Complete captured Salmon alignment/FASTA combined input-byte capacity exceeded");
            retained = Math.addExact(reference.census().estimatedRetainedBytes(), alignments.estimatedRetainedBytes());
            processed = alignments.fragments(); document.put("reference_fasta", SalmonIndexWorkflow.fingerprint(reference.fastaPin())); document.put("alignment", SalmonIndexWorkflow.fingerprint(alignments.pin()));
            document.put("physical_alignment_records", alignments.records()); document.put("query_fragments", alignments.fragments()); document.put("input_mapped_query_groups", alignments.mappedFragments()); document.put("input_unmapped_query_groups", alignments.unmappedFragments());
            document.put("secondary_records", alignments.secondaryRecords()); document.put("supplementary_records", alignments.supplementaryRecords());
            document.put("reference_population", targets.size()); document.put("alignment_reference_contract", "complete exact transcriptome name/order/length; no genome projection");
        } else {
            index = SalmonIndexWorkflow.verify(root.resolve("index-bundle"), check); require(index.recipe().profile().equals(request.profile()), "Captured Salmon index and fit exact editions differ");
            retained = Math.addExact(262144, index.audit().tables().estimatedRetainedBytes());
            for (var row : index.audit().ledger().inputs()) retained = Math.addExact(retained, Math.addExact(1024, Math.multiplyExact(16L, row.input().raw().header().length() + 2L * row.input().indexName().length())));
            var assignments = index.audit().ledger().decoys(); matches = assignments.matchesDeclaredPopulation(); decoys = assignments.originalRuntimeDecoys();
            targets = request.population() == SalmonQuantRecipe.Population.REQUIRE_DECLARED_NONDECOYS ? assignments.declaredNondecoys() : assignments.originalRuntimeNondecoys();
            var raw = request.reads(); reads = SalmonReads.inspect(root.resolve("inputs/read1.fastq"), request.mode() == SalmonQuantRecipe.Mode.PAIRED_FASTQ ? root.resolve("inputs/read2.fastq") : null, readLimits(request), raw.alphabet(), raw.pairing(), check);
            processed = reads.firstStats().reads(); document.put("index_manifest", SalmonIndexWorkflow.fingerprint(index.manifest())); document.put("read1", readDocument(reads.firstPin(), reads.firstStats()));
            if (reads.secondPin() != null) document.put("read2", readDocument(reads.secondPin(), reads.secondStats())); document.put("read_interpretation", reads.interpretation());
            document.put("raw_fasta_nondecoys", index.audit().ledger().inputs().stream().filter(r -> !r.input().declaredDecoy()).count()); document.put("raw_references_not_retained", index.audit().ledger().notRetained());
            document.put("retained_declared_nondecoys", assignments.declaredNondecoys().size()); document.put("original_runtime_nondecoys", assignments.originalRuntimeNondecoys().size()); document.put("declared_source_disagreements", assignments.disagreements().size());
            document.put("complete_reference_ledger", "index-bundle/REFERENCE-LEDGER.jsonl");
        }
        require(!targets.isEmpty(), "Original Salmon source population contains no quantified targets");
        document.put("declared_retained_population_matches_source", matches); document.put("quantified_population_targets", targets.size()); document.put("complete_processed_input_fragments", processed); document.put("original_runtime_decoys", decoys);
        document.put("user_analysis_admitted", false); var process = SalmonQuantRecipe.process(request.capacities());
        var protectedInputs = SalmonIndexWorkflow.inventory(root.resolve("inputs"), process, check); var protectedIndex = index == null ? Map.<String, Object>of() : SalmonIndexWorkflow.inventory(root.resolve("index-bundle"), process, check);
        for (String name : protectedInputs.keySet()) retained = Math.addExact(retained, 2048 + 16L * name.length());
        for (String name : protectedIndex.keySet()) retained = Math.addExact(retained, 2048 + 16L * name.length());
        require(retained <= request.capacity("retained_bytes"), "Combined complete Salmon input/reference/identity metadata exceeds retained capacity");
        document.put("estimated_input_metadata_bytes", retained); document.put("metadata_estimate_is_jvm_heap_guarantee", false);
        return new Context(root, reads, reference, alignments, index, targets, processed, decoys, matches, protectedInputs, protectedIndex, document);
    }
    static String referenceName(SalmonQuantRecipe.Request request) { return request.reference().encoding() == RepeatFasta.Encoding.GZIP_UTF8 ? "reference.fa.gz" : "reference.fa"; }
    static String alignmentName(SalmonQuantRecipe.Request request) { return request.alignments().format() == SalmonQuantRecipe.Format.BAM ? "alignments.bam" : "alignments.sam"; }
    private static SalmonReads.Limits readLimits(SalmonQuantRecipe.Request request) {
        var f = new FastpFastq.Limits(request.capacity("input_bytes"), request.capacity("decoded_characters"), request.capacity("read_records"), request.capacity("read_bases"), (int) request.capacity("read_length"), (int) request.capacity("header_characters"));
        return new SalmonReads.Limits(f, request.capacity("input_bytes"), request.capacity("read_bases"));
    }
    private static SalmonReferences.Limits referenceLimits(SalmonQuantRecipe.Request request) {
        long retained = request.capacity("retained_bytes"); var f = new RepeatFasta.Limits(request.capacity("input_bytes"), request.capacity("decoded_characters"), request.capacity("reference_residues"), 2147483647, (int) request.capacity("references"),
            (int) request.capacity("line_characters"), (int) request.capacity("header_characters"), retained);
        return new SalmonReferences.Limits(f, 1, 1, 1, retained);
    }
    private static SalmonAlignments.Limits alignmentLimits(SalmonQuantRecipe.Request request, long referenceEstimate) throws IOException {
        long remaining = request.capacity("retained_bytes") - referenceEstimate; require(remaining >= 262144, "Complete Salmon alignment identity metadata has no remaining declared capacity after reference admission");
        return new SalmonAlignments.Limits(request.capacity("input_bytes"), request.capacity("alignment_decoded_bytes"), request.capacity("alignment_records"), request.capacity("alignment_groups"), (int) request.capacity("hits_per_group"), (int) request.capacity("references"),
        (int) request.capacity("read_length"), (int) request.capacity("line_characters"), (int) request.capacity("metadata_bytes"), request.capacity("name_bytes"), remaining);
    }
    private static Map<String, Object> readDocument(NextflowFiles.Pin pin, SalmonReads.Statistics statistics) {
        var result = new LinkedHashMap<String, Object>(SalmonIndexWorkflow.fingerprint(pin)); result.put("reads", statistics.reads()); result.put("bases", statistics.bases()); result.put("minimum_length", statistics.minimumLength()); result.put("maximum_length", statistics.maximumLength());
        result.put("gzip", statistics.gzip()); result.put("unterminated_final_line", statistics.unterminatedFinalLine()); result.put("bases_outside_upper_acgtn", statistics.basesOutsideUpperAcgtn()); result.put("nonempty_plus_lines", statistics.nonemptyPlusLines()); return Map.copyOf(result);
    }
    private static void copyIndex(Path source, Path target, Map<String, Object> inventory, Runnable check) throws IOException {
        Files.createDirectory(target);
        for (var entry : new TreeMap<>(inventory).entrySet()) {
            check.run(); String name = NextflowFiles.member(entry.getKey()); var row = NextflowFiles.object(entry.getValue()); Path destination = target.resolve(name);
            if (row.get("kind").equals("directory")) Files.createDirectory(destination);
            else SalmonIndexWorkflow.copy(source.resolve(name), destination, NextflowFiles.fingerprint(row, 1000000000000L), check);
        }
    }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
