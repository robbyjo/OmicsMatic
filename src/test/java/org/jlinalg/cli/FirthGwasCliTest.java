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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirthGwasCliTest {
    @TempDir Path root;
    private static final Path REFERENCE=Path.of("src/test/resources/firth-logistic");
    private Path fixture(String name)throws Exception {
        Path folder=Files.createDirectory(root.resolve(name));var input=DelimitedData.read(REFERENCE.resolve(name+".tsv"));int p=input.header().size()-2;
        StringBuilder pheno=new StringBuilder("id\ty\tz\to\tw\n");
        for(int i=input.rows().size()-1;i>=0;i--){var row=input.rows().get(i);pheno.append("a").append(i).append('\t').append(row[0].equals("1")?"disease":"healthy").append('\t').append(p==3?row[3]:"0").append('\t').append(row[1]).append("\t2\n");}
        Files.writeString(folder.resolve("phenotype.tsv"),pheno);
        StringBuilder vcf=new StringBuilder("##fileformat=VCFv4.2\n##FORMAT=<ID=DS,Number=1,Type=Float,Description=\"Dosage\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
        for(int i=0;i<input.rows().size();i++)vcf.append("\ta").append(i);
        vcf.append("\n1\t100\tv\tA\tG\t.\tPASS\t.\tDS");for(var row:input.rows())vcf.append('\t').append(row[row.length-1]);
        vcf.append("\n1\t200\tlow-mac\tA\tG\t.\tPASS\t.\tDS");for(int i=0;i<input.rows().size();i++)vcf.append(i<19?"\t1":"\t0");vcf.append('\n');
        Files.writeString(folder.resolve("genotype.vcf"),vcf);return folder;
    }
    private List<String> arguments(Path folder,String output)throws Exception {
        boolean z=DelimitedData.read(REFERENCE.resolve(folder.getFileName()+".tsv")).header().size()==5;
        return new ArrayList<>(List.of("--omics",folder.resolve("genotype.vcf").toString(),"--pheno",folder.resolve("phenotype.tsv").toString(),"--id","id",
            "--formula","y~"+(z?"z+":"")+"offset(o)+<omics>","--model","glm","--family","binomial","--binary-test","firth-lrt",
            "--case-value","disease","--control-value","healthy","--out",folder.resolve(output).toString(),"--threads","2","--no-log","--no-config"));
    }
    private String run(List<String> arguments,int expected) {
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int status=JLinAlgCli.run(arguments.toArray(String[]::new),stream,stream);if(expected<0)assertNotEquals(0,status,bytes.toString());else assertEquals(expected,status,bytes.toString());return bytes.toString();
    }
    @Test void packagedRouteMatchesReferenceFitsAndKeepsQcAndLrtIdentity()throws Exception {
        var references=DelimitedData.read(REFERENCE.resolve("reference.tsv"));
        for(String name:List.of("balanced","complete-separation","nuisance-separation","imbalanced","offset","dosage")) {
            Path folder=fixture(name);run(arguments(folder,"results.tsv"),0);var table=DelimitedData.read(folder.resolve("results.tsv"));assertEquals(2,table.rows().size());var row=table.rows().get(0);
            var expected=references.rows().stream().filter(r->r[0].equals(name)).findFirst().orElseThrow();
            assertEquals("ok",row[table.column("status")],name);near(expected[2],row[table.column("beta")],2e-6);near(expected[3],row[table.column("standard_error")],2e-6);near(expected[7],row[table.column("p_value")],2e-7);
            assertEquals("firth_penalized_likelihood_ratio",row[table.column("test_method")]);assertEquals("chi_square",row[table.column("statistic_type")]);assertEquals("true",row[table.column("fit_converged")]);assertEquals("true",row[table.column("null_fit_converged")]);
            assertEquals("filtered",table.rows().get(1)[table.column("status")]);assertTrue(table.rows().get(1)[table.column("filter_reason")].contains("BELOW_MINIMUM_MAC"));assertEquals("",table.rows().get(1)[table.column("test_method")]);
            assertTrue(Files.readString(Path.of(folder.resolve("results.tsv")+".manifest.json")).contains("asymptotic-chi-square-1df"));
        }
    }
    @Test void nonconvergedFitsRetainSourceCountsAndHaveNoPValueOrFdr()throws Exception {
        Path folder=fixture("complete-separation");var args=arguments(folder,"limited.tsv");args.addAll(List.of("--firth-max-iterations","1"));run(args,0);
        var table=DelimitedData.read(folder.resolve("limited.tsv"));var row=table.rows().get(0);assertEquals("failed",row[table.column("status")]);assertEquals("false",row[table.column("fit_converged")]);assertEquals("120",row[table.column("n_samples")]);assertEquals("24",row[table.column("n_cases")]);
        assertEquals("",row[table.column("p_value")]);assertEquals("",row[table.column("fdr_bh")]);assertTrue(row[table.column("convergence_reason")].contains("iteration_limit"));
    }
    @Test void missingDosageIsMeanImputedWithoutChangingSourceCounts()throws Exception {
        Path folder=fixture("dosage");Path vcf=folder.resolve("genotype.vcf");var lines=Files.readAllLines(vcf);
        for(int i=0;i<lines.size();i++)if(lines.get(i).startsWith("1\t100\t")){String[] cells=lines.get(i).split("\t");cells[9]=".";lines.set(i,String.join("\t",cells));}
        Files.write(vcf,lines);run(arguments(folder,"missing.tsv"),0);var table=DelimitedData.read(folder.resolve("missing.tsv"));var row=table.rows().get(0);
        assertEquals("ok",row[table.column("status")]);assertEquals("160",row[table.column("n_samples")]);assertEquals("159",row[table.column("called")]);assertEquals("1",row[table.column("missing")]);
        assertTrue(Double.isFinite(Double.parseDouble(row[table.column("log_p_value")])));
    }
    @Test void studyAdapterRetainsFirthMetadataAndItsMandatoryCaveat()throws Exception {
        Path folder=fixture("complete-separation");var association=Map.of("model","glm","family","binomial","binary_test","firth-lrt","case_value","disease","control_value","healthy");
        var study=Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(Map.of("id","a","type","gwas","stage","processed_matrix","genome_build","GRCh38","files",Map.of("matrix","genotype.vcf","phenotype","phenotype.tsv"),"columns",Map.of("sample_id","id"))),
            "hypotheses",List.of(Map.of("id","H","outcome","y","analysis",Map.of("association",association,"harmonization",Map.of("effect_outliers",Map.of("enabled",false))))));
        Path output=Files.createDirectory(folder.resolve("study"));var result=new StudyCliExecutor().execute(new Context(folder,output,Map.of("id","association","type","association","hypothesis","H","dataset_id","a","datasets",List.of("a")),study,Map.of(),Map.of()));
        assertEquals(org.jlinalg.study.StudyOperationExecutor.State.COMPLETE,result.state(),result.summary());var standard=DelimitedData.read(output.resolve("standard-results/a.tsv"));assertEquals(1,standard.rows().size());var row=standard.rows().get(0);
        assertEquals("firth_jeffreys_logit",row[standard.column("estimator")]);assertEquals("ordinary_fisher_at_penalized_estimate",row[standard.column("standard_error_method")]);assertEquals("chi_square",row[standard.column("statistic_type")]);assertEquals("true",row[standard.column("fit_converged")]);
        assertTrue(result.details().get("required_manuscript_caveat").toString().contains("not a Wald test"));assertTrue(result.details().get("required_manuscript_caveat").toString().contains("No finite-sample"));
    }
    @Test void incompatibleModelsAndControlsFailBeforePublishingResults()throws Exception {
        Path folder=fixture("complete-separation");
        for(List<String> extras:List.of(List.of("--family","poisson"),List.of("--conditional-gwas-summary","--score-genome-build","GRCh38"),List.of("--individual-id","id"),List.of("--backend","gpu"),List.of("--model","ols"))) {
            var args=arguments(folder,"invalid"+extras.hashCode()+".tsv");args.addAll(extras);String console=run(args,-1);assertFalse(Files.exists(folder.resolve("invalid"+extras.hashCode()+".tsv")));
        }
    }
    @Test void missingRegisteredModelStillReachesTheModelReviewGate() {
        assertDoesNotThrow(()->org.jlinalg.study.StudyWorkflowPolicy.validateAssociation(Map.of("family","binomial","binary_test","firth-lrt")));
        assertThrows(IllegalArgumentException.class,()->org.jlinalg.study.StudyWorkflowPolicy.validateAssociation(Map.of("family","probit","model","glm","binary_test","firth-lrt")));
        assertThrows(IllegalArgumentException.class,()->org.jlinalg.study.StudyWorkflowPolicy.validateAssociation(Map.of("family","binomial","model","glm","firth_maximum_iterations",1)));
    }
    private static void near(String expected,String actual,double tolerance){double value=Double.parseDouble(expected);assertEquals(value,Double.parseDouble(actual),Math.max(1e-10,Math.abs(value)*tolerance));}
}
