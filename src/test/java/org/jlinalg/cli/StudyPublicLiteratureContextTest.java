/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyProject;
import org.jlinalg.study.StudyValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyPublicLiteratureContextTest {
    @TempDir Path root;
    static final String DOI = "10.5555/public-context-fixture";
    static final String EXCERPT = "A controlled synthetic perturbation changed a molecular readout; its negative control did not.";
    static final String OMITTED = "UNSELECTED_FULL_TEXT_SENTINEL";

    static Map<String,Object> fixture(Path root) throws Exception {
        Files.createDirectories(root); Path policy = Files.createDirectories(root.resolve("policy"));
        Files.writeString(policy.resolve("resolved-study.yaml"), "resources: {downloads: automatic}\n");
        Path request = root.resolve("references.yaml"); write(request, Map.of("schema_version", 1, "references", List.of(Map.of("id", DOI, "full_text", true, "rationale", "Synthetic protocol source; no scientific approval"))));
        String metadata = StudyAssistance.json(Map.of("resultList", Map.of("result", List.of(Map.of("source", "MED", "id", "123", "pmcid", "PMC123", "doi", DOI, "title", "Synthetic public context")))));
        String xml = "<article><front><article-meta><article-id pub-id-type=\"doi\">" + DOI + "</article-id><article-id pub-id-type=\"pmid\">123</article-id><title-group><article-title>Synthetic public context</article-title></title-group><permissions><license>CC BY synthetic fixture</license></permissions></article-meta></front><body><sec><title>Results</title><p>" + EXCERPT + "</p><p>" + OMITTED + " \uD83D\uDE00</p></sec></body></article>";
        Path bundle = StudyCitationSources.fetch(policy, request, root.resolve("native-reference"), new StudyProject.Actor("external", "synthetic-public-context", null), false,
            new StudyPublicEvidence(uri -> new StudyPublicEvidence.Reply(200, uri.getPath().endsWith("fullTextXML") ? xml : metadata)));
        Path manifest = bundle.resolve("metadata/manifest.yaml"); var source = map(list(StudyYaml.loadArtifact(bundle.resolve("artifacts/full-text-sources.yaml")).get("sources")).get(0));
        String body = Files.readString(bundle.resolve(text(source.get("text_file")))); int start = body.indexOf(EXCERPT);
        var entry = new LinkedHashMap<String,Object>(); entry.put("id", "paper"); entry.put("hypotheses", List.of("H1")); entry.put("feature_ids", List.of("H1-f0")); entry.put("purposes", List.of("impact", "manuscript"));
        entry.put("reference", DOI); entry.put("reference_bundle", Map.of("manifest", manifest.toString(), "sha256", FollowupSupport.hash(manifest)));
        entry.put("source_id", source.get("id")); entry.put("source_sha256", source.get("sha256")); entry.put("passages", List.of(Map.of("id", "controlled", "start", start, "end", start + EXCERPT.length())));
        Path context = root.resolve("context.yaml"); write(context, Map.of("schema_version", 1, "sources", List.of(entry)));
        return Map.of("file", context.toString(), "sha256", FollowupSupport.hash(context));
    }
    private Map<String,Object> study(Map<String,Object> pin) { return Map.of("assistance", Map.of("public_literature_context", List.of(pin)), "hypotheses", List.of(Map.of("id", "H1"), Map.of("id", "H2"))); }
    private Context context(Map<String,Object> study) { return new Context(root, root.resolve("output"), Map.of("type", "finding_inventory"), study, Map.of(), Map.of()); }
    private Map<String,Object> read(Map<String,Object> pin) { return StudyPublicLiteratureContext.read(context(study(pin))); }
    private Map<String,Object> edit(Map<String,Object> pin, java.util.function.Consumer<Map<String,Object>> mutation) throws Exception {
        Path path = Path.of(text(pin.get("file"))); var spec = StudyYaml.loadArtifact(path); var entry = new LinkedHashMap<>(map(list(spec.get("sources")).get(0))); mutation.accept(entry); spec.put("sources", List.of(entry)); write(path, spec);
        return Map.of("file", path.toString(), "sha256", FollowupSupport.hash(path));
    }
    @Test void absentConfigurationDoesNotReadOrExposeAnySource() {
        assertTrue(StudyPublicLiteratureContext.read(context(Map.of())).isEmpty());
        assertTrue(StudyPublicLiteratureContext.inputHashes(root, Map.of(), Map.of("type", "finding_inventory")).isEmpty());
    }
    @Test void nativePassagesRetainIdentityAndOffsetsWithoutUnselectedTextOrPaths() throws Exception {
        var result = read(fixture(root)); assertEquals("complete", result.get("status")); String encoded = StudyAssistance.json(result);
        assertTrue(encoded.contains(EXCERPT)); assertTrue(encoded.contains(DOI)); assertFalse(encoded.contains(OMITTED)); assertFalse(encoded.contains(root.toString()));
        var paper = map(list(result.get("papers")).get(0)); assertEquals("selected_passages_from_full_text", paper.get("source_scope")); assertEquals(false, result.get("scientific_support_established"));
        var catalog = new LinkedHashMap<String,Object>(); StudyPublicLiteratureContext.addSources(catalog, result); assertTrue(catalog.containsKey("public-literature.paper")); assertTrue(StudyAssistance.json(catalog).contains("paper.controlled"));
    }
    @Test void exactFeaturesAndHypothesesControlImpactContext() throws Exception {
        var result = read(fixture(root));
        for (var finding : List.of(Map.<String,Object>of("hypothesis", "H2", "feature_id", "H1-f0"), Map.<String,Object>of("hypothesis", "H1", "feature_id", "other")))
            assertTrue(list(StudyPublicLiteratureContext.forFindings(result, List.of(finding), "impact", false).get("papers")).isEmpty());
        assertEquals(1, list(StudyPublicLiteratureContext.forFindings(result, List.of(Map.of("hypothesis", "H1", "feature_id", "H1-f0")), "impact", false).get("papers")).size());
    }
    @Test void manuscriptBackgroundCanCoverAnUnselectedHypothesisWithoutCrossingPurpose() throws Exception {
        var pin = edit(fixture(root), entry -> { entry.put("hypotheses", List.of("H2")); entry.remove("feature_ids"); entry.put("purposes", List.of("manuscript")); }); var result = read(pin);
        var findings = List.of(Map.<String,Object>of("hypothesis", "H1", "feature_id", "H1-f0"));
        assertTrue(list(StudyPublicLiteratureContext.forFindings(result, findings, "impact", false).get("papers")).isEmpty());
        assertEquals(1, list(StudyPublicLiteratureContext.forFindings(result, findings, "manuscript", true).get("papers")).size());
    }
    @Test void impactOnlyEvidenceIsNotAmanuscriptDisclosure() throws Exception {
        var result = read(edit(fixture(root), e -> e.put("purposes", List.of("impact"))));
        assertTrue(list(StudyPublicLiteratureContext.forFindings(result, List.of(Map.of("hypothesis", "H1", "feature_id", "H1-f0")), "manuscript", true).get("papers")).isEmpty());
    }
    @Test void changedPinnedContextWithholdsAllPassagesAndChangesCacheInputs() throws Exception {
        var pin = fixture(root); var before = StudyPublicLiteratureContext.inputHashes(root, study(pin), Map.of("type", "finding_inventory")); Files.writeString(Path.of(text(pin.get("file"))), "schema_version: 1\nsources: []\n");
        assertEquals("needs_input", read(pin).get("status")); assertTrue(list(read(pin).get("papers")).isEmpty()); assertNotEquals(before, StudyPublicLiteratureContext.inputHashes(root, study(pin), Map.of("type", "finding_inventory")));
    }
    @Test void changedNativeTextWithUnchangedManifestsCannotReuseSuccessfulContext() throws Exception {
        var pin = fixture(root); var before = StudyPublicLiteratureContext.inputHashes(root, study(pin), Map.of("type", "impact_assessment"));
        Files.writeString(root.resolve("native-reference/artifacts/full-text/reference-text-0001.txt"), "Controlled tamper fixture\n");
        assertEquals("needs_input", read(pin).get("status")); assertNotEquals(before, StudyPublicLiteratureContext.inputHashes(root, study(pin), Map.of("type", "impact_assessment")));
    }
    @Test void changedNativeBundlePinCannotBeAccepted() throws Exception {
        var pin = edit(fixture(root), entry -> entry.put("reference_bundle", Map.of("manifest", root.resolve("native-reference/metadata/manifest.yaml").toString(), "sha256", "0".repeat(64))));
        assertEquals("needs_input", read(pin).get("status"));
    }
    @Test void rawTextAndArbitraryFileFieldsCannotSubstituteForNativeEvidence() throws Exception {
        var pin = edit(fixture(root), entry -> entry.put("text", "PRIVATE_PATIENT_SECRET"));
        var result = read(pin); assertEquals("needs_input", result.get("status")); assertFalse(StudyAssistance.json(result).contains("PRIVATE_PATIENT_SECRET"));
    }
    @Test void wrongReferenceCannotBorrowAnotherPapersText() throws Exception {
        assertEquals("needs_input", read(edit(fixture(root), entry -> entry.put("reference", "10.5555/other"))).get("status"));
    }
    @Test void unknownHypothesisAndUnsupportedPurposeAreNotInferred() throws Exception {
        var pin = fixture(root); assertEquals("needs_input", read(edit(pin, e -> e.put("hypotheses", List.of("absent")))).get("status"));
        pin = edit(pin, e -> { e.put("hypotheses", List.of("H1")); e.put("purposes", List.of("all")); }); assertEquals("needs_input", read(pin).get("status"));
    }
    @Test void duplicateOrOutOfBoundsRangesDoNotSilentlyTruncate() throws Exception {
        var pin = fixture(root); pin = edit(pin, entry -> { var one = list(entry.get("passages")).get(0); entry.put("passages", List.of(one, one)); }); assertEquals("needs_input", read(pin).get("status"));
        pin = edit(pin, entry -> entry.put("passages", List.of(Map.of("id", "beyond", "start", 0, "end", 999999)))); assertEquals("needs_input", read(pin).get("status"));
    }
    @Test void offsetsCannotSplitASupplementaryUnicodeCharacter() throws Exception {
        var pin = fixture(root); String body = Files.readString(root.resolve("native-reference/artifacts/full-text/reference-text-0001.txt")); int at = body.indexOf("\uD83D\uDE00");
        assertEquals("needs_input", read(edit(pin, entry -> entry.put("passages", List.of(Map.of("id", "half", "start", at + 1, "end", at + 2))))).get("status"));
    }
    @Test void malformedYamlIsARecordedNeedAndNotAtransportAttempt() throws Exception {
        var pin = fixture(root); Path path = Path.of(text(pin.get("file"))); Files.writeString(path, "{[invalid\n"); pin = Map.of("file", path.toString(), "sha256", FollowupSupport.hash(path));
        assertEquals("needs_input", read(pin).get("status")); assertFalse(StudyPublicLiteratureContext.inputHashes(root, study(pin), Map.of("type", "manuscript_draft")).isEmpty());
    }
    @Test void unrelatedAssociationStagesDoNotReadLiteratureInputs() throws Exception {
        assertTrue(StudyPublicLiteratureContext.inputHashes(root, study(fixture(root)), Map.of("type", "association")).isEmpty());
    }
    @Test void duplicateSourceCatalogIDsAreRejected() throws Exception {
        var result = read(fixture(root)); var catalog = new LinkedHashMap<String,Object>(); catalog.put("public-literature.paper", Map.of());
        assertThrows(IllegalArgumentException.class, () -> StudyPublicLiteratureContext.addSources(catalog, result));
    }
    @Test void configuredPinShapeIsValidatedBeforeExecution() throws Exception {
        Path path = root.resolve("study.yaml");
        var study = new LinkedHashMap<String,Object>(); study.put("schema_version", 1); study.put("project", Map.of("id", "pin-shape", "title", "Synthetic", "description", "Synthetic configuration validation")); study.put("datasets", List.of()); study.put("hypotheses", List.of());
        study.put("assistance", Map.of("public_literature_context", List.of(Map.of("file", "missing.yaml", "sha256", "0".repeat(64))))); write(path, study);
        assertTrue(StudyValidator.validate(path, false).valid(), StudyValidator.validate(path, false).report());
        study.put("assistance", Map.of("public_literature_context", List.of(Map.of("file", "missing.yaml", "sha256", "not-a-hash")))); write(path, study); assertFalse(StudyValidator.validate(path, false).valid());
    }
}
