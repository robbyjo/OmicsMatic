/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExactGwasCliTest {
    @TempDir Path root;
    private static final Path REFERENCE=Path.of("src/test/resources/exact-conditional-logit");
    private Path fixture(String name)throws Exception {
        Path folder=Files.createDirectory(root.resolve(name));var table=DelimitedData.read(REFERENCE.resolve(name+".tsv"));
        StringBuilder phenotype=new StringBuilder("id\ty\tstratum\to\tz\n");
        for(int i=table.rows().size()-1;i>=0;i--){var r=table.rows().get(i);phenotype.append("a").append(i).append('\t').append(r[0]).append('\t').append(r[2]).append('\t').append(r[3]).append('\t').append(i).append('\n');}
        Files.writeString(folder.resolve("phenotype.tsv"),phenotype);
        StringBuilder vcf=new StringBuilder("##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
        for(int i=0;i<table.rows().size();i++)vcf.append("\ta").append(i);
        vcf.append("\n1\t100\tv\tA\tG\t.\tPASS\t.\tGT");for(var r:table.rows())vcf.append('\t').append(switch(r[1]){case "0"->"0/0";case "1"->"0/1";case "2"->"1/1";default->"./.";});vcf.append('\n');
        Files.writeString(folder.resolve("genotype.vcf"),vcf);return folder;
    }
    private List<String> arguments(Path folder,String out) {
        boolean stratified=!folder.getFileName().toString().equals("ternary");
        var args=new ArrayList<>(List.of("--omics",folder.resolve("genotype.vcf").toString(),"--pheno",folder.resolve("phenotype.tsv").toString(),"--id","id","--formula","y~"+(stratified?"stratum+":"")+"offset(o)+<omics>","--model","glm","--family","binomial","--binary-test","exact-conditional","--min-mac","0","--max-marker-missing","1","--out",folder.resolve(out).toString(),"--threads","2","--no-log","--no-config"));
        if(stratified)args.addAll(List.of("--exact-strata-column","stratum"));return args;
    }
    private String run(List<String> args,boolean success){var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);if(success)assertEquals(0,code,bytes.toString());else assertNotEquals(0,code,bytes.toString());return bytes.toString();}
    @Test void nativeRouteMatchesAllIndependentConditionalReferences()throws Exception {
        for(var reference:DelimitedData.read(REFERENCE.resolve("reference.tsv")).rows()){
            Path folder=fixture(reference[0]);run(arguments(folder,"result.tsv"),true);var table=DelimitedData.read(folder.resolve("result.tsv"));var row=table.rows().get(0);
            assertEquals("ok",row[table.column("status")],reference[0]);assertEquals(Double.parseDouble(reference[2]),Double.parseDouble(row[table.column("p_value")]),1e-12);assertEquals(Double.parseDouble(reference[3]),Double.parseDouble(row[table.column("log_p_value")]),1e-12);
            assertEquals(reference[8],row[table.column("test_n_samples")]);assertEquals("exact_conditional_genotype_score",row[table.column("test_method")]);assertEquals("conditional_centered_allele_score",row[table.column("statistic_type")]);assertEquals("",row[table.column("df_numerator")]);assertFalse(row[table.column("fdr_bh")].isBlank());
        }
    }
    @Test void effectFailureOrBudgetDoesNotDiscardValidExactTests()throws Exception {
        Path folder=fixture("stratified");
        for(var controls:List.of(List.of("--exact-effect-max-parameters","0"),List.of("--firth-max-iterations","1"))){var args=arguments(folder,"test"+controls.hashCode()+".tsv");args.addAll(controls);run(args,true);var table=DelimitedData.read(Path.of(args.get(args.indexOf("--out")+1)));var row=table.rows().get(0);assertEquals("ok",row[table.column("status")]);assertEquals("p_value_only",row[table.column("result_type")]);assertEquals("",row[table.column("beta")]);assertEquals("",row[table.column("standard_error")]);assertEquals(1.0/48,Double.parseDouble(row[table.column("p_value")]),1e-12);}
    }
    @Test void incompleteFamilyPublishesNoAdjustedValuesEvenForCompletedTests()throws Exception {
        Path folder=fixture("stratified");Path vcf=folder.resolve("genotype.vcf");String row=Files.readAllLines(vcf).get(3);String[] cells=row.split("\t");cells[2]="binary";for(int i=9;i<cells.length;i++)cells[i]=i<13&&i%2==0?"0/1":"0/0";Files.writeString(vcf,String.join("\t",cells)+"\n",java.nio.file.StandardOpenOption.APPEND);
        var args=arguments(folder,"partial.tsv");args.addAll(List.of("--exact-max-states","5"));run(args,true);var table=DelimitedData.read(folder.resolve("partial.tsv"));assertTrue(table.rows().stream().anyMatch(r->r[table.column("status")].equals("ok")));assertTrue(table.rows().stream().anyMatch(r->r[table.column("exact_status")].equals("budget_exceeded")));for(var r:table.rows())assertEquals("",r[table.column("fdr_bh")]);assertTrue(Files.readString(folder.resolve("partial.tsv.manifest.json")).contains("incomplete_exact_tests"));
    }
    @Test void continuousCovariatesAndDosagesAreNeverSilentlyRoundedOrDropped()throws Exception {
        Path folder=fixture("stratified");var args=arguments(folder,"invalid.tsv");args.set(args.indexOf("--formula")+1,"y~stratum+z+offset(o)+<omics>");run(args,false);assertFalse(Files.exists(folder.resolve("invalid.tsv")));
        Path vcf=folder.resolve("genotype.vcf");String text=Files.readString(vcf).replace("ID=GT,Number=1,Type=String","ID=DS,Number=1,Type=Float").replace("\tGT\t","\tDS\t").replace("0/0","0").replace("0/1","1").replace("1/1","2");Files.writeString(vcf,text);run(arguments(folder,"ds.tsv"),true);var table=DelimitedData.read(folder.resolve("ds.tsv"));assertEquals("failed",table.rows().get(0)[table.column("status")]);assertEquals("",table.rows().get(0)[table.column("p_value")]);
    }
    private Context context(Path folder,String out,Map<String,Object> extra)throws Exception {
        var association=new LinkedHashMap<String,Object>(Map.of("model","glm","family","binomial","binary_test","exact-conditional","minimum_mac",0,"maximum_marker_missing",1,"exact_strata_column","stratum"));association.putAll(extra);
        var study=Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(Map.of("id","a","type","gwas","stage","processed_matrix","genome_build","GRCh38","files",Map.of("matrix","genotype.vcf","phenotype","phenotype.tsv"),"columns",Map.of("sample_id","id"))),"hypotheses",List.of(Map.of("id","H","outcome","y","analysis",Map.of("association",association,"harmonization",Map.of("effect_outliers",Map.of("enabled",false))))));
        return new Context(folder,Files.createDirectory(folder.resolve(out)),Map.of("id","association","type","association","hypothesis","H","dataset_id","a","datasets",List.of("a")),study,Map.of(),Map.of());
    }
    @Test void studyPOnlyKeepsVariantSpecificCountsAndMandatoryCaveat()throws Exception {
        var c=context(fixture("missing"),"study",Map.of("exact_effect_maximum_parameters",0,"offset_column","o"));var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());var table=DelimitedData.read(c.outputDirectory().resolve("standard-results/a.tsv"));var row=table.rows().get(0);assertEquals("p_value_only",row[table.column("result_type")]);assertEquals("7",row[table.column("n")]);assertEquals("8",row[table.column("qc_n_samples")]);assertEquals("NA",row[table.column("beta")]);assertTrue(result.details().get("required_manuscript_caveat").toString().contains("P-only tests establish no effect magnitude"));
        StudyTestSources.seal(c.outputDirectory(),"association","association","H");var selection=new Context(c.project(),Files.createDirectory(c.project().resolve("primary")),Map.of("id","candidate","type","candidate_prioritization","hypothesis","H","datasets",List.of("a")),c.study(),Map.of(),Map.of("association",c.outputDirectory()));assertEquals(1,StudyPrimaryResults.official(selection).probabilities().size());
    }
    @Test void studyIncompleteFamilyWaitsAndSealsPartialResultsInsteadOfDeclaringPrimaryEligibility()throws Exception {
        var c=context(fixture("stratified"),"limited",Map.of("exact_maximum_work",1));var result=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(false,result.details().get("primary_selection_eligible"));assertTrue(result.outputs().contains("association.tsv"));assertTrue(result.outputs().contains("request.yaml"));assertTrue(Files.readString(c.outputDirectory().resolve("request.yaml")).contains("bh_suppressed"));
    }
}
