/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class RegenieToolsCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path directory;
    private String output;
    private Path fixture(String name)throws Exception{return Path.of(getClass().getResource("/regenie/"+name).toURI());}
    private String runtime(){String value=System.getenv("JLINALG_REGENIE_HOME");assumeTrue(value!=null,"Set JLINALG_REGENIE_HOME for real pinned REGENIE model tests");return value;}
    private int run(String method,String name,String format,String genotype,String... extra)throws Exception {
        var args=new ArrayList<>(List.of("regenie-tools","--method",method,"--runtime",runtime(),"--format",format,"--genotypes",genotype,"--out",directory.resolve(name).toString(),"--no-config"));
        for(String[] option:new String[][]{{"--allele-order",format.equals("pgen")?"pvar":"last"},{"--genome-build","b37"},{"--block-size","100"}})if(!List.of(extra).contains(option[0]))args.addAll(List.of(option));args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();
        if(code!=0)for(String file:List.of("WARNINGS.md","upstream/analysis.log","partial/upstream/analysis.log","version.log")){Path p=directory.resolve(name).resolve(file);if(Files.isRegularFile(p))output+="\n"+Files.readString(p);}return code;
    }
    private Path fit(String name,String trait,String coding,String phenotype,String genotype)throws Exception {
        var args=new ArrayList<>(List.of("--phenotypes",phenotype,"--phenotype-columns","Y1","--trait",trait,"--rint","false","--missing","per-trait","--covariates",fixture("covariates.txt").toString(),"--covariate-columns","V1,V2,V3"));if(trait.equals("binary"))args.addAll(List.of("--binary-coding",coding));
        assertEquals(0,run("fit",name,"bed",genotype,args.toArray(String[]::new)),output);return directory.resolve(name);
    }
    private Path fit(String name,boolean binary)throws Exception{return fit(name,binary?"binary":"quantitative","0/1",fixture(binary?"phenotype_bin.txt":"phenotype.txt").toString(),fixture("example.bed").resolveSibling("example").toString());}
    private Map<String,Map<String,String>> table(Path file)throws Exception {
        var result=new LinkedHashMap<String,Map<String,String>>();var lines=Files.readAllLines(file).stream().filter(s->!s.startsWith("##")).toList();var header=List.of(lines.get(0).strip().split("\\s+"));
        for(String line:lines.subList(1,lines.size())){String[] cells=line.strip().split("\\s+");var row=new LinkedHashMap<String,String>();for(int i=0;i<header.size();i++)row.put(header.get(i),cells[i]);assertNull(result.put(row.get("ID")+"/"+row.get("TEST"),row));}return result;
    }
    private void sameResults(Path expected,Path actual)throws Exception {
        var first=table(expected);var second=table(actual);assertEquals(first.keySet(),second.keySet());
        for(String id:first.keySet()){
            for(String key:List.of("CHROM","GENPOS","ALLELE0","ALLELE1","N","TEST"))assertEquals(first.get(id).get(key),second.get(id).get(key),id+" "+key);
            for(String key:List.of("BETA","SE","LOG10P")){String a=first.get(id).get(key),b=second.get(id).get(key);if(a.equals("NA")||b.equals("NA"))assertEquals(a,b,id+" "+key);else assertEquals(Double.parseDouble(a),Double.parseDouble(b),2e-5*Math.max(1,Math.abs(Double.parseDouble(a))),id+" "+key);}
        }
    }
    private String[] testArgs(Path model,String... extra){var args=new ArrayList<>(List.of("--model",model.toString(),"--test","additive","--min-mac","1","--correction","none"));args.addAll(List.of(extra));return args.toArray(String[]::new);}
    @Test void unavailableRuntimeSkipsWithRecoveryAndInvalidOptionsDoNotLaunch()throws Exception {
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);
        String[] args={"regenie-tools","--method","fit","--runtime",directory.resolve("missing").toString(),"--format","bed","--genotypes","absent","--allele-order","last","--genome-build","b37","--block-size","100","--phenotypes","absent","--phenotype-columns","Y1","--trait","quantitative","--rint","false","--missing","complete","--out",directory.resolve("skip").toString(),"--no-config"};
        assertEquals(3,JLinAlgCli.run(args,stream,stream),bytes.toString());assertTrue(Files.readString(directory.resolve("skip/WARNINGS.md")).contains("install_regenie.py --allow-download"));assertTrue(bytes.toString().contains("SKIPPED"));assertFalse(Files.exists(directory.resolve("skip/results.tsv")));
        args[Arrays.asList(args).indexOf("100")]="0";args[Arrays.asList(args).indexOf(directory.resolve("skip").toString())]=directory.resolve("bad-option").toString();assertEquals(2,JLinAlgCli.run(args,stream,stream));assertFalse(Files.exists(directory.resolve("bad-option")));
    }
    @Test void realQuantitativeFitAndAllThreeFormatsMatchFrozenDirectUpstreamResults()throws Exception {
        Path model=fit("quantitative model with spaces",false);assertTrue(Files.isRegularFile(model.resolve("regenie-model.json")));assertFalse(Files.readString(model.resolve("predictions.list")).contains("\r"));
        for(String format:List.of("bed","bgen","pgen")){
            Path genotype=fixture(format.equals("bgen")?"example.bgen":"example.bed");if(!format.equals("bgen"))genotype=genotype.resolveSibling("example");
            assertEquals(0,run("test","qt-"+format,format,genotype.toString(),testArgs(model)),output);assertFalse(Files.readString(directory.resolve("qt-"+format+"/upstream/predictions.list")).contains("\r"));sameResults(fixture("reference-qt.regenie"),directory.resolve("qt-"+format+"/upstream/assoc_Y1.regenie"));
        }
    }
    @Test void realBinaryCodingZeroOneAndOneTwoGiveIdenticalFirthResults()throws Exception {
        Path model01=fit("binary01",true);Path coded=directory.resolve("binary12.txt");var lines=Files.readAllLines(fixture("phenotype_bin.txt"));
        var converted=new ArrayList<String>();converted.add(lines.get(0));for(String line:lines.subList(1,lines.size())){String[] f=line.split("\\s+");for(int i=2;i<f.length;i++)f[i]=Integer.toString(Integer.parseInt(f[i])+1);converted.add(String.join(" ",f));}Files.write(coded,converted);
        Path model12=fit("binary12","binary","1/2",coded.toString(),fixture("example.bed").resolveSibling("example").toString());
        for(Path model:List.of(model01,model12))for(String format:List.of("bed","bgen","pgen")){
            String name="tested-"+model.getFileName()+"-"+format;String genotype=format.equals("bgen")?fixture("example.bgen").toString():fixture("example.bed").resolveSibling("example").toString();
            assertEquals(0,run("test",name,format,genotype,"--model",model.toString(),"--test","additive","--min-mac","1","--correction","firth-approx","--p-threshold","0.999"),output);sameResults(fixture("reference-binary.regenie"),directory.resolve(name+"/upstream/assoc_Y1.regenie"));
        }
    }
    @Test void realRgcGeneTestsRetainMetadataSkatAcatSbatAndGeneP()throws Exception {
        Path model=fit("genes-fit","quantitative","none",fixture("phenotype.txt").toString(),fixture("example_3chr.bed").resolveSibling("example_3chr").toString());
        assertEquals(0,run("genes","genes","bgen",fixture("example_3chr.bgen").toString(),"--sample",fixture("example_3chr.sample").toString(),"--model",model.toString(),"--test","additive","--min-mac","5","--correction","none","--annotations",fixture("example_3chr.annotations").toString(),"--set-list",fixture("example_3chr.setlist").toString(),"--masks",fixture("example_3chr.masks").toString(),"--mask-rule","max","--aaf-bins","0.01,0.05,0.2","--vc-max-aaf","0.2","--gene-tests","rgc","--sbat-draws","10"),output);
        Path raw=directory.resolve("genes/upstream/assoc_Y1.regenie");assertTrue(Files.readString(raw).startsWith("##MASKS="));sameResults(fixture("reference-genes.regenie"),raw);String session=Files.readString(directory.resolve("genes/session.yaml"));for(String test:List.of("SKAT","ACAT","SBAT","GENE_P"))assertTrue(session.contains(test));
    }
    @Test void realModelTamperingAndWrongBuildFailBeforeAssociationAndPreserveSources()throws Exception {
        Path model=fit("sealed",false);String original=Files.readString(model.resolve("phenotypes.tsv"));
        Files.writeString(model.resolve("phenotypes.tsv"),original+" ");assertEquals(2,run("test","tampered","bed",fixture("example.bed").resolveSibling("example").toString(),testArgs(model)),output);assertTrue(output.contains("Sealed model file"));assertFalse(Files.exists(directory.resolve("tampered/upstream/analysis.log")));assertFalse(Files.exists(directory.resolve("tampered/results.tsv")));assertTrue(Files.readString(model.resolve("phenotypes.tsv")).endsWith(" "));
        Files.writeString(model.resolve("phenotypes.tsv"),original);assertEquals(2,run("test","wrong-build","bed",fixture("example.bed").resolveSibling("example").toString(),testArgs(model,"--genome-build","b38")),output);assertTrue(output.contains("declared genome build"));
    }
    @Test void realDeclaredQuantitativeBinaryValuesDoNotEnableLogisticFlags()throws Exception {
        Path model=fit("forced-qt","quantitative","none",fixture("phenotype_bin.txt").toString(),fixture("example.bed").resolveSibling("example").toString());
        String command=Files.readString(model.resolve("upstream/analysis.log"));assertTrue(command.contains("--force-qt"));assertFalse(command.contains("--bt"));assertTrue(Files.readString(model.resolve("WARNINGS.md")).contains("FEW QUANTITATIVE VALUES"));
        assertEquals(2,run("test","wrong-correction","bed",fixture("example.bed").resolveSibling("example").toString(),"--model",model.toString(),"--test","additive","--min-mac","1","--correction","spa","--p-threshold","0.05"),output);assertTrue(output.contains("declared quantitative"));
    }
    @Test void realIndependentSignalRecoversTheKnownAdditiveEffect()throws Exception {
        int n=500,m=100;var random=new Random(8617);int[][] dosages=new int[m][n];for(int j=0;j<m;j++)for(int i=0;i<n;i++)dosages[j][i]=(random.nextDouble()<.3?1:0)+(random.nextDouble()<.3?1:0);
        Path prefix=directory.resolve("independent");var fam=new StringBuilder();var bim=new StringBuilder();var pheno=new StringBuilder("FID IID Y1\n");for(int i=0;i<n;i++){fam.append(i).append(' ').append(i).append(" 0 0 0 -9\n");pheno.append(i).append(' ').append(i).append(' ').append(1.2+.7*dosages[0][i]+.25*random.nextGaussian()).append('\n');}for(int j=0;j<m;j++)bim.append("1 v").append(j).append(" 0 ").append(j+1).append(" A C\n");
        Files.writeString(Path.of(prefix+".fam"),fam);Files.writeString(Path.of(prefix+".bim"),bim);Path phenotype=directory.resolve("truth.txt");Files.writeString(phenotype,pheno);
        try(var bytes=Files.newOutputStream(Path.of(prefix+".bed"))){bytes.write(new byte[]{0x6c,0x1b,1});for(int[] variant:dosages)for(int i=0;i<n;i+=4){int value=0;for(int k=0;k<4&&i+k<n;k++)value|=(variant[i+k]==0?3:variant[i+k]==1?2:0)<<(2*k);bytes.write(value);}}
        assertEquals(0,run("fit","truth-fit","bed",prefix.toString(),"--phenotypes",phenotype.toString(),"--phenotype-columns","Y1","--trait","quantitative","--rint","false","--missing","complete"),output);
        assertEquals(0,run("test","truth-test","bed",prefix.toString(),testArgs(directory.resolve("truth-fit"))),output);var causal=table(directory.resolve("truth-test/upstream/assoc_Y1.regenie")).get("v0/ADD");assertNotNull(causal);assertEquals(.7,Double.parseDouble(causal.get("BETA")),.04);assertTrue(Double.parseDouble(causal.get("LOG10P"))>50);assertEquals("500",causal.get("N"));
    }
    @Test void realAlleleReversalAndDominantRecessiveTestsRetainExplicitConventions()throws Exception {
        Path model=fit("m",false);String genotype=fixture("example.bed").resolveSibling("example").toString();
        assertEquals(0,run("test","first","bed",genotype,testArgs(model,"--allele-order","first")),output);var first=table(directory.resolve("first/upstream/assoc_Y1.regenie"));var last=table(fixture("reference-qt.regenie"));
        for(String id:last.keySet()){assertEquals(last.get(id).get("ALLELE0"),first.get(id).get("ALLELE1"));assertEquals(-Double.parseDouble(last.get(id).get("BETA")),Double.parseDouble(first.get(id).get("BETA")),2e-5);assertEquals(Double.parseDouble(last.get(id).get("LOG10P")),Double.parseDouble(first.get(id).get("LOG10P")),2e-5);}
        for(String test:List.of("dominant","recessive")){
            assertEquals(0,run("test",test,"pgen",genotype,"--model",model.toString(),"--test",test,"--min-mac","1","--correction","none"),output);assertTrue(table(directory.resolve(test+"/upstream/assoc_Y1.regenie")).values().stream().allMatch(row->row.get("TEST").equals(test.equals("dominant")?"DOM":"REC")));
        }
    }
    @Test void realBinaryExactFirthAndSpaProduceFiniteTestsAndRetainedFlags()throws Exception {
        Path model=fit("m",true);String genotype=fixture("example.bed").resolveSibling("example").toString();
        for(String correction:List.of("firth","spa")){
            var extra=new ArrayList<>(List.of("--model",model.toString(),"--test","additive","--min-mac","1","--correction",correction,"--p-threshold","0.999"));if(correction.equals("firth"))extra.addAll(List.of("--firth-se","true"));
            assertEquals(0,run("test",correction,"bed",genotype,extra.toArray(String[]::new)),output);assertEquals(1000,table(directory.resolve(correction+"/upstream/assoc_Y1.regenie")).size());String log=Files.readString(directory.resolve(correction+"/upstream/analysis.log"));assertTrue(log.contains(correction.equals("firth")?"--firth-se":"--spa"));
        }
    }
    @Test void realCategoricalCovariatesRintAndMissingPoliciesRetainDifferentSampleUniverses()throws Exception {
        var original=Files.readAllLines(fixture("phenotype.txt"));var pheno=new ArrayList<String>();pheno.add(original.get(0));for(int i=1;i<original.size();i++){String[] row=original.get(i).strip().split("\\s+");if(i==1)row[2]="NA";if(i==2)row[3]="NA";pheno.add(String.join(" ",row));}Path phenotype=directory.resolve("missing.txt");Files.write(phenotype,pheno);
        Path covariates=directory.resolve("categorical.txt");var cov=new StringBuilder("FID IID batch\n");for(int i=1;i<=500;i++)cov.append(i).append(' ').append(i).append(i%2==0?" B\n":" A\n");Files.writeString(covariates,cov);
        String genotype=fixture("example.bed").resolveSibling("example").toString();
        for(String missing:List.of("complete","per-trait")){
            assertEquals(0,run("fit","fit-"+missing,"bed",genotype,"--phenotypes",phenotype.toString(),"--phenotype-columns","Y1,Y2","--trait","quantitative","--rint","true","--missing",missing,"--covariates",covariates.toString(),"--covariate-columns","batch","--categorical","batch"),output);
            assertEquals(0,run("test","test-"+missing,"bed",genotype,testArgs(directory.resolve("fit-"+missing))),output);
            for(String trait:List.of("Y1","Y2")){var rows=table(directory.resolve("test-"+missing+"/upstream/assoc_"+trait+".regenie"));assertTrue(rows.values().stream().allMatch(row->row.get("N").equals(missing.equals("complete")?"498":"499")));}
            assertTrue(Files.readString(directory.resolve("test-"+missing+"/WARNINGS.md")).contains("SAMPLE EXCLUSIONS"));assertTrue(Files.readString(directory.resolve("test-"+missing+"/upstream/analysis.log")).contains("--apply-rint"));
        }
    }
    @Test void realBurdenMaskRulesAndRecessiveRgcRetainOmissionWarnings()throws Exception {
        Path model=fit("m","quantitative","none",fixture("phenotype.txt").toString(),fixture("example_3chr.bed").resolveSibling("example_3chr").toString());String genotype=fixture("example_3chr.bed").resolveSibling("example_3chr").toString();
        for(String rule:List.of("sum","comphet","max")){
            String test=rule.equals("max")?"recessive":"additive",strategy=rule.equals("max")?"rgc":"burden";
            assertEquals(0,run("genes",rule,"bed",genotype,"--model",model.toString(),"--test",test,"--min-mac","5","--correction","none","--annotations",fixture("example_3chr.annotations").toString(),"--set-list",fixture("example_3chr.setlist").toString(),"--masks",fixture("example_3chr.masks").toString(),"--mask-rule",rule,"--aaf-bins","0.01,0.05,0.2","--vc-max-aaf","0.2","--gene-tests",strategy),output);
            assertFalse(table(directory.resolve(rule+"/upstream/assoc_Y1.regenie")).isEmpty());String warnings=Files.readString(directory.resolve(rule+"/WARNINGS.md"));if(rule.equals("comphet"))assertTrue(warnings.contains("not proof"));if(rule.equals("max"))assertTrue(warnings.contains("SKATO/ACATV will be skipped"));
        }
    }
}
