/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyCleanupTest {
    @TempDir Path root;
    private final StudyProject.Actor actor = StudyProject.Actor.human("cleanup-scientist");

    @Test void identicalNewerOutputsCanBeDeletedWithoutLosingProvenanceResumeOrExport() throws Exception {
        prepare();
        String first = run(true);
        Map<String, String> original = manifests(first);
        String input = Files.readString(root.resolve("results.tsv"));
        String second = run(true);
        String ledger = Files.readString(root.resolve("ledger/events.jsonl"));
        Path preview = root.resolve("cleanup.yaml");
        Map<String, Object> plan = StudyCleanup.writePreview(root, preview, List.of());
        assertEquals(ledger, Files.readString(root.resolve("ledger/events.jsonl")), "Preview is read-only");
        assertEquals(original.size(), selected(plan).size());
        assertTrue(((Number) plan.get("reclaimable_bytes")).longValue() > 0);
        for (Map<String, Object> result : selected(plan)) {
            assertTrue(result.get("result_id").toString().startsWith(first + "/"));
            assertTrue(result.get("replacement_id").toString().startsWith(second + "/"));
            assertEquals("identical_cache_and_outputs", map(list(result.get("replacement_evidence")).get(0)).get("basis"));
        }
        Map<String, Object> receipt = StudyCleanup.apply(root, preview, actor);
        assertEquals(plan.get("reclaimable_bytes"), receipt.get("bytes_deleted"));
        assertEquals(original, manifests(first), "Original manifests must remain byte-for-byte intact");
        assertEquals(input, Files.readString(root.resolve("results.tsv")));
        for (Map<String, Object> result : selected(plan)) for (Object raw : list(result.get("files")))
            assertFalse(Files.exists(root.resolve(map(raw).get("path").toString())));
        assertEquals(second, StudyProject.status(root).get("run_id"), "Cleanup cannot change the current run");
        StudyProject.verify(root);
        for (Object raw : list(StudyEvidenceIndex.snapshot(root).get("entries"))) {
            Map<String, Object> entry = map(raw);
            if (entry.get("run_id").equals(first)) {
                assertEquals("complete", entry.get("state"));
                assertEquals("deleted", entry.get("availability"));
                assertEquals(actor.asMap(), map(entry.get("cleanup")).get("actor"));
                assertFalse(entry.containsKey("key_findings"));
            }
        }
        String html = Files.readString(root.resolve("index/index.html"));
        assertTrue(html.contains("deleted by cleanup"));
        assertFalse(html.contains("href=\"../runs/" + first + "/operations/H1.meta_analysis/payload.bin\""));
        Path archive = root.resolve("after-cleanup.zip");
        StudyEvidenceArchive.export(root, archive, actor, true);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("ledger/events.jsonl"));
            String bundle = new String(zip.getInputStream(zip.getEntry("EVIDENCE_BUNDLE.json")).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(bundle.contains("cleaned_results"));
            assertTrue(bundle.contains("replacement_id"));
        }
        AtomicInteger computations = new AtomicInteger();
        var resumed = StudyExecutionEngine.run(root, actor, context -> {
            computations.incrementAndGet();
            return execute(context);
        }, true, true);
        assertEquals(0, computations.get());
        assertEquals(original.size(), resumed.reused());
        for (String id : ids(resumed.runId())) {
            assertTrue(StudyIO.loadArtifact(directory(id).resolve("manifest.yaml")).get("reused_from").toString().contains(second));
        }
        String after = Files.readString(root.resolve("ledger/events.jsonl"));
        assertEquals(StudyIO.canonicalJson(receipt), StudyIO.canonicalJson(StudyCleanup.apply(root, preview, actor)));
        assertEquals(after, Files.readString(root.resolve("ledger/events.jsonl")), "Repeated apply is idempotent");
        StudyProject.verify(root);
    }

    @Test void changedInputsNeedAnExplicitRecordedScientistReplacement() throws Exception {
        prepare();
        String first = run(true);
        Files.writeString(root.resolve("results.tsv"), "feature_id\tbeta\tse\tp_value\nGENE1\t2\t.2\t.001\n");
        String second = run(true);
        assertTrue(selected(StudyCleanup.preview(root, List.of())).isEmpty(), "A newer date does not establish replacement");
        String old = leaf(first);
        String newer = second + old.substring(first.length());
        StudyCleanup.supersede(root, old, newer, "Corrected input edition replaces the original analysis", actor);
        Map<String, Object> plan = StudyCleanup.writePreview(root, root.resolve("changed.yaml"), List.of(old));
        assertEquals(1, selected(plan).size());
        Map<String, Object> evidence = map(list(selected(plan).get(0).get("replacement_evidence")).get(0));
        assertEquals("scientist_supersession", evidence.get("basis"));
        assertTrue(evidence.get("reason").toString().contains("Corrected input"));
        StudyCleanup.apply(root, root.resolve("changed.yaml"), actor);
        StudyProject.verify(root);
        String history = Files.readString(root.resolve("SESSION_HISTORY.md"));
        assertTrue(history.contains("result_superseded"));
        assertTrue(history.contains("cleanup_started"));
        assertTrue(history.contains("cleanup_completed"));
        assertTrue(history.contains("cleanup-scientist"));
    }

    @Test void mtimeAndRunNamesDoNotDefineNewerAndMismatchedOperationsAreRejected() throws Exception {
        prepare();
        String first = run(true), second = run(true);
        String old = leaf(first), newer = second + old.substring(first.length());
        Files.setLastModifiedTime(directory(old).resolve("manifest.yaml"), java.nio.file.attribute.FileTime.fromMillis(Long.MAX_VALUE / 1000));
        assertFalse(selected(StudyCleanup.preview(root, List.of())).isEmpty());
        assertThrows(IOException.class, () -> StudyCleanup.supersede(root, newer, old, "Reverse time", actor));
        String other = ids(second).stream().filter(id -> !id.equals(newer)).findFirst().orElseThrow();
        assertThrows(IOException.class, () -> StudyCleanup.supersede(root, old, other, "Different task", actor));
        assertThrows(IllegalArgumentException.class, () -> StudyCleanup.supersede(root, old, newer, "", actor));
        assertThrows(IllegalArgumentException.class, () -> StudyCleanup.supersede(root, old, newer, "AI choice",
            new StudyProject.Actor("llm", "assistant", "fixture")));
    }

    @Test void newerFailureDoesNotMakeSuccessfulResultsObsolete() throws Exception {
        prepare();
        String first = run(true);
        var failed = StudyExecutionEngine.run(root, actor, context -> {
            Files.writeString(context.outputDirectory().resolve("failure.log"), "failed fixture");
            return new StudyOperationExecutor.Result(StudyOperationExecutor.State.FAILED, "Fixture failure", List.of("failure.log"), Map.of());
        }, false, true);
        assertEquals("failed", failed.state());
        assertTrue(selected(StudyCleanup.preview(root, List.of())).isEmpty());
        assertThrows(IOException.class, () -> StudyCleanup.supersede(root, leaf(first),
            failed.runId() + leaf(first).substring(first.length()), "Failed replacement", actor));
    }

    @Test void failedOldResultCanOnlyBeRemovedWithExplicitSuccessfulReplacement() throws Exception {
        prepare();
        var failed = StudyExecutionEngine.run(root, actor, context -> {
            Files.writeString(context.outputDirectory().resolve("failure.log"), "diagnostic history");
            return new StudyOperationExecutor.Result(StudyOperationExecutor.State.FAILED, "Fixture failure", List.of("failure.log"), Map.of());
        }, false, true);
        String second = run(true), old = leaf(failed.runId());
        assertTrue(selected(StudyCleanup.preview(root, List.of())).isEmpty());
        StudyCleanup.supersede(root, old, second + old.substring(failed.runId().length()), "Successful corrected execution replaces failed attempt", actor);
        Path preview = root.resolve("failed.yaml");
        StudyCleanup.writePreview(root, preview, List.of(old));
        StudyCleanup.apply(root, preview, actor);
        assertEquals("failed", StudyIO.loadArtifact(directory(old).resolve("manifest.yaml")).get("state"));
        StudyProject.verify(root);
    }

    @Test void pinsAndRetainedDependencyClosureAreProtectedAndChangesInvalidatePreview() throws Exception {
        prepare();
        String first = run(true);
        run(true);
        Path preview = root.resolve("stale.yaml");
        StudyCleanup.writePreview(root, preview, List.of());
        String leaf = leaf(first);
        StudyCleanup.pin(root, leaf, true, "Cited by external manuscript", actor);
        assertThrows(IOException.class, () -> StudyCleanup.apply(root, preview, actor));
        assertThrows(IOException.class, () -> StudyCleanup.preview(root, List.of(leaf)));
        Map<String, Object> protectedPlan = StudyCleanup.preview(root, List.of());
        assertTrue(list(protectedPlan.get("retained")).stream().map(StudyCleanupTest::map)
            .anyMatch(item -> item.get("reason").toString().contains("Required by retained result")));
        StudyCleanup.pin(root, leaf, false, "Manuscript has been revised to cite the newer run", actor);
        assertFalse(selected(StudyCleanup.preview(root, List.of(leaf))).isEmpty());
        String parent = dependency(first);
        assertThrows(IOException.class, () -> StudyCleanup.preview(root, List.of(parent)), "A retained dependent still needs its source");
        assertTrue(Files.isRegularFile(directory(parent).resolve("payload.bin")));
    }

    @Test void retainedExplicitPathReferencesProtectOlderInputs() throws Exception {
        prepare();
        String first = run(true);
        String old = ids(first).get(0);
        String reference = "runs/" + first + "/operations/" + old.substring(first.length() + 1) + "/payload.bin";
        StudyExecutionEngine.run(root, actor, context -> {
            var result = execute(context);
            return new StudyOperationExecutor.Result(result.state(), result.summary(), result.outputs(), Map.of("source_file", reference));
        }, false, true);
        Map<String, Object> plan = StudyCleanup.preview(root, List.of());
        assertFalse(selected(plan).stream().anyMatch(item -> old.equals(item.get("result_id"))));
        assertTrue(list(plan.get("retained")).stream().map(StudyCleanupTest::map)
            .anyMatch(item -> old.equals(item.get("result_id")) && item.get("reason").toString().contains("Required by retained")));
    }

    @Test void observationEvidenceIsProtectedEvenAfterItsRunIsReplaced() throws Exception {
        prepare();
        String first = run(true);
        run(true);
        String old = leaf(first);
        StudyObservations.record(root, "review-output", "review", directory(old).resolve("payload.bin"), List.of("H1"),
            "Retain the observed evidence", List.of("Possible input issue"), "review_inputs", null, actor);
        Map<String, Object> preview = StudyCleanup.preview(root, List.of());
        assertFalse(selected(preview).stream().anyMatch(entry -> old.equals(entry.get("result_id"))));
        assertTrue(list(preview.get("retained")).stream().map(StudyCleanupTest::map)
            .anyMatch(entry -> old.equals(entry.get("result_id")) && entry.get("reason").toString().contains("observation")));
    }

    @Test void forgedCleanupWithoutReplacementEvidenceCannotExemptMissingOutputs() throws Exception {
        prepare();
        run(true); run(true);
        Map<String, Object> plan = StudyCleanup.preview(root, List.of());
        List<Object> entries = list(plan.get("results"));
        Map<String, Object> entry = map(entries.get(0));
        entry.put("replacement_evidence", List.of());
        entries.set(0, entry); plan.put("results", entries); reseal(plan);
        StudyProject.append(root, "cleanup_started", actor, Map.of("cleanup_id", plan.get("cleanup_id"), "plan", plan));
        assertThrows(IOException.class, () -> StudyProject.verify(root));
    }

    @Test void changedOrUnexpectedFilesAreNeverSilentlyDeleted() throws Exception {
        prepare();
        String first = run(true);
        run(true);
        Path preview = root.resolve("inventory.yaml");
        StudyCleanup.writePreview(root, preview, List.of());
        Path payload = directory(leaf(first)).resolve("payload.bin");
        byte[] original = Files.readAllBytes(payload);
        Files.writeString(payload, "tampered");
        assertThrows(IOException.class, () -> StudyCleanup.apply(root, preview, actor));
        Files.write(payload, original);
        Path extra = directory(leaf(first)).resolve("investigator-notes.txt");
        Files.writeString(extra, "unregistered work must not be removed");
        assertThrows(IOException.class, () -> StudyCleanup.apply(root, preview, actor));
        assertTrue(Files.isRegularFile(payload));
        assertTrue(Files.isRegularFile(extra));
        assertTrue(StudyLedger.read(root).stream().noneMatch(event -> "cleanup_started".equals(event.get("event_type"))));
    }

    @Test void tamperedSelectionAndEscapingPathsFailBeforeDeletion() throws Exception {
        prepare();
        String first = run(true);
        run(true);
        Path preview = root.resolve("tampered.yaml");
        Map<String, Object> plan = StudyCleanup.writePreview(root, preview, List.of());
        Map<String, Object> changed = StudyIO.copyMap(plan);
        List<Object> entries = list(changed.get("results"));
        Map<String, Object> entry = map(entries.get(0));
        List<Object> files = list(entry.get("files"));
        Map<String, Object> file = map(files.get(0));
        file.put("path", "../outside.bin");
        files.set(0, file); entry.put("files", files); entries.set(0, entry); changed.put("results", entries);
        StudyIO.writeYaml(preview, changed);
        assertThrows(IOException.class, () -> StudyCleanup.apply(root, preview, actor));
        reseal(changed);
        StudyIO.writeYaml(preview, changed);
        assertThrows(IOException.class, () -> StudyCleanup.apply(root, preview, actor));
        assertTrue(Files.isRegularFile(directory(leaf(first)).resolve("payload.bin")));
    }

    @Test void oversizedPreviewFailsBeforeAnUnreadableIntentCanBeRecorded() throws Exception {
        prepare();
        run(true); run(true);
        Map<String, Object> plan = StudyCleanup.preview(root, List.of());
        plan.put("large_review_note", "x".repeat(12_000_001));
        reseal(plan);
        Path preview = root.resolve("oversized.yaml");
        StudyIO.writeYaml(preview, plan);
        String before = Files.readString(root.resolve("ledger/events.jsonl"));
        IOException failure = assertThrows(IOException.class, () -> StudyCleanup.apply(root, preview, actor));
        assertTrue(failure.getMessage().contains("too large for one audit event"));
        assertEquals(before, Files.readString(root.resolve("ledger/events.jsonl")));
        StudyProject.verify(root);
    }

    @Test void linkedOutputsAreRefusedEvenWhenTheirContentHashesMatch() throws Exception {
        prepare();
        String first = run(true);
        run(true);
        Path payload = directory(leaf(first)).resolve("payload.bin");
        byte[] bytes = Files.readAllBytes(payload);
        Path source = root.resolve("private-original.bin");
        Files.write(source, bytes);
        Files.delete(payload);
        try {
            Files.createSymbolicLink(payload, source);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            Files.write(payload, bytes);
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Host does not allow test symbolic links");
        }
        StudyProject.verify(root);
        assertThrows(IOException.class, () -> StudyCleanup.preview(root, List.of()));
        assertArrayEquals(bytes, Files.readAllBytes(source));
        assertTrue(Files.isSymbolicLink(payload));
    }

    @Test void declaredInputAliasesProtectTheResultTheyActuallyReference() throws Exception {
        prepare();
        String first = run(true);
        run(true);
        String old = leaf(first);
        Path input = root.resolve("results.tsv");
        byte[] original = Files.readAllBytes(input);
        Files.delete(input);
        try {
            Files.createSymbolicLink(input, directory(old).resolve("payload.bin"));
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            Files.write(input, original);
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Host does not allow test symbolic links");
        }
        Map<String, Object> preview = StudyCleanup.preview(root, List.of());
        assertFalse(selected(preview).stream().anyMatch(entry -> old.equals(entry.get("result_id"))));
        assertTrue(list(preview.get("retained")).stream().map(StudyCleanupTest::map)
            .anyMatch(entry -> old.equals(entry.get("result_id")) && entry.get("reason").equals("Declared study input/reference")));
        assertTrue(Files.isRegularFile(directory(old).resolve("payload.bin")));
    }

    @Test void recordedPartialDeletionVerifiesAndCanBeResumedWithTheSamePreview() throws Exception {
        prepare();
        String first = run(true);
        run(true);
        Path preview = root.resolve("interrupted.yaml");
        Map<String, Object> plan = StudyCleanup.writePreview(root, preview, List.of());
        StudyProject.append(root, "cleanup_started", actor, Map.of("cleanup_id", plan.get("cleanup_id"), "plan", plan,
            "summary", "Simulated interruption after recorded cleanup intent"));
        Path file = root.resolve(map(list(selected(plan).get(0).get("files")).get(0)).get("path").toString());
        Files.delete(file);
        StudyProject.verify(root);
        assertTrue(StudyCleanup.unavailable(root).contains(leaf(first)));
        StudyEvidenceIndex.rebuild(root);
        assertTrue(Files.readString(root.resolve("index/index.json")).contains("cleanup_pending"));
        // Simulate a crash between an event append and regeneration of derived views.
        Files.delete(root.resolve("SESSION_HISTORY.md"));
        StudyCleanup.apply(root, preview, actor);
        StudyProject.verify(root);
        assertTrue(StudyCleanup.removals(StudyLedger.read(root)).values().stream()
            .allMatch(removal -> "deleted".equals(removal.get("availability"))));
        Files.writeString(file, "reappeared");
        assertThrows(IOException.class, () -> StudyProject.verify(root));
    }

    @Test void interruptionRecordsFailureAndDoesNotExpandTheAuthorizedInventory() throws Exception {
        prepare();
        String first = run(true);
        run(true);
        Path preview = root.resolve("retry.yaml");
        Map<String, Object> plan = StudyCleanup.writePreview(root, preview, List.of());
        StudyProject.append(root, "cleanup_started", actor, Map.of("cleanup_id", plan.get("cleanup_id"), "plan", plan,
            "summary", "Simulated started cleanup"));
        Path extra = directory(leaf(first)).resolve("new-notes.txt");
        Files.writeString(extra, "written after cleanup started");
        assertThrows(IOException.class, () -> StudyCleanup.apply(root, preview, actor));
        assertTrue(Files.exists(extra));
        assertTrue(StudyLedger.read(root).stream().anyMatch(event -> "cleanup_interrupted".equals(event.get("event_type"))));
        Files.delete(extra);
        StudyCleanup.apply(root, preview, actor);
        StudyProject.verify(root);
    }

    @Test void ordinaryMissingFilesAndMissingManifestsRemainIntegrityFailures() throws Exception {
        prepare();
        String run = run(true);
        Path payload = directory(leaf(run)).resolve("payload.bin");
        byte[] content = Files.readAllBytes(payload);
        Files.delete(payload);
        assertThrows(IOException.class, () -> StudyProject.verify(root));
        Files.write(payload, content);
        Path manifest = directory(leaf(run)).resolve("manifest.yaml");
        Files.delete(manifest);
        assertThrows(IOException.class, () -> StudyProject.verify(root));
    }

    @Test void noCleanupWhileAnotherMutationHoldsTheProjectLock() throws Exception {
        prepare();
        run(true); run(true);
        Path preview = root.resolve("locked.yaml");
        StudyCleanup.writePreview(root, preview, List.of());
        try (FileChannel channel = FileChannel.open(root.resolve(".study-run.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertTrue(lock.isValid());
            assertThrows(IOException.class, () -> StudyCleanup.apply(root, preview, actor));
        }
        assertThrows(IllegalArgumentException.class, () -> StudyCleanup.apply(root, preview, StudyProject.Actor.human("unspecified")));
    }

    @Test void replacementChainsResolveToLiveNewerEvidence() throws Exception {
        prepare();
        String first = run(true);
        Files.writeString(root.resolve("results.tsv"), "feature_id\tbeta\tse\tp_value\nGENE1\t3\t.3\t.001\n");
        String second = run(true);
        String old = leaf(first), middle = second + old.substring(first.length());
        StudyCleanup.supersede(root, old, middle, "Revised analysis input", actor);
        String third = run(true);
        Map<String, Object> plan = StudyCleanup.preview(root, List.of(old));
        assertEquals(third + old.substring(first.length()), selected(plan).get(0).get("replacement_id"));
        assertEquals(2, list(selected(plan).get(0).get("replacement_evidence")).size());
    }

    private void prepare() throws Exception {
        Files.writeString(root.resolve("results.tsv"), "feature_id\tbeta\tse\tp_value\nGENE1\t1\t.1\t.001\n");
        Files.writeString(root.resolve("study.yaml"), """
            schema_version: 1
            project: {id: cleanup-fixture, title: Cleanup, description: Synthetic cleanup integration tests.}
            resources: {downloads: deny, network: offline}
            datasets:
              - id: data
                type: transcriptomics
                stage: association_results
                feature_id_type: gene_symbol
                files: {results: results.tsv}
            hypotheses:
              - id: H1
                description: Test result retention.
                outcome: BMI
                analysis: {mode: manual, goals: [meta_analysis]}
            """);
        StudyProject.resolve(root.resolve("study.yaml"), root, actor);
        var plan = StudyPlanner.create(root, actor);
        StudyPlanner.approve(root, plan.id(), actor, true);
    }

    private String run(boolean resume) throws Exception {
        var result = StudyExecutionEngine.run(root, actor, StudyCleanupTest::execute, resume, true);
        assertEquals("completed", result.state());
        return result.runId();
    }

    private static StudyOperationExecutor.Result execute(StudyOperationExecutor.Context context) throws IOException {
        Files.writeString(context.outputDirectory().resolve("payload.bin"), "Synthetic payload for " + context.operation().get("id"));
        Files.createDirectories(context.outputDirectory().resolve("nested"));
        Files.writeString(context.outputDirectory().resolve("nested/second.bin"), "second file");
        return StudyOperationExecutor.Result.complete("Synthetic operation", List.of("payload.bin", "nested/second.bin"));
    }

    private List<String> ids(String run) throws IOException {
        List<String> result = new ArrayList<>();
        for (Map<String, Object> event : StudyLedger.read(root)) {
            Map<String, Object> payload = map(event.get("payload"));
            if (run.equals(payload.get("run_id")) && Set.of("operation_completed", "operation_reused",
                    "operation_failed", "operation_waiting", "operation_skipped").contains(event.get("event_type")))
                result.add(run + "/" + payload.get("operation_id"));
        }
        return result;
    }

    private String leaf(String run) throws IOException {
        List<String> ids = ids(run);
        return ids.get(ids.size() - 1);
    }

    private String dependency(String run) throws IOException {
        for (String id : ids(run)) {
            Map<String, Object> manifest = StudyIO.loadArtifact(directory(id).resolve("manifest.yaml"));
            for (String key : map(manifest.get("input_sha256")).keySet())
                if (key.startsWith("operation:")) return run + "/" + key.substring("operation:".length());
        }
        throw new AssertionError("Fixture must have a dependency");
    }

    private Map<String, String> manifests(String run) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : ids(run)) result.put(id, StudyIO.hash(directory(id).resolve("manifest.yaml")));
        return result;
    }

    private Path directory(String id) {
        String[] parts = id.split("/");
        return root.resolve("runs/" + parts[0] + "/operations/" + parts[1]);
    }

    private static void reseal(Map<String, Object> document) {
        document.remove("cleanup_id"); document.remove("plan_sha256");
        String hash = StudyIO.hash(document);
        document.put("cleanup_id", "cleanup-" + hash.substring(0, 16)); document.put("plan_sha256", hash);
    }

    private static List<Map<String, Object>> selected(Map<String, Object> plan) {
        return list(plan.get("results")).stream().map(StudyCleanupTest::map).toList();
    }
    private static Map<String, Object> map(Object value) { return StudyIO.map(value, "fixture"); }
    private static List<Object> list(Object value) { return StudyIO.list(value, "fixture"); }

}
