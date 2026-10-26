/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyRiskScorePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyGenotypeScoresTest {
    @TempDir Path root;
    private Map<String,Object> fixture()throws Exception {
        double[][] dosage=new double[2][12];var participants=new StringBuilder("sample_id\tpartition\tfid\tiid\tcohort\tunit\ty\tz\tbase\n");
        for(int i=0;i<12;i++){dosage[0][i]=i%3;dosage[1][i]=(i/3)%3;double score=2*(2-dosage[0][i])-.5*dosage[1][i];participants.append("private-").append(i).append("\tevaluation\tF").append(i/4).append("\tI").append(i%4).append("\tA\tu").append(i).append('\t').append(1+score+.03*Math.sin(i)).append('\t').append(-score+.1*Math.cos(i)).append("\t1\n");}
        Files.writeString(root.resolve("participants.tsv"),participants);bed("A",dosage,"1 source-alias-1 0 10 C A\nchr2 source-alias-2 0 20 T G\n");
        Files.writeString(root.resolve("weights.tsv"),"variant\tchromosome\tposition\tea\toa\tweight\tea_frequency\tgenome_build\nmodel-name-1\tchr1\t10\tA\tC\t2\t.2\tGRCh38\nmodel-name-2\t2\t20\tT\tG\t-.5\t.7\tGRCh38\n");
        var p=new LinkedHashMap<String,Object>();p.put("file","participants.tsv");p.put("mode","weights");p.put("weights","weights.tsv");p.put("outcome","y");p.put("baseline_column","base");p.put("independent_rows",true);p.put("external_independence","Synthetic independently frozen coefficients; no discovery/evaluation overlap");p.put("bootstraps",0);p.put("related_phenotypes",List.of(Map.of("column","z")));
        var g=new LinkedHashMap<String,Object>();g.put("sources",List.of(Map.of("id","A","bfile","A","genome_build","GRCh38")));g.put("weights_genome_build","GRCh38");g.put("prediction_link","identity");g.put("intercept",1.0);g.put("prediction_rationale","Frozen additive source intercept and source-unit quantitative outcome");p.put("genotypes",g);return p;
    }
    private void bed(String name,double[][] dosages,String bim)throws Exception {
        var fam=new StringBuilder();int n=dosages[0].length;for(int i=0;i<n;i++)fam.append("F").append(i/4).append(" I").append(i%4).append(" 0 0 0 -9\n");Files.writeString(root.resolve(name+".fam"),fam);Files.writeString(root.resolve(name+".bim"),bim);
        byte[] bytes=new byte[3+dosages.length*((n+3)/4)];bytes[0]=0x6c;bytes[1]=0x1b;bytes[2]=1;for(int j=0;j<dosages.length;j++)for(int i=0;i<n;i++){double d=dosages[j][i];int code=Double.isNaN(d)?1:d==2?0:d==1?2:3;bytes[3+j*((n+3)/4)+i/4]|=(byte)(code<<(2*(i%4)));}Files.write(root.resolve(name+".bed"),bytes);
    }
    private Context context(String name,Map<String,Object> p)throws Exception{return new Context(root,Files.createDirectory(root.resolve(name)),Map.of("id",name,"type","risk_score_evaluation","hypothesis","H","datasets",List.of()),Map.of("datasets",List.of(),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("risk_score",p)))),Map.of(),Map.of());}
    @SuppressWarnings("unchecked") private Map<String,Object> g(Map<String,Object> p){return (Map<String,Object>)p.get("genotypes");}
    private Result execute(String name,Map<String,Object> p)throws Exception {return new StudyCliExecutor().execute(context(name,p));}
    private void hold(String name,Map<String,Object> p,String message)throws Exception {var r=execute(name,p);assertEquals(State.WAITING_FOR_INPUT,r.state(),r.summary());assertTrue(Files.readString(root.resolve(name+"/genetic-score-local-diagnostic.txt")).contains(message));assertFalse(Files.exists(root.resolve(name+"/risk-score-evaluation.tsv")));assertFalse(Files.exists(root.resolve(name+"/participant-scores.tsv")));}

    @Test void exactCoordinatesAlleleSwapAndCompositeIdsFeedHeldOutAndRelatedFamilies()throws Exception {
        var p=fixture();var hashes=StudyRiskScoreResources.inputHashes(root,context("hash",p).study(),Map.of("type","risk_score_evaluation","hypothesis","H"));var r=execute("native",p);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(false,r.details().get("model_refitted"));var scores=DelimitedData.read(root.resolve("native/participant-scores.tsv"));
        for(int i=0;i<12;i++){double expected=2*(2-i%3)-.5*((i/3)%3);assertEquals(expected,Double.parseDouble(scores.rows().get(i)[2]),0);assertEquals(1+expected,Double.parseDouble(scores.rows().get(i)[3]),0);}
        assertTrue(Files.readString(root.resolve("native/genetic-variant-alignment.tsv")).contains("2-minus-A1"));assertEquals(1,DelimitedData.read(root.resolve("native/related-phenotypes.tsv")).rows().size());
        var c=context("rehash",p);assertEquals(hashes,StudyRiskScoreResources.inputHashes(root,c.study(),c.operation()));byte[] bytes=Files.readAllBytes(root.resolve("A.bed"));bytes[3]^=2;Files.write(root.resolve("A.bed"),bytes);assertNotEquals(hashes,StudyRiskScoreResources.inputHashes(root,c.study(),c.operation()));
    }
    @Test void completeModelAndAmbiguousGenomicKeysHoldInsteadOfPartialScores()throws Exception {
        var p=fixture();String original=Files.readString(root.resolve("A.bim"));Files.writeString(root.resolve("A.bim"),original.replace("20 T G","21 T G"));hold("missing",p,"Complete frozen model");
        Files.writeString(root.resolve("A.bim"),original+"1 duplicate 0 10 A C\n");hold("duplicate",p,"Ambiguous duplicate");Files.writeString(root.resolve("A.bim"),original);Files.writeString(root.resolve("weights.tsv"),Files.readString(root.resolve("weights.tsv"))+"duplicate\t1\t10\tC\tA\t1\t.8\tGRCh38\n");hold("model-duplicate",p,"Duplicate genomic");
    }
    @Test void referenceMeanUsesExternalEffectFrequencyAndParticipantQcIsPrespecified()throws Exception {
        var p=fixture();byte[] bytes=Files.readAllBytes(root.resolve("A.bed"));bytes[3]=(byte)((bytes[3]&~3)|1);Files.write(root.resolve("A.bed"),bytes);hold("missing-error",p,"Missing dosage");g(p).put("missing","reference_mean");g(p).put("frequency_provenance","Independent source reference EA frequencies; no target estimation");g(p).put("minimum_observed_fraction",.5);
        assertEquals(State.COMPLETE,execute("impute",p).state());var t=DelimitedData.read(root.resolve("impute/participant-scores.tsv"));assertEquals(.8,Double.parseDouble(t.rows().get(0)[2]),1e-15);assertTrue(Files.readString(root.resolve("impute/genetic-participant-disposition.tsv")).contains("PARTIALLY_IMPUTED"));
        bytes[5]=(byte)0xff;Files.write(root.resolve("A.bed"),bytes);assertEquals(State.COMPLETE,execute("other-target",p).state());var other=DelimitedData.read(root.resolve("other-target/participant-scores.tsv"));assertEquals(t.rows().get(0)[2],other.rows().get(0)[2]);
        g(p).put("minimum_observed_fraction",1.0);assertEquals(State.COMPLETE,execute("qc",p).state());assertTrue(Files.readString(root.resolve("qc/evaluation-exclusions.tsv")).contains("private-0"));assertFalse(Files.readString(root.resolve("qc/risk-score-analysis.yaml")).contains("private-0"));
    }
    @Test void multipleSourcesDisambiguateSharedIidsAndShareWorkAdmission()throws Exception {
        var p=fixture();for(String ext:List.of("bed","bim","fam"))Files.copy(root.resolve("A."+ext),root.resolve("B."+ext));String s=Files.readString(root.resolve("participants.tsv"));Files.writeString(root.resolve("participants.tsv"),s+s.substring(s.indexOf('\n')+1).replace("private-","cohort-B-private-").replace("\tA\t","\tB\t"));g(p).put("sources",List.of(Map.of("id","A","bfile","A","genome_build","GRCh38"),Map.of("id","B","bfile","B","genome_build","GRCh38")));g(p).put("source_column","cohort");
        assertEquals(State.COMPLETE,execute("multi",p).state());var t=DelimitedData.read(root.resolve("multi/participant-scores.tsv"));assertEquals(24,t.rows().size());for(int i=0;i<12;i++)assertEquals(t.rows().get(i)[2],t.rows().get(i+12)[2]);g(p).put("maximum_cells",30);hold("shared-budget",p,"shared maximum_cells");
        g(p).put("maximum_cells",100);Files.writeString(root.resolve("participants.tsv"),s);hold("empty-source",p,"no active participant");
    }
    @Test void repeatedGenotypesCannotMasqueradeAsIndependentRowsOrCrossPartitions()throws Exception {
        var p=fixture();String s=Files.readString(root.resolve("participants.tsv"));String row=s.split("\n")[1].replace("private-0","repeat-0");Files.writeString(root.resolve("participants.tsv"),s+row+"\n");hold("repeat-independent",p,"Repeated genotype identity");p.put("independent_rows",false);p.put("group_columns",List.of("unit"));p.put("resampling_group","unit");assertEquals(State.COMPLETE,execute("repeat-cluster",p).state());
        Files.writeString(root.resolve("participants.tsv"),s+row.replace("\tevaluation\t","\ttraining\t").replace("\tu0\t","\tu-other\t")+"\n");hold("repeat-cross",p,"Repeated genotype identity");
    }
    @Test void declaredBuildPalindromicAndPredictionContractsAreNotGuessed()throws Exception {
        var p=fixture();g(p).put("weights_genome_build","GRCh37");var invalidBuild=p;assertThrows(IllegalArgumentException.class,()->StudyRiskScorePolicy.resolve(invalidBuild));g(p).put("weights_genome_build","GRCh38");
        Files.writeString(root.resolve("weights.tsv"),Files.readString(root.resolve("weights.tsv")).replace("\tA\tC\t2","\tA\tT\t2"));hold("palindrome",p,"Palindromic");
        p=fixture();g(p).put("prediction_link","none");g(p).remove("intercept");g(p).remove("prediction_rationale");hold("raw-quant",p,"identity prediction");
        g(p).put("prediction_link","identity");g(p).put("intercept",1);g(p).put("prediction_rationale","Frozen source prediction");g(p).put("prediction_linear_column","y");hold("target-leakage",p,"cannot be an outcome");
    }
    @Test void binaryRawAndFrozenLogitHaveDistinctPredictiveMeaning()throws Exception {
        var p=fixture();p.put("family","binary");p.put("related_phenotypes",List.of());String[] lines=Files.readString(root.resolve("participants.tsv")).split("\n");var out=new StringBuilder(lines[0]+"\n");for(int i=1;i<lines.length;i++){String[] f=lines[i].split("\t");f[6]=String.valueOf(i%2);f[8]=".5";out.append(String.join("\t",f)).append('\n');}Files.writeString(root.resolve("participants.tsv"),out);
        g(p).put("prediction_link","none");g(p).remove("intercept");g(p).remove("prediction_rationale");assertEquals(State.COMPLETE,execute("raw",p).state());assertFalse(Files.readString(root.resolve("raw/risk-score-evaluation.tsv")).contains("brier"));
        p.put("prediction_scale","probability");g(p).put("prediction_link","logit");g(p).put("intercept",-2);g(p).put("prediction_rationale","Source log-odds intercept; case/control prevalence caveat applies");assertEquals(State.COMPLETE,execute("logit",p).state());var scores=DelimitedData.read(root.resolve("logit/participant-scores.tsv"));for(String[] row:scores.rows()){double expected=1/(1+Math.exp(2-Double.parseDouble(row[2])));assertEquals(expected,Double.parseDouble(row[3]),1e-15);}
    }
    @Test void sourceScaleRatioTransformNormalizationAndMappedGzipWeightsAreFrozen()throws Exception {
        var p=fixture();String text="### PGS-style synthetic file\n#genome_build=GRCh38\nrsID\thm_chr\thm_pos\teffect_allele\tother_allele\teffect_weight\nmodel1\t1\t10\tA\tC\t"+Math.exp(2)+"\nmodel2\t2\t20\tT\tG\t"+Math.exp(-.5)+"\n";try(var out=new java.util.zip.GZIPOutputStream(Files.newOutputStream(root.resolve("pgs.txt.gz")))){out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}p.put("weights","pgs.txt.gz");g(p).put("weight_columns",Map.of("variant","rsID","chromosome","hm_chr","position","hm_pos","ea","effect_allele","oa","other_allele","weight","effect_weight"));g(p).put("coefficient_scale","odds_ratio");g(p).put("score_center",1);g(p).put("score_scale",2);
        assertEquals(State.COMPLETE,execute("pgs",p).state());var scores=DelimitedData.read(root.resolve("pgs/participant-scores.tsv"));for(int i=0;i<12;i++)assertEquals((2*(2-i%3)-.5*((i/3)%3)-1)/2,Double.parseDouble(scores.rows().get(i)[2]),1e-15);try(var out=new java.util.zip.GZIPOutputStream(Files.newOutputStream(root.resolve("pgs.txt.gz")))){out.write(("#"+"x".repeat(100)+"\n"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8));}g(p).put("maximum_line_bytes",64);hold("line-bound",p,"line exceeds");
    }
    @Test void modelCanExceedDenseFeatureCapAndRetainsLowMacMembers()throws Exception {
        var p=fixture();int count=2101;var weights=new StringBuilder("chromosome\tposition\tea\toa\tweight\n");var bim=new StringBuilder();double[][] dosage=new double[count][12];for(int j=0;j<count;j++){weights.append("1\t").append(j+1).append("\tA\tC\t").append(j<2?1:j==2?.75:j==3?.5:0).append('\n');bim.append("1 v").append(j).append(" 0 ").append(j+1).append(" A C\n");for(int i=0;i<12;i++)dosage[j][i]=j<2?(i+j)%3:j==2&&i==0?1:0;}Files.writeString(root.resolve("weights.tsv"),weights);bed("A",dosage,bim.toString());var r=execute("many",p);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(2101,StudyPublicEvidence.map(r.details().get("genotype_score")).get("model_variants"));assertEquals(2101,DelimitedData.read(root.resolve("many/genetic-variant-alignment.tsv")).rows().size());
    }
    @Test void nonadditiveScoringRowsAreNotSilentlyConvertedAndFindingContextExcludesIdentities()throws Exception {
        var p=fixture();String original=Files.readString(root.resolve("weights.tsv"));String[] lines=original.split("\n");Files.writeString(root.resolve("weights.tsv"),lines[0]+"\tis_dominant\n"+lines[1]+"\ttrue\n"+lines[2]+"\tfalse\n");hold("dominant",p,"Nonadditive");Files.writeString(root.resolve("weights.tsv"),original);
        var c=context("source",p);var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state());var hashes=new LinkedHashMap<String,String>();for(String file:r.outputs())hashes.put(file,FollowupSupport.hash(c.outputDirectory().resolve(file)));StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id","source","operation_type","risk_score_evaluation","hypothesis","H","state","complete","output_sha256",hashes,"details",r.details()));
        var inv=new Context(root,Files.createDirectory(root.resolve("inventory")),Map.of("id","inventory","type","finding_inventory","hypothesis","H"),c.study(),Map.of(),Map.of("score",c.outputDirectory()));new StudyFindingInventory().execute(inv);String json=Files.readString(inv.outputDirectory().resolve("finding-inventory.jsonl"));assertTrue(json.contains("genetic_score_context"));assertTrue(json.contains("complete_in_every_registered_source"));for(String privateText:List.of("private-0","F0","I0","source-alias-","model-name-"))assertFalse(json.contains(privateText),privateText);
    }
}
