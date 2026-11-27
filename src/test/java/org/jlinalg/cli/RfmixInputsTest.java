/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

class RfmixInputsTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path root;
    private Path file(String name,String value)throws IOException{Path p=root.resolve(name);Files.writeString(p,value,StandardCharsets.UTF_8);return p;}
    private String vcf(String samples,String...rows){return "##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t"+samples+"\n"+String.join("\n",rows)+"\n";}
    private String row(String chr,int pos,String gt){return chr+"\t"+pos+"\t.\tA\tG\t.\tPASS\t.\tGT\t"+gt;}
    private Map<String,Object> request()throws IOException{
        var value=RfmixToolsCli.example("analysis");value.put("runtime",root.resolve("runtime").toString());value.put("query",map("path",root.resolve("q").toString(),"format","vcf","index",null));value.put("reference",map("path",root.resolve("r").toString(),"format","vcf","index",null));value.put("sample_map",map("path",root.resolve("s").toString(),"format","text"));value.put("genetic_map",root.resolve("g").toString());return value;
    }
    private void fixture()throws IOException{file("q",vcf("Q",row("22",100,"0|1"),row("22",200,"1|0")));file("r",vcf("R1\tR2\tUNUSED",row("22",100,"0|0\t1|1\t0|1"),row("22",200,"0|0\t1|1\t0|1")));file("s","R2\tZulu\nR1\tNorth Africa\nUNSEEN\tIgnored\n");file("g","22\t100\t-1\n22\t200\t0\n");}
    private RfmixInputs.Result audit(Map<String,Object> request)throws IOException{var spec=RfmixRequest.parse(request);return RfmixInputs.audit(spec.query().path(),spec.query().format().equals("vcf-gzip"),spec.reference().path(),false,spec.sampleMap(),spec.compressedMap(),spec.geneticMap(),spec,()->{});}
    @Test void keepsOriginalMapRowOrderSpacedLabelsNegativeOffsetAndExcludedSamples()throws Exception{fixture();var a=audit(request());assertEquals(List.of("R2","R1"),a.contract().references().stream().map(x->x.sample()).toList());assertEquals("North Africa",a.contract().references().get(1).population());assertEquals(-1,a.contract().retainedLoci().get(0).geneticCm());assertEquals(List.of("UNUSED"),a.audit().get("reference_samples_excluded_by_map"));}
    @Test void missingnessIncludesUnmappedReferenceColumn()throws Exception{fixture();fileReplace("r","0|0\t1|1\t0|1","0|0\t1|1\t.|.");var request=request();var settings=NextflowFiles.object(request.get("settings"));@SuppressWarnings("unchecked")var s=(Map<String,Object>)settings;s.put("max_missing",.3);assertThrows(IOException.class,()->audit(request));}
    @Test void sourceThresholdEqualityIsRetained()throws Exception{fixture();fileReplace("r","0|0\t1|1\t0|1","0|0\t1|1\t.|.");var request=request();@SuppressWarnings("unchecked")var settings=(Map<String,Object>)request.get("settings");settings.put("max_missing",1.0/3);assertEquals(2,audit(request).contract().retainedLoci().size());}
    @Test void referenceLabelsCoalesceUsingOriginalAsciiCaseFold()throws Exception{fixture();file("s","R2\tNorth Africa\nR1\tnORTH aFRICA\n");assertEquals(List.of("North Africa","North Africa"),audit(request()).contract().references().stream().map(x->x.population()).toList());}
    @Test void missingnessExclusionDoesNotForceUnusedGenotypeRepair()throws Exception{fixture();fileReplace("r","0|0\t1|1\t0|1","0|0\t1|1\t9/9");var request=request();@SuppressWarnings("unchecked")var settings=(Map<String,Object>)request.get("settings");settings.put("max_missing",1);assertEquals(2,audit(request).contract().retainedLoci().size());}
    @Test void uncertainRetainedPhaseFailsWithoutAutoPhasing()throws Exception{fixture();fileReplace("q","0|1","0/1");assertThrows(IOException.class,()->audit(request()));}
    @Test void unambiguousHomozygousSlashIsKeptWithWarning()throws Exception{fixture();fileReplace("q","0|1","0/0");assertTrue(audit(request()).warnings().stream().anyMatch(x->x.contains("homozygous slash")));}
    @Test void retainedAlleleMismatchFailsWithoutSwap()throws Exception{fixture();fileReplace("r","\tA\tG\t","\tG\tA\t");assertThrows(IOException.class,()->audit(request()));}
    @Test void multipleSelectedChromosomeBlocksFailInsteadOfSilentTruncation()throws Exception{fixture();file("q",vcf("Q",row("22",100,"0|1"),row("21",1,"0|1"),row("22",200,"0|1")));assertThrows(IOException.class,()->audit(request()));}
    @Test void formatMustPutGtFirstForRetainedCalls()throws Exception{fixture();fileReplace("q","\tGT\t","\tDP:GT\t");assertThrows(IOException.class,()->audit(request()));}
    @Test void duplicateMapCoordinateFailsWithoutInterpolationRepair()throws Exception{fixture();file("g","22\t100\t0\n22\t100\t1\n");assertThrows(IOException.class,()->audit(request()));}
    @Test void gzipCrcAndCompleteRawBytesAreChecked()throws Exception{fixture();byte[] raw=Files.readAllBytes(root.resolve("q"));try(var gz=new GZIPOutputStream(Files.newOutputStream(root.resolve("q.gz")))){gz.write(raw);}var request=request();request.put("query",map("path",root.resolve("q.gz").toString(),"format","vcf-gzip","index",null));assertEquals(2,audit(request).contract().retainedLoci().size());byte[] broken=Files.readAllBytes(root.resolve("q.gz"));broken[broken.length-8]^=1;Files.write(root.resolve("q.gz"),broken);assertThrows(IOException.class,()->audit(request));}
    @Test void unitsAndDeterministicEqualsArgumentsAreExplicit()throws Exception{fixture();var request=request();var spec=RfmixRequest.parse(request);assertTrue(spec.arguments().stream().filter(x->!x.equals("--reanalyze-reference")).allMatch(x->x.contains("=")));@SuppressWarnings("unchecked")var settings=(Map<String,Object>)request.get("settings");settings.put("rf_window",map("unit","cM","value",2));assertThrows(IOException.class,()->RfmixRequest.parse(request));}
    @Test void bcfMissingIndexIsSkipThreePrerequisite()throws Exception{fixture();var request=request();request.put("query",map("path",root.resolve("q.bcf").toString(),"format","bcf","index",null));assertThrows(RfmixRuntime.Unavailable.class,()->RfmixRequest.parse(request));}
    @Test void rawCellCapacityFailsRatherThanTruncates()throws Exception{fixture();var request=request();@SuppressWarnings("unchecked")var c=(Map<String,Object>)request.get("capacity");c.put("native_cells",1);assertThrows(IOException.class,()->audit(request));}
    @Test void finalCrWithoutLfFollowsOriginalChomp()throws Exception{fixture();file("g","22\t100\t-1\n22\t200\t0\r");assertEquals(2,audit(request()).contract().retainedLoci().size());}
    private void fileReplace(String name,String before,String after)throws IOException{Path path=root.resolve(name);String value=Files.readString(path);assertTrue(value.contains(before));Files.writeString(path,value.replace(before,after));}
}
