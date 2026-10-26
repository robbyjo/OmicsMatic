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
import org.jlinalg.study.StudyHumanPloidyPolicy;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HumanPloidyCliTest {
    @TempDir Path root;
    private void files(boolean scaled,boolean missingCovariate,boolean binary)throws Exception {
        var phenotype=new StringBuilder("id\ty\tage\n");var complements=new StringBuilder("id\tchromosome_complement\n");
        for(int i=159;i>=0;i--){double g=i<20?1:0;phenotype.append("s").append(i).append('\t').append(binary?i%3==0?"1":"0":Double.toString(2+.8*g+.12*(i%7)+Math.sin(i*1.4))).append('\t').append(missingCovariate&&i==0?"NA":i%7).append('\n');complements.append("s").append(i).append('\t').append(i<80?"XY":"XX").append('\n');}
        Files.writeString(root.resolve("phenotype.tsv"),phenotype);Files.writeString(root.resolve("ploidy.tsv"),complements);
        var vcf=new StringBuilder("##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");for(int i=0;i<160;i++)vcf.append("\ts").append(i);vcf.append('\n');
        for(String marker:List.of("x20","x19","xflip20","y20","par","y-par-duplicate")) {
            boolean y=marker.startsWith("y"),par=marker.contains("par");vcf.append(y?"Y":"X").append('\t').append(par?10001:3000000).append('\t').append(marker).append("\tA\tG\t.\tPASS\t.\tGT");
            for(int i=0;i<160;i++){int g=par?i%3:i<(marker.equals("x19")?19:20)?1:0;if(marker.equals("xflip20"))g=1-g;vcf.append('\t');if(!par&&i<80&&!scaled)vcf.append(g);else if(!par&&i<80)vcf.append(g==0?"0/0":"1/1");else vcf.append(g==0?"0/0":g==1?"0/1":"1/1");}vcf.append('\n');
        }Files.writeString(root.resolve("genotype.vcf"),vcf);
    }
    private String errors="";
    private int run(String name,String region,String...extra)throws Exception {
        var args=new ArrayList<>(List.of("--omics",root.resolve("genotype.vcf").toString(),"--pheno",root.resolve("phenotype.tsv").toString(),"--id","id","--formula","y~age+<omics>","--model","ols","--omics-type","gwas","--out",root.resolve(name+".tsv").toString(),"--human-ploidy-region",region,"--human-ploidy-build","GRCh38","--ploidy-samples",root.resolve("ploidy.tsv").toString(),"--threads","1","--no-config","--no-log"));args.addAll(List.of(extra));var out=new ByteArrayOutputStream();int status=JLinAlgCli.run(args.toArray(String[]::new),new PrintStream(new ByteArrayOutputStream()),new PrintStream(out));errors=out.toString();return status;
    }
    private Map<String,String[]> rows(Path path)throws Exception {var t=DelimitedData.readResult(path);var result=new LinkedHashMap<String,String[]>();for(var r:t.rows())result.put(r[t.column("id")],r);return result;}
    @Test void haploidMac20AndEffectsUseOnlyTheRegisteredCompleteCasePopulation()throws Exception {
        files(false,false,false);assertEquals(0,run("x","x_nonpar_xy"),errors);Path file=root.resolve("x.tsv");var t=DelimitedData.readResult(file);var rows=rows(file);assertEquals(3,rows.size());assertEquals("ok",rows.get("x20")[t.column("status")]);assertEquals("filtered",rows.get("x19")[t.column("status")]);assertEquals("80.0",rows.get("x20")[t.column("allele_number")]);assertEquals("20.0",rows.get("x20")[t.column("minor_allele_count")]);assertEquals("80",rows.get("x20")[t.column("n_samples")]);
        double beta=Double.parseDouble(rows.get("x20")[t.column("beta")]);assertEquals(-beta,Double.parseDouble(rows.get("xflip20")[t.column("beta")]),1e-10);assertEquals(Double.parseDouble(rows.get("x20")[t.column("p_value")]),Double.parseDouble(rows.get("xflip20")[t.column("p_value")]),1e-10);
        assertTrue(Files.readString(Path.of(file+".human-ploidy.yaml")).contains("scanned_source_variants: 6"));
        files(false,true,false);assertEquals(0,run("missing","x_nonpar_xy"),errors);var m=DelimitedData.readResult(root.resolve("missing.tsv"));var r=rows(root.resolve("missing.tsv"));assertEquals("79.0",r.get("x20")[m.column("allele_number")]);assertEquals("19.0",r.get("x20")[m.column("minor_allele_count")]);assertEquals("filtered",r.get("x20")[m.column("status")]);
    }
    @Test void explicitDiploidEncodingMatchesPhysicalCopiesAndCannotSilentlyPassTheObservedPolicy()throws Exception {
        files(true,false,false);assertNotEquals(0,run("wrong","x_nonpar_xy"));assertTrue(errors.contains("Invalid source ploidy"),errors);assertTrue(Files.readString(root.resolve("wrong.tsv.human-ploidy-invalid-calls.tsv")).contains("s0"));
        assertEquals(0,run("scaled","x_nonpar_xy","--haploid-source-scale","diploid"),errors);var table=DelimitedData.readResult(root.resolve("scaled.tsv"));assertEquals("20.0",rows(root.resolve("scaled.tsv")).get("x20")[table.column("minor_allele_count")]);
    }
    @Test void parHweAndYPopulationDoNotCountDuplicatedOrStructurallyAbsentCopies()throws Exception {
        files(false,false,false);assertEquals(0,run("par","par","--min-hwe-p",".00000001"),errors);var par=DelimitedData.readResult(root.resolve("par.tsv"));assertEquals(1,par.rows().size());assertEquals("par",par.rows().get(0)[par.column("id")]);assertEquals("320.0",par.rows().get(0)[par.column("allele_number")]);assertEquals("160",par.rows().get(0)[par.column("hwe_filter_n")]);
        assertEquals(0,run("y","y_nonpar_xy"),errors);var y=DelimitedData.readResult(root.resolve("y.tsv"));assertEquals(1,y.rows().size());assertEquals("80",y.rows().get(0)[y.column("n_samples")]);assertEquals("80.0",y.rows().get(0)[y.column("allele_number")]);
    }
    @Test void unknownAndUnsupportedComplementsRequireAnExplicitExclusionAndStayOutOfRemoteContext()throws Exception {
        files(false,false,false);var path=root.resolve("ploidy.tsv");Files.writeString(path,Files.readString(path).replace("s0\tXY","s0\tXXY"));assertNotEquals(0,run("unknown","x_nonpar_xy"));assertTrue(errors.contains("unknown declared complement"),errors);assertEquals(0,run("exclude","x_nonpar_xy","--unknown-ploidy-samples","exclude"),errors);assertTrue(Files.readString(root.resolve("exclude.tsv.human-ploidy-samples.tsv")).contains("s0\tXXY\tunknown_complement_excluded"));
        var study=study(Map.of("region","x_nonpar_xy","genome_build","GRCh38","unknown_samples","exclude"));String projected=StudyContextReview.inventory(study,"H").toString();assertTrue(projected.contains("x_nonpar_xy"));assertFalse(projected.contains("ploidy.tsv"));assertFalse(projected.contains("s0"));
    }
    private Map<String,Object> study(Map<String,Object> profile) {
        return Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(Map.of("id","a","type","gwas","species","human","stage","processed_matrix","genome_build","GRCh38","files",Map.of("matrix","genotype.vcf","phenotype","phenotype.tsv","ploidy_samples","ploidy.tsv"))),"hypotheses",List.of(Map.of("id","H","outcome","y","covariates",List.of("age"),"analysis",Map.of("association",Map.of("model","ols","human_ploidy",profile),"harmonization",Map.of("effect_outliers",Map.of("enabled",false))))));
    }
    @Test void studyPrefitUsesScopedCallRateAndRetainsLocalAudits()throws Exception {
        files(false,false,false);var study=study(Map.of("region","y_nonpar_xy","genome_build","GRCh38"));Path prefit=Files.createDirectory(root.resolve("prefit"));var qc=new StudyPrefitQc().execute(new Context(root,prefit,Map.of("id","qc","type","participant_qc","hypothesis","H","dataset_id","a","datasets",List.of("a")),study,Map.of(),Map.of()));assertEquals(State.COMPLETE,qc.state(),qc.summary());assertEquals(80,DelimitedData.read(prefit.resolve("analysis-phenotype.tsv")).rows().size());assertEquals(1L,qc.details().get("examined_genotype_variants"));assertTrue(qc.outputs().contains("prefit-callrate.human-ploidy-variants.tsv"));assertTrue(qc.outputs().contains("ploidy-population-selection.tsv"));
        Path association=Files.createDirectory(root.resolve("association"));var result=new StudyCliExecutor().execute(new Context(root,association,Map.of("id","association","type","association","hypothesis","H","dataset_id","a","datasets",List.of("a")),study,Map.of(),Map.of()));assertEquals(State.COMPLETE,result.state(),result.summary());assertTrue(result.outputs().contains("association.tsv.human-ploidy.yaml"));assertTrue(result.details().get("method_report").toString().contains("physical alternate copies"));
    }
    @Test void incompatibleModelsAndUndefinedHweFailBeforeEstimation()throws Exception {
        files(false,false,false);assertNotEquals(0,run("hwe","x_nonpar_xy","--min-hwe-p",".001"));assertTrue(errors.contains("HWE"));assertNotEquals(0,run("mixed","x_nonpar_xy","--model","lmm"));assertTrue(errors.contains("independent OLS"),errors);
        assertThrows(IllegalArgumentException.class,()->StudyHumanPloidyPolicy.resolve(Map.of("model","lmm","human_ploidy",Map.of("region","x_nonpar_xy","genome_build","GRCh38"))));
        var profile=StudyHumanPloidyPolicy.resolve(Map.of("human_ploidy",Map.of("region","par","genome_build","GRCh38")));assertThrows(IllegalArgumentException.class,()->StudyHumanPloidyPolicy.dataset(Map.of("type","gwas","species","mouse","genome_build","GRCh38"),profile));
    }
    @Test void binaryRoutesUseTheSamePhysicalDesignAfterExplicitConversion()throws Exception {
        files(false,false,true);assertEquals(0,run("binary","x_nonpar_xy","--model","glm","--family","binomial","--binary-test","firth-lrt"),errors);var t=DelimitedData.readResult(root.resolve("binary.tsv"));double beta=Double.parseDouble(rows(root.resolve("binary.tsv")).get("x20")[t.column("beta")]);files(true,false,true);assertEquals(0,run("binary-scaled","x_nonpar_xy","--haploid-source-scale","diploid","--model","glm","--family","binomial","--binary-test","firth-lrt"),errors);var u=DelimitedData.readResult(root.resolve("binary-scaled.tsv"));assertEquals(beta,Double.parseDouble(rows(root.resolve("binary-scaled.tsv")).get("x20")[u.column("beta")]),1e-10);
    }
    @Test void ploidyInputsCannotBeOverwrittenByAnAuditSidecar()throws Exception {
        files(false,false,false);Path audit=root.resolve("alias.tsv.human-ploidy-samples.tsv");Files.copy(root.resolve("ploidy.tsv"),audit);String before=FollowupSupport.hash(audit);
        assertNotEquals(0,run("alias","x_nonpar_xy","--ploidy-samples",audit.toString(),"--overwrite"));assertTrue(errors.contains("aliases an input"),errors);assertEquals(before,FollowupSupport.hash(audit));assertFalse(Files.exists(root.resolve("alias.tsv")));
    }
    @Test void nativeRareSetScoresDoNotSilentlyReuseASingleVariantChromosomeModel()throws Exception {
        Path out=Files.createDirectory(root.resolve("rare"));var result=new StudyRareScores().execute(new Context(root,out,Map.of("id","scores","type","rare_variant_scores","hypothesis","H","datasets",List.of("a")),study(Map.of("region","x_nonpar_xy","genome_build","GRCh38")),Map.of(),Map.of()));assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains("single-variant"));assertTrue(Files.exists(out.resolve("request.yaml")));
    }
    @Test void optionalDatasetStageKeepsItsProcessedMatrixDefaultDuringPloidyValidation()throws Exception {
        var config=new LinkedHashMap<>(study(Map.of("region","par","genome_build","GRCh38")));config.put("schema_version",1);config.put("project",Map.of("id","ploidy-default-stage","title","Synthetic optional stage","description","Synthetic metadata validation."));
        var dataset=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(config.get("datasets")).get(0)));dataset.remove("stage");config.put("datasets",List.of(dataset));
        var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(config.get("hypotheses")).get(0)));h.put("description","Synthetic ploidy validation.");h.put("inputs",Map.of("discovery",List.of("a")));config.put("hypotheses",List.of(h));
        Path path=root.resolve("default-stage.yaml");StudyPublicEvidence.write(path,config);var validation=org.jlinalg.study.StudyValidator.validate(path,false);assertTrue(validation.valid(),validation.report());
        dataset.remove("type");var policy=StudyHumanPloidyPolicy.resolve(Map.of("human_ploidy",Map.of("region","par","genome_build","GRCh38")));assertThrows(IllegalArgumentException.class,()->StudyHumanPloidyPolicy.dataset(dataset,policy));
    }
}
