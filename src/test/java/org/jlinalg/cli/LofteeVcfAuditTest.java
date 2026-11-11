/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.jlinalg.genetics.LofteeVcfAudit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

/** Structural/adversarial contracts only; these fixtures do not validate upstream LOFTEE predictions. */
final class LofteeVcfAuditTest {
    @TempDir Path home;
    static final String META="##fileformat=VCFv4.2\n##contig=<ID=1,length=1000>\n";
    static final String HEADER="#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1\tS2\n";
    static final String CSQ="##INFO=<ID=CSQ,Number=.,Type=String,Description=\"Consequence annotations. Format: Allele|Consequence|Gene|Feature|Feature_type|BIOTYPE|ALLELE_NUM|LoF|LoF_filter|LoF_flags|LoF_info\">\n";
    static final String HC="C|stop_gained|GENE1|TX1|Transcript|protein_coding|1|HC||SINGLE_EXON|GERP_DIST:NaN";
    static final String LC="G|splice_donor_variant|GENE1|TX2|Transcript|protein_coding|2|LC|SMALL_INTRON||INTRON_SIZE:14";
    static final String OTHER="C|missense_variant|GENE1|TX3|Transcript|protein_coding|1||||";
    static final String SITE="1\t101\t.\tA\tC,G\t.\tPASS\tFLAG;X=raw%3Bvalue";
    static final String GENOTYPES="\tGT:DP\t1/2:24\t0/1:40\n";
    static final String INPUT=META+HEADER+SITE+GENOTYPES;
    static final String OUTPUT=META+CSQ+HEADER+SITE+";CSQ="+HC+","+LC+","+OTHER+GENOTYPES;
    static final LofteeVcfAudit.Limits LIMITS=new LofteeVcfAudit.Limits(1000000,100,1000,100000);
    final List<LofteeVcfAudit.Annotation> annotations=new ArrayList<>();
    final List<LofteeVcfAudit.Variant> variants=new ArrayList<>();
    private Path put(String name,String text)throws IOException {Path p=home.resolve(name);Files.writeString(p,text,StandardCharsets.UTF_8);return p;}
    private LofteeVcfAudit.Summary audit(String input,String output,boolean extended,LofteeVcfAudit.Limits limits,String log)throws IOException {
        return LofteeVcfAudit.audit(put("in.vcf",input),put("out.vcf",output),List.of(put("original.stderr",log)),extended,limits,variants::add,annotations::add);
    }
    @Test void preservesEveryAlleleTranscriptFlagAndRawNonfiniteInfo()throws Exception {
        var result=audit(INPUT,OUTPUT,false,LIMITS,"");assertEquals(1,result.variants());assertEquals(3,result.annotations());assertEquals(2,result.targetTranscripts());assertEquals(1,result.highConfidence());assertEquals(1,result.lowConfidence());assertEquals(1,result.flaggedAnnotations());assertEquals(1,result.unclassifiedAnnotations());
        assertEquals(List.of("C","G","C"),annotations.stream().map(LofteeVcfAudit.Annotation::alternate).toList());assertEquals(List.of("TX1","TX2","TX3"),annotations.stream().map(LofteeVcfAudit.Annotation::feature).toList());assertEquals(".",annotations.get(0).id());assertEquals("GERP_DIST:NaN",annotations.get(0).info());assertEquals("SINGLE_EXON",annotations.get(0).flags());assertEquals(HC,annotations.get(0).rawCsq());assertEquals("HC",annotations.get(0).confidence());assertEquals("",annotations.get(2).confidence());assertEquals(3,variants.get(0).annotations());assertThrows(UnsupportedOperationException.class,()->result.csqFields().clear());
    }
    @Test void retainsRepeatedTranscriptRowsWithoutDeduplication()throws Exception {
        var result=audit(INPUT,OUTPUT.replace(HC,HC+","+HC),false,LIMITS,"");assertEquals(4,result.annotations());assertEquals(2,result.highConfidence());assertEquals(annotations.get(0).rawCsq(),annotations.get(1).rawCsq());
    }
    @Test void explicitlyDeclaredOtherSpliceRemainsDifferentFromHighConfidence()throws Exception {
        String extra="C|splice_region_variant|GENE1|TX3|Transcript|protein_coding|1|OS|||SCORE:Inf";
        var result=audit(INPUT,OUTPUT.replace(OTHER,extra),true,LIMITS,"");assertEquals(1,result.otherSplice());assertEquals(1,result.highConfidence());assertEquals("SCORE:Inf",annotations.get(2).info());
    }
    @Test void noncodingTargetConsequenceDoesNotAcquireAConfidenceCall()throws Exception {
        var result=audit(INPUT,OUTPUT.replace(OTHER,"C|stop_gained|GENE1|TX3|Transcript|lncRNA|1||||"),false,LIMITS,"");assertEquals(2,result.targetTranscripts());assertEquals("",annotations.get(2).confidence());
    }
    @Test void acceptsGzipCrLfAndMissingFinalNewlineWithoutInventingRecords()throws Exception {
        Path input=home.resolve("gzip-input"),output=home.resolve("gzip-output");
        try(var a=new GZIPOutputStream(Files.newOutputStream(input));var b=new GZIPOutputStream(Files.newOutputStream(output))){a.write(INPUT.stripTrailing().replace("\n","\r\n").getBytes(StandardCharsets.UTF_8));b.write(OUTPUT.stripTrailing().replace("\n","\r\n").getBytes(StandardCharsets.UTF_8));}
        assertEquals(3,LofteeVcfAudit.audit(input,output,List.of(put("log","")),false,LIMITS,variants::add,annotations::add).annotations());assertEquals(1,variants.size());
    }
    @Test void ordinaryWarningsAreReadWithoutBeingRelabelledPluginFailures()throws Exception {
        assertEquals(3,audit(INPUT,OUTPUT,false,LIMITS,"WARNING: sample label retained: \u03b1\nWARNING: non-canonical transcript flag retained\n").annotations());
    }
    @Test void sourceGtfCustomColumnNamesArePreservedWithoutInventedRenaming()throws Exception {
        String output=OUTPUT.replace("LoF_info\">","LoF_info|genes.gtf.gz\">").replace(HC,HC+"|source-transcript").replace(LC,LC+"|source-transcript").replace(OTHER,OTHER+"|source-transcript");
        var result=audit(INPUT,output,false,LIMITS,"");assertEquals(3,result.annotations());assertEquals("genes.gtf.gz",result.csqFields().get(result.csqFields().size()-1));assertEquals(HC+"|source-transcript",annotations.get(0).rawCsq());
    }
    static Stream<UnaryOperator<String>> invalidOutputs(){return Stream.of(
        x->x.replace(CSQ,""),x->x.replace("|BIOTYPE|","|MISSING|"),x->x.replace("|LoF_flags|","|LoF|"),x->x.replace(CSQ,CSQ+CSQ),
        x->x.replace("1\t101\t.","1\t101\trs-repaired"),x->x.replace("1\t101\t.","1\t102\t."),x->x.replace("0/1:40","0/0:40"),x->x.replace("S1\tS2","S2\tS1"),
        x->x.replace("|1|HC|","|1||"),x->x.replace("|HC||","|HC|ANC_ALLELE|"),x->x.replace("|LC|SMALL_INTRON|","|LC||"),x->x.replace("|1|HC|","|1|OS|"),
        x->x.replace("|1|HC|","|1|UNKNOWN|"),x->x.replace("|1|HC|","|0|HC|"),x->x.replace("|2|LC|","|3|LC|"),x->x.replace("|1|HC|","|01|HC|"),
        x->x.replace(HC,HC+"|extra"),x->x.replace(","+LC,""),x->x.replace("X=raw%3Bvalue","X=changed"),x->x.replace("FLAG;X=","FLAG;FLAG;X="),
        x->x.substring(0,x.indexOf("1\t101")),x->x+x.substring(x.indexOf("1\t101")),x->x.replace("|TX1|Transcript|","|TX1|RegulatoryFeature|"));}
    @ParameterizedTest @MethodSource("invalidOutputs") void rejectsInvalidOrIncompleteOriginalOutput(UnaryOperator<String> change)throws Exception {
        IOException error=assertThrows(IOException.class,()->audit(INPUT,change.apply(OUTPUT),false,LIMITS,""));assertTrue(error.getMessage().contains("No complete result is admitted"));
    }
    static Stream<String> pluginFailures(){return Stream.of("WARNING: Plugin 'LoF' went wrong: database disk image is malformed", "WARNING: Failed to instantiate plugin LoF: Can't locate DBI.pm", "ERROR: Could not load plugin 'LoF'");}
    @ParameterizedTest @MethodSource("pluginFailures") void detectsOriginalPluginFailureEvenWhenVcfLooksValid(String log)throws Exception {
        assertThrows(IOException.class,()->audit(INPUT,OUTPUT,false,LIMITS,log));assertTrue(annotations.isEmpty());
    }
    @Test void refusesPriorAnnotationsRatherThanReplacingThem()throws Exception {assertThrows(IOException.class,()->audit(INPUT.replace(HEADER,CSQ+HEADER),OUTPUT,false,LIMITS,""));}
    @Test void rejectsMalformedUtf8RatherThanReplacingIdentifiers()throws Exception {
        Path in=put("in.vcf",INPUT),out=put("out.vcf",OUTPUT),log=home.resolve("log");Files.write(log,new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->LofteeVcfAudit.audit(in,out,List.of(log),false,LIMITS,variants::add,annotations::add));
    }
    @Test void capacitiesFailWithoutImplicitTranscriptOrRecordSelection()throws Exception {
        assertThrows(IOException.class,()->audit(INPUT,OUTPUT,false,new LofteeVcfAudit.Limits(1000000,100,2,100000),""));assertEquals(2,annotations.size());assertTrue(variants.isEmpty());
    }
    @Test void compressedAndLogTextShareTheExplicitCharacterBudget()throws Exception {
        assertThrows(IOException.class,()->audit(INPUT,OUTPUT,false,new LofteeVcfAudit.Limits(10,100,100,256),"a long original log is not silently skipped"));
    }
    @Test void aSingleHugeLineCannotBypassTheCapacity()throws Exception {
        assertThrows(IOException.class,()->audit(INPUT,OUTPUT,false,new LofteeVcfAudit.Limits(1000000,100,100,256),"x".repeat(257)));
    }
    @Test void sinkFailureDoesNotReturnAnAdmittedSummary()throws Exception {
        Path in=put("in.vcf",INPUT),out=put("out.vcf",OUTPUT),log=put("log","");assertThrows(IOException.class,()->LofteeVcfAudit.audit(in,out,List.of(log),false,LIMITS,variants::add,a->{throw new IOException("ledger storage failed");}));assertTrue(variants.isEmpty());
    }
}
