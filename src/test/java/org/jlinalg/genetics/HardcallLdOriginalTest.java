/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class HardcallLdOriginalTest {
    @TempDir static Path temp;
    private static final Runnable CHECK=()->{};
    private static final List<String> NAMES=List.of("exhaustive-three","word-63","word-64","word-65","word-129","word-2504","explicit-keep","window-2","window-3","window-99999");
    private static Map<String,byte[]> fixture()throws Exception {
        byte[] raw;try(var stream=HardcallLdOriginalTest.class.getResourceAsStream("hardcall-ld/original-controls.zip")){assertNotNull(stream);raw=stream.readNBytes(200000);assertEquals(-1,stream.read());}
        assertEquals(140388,raw.length);assertEquals("06e002a1c8c23e462176d32726abaedcb6b71fae3a6f9f349959a6befed04719",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var files=new HashMap<String,byte[]>();long total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){String name=entry.getName();assertFalse(entry.isDirectory());assertFalse(name.startsWith("/"));assertFalse(name.contains(".."));assertFalse(name.contains("\\"));byte[] b=zip.readNBytes(1048577);total+=b.length;assertTrue(b.length<=1048576&&total<=8388608);assertNull(files.put(name,b));}}
        assertTrue(text(files,"original-source-receipt.json").contains("PLINK v1.9.0 64-bit (27 Sep 2026)"));return files;
    }
    private static String text(Map<String,byte[]> files,String name){assertTrue(files.containsKey(name),name);return new String(files.get(name),StandardCharsets.UTF_8);}
    @TestFactory Stream<DynamicTest> actualOriginalBinaryMatricesFrequenciesAndWindows()throws Exception {
        var files=fixture();return NAMES.stream().map(name->DynamicTest.dynamicTest(name,()->compare(files,name)));
    }
    private static void compare(Map<String,byte[]> files,String name)throws Exception {
        Path folder=Files.createDirectory(temp.resolve(name)),prefix=folder.resolve("input");
        for(String suffix:List.of("bed","bim","fam"))Files.write(folder.resolve("input."+suffix),files.get(name+"/input."+suffix));
        List<String[]> bim=text(files,name+"/input.bim").lines().map(s->s.split("\\s+")).toList();Set<String> ids=new HashSet<>();for(String[] row:bim)assertTrue(ids.add(row[1]));
        Set<String> keep=null;if(files.containsKey(name+"/input.keep")){keep=new HashSet<>();for(String line:text(files,name+"/input.keep").lines().toList()){String[] a=line.split("\\s+");assertTrue(keep.add(a[0]+"\t"+a[1]));}}
        try(var dataset=PlinkBedDataset.open(prefix,ids)){
            Set<String> selectedIds=keep;List<PlinkBedDataset.Sample> samples=dataset.samples();int[] selected=IntStream.range(0,samples.size()).filter(i->samples.get(i).founder()&&(selectedIds==null||selectedIds.contains(samples.get(i).familyId()+"\t"+samples.get(i).individualId()))).toArray();
            var frame=HardcallLd.frame("source-"+name,samples.size(),selected,HardcallLd.Limits.defaults(),CHECK);var variants=dataset.selectedVariants();assertEquals(bim.size(),variants.size());var calls=new ArrayList<HardcallLd.Calls>();for(var variant:variants)calls.add(HardcallLd.encode(frame,dataset.readPackedRow(variant),CHECK));
            var original=ByteBuffer.wrap(files.get(name+"/original.ld.bin")).order(ByteOrder.LITTLE_ENDIAN);assertEquals(variants.size()*variants.size()*8,original.remaining());
            for(int i=0;i<variants.size();i++)for(int j=0;j<variants.size();j++){
                double expected=original.getDouble();var actual=HardcallLd.pair(calls.get(i),calls.get(j),CHECK);
                if(Double.isNaN(expected)){assertNotEquals(HardcallLd.Status.DEFINED,actual.status());assertTrue(Double.isNaN(actual.rSquared()));}
                else {assertEquals(HardcallLd.Status.DEFINED,actual.status());if(expected==0||expected==1)assertEquals(expected,actual.rSquared());else assertEquals(expected,actual.rSquared(),3e-15+1e-12*Math.abs(expected));}
            }
            List<String> frequencyRows=text(files,name+"/original.frqx").lines().toList();assertEquals(variants.size()+1,frequencyRows.size());assertEquals("CHR\tSNP\tA1\tA2\tC(HOM A1)\tC(HET)\tC(HOM A2)\tC(HAP A1)\tC(HAP A2)\tC(MISSING)",frequencyRows.get(0));
            for(int i=0;i<variants.size();i++){
                String[] row=frequencyRows.get(i+1).split("\t");assertEquals(variants.get(i).id(),row[1]);assertEquals(variants.get(i).allele1(),row[2]);assertEquals(variants.get(i).allele2(),row[3]);long hom1=Long.parseLong(row[4]),het=Long.parseLong(row[5]),hom2=Long.parseLong(row[6]);assertEquals("0",row[7]);assertEquals("0",row[8]);
                var f=calls.get(i).frequencies();assertEquals(hom1+het+hom2,f.called());assertEquals(Long.parseLong(row[9]),f.missing());assertEquals(2*hom1+het,f.allele1Count());assertEquals(2*hom2+het,f.allele2Count());assertEquals(selected.length,f.called()+f.missing());if(f.called()==0)assertTrue(Double.isNaN(f.minorAlleleFrequency()));else assertEquals((double)Math.min(2*hom1+het,2*hom2+het)/(2*f.called()),f.minorAlleleFrequency());
            }
            if(name.startsWith("window-"))window(files,name,bim,calls);
        }
    }
    private static void window(Map<String,byte[]> files,String name,List<String[]> bim,List<HardcallLd.Calls> calls){
        int gap=Integer.parseInt(name.substring("window-".length()))-1;var variants=new ArrayList<FumaLocusDefinition.ReferenceVariant>();var pairs=new ArrayList<FumaLocusDefinition.LdPair>();var nativeKeys=new HashSet<String>();
        for(int i=0;i<bim.size();i++){String[] v=bim.get(i);variants.add(new FumaLocusDefinition.ReferenceVariant(Integer.parseInt(v[0]),Integer.parseInt(v[3]),v[4],v[5],v[1],calls.get(i).frequencies().minorAlleleFrequency()));}
        for(int i=0;i<bim.size();i++)for(int j=i+1;j<bim.size();j++){
            var a=variants.get(i);var b=variants.get(j);if(a.chromosome()!=b.chromosome()||b.position()-a.position()>1000||j-i>gap)continue;
            var score=HardcallLd.pair(calls.get(i),calls.get(j),CHECK);assertEquals(1,score.rSquared());pairs.add(new FumaLocusDefinition.LdPair(a.chromosome(),a.position(),b.position(),score.rSquared()));nativeKeys.add(a.rsId()+"\t"+b.rsId());
        }
        var ref=new FumaLocusDefinition.Reference("original-window-"+gap,variants,pairs,0,new FumaLocusDefinition.LdWindow(1000,gap));var sourceKeys=new HashSet<String>();
        for(String line:text(files,name+"/window.ld").lines().skip(1).toList()){String[] r=line.strip().split("\\s+");assertTrue(sourceKeys.add(r[2]+"\t"+r[5]));assertEquals(1,Double.parseDouble(r[6]));}
        assertEquals(sourceKeys,nativeKeys,"Actual --ld-window/--ld-window-kb/--ld-window-r2 eligibility differs");
        for(int i=0;i<variants.size();i++)for(int j=i+1;j<variants.size();j++){
            var a=variants.get(i);var b=variants.get(j);var state=ref.coverage(a.uid(),b.uid());if(sourceKeys.contains(a.rsId()+"\t"+b.rsId()))assertEquals(FumaLocusDefinition.PairCoverage.STORED,state);else assertTrue(state==FumaLocusDefinition.PairCoverage.OUTSIDE_DECLARED_WINDOW||state==FumaLocusDefinition.PairCoverage.DIFFERENT_CHROMOSOMES);
        }
    }
}
