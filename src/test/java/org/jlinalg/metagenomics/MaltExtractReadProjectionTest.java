/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaltExtractReadProjectionTest {
    private static Map<String,String> fixture()throws IOException {
        var result=new HashMap<String,String>();try(var raw=MaltExtractReadProjectionTest.class.getResourceAsStream("/maltextract17-native-controls.zip")){assertNotNull(raw);try(var zip=new ZipInputStream(raw)){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())assertNull(result.put(entry.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8)));}}return result;
    }
    private static String decode(String value){return value.equals("NULL")?null:new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
    private static float bits(String value){return Float.intBitsToFloat((int)Long.parseLong(value,16));}
    @Test void everyOriginalJava8ComplexityAndTerminalDamageControlMatches()throws Exception {
        var data=fixture();var input=data.get("primitives/inputs.tsv").lines().skip(1).toList();var expected=data.get("primitives/source.tsv").lines().skip(1).toList();assertEquals(4018,input.size());assertEquals(input.size(),expected.size());
        for(int i=0;i<input.size();i++) {
            String[] in=input.get(i).split("\t",-1),out=expected.get(i).split("\t",-1);assertEquals(in[0],out[0]);String sequence=decode(in[1]),text=decode(in[2]);boolean ss=Boolean.parseBoolean(in[3]);
            if(out[1].equals("ERROR"))assertThrows(IOException.class,()->MaltExtractReadProjection.minimumComplexity(sequence),in[0]);else assertEquals(out[1],Integer.toHexString(Float.floatToRawIntBits(MaltExtractReadProjection.minimumComplexity(sequence))),in[0]);
            if(out[2].equals("ERROR"))assertThrows(IOException.class,()->MaltExtractReadProjection.terminalDamage(text,ss),in[0]);else assertEquals(Boolean.parseBoolean(out[2]),MaltExtractReadProjection.terminalDamage(text,ss),in[0]);
        }
    }
    @Test void everyOriginalFloatThresholdNaNInfinityFirstScoreAndAncientCapControlMatches()throws Exception {
        var data=fixture();var input=data.get("filters/inputs.tsv").lines().skip(1).toList();var expected=data.get("filters/source.tsv").lines().skip(1).toList();assertEquals(2640,input.size());assertEquals(input.size(),expected.size());
        for(int at=0;at<input.size();at++) {
            String[] in=input.get(at).split("\t",-1),out=expected.get(at).split("\t",-1);assertEquals(in[0],out[0]);var scores=in[6].isEmpty()?new String[0]:in[6].split(",");var identities=in[7].isEmpty()?new String[0]:in[7].split(",");String text=decode(in[8]);var matches=new ArrayList<MaltExtractRmaReader.Match>();
            for(int i=0;i<scores.length;i++)matches.add(new MaltExtractRmaReader.Match(i,1,bits(scores[i]),0,bits(identities[i]),false,"reference",text,1,20,1000));
            var read=new MaltExtractRmaReader.Read(1,"read",">read","ACGTACGTACGTACGTACGTACGTACGTACGTACGTACGT",40,1,scores.length,matches,1);
            var options=new MaltExtractReadProjection.Options(MaltExtractReadProjection.Mode.valueOf(in[1]),Double.parseDouble(in[2]),Double.parseDouble(in[3]),0,0,Boolean.parseBoolean(in[4]),Boolean.parseBoolean(in[5]));
            if(out[1].equals("ERROR")){assertThrows(IOException.class,()->MaltExtractReadProjection.project(List.of(read),1,options),in[0]);continue;}
            var result=MaltExtractReadProjection.project(List.of(read),1,options);assertEquals(Long.parseLong(out[1]),result.sequenceEligible(),in[0]);assertEquals(Long.parseLong(out[2]),result.consideredMatches(),in[0]);assertEquals(Long.parseLong(out[3]),result.retainedMatches(),in[0]);assertEquals(Integer.parseInt(out[4]),result.reads().size(),in[0]);
        }
    }
    private static List<MaltExtractRmaReader.Read> rows(Map<String,String> data,String prefix) {
        var matches=new HashMap<String,List<MaltExtractRmaReader.Match>>();for(String line:data.get(prefix+"matches.tsv").lines().skip(1).toList()){String[] f=line.split("\t",-1);matches.computeIfAbsent(f[0],ignored->new ArrayList<>()).add(new MaltExtractRmaReader.Match(Integer.parseInt(f[1]),Integer.parseInt(f[2]),bits(f[3]),bits(f[4]),bits(f[5]),Boolean.parseBoolean(f[6]),decode(f[7]),decode(f[8]),Integer.parseInt(f[9]),Integer.parseInt(f[10]),Integer.parseInt(f[11])));}
        var identities=new HashMap<String,Long>();for(String line:data.get(prefix+"identities.tsv").lines().skip(1).toList()){String[] f=line.split("\t",-1);identities.put(f[1],Long.parseLong(f[0]));}
        var rows=new ArrayList<MaltExtractRmaReader.Read>();for(String line:data.get(prefix+"reads.tsv").lines().skip(1).toList()){String[] f=line.split("\t",-1);rows.add(new MaltExtractRmaReader.Read(identities.get(f[0]),decode(f[0]),decode(f[1]),decode(f[2]),Integer.parseInt(f[3]),Integer.parseInt(f[4]),Integer.parseInt(f[5]),matches.getOrDefault(f[0],List.of()),Integer.parseInt(f[7])));}return rows;
    }
    private static List<MaltExtractReadProjection.ReadPair> fasta(String text) {
        if(text.isEmpty()||text.equals("None\n"))return List.of();var lines=text.lines().toList();assertEquals(0,lines.size()%2);var result=new ArrayList<MaltExtractReadProjection.ReadPair>();for(int i=0;i<lines.size();i+=2){assertTrue(lines.get(i).startsWith(">"));result.add(new MaltExtractReadProjection.ReadPair(lines.get(i),lines.get(i+1)));}result.sort(Comparator.comparing(MaltExtractReadProjection.ReadPair::header).thenComparing(MaltExtractReadProjection.ReadPair::sequence));return result;
    }
    @Test void allFourCompleteOriginalRmaPopulationsAndEverySelectedClassFilterTableAndFastaMatch()throws Exception {
        var data=fixture();Map<Integer,String> names=Map.of(8574,"Boa_constrictor",9606,"Homo_sapiens",37349,"Mammuthus_primigenius",40674,"Mammalia",160,"Treponema_pallidum");long completeReads=0,completeMatches=0;int comparisons=0;
        for(String label:List.of("A","B","dna5","support59")) {
            var rows=rows(data,"rma/"+label+"/");completeReads+=rows.size();for(var row:rows)completeMatches+=row.totalMatches();
            for(var mode:MaltExtractReadProjection.Mode.values()) {
                String prefix="original/"+label+"/"+mode.name().toLowerCase(Locale.ROOT)+"/";var tables=new HashMap<String,String[]>();for(String line:data.get(prefix+"filter-table.tsv").lines().skip(1).toList()){String[] f=line.split("\t",-1);tables.put(f[0],f);}
                for(var entry:names.entrySet()) {
                    var result=MaltExtractReadProjection.project(rows,entry.getKey(),MaltExtractReadProjection.Options.complete(mode));String[] source=tables.get(entry.getValue());assertNotNull(source);assertEquals(Long.parseLong(source[1]),result.sequenceEligible());assertEquals(Long.parseLong(source[2]),result.reads().size());assertEquals(Long.parseLong(source[3]),result.consideredMatches());assertEquals(Long.parseLong(source[4]),result.retainedMatches());assertEquals(fasta(data.get(prefix+entry.getValue()+".fasta")),result.reads());comparisons++;
                }
            }
        }
        assertEquals(112,completeReads);assertEquals(122,completeMatches);assertEquals(40,comparisons);
    }
    @Test void unsupportedNonfiniteOptionsAndAmbiguousConcatenatedIdentityCannotAdmitARepairedResult()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new MaltExtractReadProjection.Options(MaltExtractReadProjection.Mode.DEFAULT,Double.NaN,0,0,0,false,false));
        var match=new MaltExtractRmaReader.Match(0,1,100,0,100,false,"reference","Query: 1 AAAAAAAAAAAAAAAAAAAA 20\nSbjct: 1 AAAAAAAAAAAAAAAAAAAA 20\n",1,20,1000);
        var first=new MaltExtractRmaReader.Read(1,"A",">A","CCCCCCCCCCCCCCCCCCCC",20,1,1,List.of(match),1);var second=new MaltExtractRmaReader.Read(2,"AC",">AC","CCCCCCCCCCCCCCCCCCC",19,1,1,List.of(match),1);
        var failure=assertThrows(IOException.class,()->MaltExtractReadProjection.project(List.of(first,second),1,MaltExtractReadProjection.Options.complete(MaltExtractReadProjection.Mode.DEFAULT)));assertTrue(failure.getMessage().contains("collide"));assertTrue(failure.getMessage().contains("NO RESULT ADMITTED"));assertTrue(failure.getMessage().contains("Required action:"));
    }
}
