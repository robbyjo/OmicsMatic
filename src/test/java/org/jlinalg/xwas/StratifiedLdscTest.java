/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Array;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StratifiedLdscTest {
    record Model(String id,double[] chi,double[][] ld,double[] weight,double[] n,double[] counts,
            int blocks,Double intercept,double universe,double[][] overlap,Map<String,String> expected) {
        StratifiedLdsc.Result fit(){return StratifiedLdsc.fit(chi,ld,weight,n,counts,blocks,intercept);}
    }
    static List<Model> models()throws Exception {
        Map<String,String> payload=new HashMap<>();
        try(var source=StratifiedLdscTest.class.getResourceAsStream("ldsc-stratified/reference-fixtures.zip")) {
            assertNotNull(source);try(var zip=new ZipInputStream(source)){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())assertNull(payload.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8)));}
        }
        var result=new ArrayList<Model>();
        for(String line:payload.get("cases.tsv").lines().skip(1).toList()) {
            String[] c=line.split("\t");String id=c[0];int rows=Integer.parseInt(c[1]),k=Integer.parseInt(c[2]);double[] y=new double[rows],w=new double[rows],n=new double[rows];double[][] ld=new double[rows][k];int index=0;
            for(String row:payload.get(id+"/input.tsv").lines().toList()){double[] a=Arrays.stream(row.split("\t")).mapToDouble(Double::parseDouble).toArray();y[index]=a[0];w[index]=a[1];n[index]=a[2];System.arraycopy(a,3,ld[index],0,k);index++;}assertEquals(rows,index);
            double[][] overlap=payload.get(id+"/overlap.tsv").lines().map(row->Arrays.stream(row.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
            var expected=new LinkedHashMap<String,String>();for(String row:payload.get(id+"/expected.tsv").lines().skip(1).toList()){String[] entry=row.split("\t");assertNull(expected.put(entry[0],entry[1]));}
            result.add(new Model(id,y,ld,w,n,Arrays.stream(c[6].split(",")).mapToDouble(Double::parseDouble).toArray(),Integer.parseInt(c[3]),c[4].equals("free")?null:Double.valueOf(c[4]),Double.parseDouble(c[5]),overlap,expected));
        }
        return result;
    }
    static void flatten(String name,Object value,Map<String,Object> result)throws Exception {
        if(value!=null&&value.getClass().isArray()){for(int j=0;j<Array.getLength(value);j++)flatten(name+"."+j,Array.get(value,j),result);}
        else if(value!=null&&value.getClass().isRecord()){for(var c:value.getClass().getRecordComponents())flatten(name+"."+c.getName(),c.getAccessor().invoke(value),result);}
        else result.put(name,value);
    }
    @Test void original48ModelsAndEveryReportedReferenceField()throws Exception {
        var models=models();assertEquals(48,models.size());int compared=0;
        for(Model model:models) {
            var fit=model.fit();var overlap=StratifiedLdsc.overlap(fit,model.overlap,model.universe);var actual=new LinkedHashMap<String,Object>();flatten("fit",fit,actual);flatten("overlap",overlap,actual);
            double absPropCov=Arrays.stream(fit.proportionCovariance()).flatMapToDouble(Arrays::stream).map(Math::abs).sum();
            for(var entry:model.expected.entrySet()) {
                String key=entry.getKey(),label=model.id+" "+key;assertTrue(actual.containsKey(key),label);Object value=actual.get(key);compared++;
                if(entry.getValue().equals("NA")){assertTrue(value==null||key.contains("enrichmentLogPValues")&&value instanceof Double d&&Double.isNaN(d),label);continue;}
                assertNotNull(value,label);double expected=Double.parseDouble(entry.getValue()),observed=((Number)value).doubleValue();assertTrue(Double.isFinite(observed),label);
                double floor=key.contains("covariance")||key.contains("Covariance")?1e-20:key.startsWith("fit.estimates.")||key.startsWith("fit.deleteValues.")||key.startsWith("overlap.coefficients.")||key.startsWith("overlap.coefficientStandardErrors.")?1e-16:2e-11;
                double tolerance=floor+5e-8*Math.abs(expected);
                // Near-zero overlapping baseline uncertainty is a cancellation
                // of the already separately checked proportion-covariance matrix.
                // Retain each runtime's value, with a bounded roundoff tolerance.
                if(key.startsWith("overlap.h2ProportionStandardErrors.")||key.startsWith("overlap.enrichmentStandardErrors.")) {
                    int j=Integer.parseInt(key.substring(key.lastIndexOf('.')+1));double factor=key.contains("enrichment")?model.universe/model.counts[j]:1;
                    tolerance=Math.max(tolerance,Math.sqrt(1e-12*absPropCov)*factor);
                }
                assertEquals(expected,observed,tolerance,label);
            }
        }
        assertEquals(22596,compared,"Complete model/reference population");System.out.println("Original partitioned LDSC: "+models.size()+" models; "+compared+" checked fields");
    }
    @Test void inputsAndEveryReturnedArrayAreDefensiveCopies()throws Exception {
        Model model=models().get(0);double[][] original=Arrays.stream(model.ld).map(double[]::clone).toArray(double[][]::new);double[] y=model.chi.clone(),w=model.weight.clone(),n=model.n.clone(),m=model.counts.clone();var fit=model.fit();assertArrayEquals(y,model.chi);assertArrayEquals(w,model.weight);assertArrayEquals(n,model.n);assertArrayEquals(m,model.counts);for(int i=0;i<original.length;i++)assertArrayEquals(original[i],model.ld[i]);
        var before=new LinkedHashMap<String,Object>();flatten("fit",fit,before);
        for(var c:fit.getClass().getRecordComponents()){Object a=c.getAccessor().invoke(fit);if(a instanceof double[] v)v[0]=Double.NaN;else if(a instanceof int[] v)v[0]=-1;else if(a instanceof double[][] v)v[0][0]=Double.NaN;}
        var after=new LinkedHashMap<String,Object>();flatten("fit",fit,after);assertEquals(before,after);
        var overlap=StratifiedLdsc.overlap(fit,model.overlap,model.universe);before.clear();after.clear();flatten("overlap",overlap,before);
        for(var c:overlap.getClass().getRecordComponents()){Object a=c.getAccessor().invoke(overlap);if(a instanceof double[] v)v[0]=Double.NaN;else if(a instanceof Double[] v)v[0]=Double.NaN;}
        flatten("overlap",overlap,after);assertEquals(before,after);
    }
    @Test void signedAndOutsideRangeResultsAreRetainedWithVisibleWarnings()throws Exception {
        boolean negative=false,aboveOne=false,signed=false,clamped=false;
        for(Model m:models()){var r=m.fit();negative|=r.totalH2()<0;aboveOne|=r.totalH2()>1;signed|=Arrays.stream(r.estimates(),0,r.annotations()).anyMatch(x->x<0);if(r.initialH2()!=r.weightingH2()){clamped=true;assertTrue(r.warnings().stream().anyMatch(s->s.contains("clamped")));}}
        assertTrue(negative);assertTrue(aboveOne);assertTrue(signed);assertTrue(clamped);
    }
    @Test void fixedInterceptIsNotReportedAsAnEstimatedZeroUncertainty()throws Exception {
        for(Model model:models()){var fit=model.fit();int k=fit.annotations();if(model.intercept!=null){assertNull(fit.interceptStandardError());assertNull(fit.ratio());for(double[] row:fit.deleteValues())assertEquals(model.intercept,row[k]);}else assertNotNull(fit.interceptStandardError());}
    }
    @Test void fullAndIndividualDeleteRankDeficiencyRefuseWithoutDroppingColumns()throws Exception {
        Model m=models().get(0);double[][] duplicate=Arrays.stream(m.ld).map(row->new double[]{row[0],row[0]}).toArray(double[][]::new);
        assertThrows(ArithmeticException.class,()->StratifiedLdsc.fit(m.chi,duplicate,m.weight,m.n,m.counts,m.blocks,null));
        int rows=12;double[] y=new double[rows],w=new double[rows],n=new double[rows];double[][] ld=new double[rows][2];
        for(int i=0;i<rows;i++){y[i]=2+i*.01;w[i]=1;n[i]=100;ld[i][0]=i+1;ld[i][1]=i<4?i*i+2:0;}
        assertThrows(ArithmeticException.class,()->StratifiedLdsc.fit(y,ld,w,n,new double[]{1000,100},3,null));
    }
    @Test void invalidAndNonfiniteRowsAreRefusedInsteadOfFiltered()throws Exception {
        Model m=models().get(0);double[] bad=m.chi.clone();bad[4]=Double.NaN;assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.fit(bad,m.ld,m.weight,m.n,m.counts,m.blocks,null));bad[4]=-1;assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.fit(bad,m.ld,m.weight,m.n,m.counts,m.blocks,null));
        double[] n=m.n.clone();n[3]=0;assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.fit(m.chi,m.ld,m.weight,n,m.counts,m.blocks,null));
        assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.fit(m.chi,m.ld,m.weight,m.n,m.counts,m.blocks,Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.fit(m.chi,m.ld,m.weight,m.n,new double[]{1000,-1},m.blocks,null));
    }
    @Test void declaredCapacityRejectsTheWholePopulationBeforeCloningRows() {
        int n=10001;double[] y=new double[n],w=new double[n],ns=new double[n],m=new double[256];double[][] ld=new double[n][];
        var e=assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.fit(y,ld,w,ns,m,2,null));assertTrue(e.getMessage().contains("capacity"));
    }
    @Test void overlapCountsUniverseAndNullDiagnosticsStayExplicit()throws Exception {
        Model model=models().get(0);var fit=model.fit();double[][] bad=Arrays.stream(model.overlap).map(double[]::clone).toArray(double[][]::new);bad[0][0]-=1;
        assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.overlap(fit,bad,model.universe));assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.overlap(fit,model.overlap,model.universe/2));
        var overlap=StratifiedLdsc.overlap(fit,model.overlap,model.universe);assertNull(overlap.enrichmentPValues()[0]);assertTrue(Double.isNaN(overlap.enrichmentLogPValues()[0]));assertEquals(0,overlap.contrastStandardErrors()[0]);
    }
    @Test void zeroTotalHeritabilityRefusesUndefinedProportions()throws Exception {
        Model model=models().get(0);double[] chi=new double[model.chi.length];Arrays.fill(chi,1);
        assertThrows(IllegalArgumentException.class,()->StratifiedLdsc.fit(chi,model.ld,model.weight,model.n,model.counts,model.blocks,1.));
    }
}
