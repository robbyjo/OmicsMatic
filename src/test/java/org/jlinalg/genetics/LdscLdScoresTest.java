/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LdscLdScoresTest {
    @TempDir Path temp;
    record Model(String id,Path prefix,Set<String> ids,int[] samples,double[] coords,
            double[][] annotations,boolean binary,double window,int chunk,double maf,
            Map<String,String> expected) {
        LdscLdScores.Result compute()throws Exception {
            try(var data=PlinkBedDataset.open(prefix,ids)){return LdscLdScores.fromPlink(data,samples,coords,annotations,binary,window,chunk,maf,LdscLdScores.Limits.defaults());}
        }
    }
    static Map<String,byte[]> fixtures()throws Exception {
        var entries=new HashMap<String,byte[]>();try(var source=LdscLdScoresTest.class.getResourceAsStream("ldsc-scores/reference-fixtures.zip")){assertNotNull(source);try(var z=new ZipInputStream(source)){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry())assertNull(entries.put(e.getName(),z.readAllBytes()));}}return entries;
    }
    static String utf8(Map<String,byte[]> entries,String name){return new String(Objects.requireNonNull(entries.get(name),name),StandardCharsets.UTF_8);}
    List<Model> models(Map<String,byte[]> entries)throws Exception {
        var models=new ArrayList<Model>();for(String line:utf8(entries,"cases.tsv").lines().skip(1).toList()) {
            String[] c=line.split("\t");String id=c[0];Path dir=temp.resolve(id);Files.createDirectories(dir);Path prefix=dir.resolve("reference");for(String ext:List.of(".bed",".bim",".fam"))Files.write(Path.of(prefix+ext),entries.get(id+"/reference"+ext));
            List<String> rows=utf8(entries,id+"/annotations.tsv").lines().skip(1).toList();int k=Integer.parseInt(c[3]);double[] coords=new double[rows.size()];double[][] ann=new double[rows.size()][k];for(int i=0;i<rows.size();i++){double[] row=Arrays.stream(rows.get(i).split("\t")).mapToDouble(Double::parseDouble).toArray();coords[i]=row[0];System.arraycopy(row,1,ann[i],0,k);}
            var expected=new LinkedHashMap<String,String>();for(String row:utf8(entries,id+"/expected.tsv").lines().skip(1).toList()){String[] fields=row.split("\t");assertNull(expected.put(fields[0],fields[1]));}
            var ids=new HashSet<>(List.of(c[9].split(",")));int[] samples=Arrays.stream(c[8].split(",")).mapToInt(Integer::parseInt).toArray();assertEquals(Integer.parseInt(c[1]),rows.size());assertEquals(Integer.parseInt(c[2]),samples.length);
            models.add(new Model(id,prefix,Set.copyOf(ids),samples,coords,ann,c[4].equals("binary"),Double.parseDouble(c[5]),Integer.parseInt(c[6]),Double.parseDouble(c[7]),expected));
        }return models;
    }
    static void flatten(String key,Object value,Map<String,Object> flat)throws Exception {
        if(value!=null&&value.getClass().isArray()){for(int i=0;i<Array.getLength(value);i++)flatten(key+"."+i,Array.get(value,i),flat);}
        else if(value instanceof List<?> list){for(int i=0;i<list.size();i++)flatten(key+"."+i,list.get(i),flat);}
        else if(value!=null&&value.getClass().isRecord()){for(var f:value.getClass().getRecordComponents())flatten(key.isEmpty()?f.getName():key+"."+f.getName(),f.getAccessor().invoke(value),flat);}
        else flat.put(key,value);
    }
    @Test void original37BedModelsAndEveryReferenceField()throws Exception {
        var fixtures=fixtures();var models=models(fixtures);assertEquals(37,models.size());int compared=0;
        for(Model model:models) {
            var result=model.compute();var actual=new HashMap<String,Object>();flatten("",result,actual);
            for(var entry:model.expected.entrySet()) {
                String key=entry.getKey(),label=model.id+" "+key;assertTrue(actual.containsKey(key),label);Object value=actual.get(key);String expected=entry.getValue();compared++;
                if(expected.equals("NA")){assertNull(value,label);continue;}assertNotNull(value,label);
                if(value instanceof Boolean flag){assertEquals(Boolean.parseBoolean(expected),flag,label);continue;}if(value instanceof String text){assertEquals(expected,text,label);continue;}
                double wanted=Double.parseDouble(expected),observed=((Number)value).doubleValue();assertTrue(Double.isFinite(observed),label);
                if(key.contains("allele2Frequency")||key.endsWith(".maf")||key.endsWith(".imputedMean"))assertEquals(Double.doubleToLongBits(wanted),Double.doubleToLongBits(observed),label);
                else assertEquals(wanted,observed,2e-11+2e-9*Math.abs(wanted),label);
            }
            if(model.id.equals("L036")){assertTrue(result.peakCachedGenotypeCells()<(long)result.samples()*result.retainedInputIndices().length/8);assertTrue(result.correlations()>100000);}
        }
        var match=Pattern.compile("\"fields\"\\s*:\\s*(\\d+)").matcher(utf8(fixtures,"provenance.json"));assertTrue(match.find());assertEquals(Integer.parseInt(match.group(1)),compared);System.out.println("Original LD-score BED references: "+models.size()+" models, "+compared+" fields");
    }
    static LdscLdScores.Genotypes provider(double[][] variants){return new LdscLdScores.Genotypes(){public int samples(){return variants[0].length;}public int variants(){return variants.length;}public double[] readVariant(int i){return variants[i];}};}
    static double[][] calls(){return new double[][]{{0,0,2,2},{0,2,0,2}};}
    @Test void orthogonalGenotypesRetainNegativeUnbiasedContributions()throws Exception {
        var result=LdscLdScores.compute(provider(calls()),new double[]{0,0},new double[][]{{1,0},{0,1}},true,0,1,0,LdscLdScores.Limits.defaults());double[][] scores=result.ldScores();assertArrayEquals(new double[]{1,-.5},scores[0]);assertArrayEquals(new double[]{-.5,1},scores[1]);assertEquals(3,result.correlations());assertEquals(2,result.commonVariants());
    }
    @Test void secondAlleleMafBoundaryAndSourceChunkFallbackAreExplicit()throws Exception {
        var models=models(fixtures());var boundary=models.get(0).compute();assertTrue(boundary.variantAudit().get(5).maf()>.05);assertEquals(.05,boundary.variantAudit().get(6).maf());assertEquals(.95,boundary.variantAudit().get(5).allele2Frequency());
        var fallback=models.get(5).compute();assertEquals(200,fallback.requestedChunkSize());assertEquals(1,fallback.effectiveChunkSize());assertEquals(1,fallback.chunks().size());assertTrue(fallback.beyondNominalWindowPairs()>0);assertTrue(fallback.warnings().stream().anyMatch(s->s.contains("fallback")));assertEquals((long)fallback.retainedInputIndices().length*(fallback.retainedInputIndices().length+1)/2,fallback.correlations());
    }
    @Test void changedProducerRefusesInsteadOfBorrowingTheFilterPopulation() {
        double[][] calls=calls();AtomicInteger reads=new AtomicInteger();var source=new LdscLdScores.Genotypes(){public int samples(){return 4;}public int variants(){return 2;}public double[] readVariant(int i){double[] row=calls[i].clone();if(reads.incrementAndGet()>2)row[0]=1;return row;}};
        var failure=assertThrows(IOException.class,()->LdscLdScores.compute(source,new double[]{0,0},null,true,0,1,0,LdscLdScores.Limits.defaults()));assertTrue(failure.getMessage().contains("changed after population filtering"));assertArrayEquals(new double[]{0,0,2,2},calls[0]);
    }
    @Test void invalidCallsAnnotationsOrderingAndNoRetainedPopulationRefuse() {
        for(double bad:new double[]{.3,9,Double.POSITIVE_INFINITY}){double[][] data=calls();data[0][0]=bad;assertThrows(IllegalArgumentException.class,()->LdscLdScores.compute(provider(data),new double[]{0,0},null,true,0,1,0,LdscLdScores.Limits.defaults()));}
        assertThrows(IllegalArgumentException.class,()->LdscLdScores.compute(provider(calls()),new double[]{1,0},null,true,1,1,0,LdscLdScores.Limits.defaults()));
        assertThrows(IllegalArgumentException.class,()->LdscLdScores.compute(provider(calls()),new double[]{0,0},new double[][]{{1,.2},{1,1}},true,1,1,0,LdscLdScores.Limits.defaults()));
        var none=assertThrows(IllegalArgumentException.class,()->LdscLdScores.compute(provider(new double[][]{{1,1,Double.NaN,1},{0,0,0,0}}),new double[]{0,0},null,true,1,1,0,LdscLdScores.Limits.defaults()));assertTrue(none.getMessage().contains("All declared variants"));
    }
    @Test void zeroAndSignedAnnotationsAndInputResultOwnershipRemainVisible()throws Exception {
        double[][] data=calls(),annotation={{0,-1},{0,2}};double[] coords={0,0};var r=LdscLdScores.compute(provider(data),coords,annotation,false,0,1,0,LdscLdScores.Limits.defaults());assertArrayEquals(new double[]{0,-1},annotation[0]);assertArrayEquals(new double[]{0,0,2,2},data[0]);assertArrayEquals(new double[]{0,0},coords);assertArrayEquals(new double[]{0,-2},r.ldScores()[0]);assertArrayEquals(new double[]{0,2.5},r.ldScores()[1]);assertNull(r.annotationOverlaps());
        var before=new HashMap<String,Object>();flatten("",r,before);for(var f:r.getClass().getRecordComponents()){Object v=f.getAccessor().invoke(r);if(v instanceof double[] a)a[0]=Double.NaN;else if(v instanceof int[] a)a[0]=-99;else if(v instanceof double[][] a)a[0][0]=Double.NaN;}var after=new HashMap<String,Object>();flatten("",r,after);assertEquals(before,after);assertThrows(UnsupportedOperationException.class,()->r.variantAudit().clear());assertThrows(UnsupportedOperationException.class,()->r.chunks().clear());
    }
    @Test void capacityRefusesEntireGraphWithoutDroppingPairsOrRows() {
        var d=LdscLdScores.Limits.defaults();for(var limit:List.of(new LdscLdScores.Limits(100,100,10,100,100,1,d.maximumMultiplyAdds(),d.maximumEstimatedBytes()),new LdscLdScores.Limits(100,100,10,100,1,100,d.maximumMultiplyAdds(),d.maximumEstimatedBytes()),new LdscLdScores.Limits(100,100,10,100,100,100,1,d.maximumEstimatedBytes()))) {
            var e=assertThrows(IllegalArgumentException.class,()->LdscLdScores.compute(provider(calls()),new double[]{0,0},null,true,0,1,0,limit));assertTrue(e.getMessage().contains("capacity"));
        }
    }
    @Test void interruptedAnalysisDoesNotReturnAPartialScore() {
        Thread.currentThread().interrupt();try{assertThrows(IOException.class,()->LdscLdScores.compute(provider(calls()),new double[]{0,0},null,true,0,1,0,LdscLdScores.Limits.defaults()));}finally{assertTrue(Thread.interrupted());}
    }
    @Test void duplicateSampleOrdinalsRefuseRatherThanReplicateIndividuals()throws Exception {
        Model model=models(fixtures()).get(0);try(var data=PlinkBedDataset.open(model.prefix,model.ids)){assertThrows(IllegalArgumentException.class,()->LdscLdScores.fromPlink(data,new int[]{0,0,1},model.coords,model.annotations,model.binary,model.window,model.chunk,model.maf,LdscLdScores.Limits.defaults()));}
    }
    @Test void overlapAndCountVectorStorageAreAdmittedBeforeReadingCalls() {
        double[][] ann=new double[2][200];for(double[] row:ann)Arrays.fill(row,1);AtomicInteger reads=new AtomicInteger();var data=calls();var source=new LdscLdScores.Genotypes(){public int samples(){return 4;}public int variants(){return 2;}public double[] readVariant(int i){reads.incrementAndGet();return data[i];}};
        var limits=new LdscLdScores.Limits(100,100,256,100000,100000,100000,1000000,1<<20);
        var failure=assertThrows(IllegalArgumentException.class,()->LdscLdScores.compute(source,new double[]{0,0},ann,true,0,1,0,limits));assertTrue(failure.getMessage().contains("retained-byte capacity"));assertEquals(0,reads.get());
        var graph=LdscLdScores.pairing(new double[]{0,1,2,100},0,50);assertEquals(10,graph.correlations());assertEquals(1,graph.effectiveChunkSize());assertEquals(4,graph.maximumResidentVariants());assertEquals(1,graph.chunks().size());graph.nominalWindowLefts()[0]=99;assertEquals(0,graph.nominalWindowLefts()[0]);
    }
}
