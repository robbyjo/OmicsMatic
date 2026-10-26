/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.Result;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyGeneticPairCacheTest {
    @TempDir Path root;
    private static final String HEADER = "variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n";
    private void initialize() throws Exception {
        Files.writeString(root.resolve("exposure.tsv"), HEADER + "rs1\t1\t100\tA\tG\t.1\t.01\t1e-12\nrs2\t1\t200\tA\tG\t.2\t.01\t1e-12\nrs3\t1\t300\tA\tG\t.3\t.01\t1e-12\n");
        outcome("outcome1.tsv", .5); outcome("outcome2.tsv", .2);
        Files.writeString(root.resolve("ld.tsv"), "variant_id\trs1\trs2\trs3\nrs1\t1\t0\t0\nrs2\t0\t1\t0\nrs3\t0\t0\t1\n");
        Files.writeString(root.resolve("alleles.tsv"), "variant_id\teffect_allele\tother_allele\nrs1\tA\tG\nrs2\tA\tG\nrs3\tA\tG\n");
        Path primary = Files.createDirectories(root.resolve("primary/standard-results"));
        Files.writeString(primary.resolve("d.tsv"), "feature_id\tbeta\tse\tp_value\nprobe1\t.5\t.1\t1e-10\nprobe2\t.4\t.1\t2e-10\n");
        Files.writeString(primary.getParent().resolve("feature-annotation.tsv"), "feature_id\tgene_symbol\tgene_id\tchromosome\tstart\tend\tgenome_build\tmapping_status\nprobe1\tGENE1\tENSG1\t1\t100\t300\tGRCh38\tlocated\nprobe2\tGENE2\tENSG2\t1\t100\t300\tGRCh38\tlocated\n");
        StudyTestSources.seal(primary.getParent(), "validate_harmonize", "primary", "H");
    }
    private void outcome(String name, double ratio) throws Exception {
        var body = new StringBuilder(HEADER);
        for (int i=1;i<=3;i++) body.append("rs").append(i).append("\t1\t").append(i*100).append("\tA\tG\t").append(i*.1*ratio).append("\t.01\t1e-8\n");
        Files.writeString(root.resolve(name), body);
    }
    private Map<String,Object> source(String role, String feature) {
        var s = new LinkedHashMap<String,Object>();
        s.put("dataset_id", "d"); s.put("file_role", role); s.put("source_id", "synthetic-"+role);
        s.put("citation", "Synthetic fixture only"); s.put("genome_build", "GRCh38"); s.put("ancestry", "European");
        s.put("sample_size", 10000); s.put("complete_region", true); s.put("feature_id", feature);
        s.put("assay", "irQTL"); s.put("tissue", "blood"); s.put("trait", role.startsWith("outcome") ? "BMI" : "isoform ratio");
        return s;
    }
    private List<Object> pairs() {
        var result = new ArrayList<Object>();
        for (int i=1;i<=2;i++) {
            var pair = new LinkedHashMap<String,Object>(); String feature = "probe"+i;
            pair.put("id", "pair"+i); pair.put("feature_id", feature); pair.put("approved", true);
            pair.put("rationale", "Synthetic independently registered sources"); pair.put("instrument_validity_rationale", "Simulation only");
            pair.put("sample_overlap", "none"); pair.put("sample_overlap_basis", "Disjoint simulated samples");
            pair.put("region", Map.of("chromosome", "1", "start", 1, "end", 500));
            pair.put("exposure", source("exposure", feature)); pair.put("outcome", source("outcome"+i, feature));
            var ld = source("ld", feature); ld.put("alleles_file_role", "alleles"); pair.put("ld", ld); result.add(pair);
        }
        return result;
    }
    private Context context(String type, String run, boolean resume, List<Object> pairs, Map<String,Object> extra) throws Exception {
        var bio = new LinkedHashMap<String,Object>(); bio.put("genetic_pairs", pairs);
        var analysis = new LinkedHashMap<String,Object>(); analysis.put("bioinformatics", bio); analysis.putAll(extra);
        var study = Map.<String,Object>of("resources", Map.of("directory", "resources", "network", "offline", "downloads", "deny"),
            "datasets", List.of(Map.of("id", "d", "type", "transcriptomics", "files", Map.of("exposure", "exposure.tsv", "outcome1", "outcome1.tsv", "outcome2", "outcome2.tsv", "ld", "ld.tsv", "alleles", "alleles.tsv"))),
            "hypotheses", List.of(Map.of("id", "H", "outcome", "BMI", "analysis", analysis)));
        Path output = Files.createDirectories(root.resolve("runs/"+run+"/operations/genetic"));
        return new Context(root, output, Map.of("id", "genetic", "type", type, "hypothesis", "H", "datasets", List.of("d")), study, Map.of(), Map.of("primary", root.resolve("primary")), resume);
    }
    private void seal(Context c, Result r) throws Exception {
        var hashes = new LinkedHashMap<String,Object>();
        try (var paths=Files.walk(c.outputDirectory())) {
            for (Path p:paths.filter(Files::isRegularFile).toList()) hashes.put(c.outputDirectory().relativize(p).toString().replace('\\','/'), FollowupSupport.hash(p));
        }
        write(c.outputDirectory().resolve("manifest.yaml"), Map.of("operation_id", "genetic", "operation_type", c.operation().get("type"), "hypothesis", "H", "state", r.state().value(), "details", r.details(), "output_sha256", hashes));
    }
    private static Map<String,Object> reuse(Result r) { return map(r.details().get("native_pair_reuse")); }
    private static List<Map<String,Object>> records(Result r) { return list(r.details().get("pairs")).stream().map(StudyPublicEvidence::map).filter(p->p.containsKey("pair_id")).toList(); }
    private void assertCounts(Result r, int reused, int attempted) { assertEquals(reused, reuse(r).get("reused_pair_count")); assertEquals(attempted, reuse(r).get("new_pair_attempt_count")); }

    @Test void completedPairInWaitingOperationIsReusedWhileOnlyMissingSourceRuns() throws Exception {
        initialize(); Files.delete(root.resolve("outcome2.tsv"));
        var first=context("mendelian_randomization", "first", false, pairs(), Map.of()); var a=new StudyGeneticEvidence().execute(first);
        assertEquals(State.WAITING_FOR_INPUT,a.state()); seal(first,a);
        String old=FollowupSupport.hash(first.outputDirectory().resolve("manifest.yaml"));
        assertTrue(Files.isRegularFile(first.outputDirectory().resolve("pair1/"+StudyGeneticPairCache.RECEIPT)));
        assertFalse(Files.exists(first.outputDirectory().resolve("pair2/"+StudyGeneticPairCache.RECEIPT)));
        outcome("outcome2.tsv",.2);
        var next=context("mendelian_randomization", "next", true, pairs(), Map.of()); var b=new StudyGeneticEvidence().execute(next);
        assertEquals(State.COMPLETE,b.state(),b.details().toString()); assertCounts(b,1,1);
        assertEquals("native_prior_run",records(b).get(0).get("execution_origin"));
        assertEquals(0,map(b.details().get("mr_testing")).get("unresolved_tests"));
        assertEquals(old,FollowupSupport.hash(first.outputDirectory().resolve("manifest.yaml")));
        assertEquals(FollowupSupport.hash(first.outputDirectory().resolve("pair1/mr-ivw-fixed.tsv")),FollowupSupport.hash(next.outputDirectory().resolve("pair1/mr-ivw-fixed.tsv")));
    }
    @Test void allThreeNativeOperationsReuseCompletePairsAndKeepEveryOutputByte() throws Exception {
        initialize();
        for(String type:List.of("mendelian_randomization","colocalization","fine_mapping")) {
            var first=context(type,type+"-first",false,pairs(),Map.of()); var a=new StudyGeneticEvidence().execute(first); assertEquals(State.COMPLETE,a.state(),a.details().toString()); seal(first,a);
            var next=context(type,type+"-next",true,pairs(),Map.of()); var b=new StudyGeneticEvidence().execute(next); assertEquals(State.COMPLETE,b.state(),b.details().toString()); assertCounts(b,2,0);
            var receipt=StudyYaml.loadArtifact(first.outputDirectory().resolve("pair1/"+StudyGeneticPairCache.RECEIPT));
            for(var file:map(receipt.get("output_sha256")).entrySet()) assertEquals(file.getValue(),FollowupSupport.hash(next.outputDirectory().resolve("pair1/"+file.getKey())));
        }
    }
    @Test void freshRerunDoesNotConsumePriorPairs() throws Exception {
        initialize();var first=context("mendelian_randomization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        var next=context("mendelian_randomization","rerun",false,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);assertCounts(b,0,2);assertTrue(records(b).stream().allMatch(p->Boolean.TRUE.equals(p.get("native_pair_attempted_this_run"))));
        assertFalse(new Context(next.project(),next.outputDirectory(),next.operation(),next.study(),next.plan(),next.dependencyDirectories()).resumeCompletedWork());
    }
    @Test void changedOutcomeBytesInvalidateOnlyTheirPair() throws Exception {
        initialize();var first=context("mendelian_randomization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);outcome("outcome2.tsv",.8);
        var next=context("mendelian_randomization","next",true,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);assertEquals(State.COMPLETE,b.state());assertCounts(b,1,1);assertEquals(.8,Double.parseDouble(text(map(records(b).get(1).get("mr_primary")).get("estimate"))),1e-12);
    }
    @Test void missingCurrentSourceCannotBeHiddenByACompletedPriorPair() throws Exception {
        initialize();var first=context("mendelian_randomization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);Files.delete(root.resolve("outcome2.tsv"));
        var next=context("mendelian_randomization","next",true,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);assertEquals(State.WAITING_FOR_INPUT,b.state());assertCounts(b,1,1);assertEquals("waiting_for_input",records(b).get(1).get("status"));assertFalse(Files.exists(next.outputDirectory().resolve("pair2/mr-ivw-fixed.tsv")));
    }
    @Test void changedOutputInvalidatesDerivedPairAndRecomputesWithoutChangingOldArtifacts() throws Exception {
        initialize();var first=context("mendelian_randomization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        Path corrupted=first.outputDirectory().resolve("pair1/mr-ivw-fixed.tsv");Files.writeString(corrupted,"retained damaged derived table\n");
        var next=context("mendelian_randomization","next",true,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);assertEquals(State.COMPLETE,b.state());assertCounts(b,1,1);assertFalse(list(reuse(b).get("invalidated_derived_pairs")).isEmpty());assertEquals("retained damaged derived table\n",Files.readString(corrupted));assertEquals(.5,Double.parseDouble(text(map(records(b).get(0).get("mr_primary")).get("estimate"))),1e-12);
    }
    @Test void changedReceiptCannotAuthorizeAReusedFit() throws Exception {
        initialize();var first=context("mendelian_randomization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        Files.writeString(first.outputDirectory().resolve("pair1/"+StudyGeneticPairCache.RECEIPT),"key: changed\n");
        var next=context("mendelian_randomization","next",true,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);assertCounts(b,1,1);assertFalse(list(reuse(b).get("invalidated_derived_pairs")).isEmpty());
    }
    @Test void changedApprovalProtocolAndEligibilityDoNotReuseOldInference() throws Exception {
        initialize();var first=context("mendelian_randomization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        for(String change:List.of("approved","instrument_p","sample_overlap")) {
            var changed=pairs();var p=map(changed.get(0));p.put(change,change.equals("approved")?false:change.equals("instrument_p")?1e-15:"unknown");changed.set(0,p);
            var next=context("mendelian_randomization",change,true,changed,Map.of());var b=new StudyGeneticEvidence().execute(next);assertCounts(b,1,1);assertFalse(Boolean.TRUE.equals(records(b).get(0).get("reused_native_result")),change);
        }
        Files.writeString(root.resolve("primary/standard-results/d.tsv"),"feature_id\tbeta\tse\tp_value\nprobe1\t.5\t.1\t.9\nprobe2\t.4\t.1\t2e-10\n");StudyTestSources.seal(root.resolve("primary"),"validate_harmonize","primary","H");
        var next=context("mendelian_randomization","ineligible",true,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);assertEquals("waiting_for_input",records(b).get(0).get("status"));assertFalse(Files.exists(next.outputDirectory().resolve("pair1/mr-ivw-fixed.tsv")));
    }
    @Test void currentMrCorrectionIsRecomputedFromRawFitsRatherThanCopied() throws Exception {
        initialize();var first=context("mendelian_randomization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        var bio=Map.of("genetic_pairs",pairs(),"mr_testing",Map.of("adjustment","BY"));
        var next=context("mendelian_randomization","next",true,pairs(),Map.of("bioinformatics",bio));var b=new StudyGeneticEvidence().execute(next);assertCounts(b,2,0);assertEquals("BY",map(map(b.details().get("mr_testing")).get("policy")).get("adjustment"));
        for(var p:records(b))assertEquals("BY",map(p.get("mr_multiplicity")).get("adjustment"));
        assertFalse(Files.readString(first.outputDirectory().resolve("pair1/"+StudyGeneticPairCache.RECEIPT)).contains("mr_multiplicity"));
        var table=DelimitedData.readResult(next.outputDirectory().resolve("mr-primary-results.tsv"));double q0=Double.parseDouble(table.rows().get(0)[table.column("adjusted_p")]),p0=Double.parseDouble(table.rows().get(0)[table.column("causal_p_value")]);assertEquals(p0*3,q0,1e-90);
    }
    @Test void partialColocalizationCompletedUnestimablePairIsReusableWithItsLimitation() throws Exception {
        initialize();Files.writeString(root.resolve("outcome2.tsv"),HEADER+"rs1\t1\t100\tA\tG\t.05\t.01\t1e-8\n");var pairs=pairs();var p=map(pairs.get(1));p.put("partial_colocalization",true);pairs.set(1,p);
        var first=context("colocalization","first",false,pairs,Map.of());var a=new StudyGeneticEvidence().execute(first);assertEquals(State.COMPLETE,a.state(),a.details().toString());assertEquals("not_estimable",records(a).get(1).get("status"));seal(first,a);
        var next=context("colocalization","next",true,pairs,Map.of());var b=new StudyGeneticEvidence().execute(next);assertEquals(State.COMPLETE,b.state());assertCounts(b,2,0);assertEquals("not_estimable",records(b).get(1).get("status"));assertTrue(text(records(b).get(1).get("required_manuscript_caveat")).contains("not evidence against"));
    }
    @Test void reusedColocalizationHasSourceLinkedDossierContextWithoutNewAssociationTests() throws Exception {
        initialize();var first=context("colocalization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        var next=context("colocalization","next",true,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);seal(next,b);
        var inventory=new Context(root,Files.createDirectories(root.resolve("inventory")),Map.of("type","finding_inventory","hypothesis","H","datasets",List.of("d")),next.study(),Map.of(),Map.of("primary",root.resolve("primary"),"coloc",next.outputDirectory()));
        var result=new StudyFindingInventory().execute(inventory);assertEquals(2L,result.details().get("candidates"));
        for(String line:Files.readAllLines(inventory.outputDirectory().resolve("finding-inventory.jsonl"))) {
            var dossier=map(SimpleJson.parse(line));var reuse=list(dossier.get("observations")).stream().map(StudyPublicEvidence::map).filter(o->"native_genetic_pair_reuse".equals(o.get("kind"))).toList();assertEquals(1,reuse.size());assertEquals(false,reuse.get(0).get("primary"));assertFalse(list(reuse.get(0).get("source_ids")).isEmpty());assertTrue(StudyAssistance.json(dossier.get("caveats")).contains("not independent replication"));
        }
    }
    @Test void unversionedAutomaticReferencesRemainOutsidePairReuse() throws Exception {
        initialize();var pairs=pairs();var p=map(pairs.get(0));p.put("ld",Map.of("database","1000g-phase3","population","EUR"));pairs.set(0,p);
        var c=context("mendelian_randomization","automatic",true,pairs,Map.of());var request=new StudyGeneticPairCache().request(c,p,StudyBioinformatics.mrFeatures(c).get(0));assertEquals("",request.key());assertEquals("automatic_reference_not_pinned_for_pair_reuse",request.reason());
    }
    @Test void nativeFineMappingInventoryReadsPosteriorsWithoutParsingInternalMatrices() throws Exception {
        initialize();var first=context("fine_mapping","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        var next=context("fine_mapping","next",true,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);seal(next,b);
        var inventory=new Context(root,Files.createDirectories(root.resolve("fine-inventory")),Map.of("type","finding_inventory","hypothesis","H","datasets",List.of("d")),next.study(),Map.of(),Map.of("primary",root.resolve("primary"),"fine",next.outputDirectory()));
        var result=new StudyFindingInventory().execute(inventory);assertEquals(2L,result.details().get("candidates"));
        for(String line:Files.readAllLines(inventory.outputDirectory().resolve("finding-inventory.jsonl"))) {
            var dossier=map(SimpleJson.parse(line));var posteriors=list(dossier.get("observations")).stream().map(StudyPublicEvidence::map).filter(o->"fine_mapping_variant_posterior".equals(o.get("kind"))).toList();assertEquals(6,posteriors.size());assertTrue(posteriors.stream().allMatch(p->Boolean.FALSE.equals(p.get("primary"))&&Boolean.TRUE.equals(p.get("converged"))&&!list(p.get("source_ids")).isEmpty()));
        }
    }
    @Test void priorsAndLdAlleleBytesArePartOfTheCurrentIdentity() throws Exception {
        initialize();var first=context("colocalization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        var next=context("colocalization","prior",true,pairs(),Map.of("colocalization",Map.of("shared_prior",1e-6,"shared_prior_sensitivity",List.of(5e-6))));var b=new StudyGeneticEvidence().execute(next);assertCounts(b,0,2);
        Files.writeString(root.resolve("alleles.tsv"),"variant_id\teffect_allele\tother_allele\nrs1\tG\tA\nrs2\tA\tG\nrs3\tA\tG\n");
        var changed=context("colocalization","alleles",true,pairs(),Map.of());var d=new StudyGeneticEvidence().execute(changed);assertCounts(d,0,2);assertEquals(State.COMPLETE,d.state());
    }
    @Test void checksumPinnedSourceReuseNeedsNoNewDownloadOrExistingAcquisitionCache() throws Exception {
        initialize();var pairs=pairs();var pair=map(pairs.get(0));var source=map(pair.get("outcome"));String hash=FollowupSupport.hash(root.resolve("outcome1.tsv"));source.put("url","https://www.ebi.ac.uk/synthetic-outcome.tsv");source.put("sha256",hash);source.put("license","Synthetic fixture");pair.put("outcome",source);pairs.set(0,pair);
        var first=context("mendelian_randomization","first",false,pairs,Map.of());Path cached=root.resolve("resources/regional-evidence/"+hash+".tsv");Files.createDirectories(cached.getParent());Files.copy(root.resolve("outcome1.tsv"),cached);
        var a=new StudyGeneticEvidence().execute(first);assertEquals(State.COMPLETE,a.state(),a.details().toString());seal(first,a);Files.delete(cached);
        var next=context("mendelian_randomization","next",true,pairs,Map.of());var b=new StudyGeneticEvidence().execute(next);assertEquals(State.COMPLETE,b.state());assertCounts(b,2,0);assertFalse(Files.exists(cached));
    }
    @Test void pairReceiptCannotEscapeItsPairEvenWithAnUpdatedParentSeal() throws Exception {
        initialize();var first=context("mendelian_randomization","first",false,pairs(),Map.of());var a=new StudyGeneticEvidence().execute(first);seal(first,a);
        Path receiptPath=first.outputDirectory().resolve("pair1/"+StudyGeneticPairCache.RECEIPT);var receipt=StudyYaml.loadArtifact(receiptPath);var hashes=map(receipt.get("output_sha256"));hashes.put("../outside.tsv","0".repeat(64));receipt.put("output_sha256",hashes);write(receiptPath,receipt);
        var manifest=StudyYaml.loadArtifact(first.outputDirectory().resolve("manifest.yaml"));var outputs=map(manifest.get("output_sha256"));outputs.put("pair1/"+StudyGeneticPairCache.RECEIPT,FollowupSupport.hash(receiptPath));manifest.put("output_sha256",outputs);write(first.outputDirectory().resolve("manifest.yaml"),manifest);
        var next=context("mendelian_randomization","next",true,pairs(),Map.of());var b=new StudyGeneticEvidence().execute(next);assertCounts(b,1,1);assertEquals(State.COMPLETE,b.state());assertFalse(Files.exists(next.outputDirectory().resolve("outside.tsv")));assertFalse(list(reuse(b).get("invalidated_derived_pairs")).isEmpty());
    }
    @Test void aPairCannotBeSealedAgainstDifferentAcquiredSourceBytes() throws Exception {
        initialize();var c=context("mendelian_randomization","first",false,pairs(),Map.of());var result=new StudyGeneticEvidence().execute(c);assertEquals(State.COMPLETE,result.state());
        var pair=map(pairs().get(0));var finding=StudyBioinformatics.mrFeatures(c).get(0);var cache=new StudyGeneticPairCache();var request=cache.request(c,pair,finding);Path folder=c.outputDirectory().resolve("pair1");Files.delete(folder.resolve(StudyGeneticPairCache.RECEIPT));
        Path snapshot=folder.resolve("exposure-source.tsv");Files.writeString(snapshot,"changed acquired source\n");var metadata=StudyYaml.loadArtifact(folder.resolve("exposure-source.tsv.source.yaml"));metadata.put("sha256",FollowupSupport.hash(snapshot));write(folder.resolve("exposure-source.tsv.source.yaml"),metadata);
        assertThrows(java.io.IOException.class,()->cache.seal(c,pair,finding,request,folder,records(result).get(0)));assertFalse(Files.exists(folder.resolve(StudyGeneticPairCache.RECEIPT)));
    }
}
