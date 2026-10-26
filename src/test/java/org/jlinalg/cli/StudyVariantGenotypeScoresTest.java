/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import com.github.luben.zstd.Zstd;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFIteratorBuilder;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyRiskScorePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyVariantGenotypeScoresTest {
    @TempDir Path root;
    private static final int N=12;
    private String vcf(boolean ds,boolean unknown,boolean reverse){
        var text=new StringBuilder("##fileformat=VCFv4.2\n##contig=<ID=1,length=10000>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n##FORMAT=<ID=DS,Number=1,Type=Float,Description=\"ALT dosage\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");for(int k=0;k<N;k++){int i=reverse?N-1-k:k;text.append("\tI").append(i);}text.append('\n');
        for(int j=0;j<3;j++){text.append("1\t").append(10+j*10).append("\tfile-alias-").append(j).append("\t").append(j==0?"A\tC":"G\tT").append("\t.\t.\t.\t").append(ds?"GT:DS":"GT");for(int k=0;k<N;k++){int i=reverse?N-1-k:k,alt=(i+j)%3;text.append('\t').append(unknown?".":alt==0?"0/0":alt==1?"0/1":"1/1");if(ds)text.append(':').append(alt==2?1.75:alt+.25);}text.append('\n');}return text.toString();
    }
    private Map<String,Object> fixture(String file,boolean ds)throws Exception {
        Files.writeString(root.resolve("input.vcf"),vcf(ds,false,false));var metadata=new StringBuilder("sample_id\tpartition\tiid\ty\tz\tbase\n");for(int i=0;i<N;i++){double alt0=ds?(i%3==2?1.75:i%3+.25):i%3,alt1=ds?((i+1)%3==2?1.75:(i+1)%3+.25):(i+1)%3,score=2*(2-alt0)-.5*alt1;metadata.append("private-").append(i).append("\tevaluation\tI").append(i).append('\t').append(1+score+.05*Math.sin(i)).append('\t').append(-score+.1*Math.cos(i)).append("\t1\n");}Files.writeString(root.resolve("participants.tsv"),metadata);Files.writeString(root.resolve("weights.tsv"),"variant\tchromosome\tposition\tea\toa\tweight\tea_frequency\nmodel1\t1\t10\tA\tC\t2\t.2\nmodel2\t1\t20\tT\tG\t-.5\t.7\n");
        var p=new LinkedHashMap<String,Object>();p.put("mode","weights");p.put("file","participants.tsv");p.put("weights","weights.tsv");p.put("outcome","y");p.put("baseline_column","base");p.put("independent_rows",true);p.put("external_independence","Synthetic fixed model, explicit independent source/evaluation design");p.put("bootstraps",0);p.put("related_phenotypes",List.of(Map.of("column","z")));var g=new LinkedHashMap<String,Object>();g.put("weights_genome_build","GRCh38");g.put("sources",List.of(Map.of("id","A","file",file,"genome_build","GRCh38")));g.put("prediction_link","identity");g.put("intercept",1);g.put("prediction_rationale","Frozen source-unit synthetic intercept");p.put("genotypes",g);return p;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> g(Map<String,Object> p){return (Map<String,Object>)p.get("genotypes");}
    private Context context(String name,Map<String,Object> p)throws Exception {return new Context(root,Files.createDirectory(root.resolve(name)),Map.of("id",name,"type","risk_score_evaluation","hypothesis","H","datasets",List.of()),Map.of("datasets",List.of(),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("risk_score",p)))),Map.of(),Map.of());}
    private Result execute(String name,Map<String,Object> p)throws Exception {return new StudyCliExecutor().execute(context(name,p));}
    private void hold(String name,Map<String,Object> p,String reason)throws Exception {var r=execute(name,p);assertEquals(State.WAITING_FOR_INPUT,r.state(),r.summary());assertTrue(Files.readString(root.resolve(name+"/genetic-score-local-diagnostic.txt")).contains(reason));assertFalse(Files.exists(root.resolve(name+"/participant-scores.tsv")));assertFalse(Files.exists(root.resolve(name+"/risk-score-evaluation.tsv")));}
    private void bcf()throws Exception {try(var input=new VCFIteratorBuilder().open(root.resolve("input.vcf"));var output=new VariantContextWriterBuilder().setOutputPath(root.resolve("input.bcf")).unsetOption(Options.INDEX_ON_THE_FLY).setOption(Options.FORCE_BCF).build()){output.writeHeader(input.getHeader());while(input.hasNext())output.add(input.next());}}
    private static void int32(ByteArrayOutputStream out,long v){for(int i=0;i<4;i++)out.write((int)(v>>>(8*i))&255);}
    private static void int16(ByteArrayOutputStream out,int v){out.write(v&255);out.write((v>>>8)&255);}
    private static void str16(ByteArrayOutputStream out,String v){byte[] b=v.getBytes(StandardCharsets.UTF_8);int16(out,b.length);out.writeBytes(b);}
    private static void str32(ByteArrayOutputStream out,String v){byte[] b=v.getBytes(StandardCharsets.UTF_8);int32(out,b.length);out.writeBytes(b);}
    private void bgen(int compression,boolean embedded,boolean phased)throws Exception {
        var out=new ByteArrayOutputStream();var sample=new ByteArrayOutputStream();int32(sample,N);for(int i=0;i<N;i++)str16(sample,"I"+i);int sampleBytes=embedded?4+sample.size():0;int32(out,20+sampleBytes);int32(out,20);int32(out,3);int32(out,N);out.writeBytes("bgen".getBytes(StandardCharsets.US_ASCII));int32(out,8|compression|(embedded?0x80000000L:0));if(embedded){int32(out,sampleBytes);out.writeBytes(sample.toByteArray());}
        for(int j=0;j<3;j++){str16(out,"bgen-alias-"+j);str16(out,"bgen-rsid-"+j);str16(out,"1");int32(out,10+j*10);int16(out,2);str32(out,j==0?"A":"G");str32(out,j==0?"C":"T");var raw=new ByteArrayOutputStream();int32(raw,N);int16(raw,2);raw.write(2);raw.write(2);for(int i=0;i<N;i++)raw.write(2);raw.write(phased?1:0);raw.write(8);for(int i=0;i<N;i++){int alt=(i+j)%3;if(phased){raw.write(alt==2?0:255);raw.write(alt==0?255:0);}else{raw.write(alt==0?255:0);raw.write(alt==1?255:0);}}
            byte[] payload=raw.toByteArray();if(compression==0){int32(out,payload.length);out.writeBytes(payload);}else{byte[] compressed;if(compression==1){var buffer=new ByteArrayOutputStream();try(var z=new DeflaterOutputStream(buffer)){z.write(payload);}compressed=buffer.toByteArray();}else compressed=Zstd.compress(payload);int32(out,compressed.length+4);int32(out,payload.length);out.writeBytes(compressed);}
        }Files.write(root.resolve("input.bgen"),out.toByteArray());
    }
    @Test void vcfGzipAndBcfHonorDsThenGtAndExactHeaderIdentity()throws Exception {
        var p=fixture("input.vcf",true);assertEquals(State.COMPLETE,execute("text",p).state());var scores=DelimitedData.read(root.resolve("text/participant-scores.tsv"));for(int i=0;i<N;i++){double a=i%3==2?1.75:i%3+.25,b=(i+1)%3==2?1.75:(i+1)%3+.25;assertEquals(2*(2-a)-.5*b,Double.parseDouble(scores.rows().get(i)[2]),0);}
        bcf();g(p).put("sources",List.of(Map.of("id","A","file","input.bcf","genome_build","GRCh38")));assertEquals(State.COMPLETE,execute("binary",p).state());assertEquals(Files.readString(root.resolve("text/participant-scores.tsv")),Files.readString(root.resolve("binary/participant-scores.tsv")));
        try(var out=new GZIPOutputStream(Files.newOutputStream(root.resolve("input.vcf.gz")))){out.write(vcf(true,false,true).getBytes(StandardCharsets.UTF_8));}g(p).put("sources",List.of(Map.of("id","A","file","input.vcf.gz","genome_build","GRCh38")));assertEquals(State.COMPLETE,execute("compressed",p).state());assertEquals(Files.readString(root.resolve("text/participant-scores.tsv")),Files.readString(root.resolve("compressed/participant-scores.tsv")));
    }
    @Test void allBgenCompressionsAndPhasedDiploidProbabilitiesAgreeWithHardCalls()throws Exception {
        var p=fixture("input.vcf",false);assertEquals(State.COMPLETE,execute("hard",p).state());String expected=Files.readString(root.resolve("hard/participant-scores.tsv"));for(int compression=0;compression<3;compression++)for(boolean phased:List.of(false,true)){bgen(compression,true,phased);g(p).put("sources",List.of(Map.of("id","A","file","input.bgen","genome_build","GRCh38")));String name="bgen-"+compression+"-"+phased;var r=execute(name,p);assertEquals(State.COMPLETE,r.state(),Files.exists(root.resolve(name+"/genetic-score-local-diagnostic.txt"))?Files.readString(root.resolve(name+"/genetic-score-local-diagnostic.txt")):r.summary());assertEquals(expected,Files.readString(root.resolve(name+"/participant-scores.tsv")));}
    }
    @Test void externalBgenSamplesAreBoundedHashedAndCannotConflictWithEmbeddedIds()throws Exception {
        var p=fixture("input.bgen",false);bgen(1,false,false);var samples=new StringBuilder("ID_1 ID_2 missing\n0 0 0\n");for(int i=0;i<N;i++)samples.append("F I").append(i).append(" 0\n");Files.writeString(root.resolve("input.sample"),samples);g(p).put("sources",List.of(Map.of("id","A","file","input.bgen","samples","input.sample","genome_build","GRCh38")));var c=context("external",p);var before=StudyRiskScoreResources.inputHashes(root,c.study(),c.operation());assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());Files.writeString(root.resolve("input.sample"),samples.toString().replace("I0 ","OTHER "));assertNotEquals(before,StudyRiskScoreResources.inputHashes(root,c.study(),c.operation()));bgen(1,true,false);hold("sample-conflict",p,"conflict with embedded");
    }
    @Test void completeStreamDetectsLaterDuplicateAndMissingModelBeforePublishing()throws Exception {
        var p=fixture("input.vcf",false);String text=Files.readString(root.resolve("input.vcf"));Files.writeString(root.resolve("input.vcf"),text+text.lines().filter(l->l.startsWith("1\t10\t")).findFirst().orElseThrow().replace("file-alias-0","later-duplicate")+"\n");hold("late-duplicate",p,"complete-stream scanning");Files.writeString(root.resolve("input.vcf"),text.replace("1\t20\t","1\t21\t"));hold("missing",p,"Complete frozen model");assertTrue(Files.readString(root.resolve("missing/genetic-model-coverage.tsv")).contains("no_coordinate_allele_match"));
    }
    @Test void selectedMultiallelicAndHaploidCallsHoldAndUnknownDsRequiresDeclaration()throws Exception {
        var p=fixture("input.vcf",true);String text=Files.readString(root.resolve("input.vcf"));Files.writeString(root.resolve("input.vcf"),text.replace("\tA\tC\t","\tA\tC,G\t"));hold("multiallelic",p,"split multiallelic");Files.writeString(root.resolve("input.vcf"),text.replace("0/1:","1:"));hold("haploid",p,"Observed non-diploid");Files.writeString(root.resolve("input.vcf"),vcf(true,true,false));hold("unknown",p,"diploid_dosage_declaration");g(p).put("sources",List.of(Map.of("id","A","file","input.vcf","genome_build","GRCh38","diploid_dosage_declaration","Synthetic autosomal diploid ALT DS with GT withheld")));var r=execute("declared",p);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(24L,StudyPublicEvidence.map(r.details().get("genotype_score")).get("assumed_diploid_dosage_cells"));
    }
    @Test void wholeSourceCellsIncludeUnselectedVariantsAndSampleCountIsAdmittedBeforeReader()throws Exception {
        var p=fixture("input.vcf",false);g(p).put("maximum_cells",1000000000000L);assertEquals(1000000000000L,((Number)StudyPublicEvidence.map(StudyRiskScorePolicy.resolve(p).get("genotypes")).get("maximum_cells")).longValue());g(p).put("maximum_cells",24);hold("cells",p,"maximum_cells");g(p).put("maximum_cells",36);assertEquals(State.COMPLETE,execute("exact-budget",p).state());g(p).put("maximum_samples",5);hold("samples",p,"maximum_samples");g(p).put("maximum_samples",12);g(p).put("maximum_source_variants",2);hold("variants",p,"maximum_source_variants");
    }
    @Test void byteFramingBoundsRejectForgedBcfAndBgenBeforeLargeAllocations()throws Exception {
        var p=fixture("input.bcf",false);var bytes=new ByteArrayOutputStream();bytes.writeBytes(new byte[]{'B','C','F',2,2});int32(bytes,1073741824);Files.write(root.resolve("input.bcf"),bytes.toByteArray());hold("bcf-bomb",p,"BCF header exceeds");
        bgen(1,true,false);byte[] data=Files.readAllBytes(root.resolve("input.bgen"));int header=24+8;for(int i=0;i<N;i++)header+=2+("I"+i).length();int probability=header+2+"bgen-alias-0".length()+2+"bgen-rsid-0".length()+2+1+4+2+4+1+4+1+4;for(int i=0;i<4;i++)data[probability+i]=(byte)(1073741824L>>>(8*i));Files.write(root.resolve("input.bgen"),data);g(p).put("sources",List.of(Map.of("id","A","file","input.bgen","genome_build","GRCh38")));hold("bgen-bomb",p,"BGEN decompressed record exceeds");for(int i=0;i<4;i++)data[probability+i]=(byte)(2097152L>>>(8*i));Files.write(root.resolve("input.bgen"),data);g(p).put("maximum_decompressed_mib",1);hold("bgen-total-bound",p,"maximum_decompressed_mib");
    }
    @Test void variantSourceSchemaRejectsAmbiguousFormatsAndSidecars()throws Exception {
        var p=fixture("input.vcf",false);g(p).put("sources",List.of(Map.of("id","A","file","input.vcf","bfile","prefix","genome_build","GRCh38")));assertThrows(IllegalArgumentException.class,()->StudyRiskScorePolicy.resolve(p));g(p).put("sources",List.of(Map.of("id","A","file","input.vcf","samples","input.sample","genome_build","GRCh38")));assertThrows(IllegalArgumentException.class,()->StudyRiskScorePolicy.resolve(p));g(p).put("sources",List.of(Map.of("id","A","file","input.tsv","genome_build","GRCh38")));assertThrows(IllegalArgumentException.class,()->StudyRiskScorePolicy.resolve(p));
    }
    @Test void nonmodelMultiallelicRowsCountTowardSourceWorkWithoutChangingFrozenScore()throws Exception {
        var p=fixture("input.vcf",false);String text=Files.readString(root.resolve("input.vcf"));Files.writeString(root.resolve("input.vcf"),text.replace("30\tfile-alias-2\tG\tT\t","30\tfile-alias-2\tG\tT,A\t"));g(p).put("maximum_cells",36);hold("expanded-budget",p,"maximum_cells");g(p).put("maximum_cells",48);var r=execute("expanded",p);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(48L,StudyPublicEvidence.map(r.details().get("genotype_score")).get("decoded_genotype_cells"));assertEquals(2,DelimitedData.read(root.resolve("expanded/genetic-variant-alignment.tsv")).rows().size());
    }
    @Test void repeatedFileIdentityNeedsOnePartitionAndUnitAndOnlyAggregateDiploidyReachesFindings()throws Exception {
        var p=fixture("input.vcf",true);String original=Files.readString(root.resolve("participants.tsv"));String[] lines=original.split("\n");Files.writeString(root.resolve("participants.tsv"),original+lines[1].replace("private-0","repeat-0")+"\n");hold("repeat-independent",p,"Repeated genotype identity");var grouped=new StringBuilder(lines[0]+"\tunit\n");for(int i=1;i<lines.length;i++)grouped.append(lines[i]).append("\tu").append(i).append('\n');grouped.append(lines[1].replace("private-0","repeat-0")).append("\tu1\n");Files.writeString(root.resolve("participants.tsv"),grouped);p.put("independent_rows",false);p.put("group_columns",List.of("unit"));p.put("resampling_group","unit");Files.writeString(root.resolve("input.vcf"),vcf(true,true,false));g(p).put("sources",List.of(Map.of("id","A","file","input.vcf","genome_build","GRCh38","diploid_dosage_declaration","private-source-declaration-sentinel")));var c=context("repeated",p);var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());
        var hashes=new LinkedHashMap<String,String>();for(String file:result.outputs())hashes.put(file,FollowupSupport.hash(c.outputDirectory().resolve(file)));StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id","repeated","operation_type","risk_score_evaluation","hypothesis","H","state","complete","output_sha256",hashes,"details",result.details()));var inventory=new Context(root,Files.createDirectory(root.resolve("inventory")),Map.of("id","inventory","type","finding_inventory","hypothesis","H"),c.study(),Map.of(),Map.of("scores",c.outputDirectory()));new StudyFindingInventory().execute(inventory);String json=Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl"));assertTrue(json.contains("assumed_diploid_dosage_cells"));assertTrue(json.contains("source_formats"));assertTrue(json.contains("VCF"));for(String secret:List.of("private-0","repeat-0","private-source-declaration-sentinel","file-alias-"))assertFalse(json.contains(secret),secret);
    }
    @Test void decoderRuntimeFailuresAreLocalPrerequisitesWithNoOfficialScores()throws Exception {
        var p=fixture("input.bcf",false);bcf();byte[] raw=Files.readAllBytes(root.resolve("input.bcf"));var buffer=java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);int record=9+buffer.getInt(5),individual=record+8+buffer.getInt(record);assertEquals(0x11,raw[individual]&255);raw[individual+1]=100;Files.write(root.resolve("input.bcf"),raw);hold("bad-decoder",p,"Variant decoder could not complete");assertFalse(Files.readString(root.resolve("bad-decoder/risk-score-analysis.yaml")).contains("file-alias-"));
    }
    @Test void retainedSampleEstimatesPrecedeHeaderAndExternalSidecarMaterialization()throws Exception {
        var p=fixture("input.vcf",false);var header=new StringBuilder("##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");for(int i=0;i<100000;i++)header.append("\tID").append(i);Files.writeString(root.resolve("input.vcf"),header+"\n");g(p).put("maximum_retained_mib",8);hold("retained-header",p,"retained variant sample metadata");bgen(1,false,false);Files.writeString(root.resolve("input.sample"),("x".repeat(50000)+"\n").repeat(N));g(p).put("sources",List.of(Map.of("id","A","file","input.bgen","samples","input.sample","genome_build","GRCh38")));g(p).put("maximum_retained_mib",1);hold("retained-sidecar",p,"retained BGEN sidecar");
    }
}
