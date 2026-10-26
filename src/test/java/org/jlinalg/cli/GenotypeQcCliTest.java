/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenotypeQcCliTest {
    @TempDir Path root;
    private void files(boolean text,boolean missingCovariate)throws Exception {
        StringBuilder phenotype=new StringBuilder("id\ty\tz\n");
        for(int i=11;i>=0;i--)phenotype.append("a").append(i).append('\t').append(text?(i<6?"healthy":"disease"):(i<6?"0":"1")).append('\t').append(missingCovariate&&i==0?"NA":i%6).append('\n');
        Files.writeString(root.resolve("phenotype.tsv"),phenotype);
        int[] values={0,0,1,1,2,2,1,1,1,1,1,1};StringBuilder genotype=new StringBuilder("##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
        for(int i=0;i<12;i++)genotype.append("\ta").append(i);genotype.append("\n1\t100\tv\tA\tG\t.\tPASS\t.\tGT");for(int value:values)genotype.append('\t').append(value==0?"0/0":value==1?"0/1":"1/1");genotype.append('\n');Files.writeString(root.resolve("genotype.vcf"),genotype);
    }
    private Path run(String name,String... extra)throws Exception {
        Path output=root.resolve(name+".tsv");var args=new ArrayList<>(List.of("--omics",root.resolve("genotype.vcf").toString(),"--pheno",root.resolve("phenotype.tsv").toString(),"--id","id","--formula","y~z+<omics>","--model","ols","--omics-type","gwas","--min-mac","0","--out",output.toString(),"--threads","1","--no-log","--no-config"));args.addAll(List.of(extra));
        var errors=new ByteArrayOutputStream();int status=JLinAlgCli.run(args.toArray(String[]::new),new PrintStream(new ByteArrayOutputStream()),new PrintStream(errors));assertEquals(0,status,errors.toString());return output;
    }
    @Test void requestedHweFilteringRunsInTheChosenBinaryScope()throws Exception {
        files(false,false);var control=DelimitedData.readResult(run("controls","--min-hwe-p",".2","--hwe-filter-scope","controls"));var row=control.rows().get(0);assertEquals("ok",row[control.column("status")]);assertEquals("6",row[control.column("hwe_filter_n")]);assertEquals("controls",row[control.column("hwe_filter_scope")]);assertEquals("12",row[control.column("n_samples")]);assertEquals("6",row[control.column("n_cases")]);
        var cases=DelimitedData.readResult(run("cases","--min-hwe-p",".2","--hwe-filter-scope","cases"));assertEquals("filtered",cases.rows().get(0)[cases.column("status")]);assertTrue(cases.rows().get(0)[cases.column("filter_reason")].contains("BELOW_MINIMUM_HWE_P"));assertTrue(cases.rows().get(0)[cases.column("p_value")].isBlank());
    }
    @Test void sourcePanelIntersectsAnalyzedSamplesAndIsRecordedWithItsHash()throws Exception {
        files(false,true);Path panel=root.resolve("panel.ids");Files.writeString(panel,"a0\na1\na2\na3\na4\na5\n");Path file=run("panel","--min-hwe-p",".2","--hwe-sample-file",panel.toString());var table=DelimitedData.readResult(file);var row=table.rows().get(0);assertEquals("11",row[table.column("n_samples")]);assertEquals("5",row[table.column("n_controls")]);assertEquals("5",row[table.column("hwe_filter_n")]);assertEquals("6",row[table.column("n_cases")]);
        String manifest=Files.readString(Path.of(file+".manifest.json"));assertTrue(manifest.contains(FollowupSupport.hash(panel)),manifest);
    }
    @Test void unknownReferencePanelIdsFailBeforeAnyEstimateIsWritten()throws Exception {
        files(false,false);Path panel=root.resolve("bad.ids");Files.writeString(panel,"other-cohort\n");var errors=new ByteArrayOutputStream();int status=JLinAlgCli.run(new String[]{"--omics",root.resolve("genotype.vcf").toString(),"--pheno",root.resolve("phenotype.tsv").toString(),"--id","id","--formula","y~<omics>","--model","ols","--omics-type","gwas","--hwe-sample-file",panel.toString(),"--out",root.resolve("bad.tsv").toString(),"--no-log","--no-config"},new PrintStream(new ByteArrayOutputStream()),new PrintStream(errors));assertNotEquals(0,status);assertTrue(errors.toString().contains("absent from the genotype source"));assertFalse(Files.exists(root.resolve("bad.tsv")));
    }
    @Test void studyAdapterPassesTextCodingAndRetainsAggregateQcMetadata()throws Exception {
        files(true,true);var association=Map.of("model","glm","family","binomial","case_value","disease","control_value","healthy","minimum_mac",0,"minimum_hwe_p",.2,"hwe_filter_scope","controls");
        var study=Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(Map.of("id","a","type","gwas","stage","processed_matrix","genome_build","GRCh38","files",Map.of("matrix","genotype.vcf","phenotype","phenotype.tsv"),"columns",Map.of("sample_id","id"))),"hypotheses",List.of(Map.of("id","H","outcome","y","covariates",List.of("z"),"analysis",Map.of("association",association,"harmonization",Map.of("effect_outliers",Map.of("enabled",false))))));
        Path output=Files.createDirectory(root.resolve("study"));var result=new StudyCliExecutor().execute(new Context(root,output,Map.of("id","association","type","association","hypothesis","H","dataset_id","a","datasets",List.of("a")),study,Map.of(),Map.of()));assertEquals(State.COMPLETE,result.state(),result.summary());
        var table=DelimitedData.readResult(output.resolve("standard-results/a.tsv"));assertEquals(1,table.rows().size());var row=table.rows().get(0);assertEquals("11",row[table.column("n")]);assertEquals("6",row[table.column("n_cases")]);assertEquals("5",row[table.column("n_controls")]);assertEquals("5",row[table.column("called_controls")]);assertEquals("controls",row[table.column("hwe_filter_scope")]);assertEquals("hard_call",row[table.column("genotype_origin")]);assertEquals("observed_diploid",row[table.column("ploidy_status")]);
        assertTrue(StudyPublicEvidence.map(result.details().get("method_report")).toString().contains("--case-value"));
    }
    @Test void knownHaploidCallsAreRetainedAsQcExclusionsWithTheirTrueAlleleDenominator()throws Exception {
        files(false,false);Path file=root.resolve("genotype.vcf");String data=Files.readString(file);data=data.replace("GT\t0/0\t0/0","GT\t0\t0/0");Files.writeString(file,data);var table=DelimitedData.readResult(run("ploidy"));var row=table.rows().get(0);assertEquals("filtered",row[table.column("status")]);assertTrue(row[table.column("filter_reason")].contains("NON_DIPLOID_GENOTYPES"));assertEquals("23.0",row[table.column("allele_number")]);assertEquals("1",row[table.column("non_diploid_samples")]);assertEquals("observed_non_diploid",row[table.column("ploidy_status")]);
    }
    @Test void manualAssociationKeepsItsDeclaredScopeAndAutomaticModeStillSchedulesRareTests()throws Exception {
        files(false,false);
        for(String mode:List.of("manual","auto")) {
            var study=Map.of("schema_version",1,"project",Map.of("id","qc-scope","title","QC scope","description","Synthetic manual versus automatic protocol."),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),
                "datasets",List.of(Map.of("id","a","type","gwas","stage","processed_matrix","genome_build","GRCh38","files",Map.of("matrix","genotype.vcf","phenotype","phenotype.tsv"))),
                "hypotheses",List.of(Map.of("id","H","description","Synthetic genotype scope.","outcome","y","analysis",Map.of("mode",mode,"goals",List.of("association"),"association",Map.of("model","ols")))));
            Path file=root.resolve(mode+".yaml");StudyPublicEvidence.write(file,study);var resolved=org.jlinalg.study.StudyResolver.resolve(file);var plan=org.jlinalg.study.StudyPlanner.compile(resolved);
            var operations=StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
            assertEquals(mode.equals("auto"),operations.stream().anyMatch(op->"rare_variant_analysis".equals(op.get("type"))));
        }
    }
}
