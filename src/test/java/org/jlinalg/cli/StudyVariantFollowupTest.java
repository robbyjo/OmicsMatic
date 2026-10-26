/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.jlinalg.study.StudyVariantFollowupPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyVariantFollowupTest {
    @TempDir Path project;
    private Context context(int count, Map<String, Object> policy, boolean before) throws Exception {
        Files.writeString(project.resolve("reference.fa"), ">1\n" + "A".repeat(2000) + "\n");
        StringBuilder result = new StringBuilder("feature_id\tbeta\tse\tp_value\n");
        for (int i=0;i<count;i++) result.append("1:").append(100+i).append(":A:G\t.2\t.1\t").append(i == 0 ? ".9" : ".0001").append('\n');
        Path producer=project.resolve("producer"); Files.createDirectories(producer.resolve("standard-results"));
        Files.writeString(producer.resolve("standard-results/a.tsv"), result);
        Files.writeString(project.resolve("results.tsv"), result + "1:1900:A:G\t.2\t.1\t.8\n");
        seal(producer, "validate_harmonize", "harmonize", Map.of());
        var effective = new LinkedHashMap<>(policy); effective.putIfAbsent("reference_fasta", "reference.fa");
        Map<String, Object> dataset=Map.of("id","a","type","gwas","stage","association_results","genome_build","GRCh38","files",Map.of("results","results.tsv","variants","results.tsv"));
        Map<String, Object> study=Map.of("datasets",List.of(dataset),"resources",Map.of("network","offline","downloads","deny"),
            "hypotheses",List.of(Map.of("id","H","outcome","y","inputs",Map.of("discovery",List.of("a")),"analysis",Map.of("variant_followup",effective))));
        Path output=project.resolve(before ? "pre" : "post"); Files.createDirectories(output);
        return new Context(project,output,Map.of("id","followup","type",before ? "variant_annotation" : "variant_scoring","hypothesis","H","datasets",List.of("a")),study,Map.of(),Map.of("producer",producer));
    }
    private void seal(Path directory, String type, String id, Map<String, Object> details) throws Exception {
        Map<String,Object> hashes=new LinkedHashMap<>();
        try (var files=Files.walk(directory)) { for (Path file:files.filter(Files::isRegularFile).toList()) if (!file.equals(directory.resolve("manifest.yaml"))) hashes.put(directory.relativize(file).toString().replace('\\','/'),FollowupSupport.hash(file)); }
        write(directory.resolve("manifest.yaml"),Map.of("operation_type",type,"operation_id",id,"hypothesis","H","state","complete","details",details,"output_sha256",hashes));
    }
    private void annotations(int count, String format) throws Exception {
        List<String> rows=new ArrayList<>();
        if (format.equals("canonical")) {
            rows.add("genome_build\tchrom\tpos\tref\talt\tgene\ttranscript\tconsequence");
            for (int i=0;i<count;i++) rows.add("GRCh38\t1\t"+(100+i)+"\tA\tG\tENSG1\tENST"+i+"\tmissense_variant");
        } else if (format.equals("vep")) {
            rows.add("## VEP fixture"); rows.add("#Uploaded_variation\tGene\tFeature\tConsequence\tExtra");
            for (int i=0;i<count;i++) rows.add("GRCh38:1:"+(100+i)+":A:G\tENSG1\tENST"+i+"\tmissense_variant\tfixture");
        } else {
            rows.add("##fileformat=VCFv4.2"); rows.add("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO");
            for (int i=0;i<count;i++) rows.add("1\t"+(100+i)+"\tGRCh38:1:"+(100+i)+":A:G\tA\tG\t.\tPASS\tGene.refGene=ENSG1;ExonicFunc.refGene=missense_variant");
        }
        Files.write(project.resolve("annotations.txt"),rows);
    }
    private Map<String,Object> annotationPolicy(String format) { return Map.of("maximum_variants_per_batch",1,"consequences",Map.of("engine","import","annotations","annotations.txt","format",format,"release","fixture-v1"),"alphagenome",Map.of("enabled",false)); }

    @Test void nativeDispatchAnnotatesEveryPrimaryVariantIncludingNonsignificantRowsAcrossImportBatches() throws Exception {
        for (String format:List.of("canonical","vep","annovar-vcf")) {
            annotations(3,format); Context original=context(2,annotationPolicy(format),false);
            Path output=project.resolve(format); Files.createDirectories(output);
            Context context=new Context(project,output,original.operation(),original.study(),Map.of(),original.dependencyDirectories());
            var result=new StudyCliExecutor().execute(context); assertEquals(State.COMPLETE,result.state(),result.summary());
            var inventory=DelimitedData.readResult(output.resolve("variant-inventory.tsv")); assertEquals(3,inventory.rows().size());
            assertEquals(2,inventory.rows().stream().filter(row->row[inventory.column("consequence_status")].equals("annotated")).count());
            assertEquals("excluded_from_primary",inventory.rows().get(2)[inventory.column("consequence_status")]);
            var consequences=DelimitedData.readResult(output.resolve("variant-consequences.tsv")); assertEquals(2,consequences.rows().size());
            assertTrue(consequences.rows().stream().anyMatch(row->row[0].equals("GRCh38:1:100:A:G")));
        }
    }
    @Test void partialConsequenceResponseCannotLeaveUsableRowsOrClaimCompleteCoverage() throws Exception {
        Context context=context(2,Map.of("alphagenome",Map.of("enabled",false)),false);
        var study=new LinkedHashMap<>(context.study()); study.put("resources",Map.of("network","online","downloads","automatic"));
        context=new Context(project,context.outputDirectory(),context.operation(),study,Map.of(),context.dependencyDirectories());
        var executor=new StudyVariantFollowup((c,p,keys,config)->List.of(Map.of("variant_id",keys.get(0).id(),"status","annotated","gene","ENSG1")),new StudyAlphaGenome());
        var result=executor.execute(context); assertEquals(State.WAITING_FOR_INPUT,result.state());
        assertTrue(DelimitedData.readResult(context.outputDirectory().resolve("variant-consequences.tsv")).rows().isEmpty());
        assertEquals(2,DelimitedData.readResult(context.outputDirectory().resolve("variant-inventory.tsv")).rows().stream().filter(row->row[6].equals("provider_or_input_error")).count());
    }
    @Test void referenceMismatchAndPolicyDenialPreventProviderCalls() throws Exception {
        Context original=context(2,Map.of(),false); AtomicInteger calls=new AtomicInteger();
        var executor=new StudyVariantFollowup((c,p,k,config)->{calls.incrementAndGet();throw new AssertionError("Network transport must not run");},new StudyAlphaGenome((c,p,r,config)->{throw new AssertionError("AlphaGenome must not run");}));
        assertEquals(State.WAITING_FOR_INPUT,executor.execute(original).state()); assertEquals(0,calls.get());
        var inventory=DelimitedData.readResult(original.outputDirectory().resolve("variant-inventory.tsv")); assertEquals("offline_policy",inventory.rows().get(0)[6]);
        Files.writeString(project.resolve("reference.fa"),">1\n"+"C".repeat(2000)+"\n");
        var study=new LinkedHashMap<>(original.study()); study.put("resources",Map.of("network","online","downloads","automatic"));
        Context changed=new Context(project,project.resolve("mismatch"),original.operation(),study,Map.of(),original.dependencyDirectories());
        assertEquals(State.WAITING_FOR_INPUT,executor.execute(changed).state()); assertEquals(0,calls.get());
        assertTrue(DelimitedData.readResult(changed.outputDirectory().resolve("variant-inventory.tsv")).rows().stream().filter(row->row[3].equals("true")).allMatch(row->row[5].equals("reference_mismatch")));
    }
    @Test void partialQueryBudgetAssignsEveryVariantAndCannotSkipTheTail() throws Exception {
        Context original=context(20,Map.of(),false); var study=new LinkedHashMap<>(original.study()); study.put("resources",Map.of("network","online","downloads","automatic"));
        Context context=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),original.dependencyDirectories());
        var catalog=StudyVariantUniverse.load(context,false); AtomicInteger requests=new AtomicInteger();
        var alpha=new StudyAlphaGenome((c,folder,request,config)->{requests.incrementAndGet();return Map.of("status","completed_with_dispositions","variants",list(request.get("variants")).stream().map(StudyPublicEvidence::map).map(row->Map.of("variant_id",row.get("variant_id"),"status","not_found")).toList());});
        var config=map(StudyVariantFollowupPolicy.resolve(Map.of("alphagenome",Map.of("maximum_queries",5))).get("alphagenome"));
        var result=alpha.execute(context,catalog.queries,config); assertEquals(20,result.statuses().size()); assertEquals(1,requests.get());
        assertEquals(5,result.statuses().values().stream().filter("not_found"::equals).count()); assertEquals(15,result.statuses().values().stream().filter("atlas_budget_exhausted"::equals).count());
    }
    @Test void completeClaimWithoutRequestedScorerValuesRemainsIncomplete() throws Exception {
        Context original=context(1,Map.of(),false); var study=new LinkedHashMap<>(original.study()); study.put("resources",Map.of("network","online","downloads","automatic"));
        Context context=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),original.dependencyDirectories());
        var alpha=new StudyAlphaGenome((c,p,r,config)->Map.of("status","completed_with_dispositions","variants",List.of(Map.of("variant_id","GRCh38:1:100:A:G","status","complete"))));
        var result=alpha.execute(context,StudyVariantUniverse.load(context,false).queries,map(StudyVariantFollowupPolicy.resolve(Map.of()).get("alphagenome")));
        assertEquals("partial_scorer_coverage",result.statuses().get("GRCh38:1:100:A:G"));
    }
    @Test void atlasMissesUseTheSeparatePredictionScorersAndRespectTheirOwnBudget() throws Exception {
        Context original=context(3,Map.of(),false); var study=new LinkedHashMap<>(original.study()); study.put("resources",Map.of("network","online","downloads","automatic"));
        Context context=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),original.dependencyDirectories()); List<Map<String,Object>> requests=new ArrayList<>();
        var alpha=new StudyAlphaGenome((c,p,r,config)->{requests.add(r); assertFalse(StudyAssistance.json(r).contains("p_value")); assertFalse(StudyAssistance.json(r).contains("beta")); return Map.of("status","completed_with_dispositions","variants",list(r.get("variants")).stream().map(StudyPublicEvidence::map).map(row->Map.of("variant_id",row.get("variant_id"),"status","not_found")).toList());});
        var config=map(StudyVariantFollowupPolicy.resolve(Map.of("alphagenome",Map.of("engine","atlas_then_predict","maximum_predictions",2))).get("alphagenome"));
        var result=alpha.execute(context,StudyVariantUniverse.load(context,false).queries,config);
        assertEquals(2,requests.size()); assertEquals(List.of("AVI_SCORE"),requests.get(0).get("scorers")); assertFalse(list(requests.get(1).get("scorers")).contains("AVI_SCORE"));
        assertEquals(3,result.statuses().size()); assertEquals(1,result.statuses().values().stream().filter("predict_budget_exhausted"::equals).count());
    }
    @Test void rootTransmissionDenialCannotReachEitherRemoteProvider() throws Exception {
        Context original=context(1,Map.of(),false); var study=new LinkedHashMap<>(original.study()); study.put("resources",Map.of("network","online","downloads","automatic")); study.put("project",Map.of("data_policy",Map.of("remote_transmission","deny_all")));
        Context context=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),original.dependencyDirectories());
        var executor=new StudyVariantFollowup((c,p,k,config)->{throw new AssertionError("Denied consequence request");},new StudyAlphaGenome((c,p,r,config)->{throw new AssertionError("Denied AlphaGenome request");}));
        assertEquals(State.WAITING_FOR_INPUT,executor.execute(context).state()); var table=DelimitedData.readResult(context.outputDirectory().resolve("variant-inventory.tsv"));
        assertEquals("policy_denied",table.rows().get(0)[6]); assertEquals("policy_denied",table.rows().get(0)[7]);
    }
    @Test void referenceIndexCoordinatesAndIndelNormalizationRemainExplicit() throws Exception {
        Path fasta=project.resolve("indexed.fa"); Files.writeString(fasta,">chr1\nACGT\nACGT\n"); Files.writeString(project.resolve("indexed.fa.fai"),"chr1\t8\t6\t4\t5\n");
        try(var reference=new StudyVariantReference(fasta)) {
            assertEquals("reference_matched",reference.check(new StudyVariantUniverse.Key("GRCh38","1",4,"TAC","T")));
            assertEquals("reference_mismatch",reference.check(new StudyVariantUniverse.Key("GRCh38","1",4,"GAC","G")));
            assertEquals("reference_position_unavailable",reference.check(new StudyVariantUniverse.Key("GRCh38","1",8,"TA","T")));
        }
        Context original=context(1,Map.of(),false); Files.writeString(project.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\n1:100:A:AT\t.1\t.1\t.9\n");
        Path primary=project.resolve("producer/standard-results/a.tsv"); Files.writeString(primary,Files.readString(project.resolve("results.tsv"))); seal(project.resolve("producer"),"validate_harmonize","harmonize",Map.of());
        var result=new StudyVariantFollowup().execute(original); assertEquals(State.WAITING_FOR_INPUT,result.state());
        assertEquals("indel_normalization_declaration_required",DelimitedData.readResult(original.outputDirectory().resolve("variant-inventory.tsv")).rows().get(0)[5]);
    }
    @Test void invalidBudgetsReferencePathsAndUnversionedLocalAnnotationsFailValidation() {
        assertThrows(IllegalArgumentException.class,()->StudyVariantFollowupPolicy.resolve(Map.of("reference_fasta",17)));
        assertThrows(IllegalArgumentException.class,()->StudyVariantFollowupPolicy.resolve(Map.of("alphagenome",Map.of("maximum_queries",0))));
        assertThrows(IllegalArgumentException.class,()->StudyVariantFollowupPolicy.resolve(Map.of("consequences",Map.of("engine","import","annotations","unversioned.tsv"))));
        assertThrows(IllegalArgumentException.class,()->StudyVariantFollowupPolicy.resolve(Map.of("alphagenome",Map.of("prediction_scorers",java.util.stream.IntStream.range(0,21).mapToObj(i->"SCORER"+i).toList()))));
    }
    @Test void importedScoresRetainTissueTranscriptAndScorerContextInFindingDossiers() throws Exception {
        annotations(1,"canonical"); var policy=new LinkedHashMap<>(annotationPolicy("canonical")); policy.put("alphagenome",Map.of("engine","import","scores","scores.tsv"));
        Context context=context(1,policy,false);
        FollowupSupport.table(project.resolve("scores.tsv"),StudyAlphaGenome.FIELDS,List.<String[]>of(new String[]{"GRCh38:1:100:A:G","atlas","fixture-atlas-v1","fixture-model","RNA_SEQ","ENSG1","ENST1","UBERON:0002107","track1","-.4","-.95","chr1:1-2000","source1"},new String[]{"GRCh38:1:100:A:G","atlas","fixture-atlas-v1","fixture-model","SPLICE_SITES","ENSG1","ENST2","UBERON:0000178","track2",".7",".9","chr1:1-2000","source2"}));
        var result=new StudyCliExecutor().execute(context); assertEquals(State.COMPLETE,result.state());
        assertEquals(2,DelimitedData.readResult(context.outputDirectory().resolve("alphagenome-scores.tsv")).rows().size());
        seal(context.outputDirectory(),"variant_scoring","followup",result.details());
        Path dossiers=project.resolve("dossiers"); var operation=Map.<String,Object>of("id","inventory","type","finding_inventory","hypothesis","__study__","datasets",List.of("a"));
        new StudyFindingInventory().execute(new Context(project,dossiers,operation,context.study(),Map.of(),Map.of("primary",project.resolve("producer"),"followup",context.outputDirectory())));
        String text=Files.readString(dossiers.resolve("finding-inventory.jsonl")); assertTrue(text.contains("UBERON:0002107")); assertTrue(text.contains("ENST2")); assertTrue(text.contains("causal probabilities"));
    }
    @Test void canonicalBuildAndAlleleConflictsAreRejected() throws Exception {
        assertThrows(java.io.IOException.class,()->StudyVariantUniverse.parseId("GRCh37:1:100:A:G","GRCh38"));
        Context context=context(1,Map.of(),false);
        Files.writeString(project.resolve("results.tsv"),"feature_id\tgenome_build\tchrom\tpos\tref\talt\nGRCh38:1:100:A:G\tGRCh37\t1\t100\tA\tG\n");
        var result=new StudyCliExecutor().execute(context); assertEquals(State.WAITING_FOR_INPUT,result.state()); assertEquals("variant_input_unresolved",result.details().get("status"));
    }
    @Test void sealedBatchesReuseOnlyMatchingVerifiedOutputs() throws Exception {
        annotations(2,"canonical"); Context original=context(2,annotationPolicy("canonical"),false);
        Path previous=project.resolve("runs/previous/operations/followup"); Files.createDirectories(previous);
        Context first=new Context(project,previous,original.operation(),original.study(),Map.of(),original.dependencyDirectories());
        var completed=new StudyVariantFollowup().execute(first); seal(previous,"variant_scoring","followup",completed.details());
        var reused=new StudyVariantFollowup((c,p,k,config)->{throw new AssertionError("Sealed complete annotation should be reused");},new StudyAlphaGenome());
        var result = reused.execute(original); assertEquals(State.COMPLETE,result.state(),result.details().toString());
        Files.writeString(previous.resolve("consequences/batch-000001/batch-consequences.tsv"),"changed");
        Context changed=new Context(project,project.resolve("tampered"),original.operation(),original.study(),Map.of(),original.dependencyDirectories());
        assertEquals(State.WAITING_FOR_INPUT,reused.execute(changed).state());
    }
    @Test void aChangedConsequenceImportInvalidatesOperationReuseWithoutChangingTheStudyContract() throws Exception {
        annotations(2,"canonical"); Context fixture=context(2,annotationPolicy("canonical"),false);
        var study=new LinkedHashMap<>(fixture.study()); study.put("schema_version",1); study.put("project",Map.of("id","annotation-cache","title","Annotation cache","description","Synthetic import invalidation."));
        var hypothesis=new LinkedHashMap<>(map(list(study.get("hypotheses")).get(0))); hypothesis.put("description","Check a changed annotation source.");
        var analysis=new LinkedHashMap<>(map(hypothesis.get("analysis"))); analysis.put("mode","manual"); analysis.put("goals",List.of("variant_scoring")); hypothesis.put("analysis",analysis); study.put("hypotheses",List.of(hypothesis));
        Path contract=project.resolve("study.yaml"); write(contract,study); String contractHash=FollowupSupport.hash(contract);
        var out=new ByteArrayOutputStream(); var error=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"study","auto",contract.toString(),"--offline"},new PrintStream(out),new PrintStream(error)),out+"\n"+error);
        String firstRun=text(org.jlinalg.study.StudyProject.status(project).get("run_id"));
        Files.writeString(project.resolve("annotations.txt"),Files.readString(project.resolve("annotations.txt")).replace("ENSG1","ENSG_CHANGED"));
        assertEquals(0,JLinAlgCli.run(new String[]{"study","run","--project",project.toString()},new PrintStream(out),new PrintStream(error)),out+"\n"+error);
        String secondRun=text(org.jlinalg.study.StudyProject.status(project).get("run_id")); assertNotEquals(firstRun,secondRun); assertEquals(contractHash,FollowupSupport.hash(contract));
        List<Path> followups;
        try(var paths=Files.walk(project.resolve("runs"))){followups=paths.filter(path->path.getFileName().toString().endsWith("-variant_scoring")).toList();}
        assertEquals(2,followups.size());
        for(Path folder:followups){var manifest=StudyYaml.loadArtifact(folder.resolve("manifest.yaml"));String result=Files.readString(folder.resolve("variant-consequences.tsv"));
            if(secondRun.equals(manifest.get("run_id"))){assertFalse(Boolean.TRUE.equals(manifest.get("reused_from_cache")));assertTrue(result.contains("ENSG_CHANGED"));}
            else assertFalse(result.contains("ENSG_CHANGED"));
        }
    }
    @Test void generatedConsequencesPrecedeMasksAndKeepRareMembersIndependentOfSingleMarkerMac() throws Exception {
        annotations(3,"canonical"); Files.writeString(project.resolve("reference.fa"),">1\n"+"A".repeat(2000)+"\n");
        StringBuilder vcf=new StringBuilder("##fileformat=VCFv4.2\n##contig=<ID=1>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
        StringBuilder phenotype=new StringBuilder("id\ty\tage\n");
        for(int i=0;i<1000;i++){vcf.append("\ts").append(i);phenotype.append("s").append(i).append('\t').append(Math.sin(i*.73)+(i<19?.5:0)).append('\t').append(30+i%71).append('\n');} vcf.append('\n');
        int[] counts={1,19,20};
        for(int j=0;j<3;j++){vcf.append("1\t").append(100+j).append("\tv").append(counts[j]).append("\tA\tG\t.\tPASS\t.\tGT");for(int i=0;i<1000;i++)vcf.append(i<counts[j]?"\t0/1":"\t0/0");vcf.append('\n');}
        Files.writeString(project.resolve("cohort.vcf"),vcf); Files.writeString(project.resolve("phenotype.tsv"),phenotype);
        Path study=project.resolve("study.yaml"); Files.writeString(study,"""
            schema_version: 1
            project: {id: variant-bridge, title: Variant bridge, description: Synthetic annotation before masks.}
            resources: {network: offline, downloads: deny}
            datasets:
              - {id: a, cohort: cohort-a, type: gwas, stage: processed_matrix, genome_build: GRCh38, files: {matrix: cohort.vcf, phenotype: phenotype.tsv}}
            hypotheses:
              - id: H
                description: Prespecified synthetic single-marker and rare-set workflow.
                outcome: y
                inputs: {discovery: [a]}
                analysis:
                  mode: manual
                  goals: [association, rare_variant_analysis, variant_scoring]
                  association: {model: ols}
                  variant_followup:
                    reference_fasta: reference.fa
                    consequences: {engine: import, annotations: annotations.txt, format: canonical, release: fixture-v1}
                    alphagenome: {enabled: false}
                  rare_variants:
                    minimum_cohorts: 1
                    trait_units: synthetic
                    simulations: 16
                    masks: [{id: missense, consequences: [missense_variant], maximum_maf: 0.01}]
            """);
        var plan=list(StudyPlanner.compile(StudyResolver.resolve(study)).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        var annotation=plan.stream().filter(op->op.get("type").equals("variant_annotation")).findFirst().orElseThrow();
        var masks=plan.stream().filter(op->op.get("type").equals("rare_variant_masks")).findFirst().orElseThrow(); assertTrue(list(masks.get("depends_on")).contains(annotation.get("id")));
        var out=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"study","auto",study.toString(),"--offline"},new PrintStream(out),new PrintStream(error)),out+"\n"+error);assertTrue(out.toString().contains("state: completed"),out.toString());
        Path followup;
        try(var paths=Files.walk(project.resolve("runs"))){followup=paths.filter(path->path.getFileName().toString().endsWith("-variant_scoring")).findFirst().orElseThrow();}
        var inventory=DelimitedData.readResult(followup.resolve("variant-inventory.tsv")); assertEquals(3,inventory.rows().size()); assertEquals(2,inventory.rows().stream().filter(row->row[4].equals("rare_set_QC_candidate")).count());
        assertEquals(3,DelimitedData.readResult(followup.resolve("variant-consequences.tsv")).rows().size());
    }
}
