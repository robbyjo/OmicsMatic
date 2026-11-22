/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reviewed removal of replaced operation outputs, retaining immutable manifests. */
public final class StudyCleanup {
    private StudyCleanup() { }

    private record Result(String id, Path directory, Map<String, Object> manifest,
            String hash, int order, Map<String, Object> operation) { }
    private record Replacement(Result result, List<Object> evidence) { }
    private record Catalog(Path root, List<Map<String, Object>> events,
            Map<String, Result> results, Map<String, Map<String, Object>> supersessions,
            Map<String, Boolean> pins, Map<String, Map<String, Object>> removals,
            Map<String, Result> newestIdentical) { }

    /** Preview only; no events, analysis outputs or derived views are changed. */
    public static Map<String, Object> preview(Path project, List<String> selection)
            throws IOException {
        return StudyProjectLock.with(project, () -> {
            Path root = project.toRealPath();
            StudyProject.verify(root);
            return plan(catalog(root), selection);
        });
    }

    /** Save a new preview outside the authoritative evidence directories. */
    public static Map<String, Object> writePreview(Path project, Path output,
            List<String> selection) throws IOException {
        return StudyProjectLock.with(project, () -> {
            Path root = project.toRealPath();
            Path target = output.toAbsolutePath().normalize();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Cleanup preview already exists; choose a new filename: " + target);
            if (target.startsWith(root)) {
                Path relative = root.relativize(target);
                if (Set.of("runs", "ledger", "state", "plans",
                        "amendments", "observations", "questions", "answers", "proposals", "index")
                        .contains(relative.getName(0).toString()))
                    throw new IOException("Cleanup preview cannot be inside an evidence directory");
                safePath(root, portable(root, target));
            }
            Map<String, Object> document = preview(root, selection);
            StudyIO.writeYaml(target, document);
            return document;
        });
    }

    /** Explicit scientist determination for changed analyses (age alone is insufficient). */
    public static void supersede(Path project, String obsolete, String replacement,
            String reason, StudyProject.Actor actor) throws IOException {
        requireHuman(actor, reason);
        StudyProjectLock.with(project, () -> {
            Path root = project.toRealPath();
            StudyProject.verify(root);
            Catalog catalog = catalog(root);
            Result old = requireResult(catalog, obsolete);
            Result newer = requireResult(catalog, replacement);
            if (catalog.removals.containsKey(obsolete) || catalog.removals.containsKey(replacement))
                throw new IOException("A cleanup has already started for one of these results");
            requireNewer(old, newer);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("result_id", old.id);
            payload.put("manifest_sha256", old.hash);
            payload.put("replacement_id", newer.id);
            payload.put("replacement_manifest_sha256", newer.hash);
            payload.put("reason", reason);
            payload.put("summary", "Marked " + old.id + " obsolete, replaced by " + newer.id + ": " + reason);
            StudyProject.append(root, "result_superseded", actor, payload);
            StudyEvidenceIndex.rebuild(root);
            return null;
        });
    }

    /** Protect evidence used outside the project, or release a previous protection. */
    public static void pin(Path project, String resultId, boolean pinned,
            String reason, StudyProject.Actor actor) throws IOException {
        requireHuman(actor, reason);
        StudyProjectLock.with(project, () -> {
            Path root = project.toRealPath();
            StudyProject.verify(root);
            Catalog catalog = catalog(root);
            Result result = requireResult(catalog, resultId);
            if (catalog.removals.containsKey(resultId))
                throw new IOException("A cleanup has already started for this result");
            StudyProject.append(root, pinned ? "result_pinned" : "result_unpinned", actor,
                Map.of("result_id", resultId, "manifest_sha256", result.hash, "reason", reason,
                    "summary", (pinned ? "Protected " : "Released protection for ") + resultId + ": " + reason));
            StudyEvidenceIndex.rebuild(root);
            return null;
        });
    }

    /** Apply exactly a reviewed preview, or resume its recorded, interrupted deletion. */
    public static Map<String, Object> apply(Path project, Path preview,
            StudyProject.Actor actor) throws IOException {
        requireHuman(actor, "Apply reviewed cleanup preview");
        return StudyProjectLock.with(project, () -> applyLocked(project.toRealPath(), preview, actor));
    }

