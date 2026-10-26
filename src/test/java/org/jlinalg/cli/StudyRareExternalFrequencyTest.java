/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyRareFrequencyPolicy;
import org.jlinalg.study.StudyRareVariantPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyRareExternalFrequencyTest {
    @TempDir Path project;
    private static final String HEADER = "variant_id\tpopulation\tallele_count\tallele_number\tfilter\tgenome_build\n";
    private Context fixture(Map<String,Object> overrides, String rows) throws Exception {
        Files.writeString(project.resolve("annotation.tsv"), "variant_id\tset_id\tconsequence\n1:100:A:G\tG1\tlof\n1:110:A:G\tG1\tlof\n1:120:A:G\tG2\tlof\n1:130:A:G\tG3\tsynonymous\n");
        Files.writeString(project.resolve("frequency.tsv"), HEADER + rows);
        var frequency = new LinkedHashMap<String,Object>(Map.of("file", "frequency.tsv", "source", "synthetic-panel", "release", "v1", "genome_build", "GRCh38", "populations", List.of("afr", "nfe"), "minimum_allele_number", 1000, "rationale", "Synthetic panel fixed before associations."));
        frequency.putAll(overrides);
        var rare = Map.of("external_frequency", frequency, "masks", List.of(Map.of("id", "lof", "consequences", List.of("lof"), "maximum_maf", .01)));
        var dataset = Map.of("id", "a", "type", "gwas", "stage", "rare_score_summary", "cohort", "A", "genome_build", "GRCh38", "annotation_release", "synthetic-v1", "files", Map.of("variant_annotation", "annotation.tsv"));
        var h = Map.of("id", "H", "description", "Synthetic external frequency filter", "inputs", Map.of("discovery", List.of("a")), "analysis", Map.of("rare_variants", rare));
        return new Context(project, Files.createTempDirectory(project, "output-"), Map.of("id", "mask", "type", "rare_variant_masks", "hypothesis", "H", "datasets", List.of("a")), Map.of("datasets", List.of(dataset), "hypotheses", List.of(h)), Map.of(), Map.of());
    }
    private static String row(int position, String pop, String ac, String an, String filter) { return "1:" + position + ":A:G\t" + pop + "\t" + ac + "\t" + an + "\t" + filter + "\tGRCh38\n"; }
    private static String good() { return row(100,"afr","0","1000","PASS") + row(100,"nfe","1","1000","PASS")
        + row(110,"afr","10","1000","PASS") + row(110,"nfe","1","1000","PASS")
        + row(120,"afr","20","1000","PASS") + row(120,"nfe","0","100000","PASS"); }
    private static List<String> members(Context c) throws Exception { return DelimitedData.readResult(c.outputDirectory().resolve("mask-membership.tsv")).rows().stream().map(row -> row[2]).toList(); }
    @Test void selectedPopulationMaximumFiltersBeforeTestingAndIncludesBoundaryAndExplicitZero() throws Exception {
        var c = fixture(Map.of(), good()); Result r = new StudyCliExecutor().execute(c);
        assertEquals(State.COMPLETE, r.state(), r.details().toString());
        assertEquals(List.of("1:100:A:G", "1:110:A:G"), members(c));
        var info = map(r.details().get("external_frequency")); assertEquals(1, info.get("excluded_mask_variant_pairs"));
        assertEquals(FollowupSupport.hash(project.resolve("frequency.tsv")), info.get("source_sha256"));
        assertEquals(Files.readString(project.resolve("frequency.tsv")), Files.readString(c.outputDirectory().resolve("external-frequency-source.tsv")));
        assertEquals(6, DelimitedData.readResult(c.outputDirectory().resolve("external-frequency-qc.tsv")).rows().size());
        assertTrue(map(r.details().get("method_report")).toString().contains("synthetic-panel"));
    }
    @Test void functionalAltIsNotFlippedToItsMinorAllele() throws Exception {
        var c = fixture(Map.of(), good().replace("10\t1000", "990\t1000"));
        assertEquals(State.COMPLETE, new StudyCliExecutor().execute(c).state()); assertEquals(List.of("1:100:A:G"), members(c));
    }
    @Test void missingPopulationAndZeroCoverageHoldTheWholeMaskFamily() throws Exception {
        var c = fixture(Map.of(), good().replace(row(100,"nfe","1","1000","PASS"), "").replace("10\t1000", "0\t0"));
        Result r = new StudyCliExecutor().execute(c); assertEquals(State.WAITING_FOR_INPUT, r.state());
        assertFalse(Files.exists(c.outputDirectory().resolve("mask-membership.tsv")));
        String audit = Files.readString(c.outputDirectory().resolve("external-frequency-qc.tsv"));
        assertTrue(audit.contains("MISSING_POPULATION")); assertTrue(audit.contains("INSUFFICIENT_ALLELE_NUMBER"));
    }
    @Test void noAlleleSwapOrSameLocusFrequencyBorrowing() throws Exception {
        var c = fixture(Map.of(), good().replace("1:100:A:G", "1:100:G:A"));
        assertEquals(State.WAITING_FOR_INPUT, new StudyCliExecutor().execute(c).state());
        assertTrue(Files.readString(c.outputDirectory().resolve("external-frequency-qc.tsv")).contains("ALLELE_KEY_MISMATCH"));
    }
    @Test void explicitFailedSourceFiltersExcludeAndUnavailableFiltersWait() throws Exception {
        var c = fixture(Map.of(), good().replace(row(110,"afr","10","1000","PASS"), row(110,"afr","10","1000","RF")));
        assertEquals(State.COMPLETE, new StudyCliExecutor().execute(c).state()); assertEquals(List.of("1:100:A:G"), members(c));
        c = fixture(Map.of(), good().replace(row(110,"afr","10","1000","PASS"), row(110,"afr","10","1000","NA")));
        assertEquals(State.WAITING_FOR_INPUT, new StudyCliExecutor().execute(c).state());
    }
    @Test void countsMustBeExactIntegersAndConsistentAndBuildsMustMatch() throws Exception {
        for (String rows : List.of(good() + row(100,"afr","0","1000","PASS"), good().replace("10\t1000", "1.0\t1000"),
                good().replace("10\t1000", "1001\t1000"), good().replace("GRCh38", "GRCh37"), good().replace("10\t1000", "-1\t1000"))) {
            var c = fixture(Map.of(), rows); assertThrows(java.io.IOException.class, () -> new StudyCliExecutor().execute(c));
        }
    }
    @Test void incompleteSourceMetadataOrRowBudgetNeverPublishesAPrefix() throws Exception {
        var c = fixture(Map.of("maximum_rows", 2), good());
        assertEquals(State.WAITING_FOR_INPUT, new StudyCliExecutor().execute(c).state()); assertFalse(Files.exists(c.outputDirectory().resolve("mask-membership.tsv")));
        var f = new LinkedHashMap<String,Object>(Map.of("enabled", true));
        assertFalse(StudyRareFrequencyPolicy.resolve(f).containsKey("minimum_allele_number"));
        var build = fixture(Map.of("genome_build", "GRCh37"), good()); assertEquals(State.WAITING_FOR_INPUT, new StudyCliExecutor().execute(build).state());
    }
    @Test void sourceChangesAtSamePathChangeMaskCacheInputs() throws Exception {
        var c = fixture(Map.of(), good()); var before = StudyRareFrequencyResources.inputHashes(project,c.study(),c.operation());
        Files.writeString(project.resolve("frequency.tsv"), HEADER + good().replace("10\t1000", "11\t1000"));
        assertNotEquals(before, StudyRareFrequencyResources.inputHashes(project,c.study(),c.operation()));
        Files.delete(project.resolve("frequency.tsv")); assertEquals("missing", StudyRareFrequencyResources.inputHashes(project,c.study(),c.operation()).get("rare_frequency:source"));
    }
    @Test void maskThresholdsAreIndependentAndDeclaredBeforeTesting() throws Exception {
        var c=fixture(Map.of(),good()); var s=new LinkedHashMap<>(c.study());var h=new LinkedHashMap<>(map(list(s.get("hypotheses")).get(0)));var a=new LinkedHashMap<>(map(h.get("analysis")));var p=new LinkedHashMap<>(map(a.get("rare_variants")));
        p.put("masks",List.of(Map.of("id","loose","consequences",List.of("lof"),"maximum_external_af",.03),Map.of("id","tight","consequences",List.of("lof"),"maximum_external_af",.001)));
        a.put("rare_variants",p);h.put("analysis",a);s.put("hypotheses",List.of(h));var run=new Context(project,c.outputDirectory(),c.operation(),s,Map.of(),Map.of());
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(run).state());var rows=DelimitedData.readResult(run.outputDirectory().resolve("mask-membership.tsv")).rows();
        assertEquals(3,rows.stream().filter(row->row[0].equals("loose")).count());assertEquals(1,rows.stream().filter(row->row[0].equals("tight")).count());
    }
    @Test void requiredMetadataIsARequestRatherThanAnInferredPanel() throws Exception {
        for(String field:List.of("source","release","file","populations","minimum_allele_number","rationale")){
            var c=fixture(Map.of(),good());var s=new LinkedHashMap<>(c.study());var h=new LinkedHashMap<>(map(list(s.get("hypotheses")).get(0)));var a=new LinkedHashMap<>(map(h.get("analysis")));var p=new LinkedHashMap<>(map(a.get("rare_variants")));var f=new LinkedHashMap<>(map(p.get("external_frequency")));f.remove(field);p.put("external_frequency",f);a.put("rare_variants",p);h.put("analysis",a);s.put("hypotheses",List.of(h));
            var result=new StudyCliExecutor().execute(new Context(project,c.outputDirectory(),c.operation(),s,Map.of(),Map.of()));assertEquals(State.WAITING_FOR_INPUT,result.state(),field);assertTrue(result.summary().contains("external_frequency."+field),field);
        }
    }
    @Test void policiesRequireExplicitPopulationsAndActiveExternalThresholds() {
        assertEquals(false, StudyRareFrequencyPolicy.resolve(Map.of()).get("enabled"));
        for (var invalid : List.of(Map.<String,Object>of("populations", List.of("afr","afr")), Map.<String,Object>of("minimum_allele_number", 0), Map.<String,Object>of("maximum_rows", 1.5), Map.<String,Object>of("unknown", true)))
            assertThrows(IllegalArgumentException.class, () -> StudyRareFrequencyPolicy.resolve(invalid));
        assertThrows(IllegalArgumentException.class, () -> StudyRareVariantPolicy.resolve(Map.of("masks",List.of(Map.of("id","lof","consequences",List.of("lof"),"maximum_external_af",.01))), Map.of(), true));
    }
    @Test void pOnlySetsCannotBeRefilteredWithoutVariantMembership() throws Exception {
        var c = fixture(Map.of(), good()); var s = new LinkedHashMap<>(c.study());
        var dataset = new LinkedHashMap<>(map(list(s.get("datasets")).get(0))); dataset.put("stage", "set_association_results");s.put("datasets", List.of(dataset));
        var op = new LinkedHashMap<>(c.operation()); op.put("type", "rare_variant_analysis");
        var run = new Context(project,c.outputDirectory(),op,s,Map.of(),Map.of());
        var result = new StudyCliExecutor().execute(run); assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains("P-only set results"));
    }
    @Test void anEmptyEligibleUniverseDoesNotFitOrInventAStatisticalFamily() throws Exception {
        var c=fixture(Map.of(),good().replace("PASS","RF"));assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());assertTrue(members(c).isEmpty());
        StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_type","rare_variant_masks","operation_id","mask","hypothesis","H","state","complete","output_sha256",Map.of("mask-membership.tsv",FollowupSupport.hash(c.outputDirectory().resolve("mask-membership.tsv")))));
        var op=new LinkedHashMap<>(c.operation());op.put("type","rare_variant_analysis");var run=new Context(project,Files.createDirectory(project.resolve("analysis")),op,c.study(),Map.of(),Map.of("masks",c.outputDirectory()));
        var r=new StudyCliExecutor().execute(run);assertEquals(State.COMPLETE,r.state());assertEquals(true,r.details().get("empty_mask_universe"));assertEquals(false,map(r.details().get("method_report")).get("performed"));assertTrue(DelimitedData.readResult(run.outputDirectory().resolve("rare-results.tsv")).rows().isEmpty());assertTrue(list(r.details().get("families")).isEmpty());
    }
}