    private static Map<String, Object> applyLocked(Path root, Path preview,
            StudyProject.Actor actor) throws IOException {
        Map<String, Object> document = StudyIO.loadArtifact(preview);
        verifyPlan(document);
        if (!root.toString().equals(document.get("project")))
            throw new IOException("Cleanup preview belongs to a different project location");
        // Recover derived views if the process died after a ledger append.
        StudyProject.repairViews(root);
        Catalog catalog = catalog(root);
        String id = String.valueOf(document.get("cleanup_id"));
        Map<String, Object> started = null, completed = null;
        for (Map<String, Object> event : catalog.events) {
            Map<String, Object> payload = StudyIO.optionalMap(event.get("payload"), "payload");
            if (!id.equals(payload.get("cleanup_id"))) continue;
            if ("cleanup_started".equals(event.get("event_type"))) started = payload;
            if ("cleanup_completed".equals(event.get("event_type"))) completed = payload;
        }
        if (completed != null) {
            StudyEvidenceIndex.rebuild(root);
            return completed;
        }
        List<Object> selected = StudyIO.list(document.get("results"), "cleanup.results");
        if (started == null) {
            Map<String, Object> current = plan(catalog,
                StudyIO.list(document.get("selection"), "selection").stream().map(Object::toString).toList());
            if (!StudyIO.canonicalJson(current).equals(StudyIO.canonicalJson(document)))
                throw new IOException("Cleanup preview is stale or modified; create and review a new --dry-run preview");
            if (selected.isEmpty()) return Map.of("files_deleted", 0, "bytes_deleted", 0, "summary", "No eligible obsolete outputs");
            StudyProject.append(root, "cleanup_started", actor, Map.of("cleanup_id", id, "plan", document,
                "summary", "Started reviewed cleanup " + id + " for " + selected.size()
                    + " replaced results; exact paths, hashes, sizes and replacement evidence recorded"));
        } else {
            if (!StudyIO.canonicalJson(started.get("plan")).equals(StudyIO.canonicalJson(document)))
                throw new IOException("Preview differs from the recorded cleanup intent");
            // State may have advanced since an interruption. Recheck protections and
            // the exact original replacement, without authorizing any additional files.
            validateRecovery(catalog, selected);
        }
        long bytes = 0;
        int files = 0;
        try {
            // Check the entire remaining inventory before deleting the first file.
            for (Object raw : selected) verifyInventory(root, StudyIO.map(raw, "result"), true);
            for (Object raw : selected) {
                Map<String, Object> result = StudyIO.map(raw, "result");
                for (Object fileValue : StudyIO.list(result.get("files"), "files")) {
                    Map<String, Object> file = StudyIO.map(fileValue, "file");
                    Path path = safePath(root, String.valueOf(file.get("path")));
                    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                        verifyFile(path, file);
                        Files.delete(path);
                    }
                    files++;
                    bytes = Math.addExact(bytes, ((Number) file.get("bytes")).longValue());
                }
            }
        } catch (IOException | RuntimeException failure) {
            try {
                StudyProject.append(root, "cleanup_interrupted", actor, Map.of("cleanup_id", id,
                    "files_processed", files, "bytes_processed", bytes,
                    "summary", "Cleanup " + id + " interrupted; rerun the same --apply preview to finish",
                    "error", String.valueOf(failure.getMessage())));
                StudyEvidenceIndex.rebuild(root);
            } catch (IOException | RuntimeException recordingFailure) { failure.addSuppressed(recordingFailure); }
            throw failure;
        }
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("cleanup_id", id);
        receipt.put("plan_sha256", document.get("plan_sha256"));
        receipt.put("files_deleted", files);
        receipt.put("bytes_deleted", bytes);
        receipt.put("summary", "Deleted " + files + " obsolete output files (" + bytes
            + " logical bytes) in " + id + "; original manifests and audit history retained");
        StudyProject.append(root, "cleanup_completed", actor, receipt);
        StudyProject.verify(root);
        StudyEvidenceIndex.rebuild(root);
        return receipt;
    }

    private static Map<String, Object> plan(Catalog catalog, List<String> selection) throws IOException {
        Set<String> requested = new LinkedHashSet<>(selection);
        if (requested.size() != selection.size()) throw new IllegalArgumentException("Duplicate result selection");
        for (String id : requested) requireResult(catalog, id);
        Map<String, Replacement> replacements = new LinkedHashMap<>();
        for (Result result : catalog.results.values()) {
            if (catalog.removals.containsKey(result.id) || outputs(result).isEmpty()) continue;
            Replacement replacement = replacement(result, catalog, new HashSet<>());
            if (replacement != null) replacements.put(result.id, replacement);
        }
        Set<String> candidates = new LinkedHashSet<>(replacements.keySet());
        if (!requested.isEmpty()) candidates.retainAll(requested);
        Map<String, String> protectedResults = protections(catalog, candidates);
        List<Object> selected = new ArrayList<>(), retained = new ArrayList<>();
        long bytes = 0;
        for (Result result : catalog.results.values()) {
            String reason = protectedResults.get(result.id);
            if (!candidates.contains(result.id) || reason != null) {
                retained.add(Map.of("result_id", result.id, "reason", catalog.removals.containsKey(result.id)
                    ? "Cleanup already recorded" : reason != null ? reason
                    : replacements.containsKey(result.id) ? "Outside requested selection"
                    : "No verified newer replacement established"));
                continue;
            }
            Replacement replacement = replacements.get(result.id);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("result_id", result.id);
            entry.put("manifest_sha256", result.hash);
            entry.put("replacement_id", replacement.result.id);
            entry.put("replacement_manifest_sha256", replacement.result.hash);
            entry.put("replacement_evidence", replacement.evidence);
            List<Object> files = new ArrayList<>();
            for (Map.Entry<String, Object> output : outputs(result).entrySet()) {
                Path path = safePath(catalog.root, portable(catalog.root, result.directory.resolve(output.getKey())));
                long size = Files.size(path);
                files.add(Map.of("path", portable(catalog.root, path), "sha256", output.getValue(), "bytes", size));
                bytes = Math.addExact(bytes, size);
            }
            entry.put("files", files);
            verifyInventory(catalog.root, entry, false);
            selected.add(entry);
        }
        if (!requested.isEmpty()) for (String id : requested)
            if (selected.stream().noneMatch(raw -> id.equals(StudyIO.map(raw, "result").get("result_id"))))
                throw new IOException("Result is protected or has no verified newer replacement: " + id
                    + ". " + protectedResults.getOrDefault(id, "Inspect an unrestricted --dry-run preview"));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schema_version", 1);
        document.put("kind", "study_cleanup");
        document.put("project", catalog.root.toString());
        document.put("ledger_head", catalog.events.isEmpty() ? "none"
            : catalog.events.get(catalog.events.size() - 1).get("event_hash"));
        document.put("selection", selection);
        document.put("results", selected);
        document.put("retained", retained);
        document.put("reclaimable_bytes", bytes);
        document.put("resume_impact", "Removed results cannot be reused. Retained verified replacements remain reusable; recomputation requires the original inputs and software.");
        String hash = StudyIO.hash(document);
        document.put("cleanup_id", "cleanup-" + hash.substring(0, 16));
        document.put("plan_sha256", hash);
        verifyPlan(document);
        return document;
    }

    private static Replacement replacement(Result old, Catalog catalog, Set<String> visited) throws IOException {
        if (!visited.add(old.id)) throw new IOException("Cyclic result replacement relationship");
        Result newer = null;
        Map<String, Object> declaration = catalog.supersessions.get(old.id);
        if (declaration != null) {
            newer = requireResult(catalog, String.valueOf(declaration.get("replacement_id")));
            if (!old.hash.equals(declaration.get("manifest_sha256"))
                    || !newer.hash.equals(declaration.get("replacement_manifest_sha256")))
                throw new IOException("Supersession manifest identity changed: " + old.id);
            requireNewer(old, newer);
        } else if ("complete".equals(old.manifest.get("state"))) {
            Result candidate = catalog.newestIdentical.get(identity(old));
            if (candidate != null && candidate.order > old.order) newer = candidate;
        }
        if (newer == null) return null;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source_result_id", old.id);
        evidence.put("source_manifest_sha256", old.hash);
        evidence.put("replacement_result_id", newer.id);
        evidence.put("replacement_manifest_sha256", newer.hash);
        evidence.put("basis", declaration == null ? "identical_cache_and_outputs" : "scientist_supersession");
        if (declaration != null) {
            evidence.put("event_id", declaration.get("event_id"));
            evidence.put("reason", declaration.get("reason"));
        }
        List<Object> proof = new ArrayList<>();
        proof.add(evidence);
        Replacement tail = replacement(newer, catalog, visited);
        if (tail != null) {
            proof.addAll(tail.evidence);
            return new Replacement(tail.result, proof);
        }
        if (catalog.removals.containsKey(newer.id)) return null;
        return new Replacement(newer, proof);
    }

    private static Map<String, String> protections(Catalog catalog, Set<String> candidates) throws IOException {
        Map<String, String> protectedResults = new LinkedHashMap<>();
        String current = String.valueOf(StudyProject.status(catalog.root).get("run_id"));
        for (Result result : catalog.results.values()) {
            if (result.id.startsWith(current + "/")) protectedResults.put(result.id, "Current run");
            boolean manuscript = String.valueOf(result.manifest.get("operation_type")).startsWith("manuscript");
            if (catalog.pins.getOrDefault(result.id, manuscript))
                protectedResults.put(result.id, manuscript ? "Manuscript evidence (use unpin only after review)" : "Scientist-pinned evidence");
        }
        protectReferences(catalog, StudyProject.resolved(catalog.root).document(), "Declared study input/reference", protectedResults, true);
        for (Map<String, Object> event : catalog.events) {
            String type = String.valueOf(event.get("event_type"));
            Map<String, Object> payload = StudyIO.map(event.get("payload"), "payload");
            if (type.equals("observation_recorded")) {
                Path path = safePath(catalog.root, "observations/observation-" + payload.get("observation_id") + ".yaml");
                protectReferences(catalog, StudyIO.load(path), "Evidence retained by an observation", protectedResults, true);
            } else if (Set.of("questions_recorded", "scientist_answers_recorded", "llm_interaction_recorded",
                    "browser_handoff_response_recorded").contains(type)) {
                protectReferences(catalog, payload, "Evidence retained by " + type, protectedResults, true);
            }
        }
        for (Map<String, Object> removal : catalog.removals.values()) {
            if (!"cleanup_pending".equals(removal.get("availability"))) continue;
            String replacement = String.valueOf(removal.get("replacement_id"));
            protectedResults.put(replacement, "Replacement supporting an interrupted cleanup");
        }
        boolean changed;
        do {
            int before = protectedResults.size();
            for (Result result : catalog.results.values()) {
                if (catalog.removals.containsKey(result.id)
                        || candidates.contains(result.id) && !protectedResults.containsKey(result.id)) continue;
                String reason = "Required by retained result " + result.id;
                String run = result.id.substring(0, result.id.indexOf('/'));
                Set<String> dependencies = new LinkedHashSet<>();
                for (String key : StudyIO.optionalMap(result.manifest.get("input_sha256"), "inputs").keySet())
                    if (key.startsWith("operation:")) dependencies.add(key.substring("operation:".length()));
                for (Object dependency : StudyIO.optionalList(result.operation.get("depends_on"), "depends_on"))
                    dependencies.add(String.valueOf(dependency));
                for (String dependency : dependencies) if (catalog.results.containsKey(run + "/" + dependency))
                    protectedResults.putIfAbsent(run + "/" + dependency, reason);
                protectReferences(catalog, result.operation, reason, protectedResults, true);
                protectReferences(catalog, result.manifest.get("details"), reason, protectedResults);
            }
            changed = before != protectedResults.size();
        } while (changed);
        return protectedResults;
    }

    private static void protectReferences(Catalog catalog, Object value, String reason,
            Map<String, String> protectedResults) {
        protectReferences(catalog, value, reason, protectedResults, false);
    }

    private static void protectReferences(Catalog catalog, Object value, String reason,
            Map<String, String> protectedResults, boolean resolveAliases) {
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> protectReferences(catalog, child, reason, protectedResults, resolveAliases));
        } else if (value instanceof Collection<?> values) {
            values.forEach(child -> protectReferences(catalog, child, reason, protectedResults, resolveAliases));
        } else if (value instanceof String text) {
            if (catalog.results.containsKey(text)) protectedResults.putIfAbsent(text, reason);
            try {
                Path path = catalog.root.resolve(text).toAbsolutePath().normalize();
                if (resolveAliases && Files.exists(path)) path = path.toRealPath();
                if (path.startsWith(catalog.root)) {
                    Path relative = catalog.root.relativize(path);
                    if (relative.getNameCount() >= 4 && relative.getName(0).toString().equals("runs")
                            && relative.getName(2).toString().equals("operations")) {
                        String id = relative.getName(1) + "/" + relative.getName(3);
                        Result result = catalog.results.get(id);
                        // The manifest itself survives cleanup; a provenance link to
                        // that manifest does not require retaining all its output bytes.
                        if (result != null && !path.equals(result.directory.resolve("manifest.yaml")))
                            protectedResults.putIfAbsent(id, reason);
                    }
                }
            } catch (InvalidPathException invalid) { /* Descriptive prose is not a path. */ }
            catch (IOException unreadable) {
                throw new java.io.UncheckedIOException("Cannot resolve a declared input/reference before cleanup", unreadable);
            }
        }
    }

    private static void validateRecovery(Catalog catalog, List<Object> selected) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (Object raw : selected) ids.add(String.valueOf(StudyIO.map(raw, "result").get("result_id")));
        Map<String, String> protections = protections(catalog, ids);
        for (Object raw : selected) {
            Map<String, Object> entry = StudyIO.map(raw, "result");
            Result old = requireResult(catalog, String.valueOf(entry.get("result_id")));
            Result newer = requireResult(catalog, String.valueOf(entry.get("replacement_id")));
            if (protections.containsKey(old.id)) throw new IOException("Cleanup recovery blocked: " + protections.get(old.id));
            requireNewer(old, newer);
            if (!old.hash.equals(entry.get("manifest_sha256")) || !newer.hash.equals(entry.get("replacement_manifest_sha256"))
                    || catalog.removals.containsKey(newer.id))
                throw new IOException("Original cleanup replacement is unavailable or modified");
        }
    }

    private static Catalog catalog(Path root) throws IOException {
        List<Map<String, Object>> events = StudyLedger.read(root);
        Map<String, Result> results = new LinkedHashMap<>();
        Map<String, Map<String, Object>> supersessions = new LinkedHashMap<>();
        Map<String, Boolean> pins = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Object>>> plans = new LinkedHashMap<>();
        int order = 0;
        for (Map<String, Object> event : events) {
            order++;
            String type = String.valueOf(event.get("event_type"));
            Map<String, Object> payload = StudyIO.optionalMap(event.get("payload"), "payload");
            if (type.equals("run_started")) {
                String run = String.valueOf(payload.get("run_id"));
                Path path = safePath(root, "plans/" + payload.get("plan_id") + "/analysis-plan.yaml");
                Map<String, Map<String, Object>> operations = new LinkedHashMap<>();
                for (Object raw : StudyIO.list(StudyIO.load(path).get("operations"), "operations")) {
                    Map<String, Object> operation = StudyIO.map(raw, "operation");
                    operations.put(String.valueOf(operation.get("id")), operation);
                }
                plans.put(run, operations);
            } else if (Set.of("operation_completed", "operation_reused", "operation_failed",
                    "operation_waiting", "operation_skipped").contains(type)) {
                String run = String.valueOf(payload.get("run_id"));
                String operation = String.valueOf(payload.get("operation_id"));
                String id = run + "/" + operation;
                Path directory = resultDirectory(root, id);
                Path manifest = safePath(root, portable(root, directory.resolve("manifest.yaml")));
                results.put(id, new Result(id, directory, StudyIO.loadArtifact(manifest), StudyIO.hash(manifest), order,
                    plans.getOrDefault(run, Map.of()).getOrDefault(operation, Map.of())));
            } else if (type.equals("result_superseded")) {
                Map<String, Object> declaration = new LinkedHashMap<>(payload);
                declaration.put("event_id", event.get("event_id"));
                supersessions.put(String.valueOf(payload.get("result_id")), declaration);
            } else if (type.equals("result_pinned") || type.equals("result_unpinned")) {
                pins.put(String.valueOf(payload.get("result_id")), type.equals("result_pinned"));
            }
        }
        Map<String, Result> newestIdentical = new LinkedHashMap<>();
        for (Result result : results.values())
            if ("complete".equals(result.manifest.get("state"))
                    && result.manifest.get("cache_key") instanceof String key && !key.isBlank())
                newestIdentical.put(identity(result), result);
        return new Catalog(root, events, results, supersessions, pins, removals(events), newestIdentical);
    }

    private static String identity(Result result) {
        Map<String, Object> identity = new LinkedHashMap<>();
        for (String key : List.of("operation_id", "operation_type", "hypothesis", "cache_key", "output_sha256"))
            identity.put(key, result.manifest.get(key));
        return StudyIO.hash(identity);
    }

    /** Verify replacement evidence at the time of each decision, including after relocation. */
    static void verifyHistory(Path root, List<Map<String, Object>> events) throws IOException {
        if (events.stream().noneMatch(event -> Set.of("cleanup_started", "result_superseded", "result_pinned", "result_unpinned")
                .contains(event.get("event_type")))) return;
        Catalog catalog = catalog(root.toRealPath());
        Map<String, Map<String, Object>> preceding = new LinkedHashMap<>();
        Map<String, Boolean> pins = new LinkedHashMap<>();
        String currentRun = "", previousHash = null;
        int order = 0;
        for (Map<String, Object> event : events) {
            order++;
            String type = String.valueOf(event.get("event_type"));
            Map<String, Object> payload = StudyIO.map(event.get("payload"), "payload");
            if (payload.containsKey("run_id")) currentRun = String.valueOf(payload.get("run_id"));
            if (Set.of("cleanup_started", "result_superseded", "result_pinned", "result_unpinned").contains(type)) {
                Map<String, Object> actor = StudyIO.map(event.get("actor"), "actor");
                if (!"human".equals(actor.get("type")) || "unspecified".equals(actor.get("id")))
                    throw new IOException("Cleanup decision lacks a named human actor");
                if (type.equals("cleanup_started")) {
                    Map<String, Object> plan = StudyIO.map(payload.get("plan"), "plan");
                    if (!Objects.equals(previousHash, plan.get("ledger_head")))
                        throw new IOException("Cleanup intent was based on stale project state");
                    Set<String> selected = new HashSet<>();
                    for (Object raw : StudyIO.list(plan.get("results"), "results"))
                        selected.add(String.valueOf(StudyIO.map(raw, "result").get("result_id")));
                    for (Object raw : StudyIO.list(plan.get("results"), "results")) {
                        Map<String, Object> entry = StudyIO.map(raw, "result");
                        Result old = requireResult(catalog, String.valueOf(entry.get("result_id")));
                        if (old.id.startsWith(currentRun + "/") || pins.getOrDefault(old.id,
                                String.valueOf(old.manifest.get("operation_type")).startsWith("manuscript")))
                            throw new IOException("Cleanup intent targets protected evidence: " + old.id);
                        if (!old.hash.equals(entry.get("manifest_sha256"))) throw new IOException("Cleanup source identity mismatch");
                        Result source = old;
                        List<Object> evidence = StudyIO.list(entry.get("replacement_evidence"), "replacement evidence");
                        if (evidence.isEmpty()) throw new IOException("Cleanup has no replacement evidence");
                        for (Object proofValue : evidence) {
                            Map<String, Object> proof = StudyIO.map(proofValue, "replacement proof");
                            Result newer = requireResult(catalog, String.valueOf(proof.get("replacement_result_id")));
                            requireNewer(source, newer);
                            if (newer.order >= order || !source.id.equals(proof.get("source_result_id"))
                                    || !source.hash.equals(proof.get("source_manifest_sha256"))
                                    || !newer.hash.equals(proof.get("replacement_manifest_sha256")))
                                throw new IOException("Cleanup replacement proof has invalid identities or chronology");
                            if ("identical_cache_and_outputs".equals(proof.get("basis"))) {
                                if (!"complete".equals(source.manifest.get("state"))
                                        || !(source.manifest.get("cache_key") instanceof String key) || key.isBlank()
                                        || !identity(source).equals(identity(newer)))
                                    throw new IOException("Cleanup does not have an identical verified replacement");
                            } else if ("scientist_supersession".equals(proof.get("basis"))) {
                                Map<String, Object> declaration = preceding.get(String.valueOf(proof.get("event_id")));
                                if (declaration == null || !"result_superseded".equals(declaration.get("event_type")))
                                    throw new IOException("Cleanup supersession decision is absent");
                                Map<String, Object> decision = StudyIO.map(declaration.get("payload"), "supersession");
                                if (!source.id.equals(decision.get("result_id")) || !newer.id.equals(decision.get("replacement_id"))
                                        || !Objects.equals(proof.get("reason"), decision.get("reason")))
                                    throw new IOException("Cleanup supersession evidence differs from the scientist's decision");
                            } else throw new IOException("Unknown cleanup replacement basis");
                            source = newer;
                        }
                        if (!source.id.equals(entry.get("replacement_id")) || !source.hash.equals(entry.get("replacement_manifest_sha256"))
                                || selected.contains(source.id)) throw new IOException("Cleanup must retain its verified replacement");
                    }
                } else {
                    Result old = requireResult(catalog, String.valueOf(payload.get("result_id")));
                    if (old.order >= order || !old.hash.equals(payload.get("manifest_sha256"))
                            || !(payload.get("reason") instanceof String reason) || reason.isBlank())
                        throw new IOException("Invalid recorded retention decision");
                    if (type.equals("result_superseded")) {
                        Result newer = requireResult(catalog, String.valueOf(payload.get("replacement_id")));
                        requireNewer(old, newer);
                        if (newer.order >= order || !newer.hash.equals(payload.get("replacement_manifest_sha256")))
                            throw new IOException("Invalid recorded replacement identity");
                    } else pins.put(old.id, type.equals("result_pinned"));
                }
            }
            preceding.put(String.valueOf(event.get("event_id")), event);
            previousHash = String.valueOf(event.get("event_hash"));
        }
    }

    static Map<String, Map<String, Object>> decisions(List<Map<String, Object>> events) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> event : events) {
            String type = String.valueOf(event.get("event_type"));
            if (!Set.of("result_superseded", "result_pinned", "result_unpinned").contains(type)) continue;
            Map<String, Object> payload = StudyIO.map(event.get("payload"), "retention");
            Map<String, Object> decision = result.computeIfAbsent(String.valueOf(payload.get("result_id")), ignored -> new LinkedHashMap<>());
            if (type.equals("result_superseded")) {
                decision.put("superseded_by", payload.get("replacement_id"));
                decision.put("supersession_reason", payload.get("reason"));
            } else decision.put("pinned", type.equals("result_pinned"));
        }
        return result;
    }

    /** Ledger-derived deletion authorizations; original operation manifests never change. */
    static Map<String, Map<String, Object>> removals(List<Map<String, Object>> events) throws IOException {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Map<String, List<String>> cleanups = new LinkedHashMap<>();
        Map<String, Map<String, Object>> plans = new LinkedHashMap<>();
        Set<String> completed = new HashSet<>();
        for (Map<String, Object> event : events) {
            String type = String.valueOf(event.get("event_type"));
            if (!Set.of("cleanup_started", "cleanup_completed", "cleanup_interrupted").contains(type)) continue;
            Map<String, Object> payload = StudyIO.map(event.get("payload"), "cleanup event");
            String id = String.valueOf(payload.get("cleanup_id"));
            if (type.equals("cleanup_started")) {
                Map<String, Object> plan = StudyIO.map(payload.get("plan"), "cleanup plan");
                verifyPlan(plan);
                if (!id.equals(plan.get("cleanup_id")) || cleanups.containsKey(id))
                    throw new IOException("Invalid or duplicate cleanup intent: " + id);
                List<String> ids = new ArrayList<>();
                for (Object raw : StudyIO.list(plan.get("results"), "results")) {
                    Map<String, Object> entry = StudyIO.map(raw, "result");
                    String resultId = String.valueOf(entry.get("result_id"));
                    if (result.containsKey(resultId)) throw new IOException("Duplicate result cleanup: " + resultId);
                    Map<String, Object> removal = new LinkedHashMap<>(entry);
                    removal.put("cleanup_id", id);
                    removal.put("availability", "cleanup_pending");
                    removal.put("started_at", event.get("timestamp"));
                    removal.put("actor", event.get("actor"));
                    removal.put("event_id", event.get("event_id"));
                    result.put(resultId, removal);
                    ids.add(resultId);
                }
                cleanups.put(id, ids);
                plans.put(id, plan);
            } else {
                if (!cleanups.containsKey(id) || completed.contains(id))
                    throw new IOException("Cleanup event has no unfinished intent: " + id);
                if (type.equals("cleanup_completed")) {
                    Map<String, Object> plan = plans.get(id);
                    long expectedFiles = 0;
                    for (Object raw : StudyIO.list(plan.get("results"), "results"))
                        expectedFiles += StudyIO.list(StudyIO.map(raw, "result").get("files"), "files").size();
                    if (!Objects.equals(plan.get("plan_sha256"), payload.get("plan_sha256"))
                            || !(payload.get("files_deleted") instanceof Number count) || count.longValue() != expectedFiles
                            || !(payload.get("bytes_deleted") instanceof Number bytes)
                            || bytes.longValue() != ((Number) plan.get("reclaimable_bytes")).longValue())
                        throw new IOException("Cleanup completion receipt differs from its reviewed inventory");
                    completed.add(id);
                    for (String resultId : cleanups.get(id)) {
                        Map<String, Object> removal = result.get(resultId);
                        removal.put("availability", "deleted");
                        removal.put("completed_at", event.get("timestamp"));
                        removal.put("completed_by", event.get("actor"));
                    }
                }
            }
        }
        return result;
    }

    static Set<String> unavailable(Path root) throws IOException {
        return unavailableResults(root);
    }

    /** Results whose payloads must not be reused, including interrupted cleanups. */
    public static Set<String> unavailableResults(Path project) throws IOException {
        return Set.copyOf(removals(StudyLedger.read(project.toAbsolutePath().normalize())).keySet());
    }

    /** Verify the exact exemption and all remaining bytes after interrupted deletion. */
    static void verifyRemoval(Path root, Path manifest, Map<String, Object> document,
            Map<String, Object> removal) throws IOException {
        Path relativeManifest = root.relativize(manifest);
        root = root.toRealPath();
        manifest = root.resolve(relativeManifest);
        if (!StudyIO.hash(manifest).equals(removal.get("manifest_sha256")))
            throw new IOException("Cleanup manifest identity mismatch: " + manifest);
        Map<String, Object> expected = new LinkedHashMap<>();
        for (Map.Entry<String, Object> output : StudyIO.map(document.get("output_sha256"), "outputs").entrySet())
            expected.put(portable(root, manifest.getParent().resolve(output.getKey())), output.getValue());
        Map<String, Object> authorized = new LinkedHashMap<>();
        for (Object raw : StudyIO.list(removal.get("files"), "cleanup.files")) {
            Map<String, Object> file = StudyIO.map(raw, "file");
            String relative = String.valueOf(file.get("path"));
            if (authorized.put(relative, file.get("sha256")) != null)
                throw new IOException("Duplicate cleanup file: " + relative);
            Path path = safePath(root, relative);
            if (!path.startsWith(manifest.getParent()) || path.equals(manifest))
                throw new IOException("Unsafe cleanup output: " + relative);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                if ("deleted".equals(removal.get("availability")))
                    throw new IOException("Deleted artifact unexpectedly reappeared: " + relative);
                verifyFile(path, file);
            }
        }
        if (!expected.equals(authorized)) throw new IOException("Cleanup does not match the original output manifest");
    }

    private static void verifyInventory(Path root, Map<String, Object> entry, boolean allowMissing) throws IOException {
        Path directory = resultDirectory(root, String.valueOf(entry.get("result_id")));
        Path manifest = safePath(root, portable(root, directory.resolve("manifest.yaml")));
        if (!StudyIO.hash(manifest).equals(entry.get("manifest_sha256"))) throw new IOException("Cleanup manifest changed");
        Set<Path> expected = new HashSet<>();
        expected.add(manifest);
        Map<String, Object> hashes = StudyIO.map(StudyIO.loadArtifact(manifest).get("output_sha256"), "outputs");
        Map<String, Object> reviewed = new LinkedHashMap<>();
        for (Object raw : StudyIO.list(entry.get("files"), "files")) {
            Map<String, Object> file = StudyIO.map(raw, "file");
            Path path = safePath(root, String.valueOf(file.get("path")));
            if (!path.startsWith(directory) || path.equals(manifest) || !expected.add(path))
                throw new IOException("Unsafe or duplicate cleanup file: " + path);
            reviewed.put(portable(directory, path), file.get("sha256"));
            if (!allowMissing || Files.exists(path, LinkOption.NOFOLLOW_LINKS)) verifyFile(path, file);
        }
        if (!hashes.equals(reviewed)) throw new IOException("Cleanup inventory differs from the output manifest");
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.toList()) {
                safePath(root, portable(root, path));
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !expected.contains(path))
                    throw new IOException("Unrecorded file in cleanup result; review it first: " + path);
            }
        }
    }

    private static void verifyFile(Path path, Map<String, Object> file) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !(file.get("bytes") instanceof Number bytes) || bytes.longValue() < 0
                || bytes.longValue() != Files.size(path) || !StudyIO.hash(path).equals(file.get("sha256")))
            throw new IOException("Cleanup file changed or is absent: " + path);
    }

    private static void verifyPlan(Map<String, Object> document) throws IOException {
        // The ledger reader limits each JSON event to 16 million code points.
        // Leave room for the event envelope instead of writing an unreadable intent.
        if (StudyIO.canonicalJson(document).length() > 12_000_000)
            throw new IOException("Cleanup inventory is too large for one audit event; narrow the --result selection");
        Map<String, Object> unhashed = StudyIO.copyMap(document);
        Object hash = unhashed.remove("plan_sha256");
        Object id = unhashed.remove("cleanup_id");
        String calculated = StudyIO.hash(unhashed);
        if (!"study_cleanup".equals(document.get("kind")) || !Integer.valueOf(1).equals(document.get("schema_version"))
                || !calculated.equals(hash) || !("cleanup-" + calculated.substring(0, 16)).equals(id))
            throw new IOException("Invalid or modified cleanup preview");
    }

    private static Result requireResult(Catalog catalog, String id) throws IOException {
        Result result = catalog.results.get(id);
        if (result == null) throw new IOException("Unknown recorded result ID: " + id);
        return result;
    }

    private static boolean sameTask(Result old, Result newer) {
        return List.of("operation_id", "operation_type", "hypothesis").stream()
            .allMatch(key -> Objects.equals(old.manifest.get(key), newer.manifest.get(key)));
    }

    private static void requireNewer(Result old, Result newer) throws IOException {
        if (newer.order <= old.order || !sameTask(old, newer) || !"complete".equals(newer.manifest.get("state")))
            throw new IOException("Replacement must be a newer completed result for the same operation and hypothesis");
    }

    private static Map<String, Object> outputs(Result result) {
        return StudyIO.map(result.manifest.get("output_sha256"), "output_sha256");
    }

    private static Path resultDirectory(Path root, String id) throws IOException {
        String[] parts = id.split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
            throw new IOException("Expected a result ID RUN/OPERATION");
        Path path = safePath(root, "runs/" + parts[0] + "/operations/" + parts[1]);
        if (root.relativize(path).getNameCount() != 4) throw new IOException("Unsafe result ID: " + id);
        return path;
    }

    /** Resolve only canonical project-relative paths; refuse links and junctions. */
    private static Path safePath(Path root, String relative) throws IOException {
        final Path path;
        try {
            Path raw = Path.of(relative);
            if (raw.isAbsolute()) throw new IOException("Absolute cleanup path is forbidden: " + relative);
            path = root.resolve(raw).normalize();
        } catch (InvalidPathException invalid) { throw new IOException("Invalid cleanup path", invalid); }
        if (!path.startsWith(root) || path.equals(root) || !portable(root, path).equals(relative))
            throw new IOException("Noncanonical or escaping cleanup path: " + relative);
        Path ancestor = root;
        for (Path component : root.relativize(path)) {
            ancestor = ancestor.resolve(component);
            if (Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(ancestor) || !ancestor.toRealPath().equals(ancestor)))
                throw new IOException("Cleanup refuses linked or redirected paths: " + ancestor);
        }
        return path;
    }

    private static String portable(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static void requireHuman(StudyProject.Actor actor, String reason) {
        if (!"human".equals(actor.type()) || "unspecified".equals(actor.id()))
            throw new IllegalArgumentException("Cleanup decisions require a named human --actor-id");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A nonblank --reason is required");
    }
}
