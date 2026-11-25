/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class GemmaKinshipTest {
    @TempDir Path root;
    byte[] resource(String caseName,String name)throws IOException{
        try(var in=getClass().getResourceAsStream("/org/jlinalg/genetics/gemma/"+caseName+"/"+name)){
            if(in==null)throw new IOException("Missing GEMMA original synthetic fixture "+name);return in.readAllBytes();
        }
    }
    record Input(PlinkBedDataset data,boolean[] mask,double[][] design)implements AutoCloseable{
        public void close()throws IOException{data.close();}
    }
    Input open(String caseName)throws IOException{
        Path prefix=root.resolve(caseName);
        for(String suffix:List.of("bed","bim","fam"))Files.write(Path.of(prefix+"."+suffix),resource(caseName,"data."+suffix));
        var ids=new LinkedHashSet<String>();for(String row:new String(resource(caseName,"data.bim"),StandardCharsets.UTF_8).lines().toList())ids.add(row.split("\\s+")[1]);
        var rows=new String(resource(caseName,"design.txt"),StandardCharsets.UTF_8).lines().toList();boolean[] mask=new boolean[rows.size()];double[][] design=new double[rows.size()][];
        for(int i=0;i<rows.size();i++){String[] f=rows.get(i).split(" ");mask[i]=f[0].equals("1");design[i]=new double[f.length-1];for(int c=1;c<f.length;c++)design[i][c-1]=Double.parseDouble(f[c]);}
        return new Input(PlinkBedDataset.open(prefix,ids),mask,design);
    }
    GemmaKinship.Options options(String name,int mode,long bytes,long work){return new GemmaKinship.Options(mode==1?GemmaKinship.Mode.CENTERED:GemmaKinship.Mode.STANDARDIZED,name.equals("qc")?.125:.01,name.equals("qc")?1.0/7:.05,name.equals("qc")?1e-5:0,.9999,bytes,work);}

    @ParameterizedTest
    @CsvSource({"baseline,0.98.1,1","baseline,0.98.1,2","baseline,0.98.3,1","baseline,0.98.3,2","baseline,0.98.5,1","baseline,0.98.5,2","qc,0.98.1,1","qc,0.98.1,2","qc,0.98.3,1","qc,0.98.3,2","qc,0.98.5,1","qc,0.98.5,2"})
    void agreesWithCompleteOriginalMatricesAndVariantMasks(String name,String version,int mode)throws Exception{
        try(var input=open(name)){
            var actual=GemmaKinship.calculate(input.data(),input.mask(),input.design(),options(name,mode,1_000_000,10_000_000));
            var expected=new String(resource(name,"expected-"+version+"-"+mode+".txt"),StandardCharsets.UTF_8).lines().toList();assertEquals(32,actual.samples().size());assertEquals(name.equals("qc")?28:32,actual.qcSamples());assertEquals(32,expected.size());
            for(int i=0;i<32;i++){
                String[] fields=expected.get(i).strip().split("\\s+");assertEquals(32,fields.length);
                for(int j=0;j<32;j++){double v=Double.parseDouble(fields[j]);assertEquals(v,actual.value(i,j),6e-6,"Full original matrix "+i+","+j);assertEquals(actual.value(i,j),actual.value(j,i),0);}
            }
            List<String> used=actual.predictors().stream().filter(p->p.status()==GemmaKinship.Status.USED).map(GemmaKinship.Predictor::id).toList();
            assertEquals(new String(resource(name,"expected-"+version+"-used.txt"),StandardCharsets.UTF_8).lines().toList(),used);assertEquals(used.size(),actual.predictorsUsed());
            if(name.equals("qc")){
                var byId=new HashMap<String,GemmaKinship.Predictor>();for(var p:actual.predictors())byId.put(p.id(),p);
                assertEquals(GemmaKinship.Status.COVARIATE_R2,byId.get("marker000").status());
                assertEquals(GemmaKinship.Status.FREQUENCY,byId.get("qc_constant_but_all_variable").status());
                assertEquals(GemmaKinship.Status.CONSTANT,byId.get("fixed_heterozygous").status());
                assertEquals(GemmaKinship.Status.HWE,byId.get("hwe_extreme").status());
                assertEquals(GemmaKinship.Status.MISSINGNESS,byId.get("missing_above").status());
                assertEquals(.125,byId.get("frequency_lower_tie").qcAllele1Frequency());
                assertEquals(.875,byId.get("frequency_upper_tie").qcAllele1Frequency());
                assertEquals(28,byId.get("missing_only_excluded").allCalled());
                assertEquals(0,byId.get("missing_only_excluded").qcMissing());
                assertEquals(4,byId.get("missing_tie").qcMissing());
            }
            double[] copy=actual.matrix();double saved=actual.value(0,0);copy[0]=999;assertEquals(saved,actual.value(0,0));
        }
    }
    @Test void capacitiesAndCancellationRefuseBeforeReturningAMatrix()throws Exception{
        try(var input=open("baseline")){
            assertThrows(IOException.class,()->GemmaKinship.calculate(input.data(),input.mask(),input.design(),options("baseline",2,1,10_000_000)));
            assertThrows(IOException.class,()->GemmaKinship.calculate(input.data(),input.mask(),input.design(),options("baseline",2,1_000_000,1)));
            assertThrows(CancellationException.class,()->GemmaKinship.calculate(input.data(),input.mask(),input.design(),options("baseline",2,1_000_000,10_000_000),()->{throw new CancellationException("controlled");}));
        }
    }
    @Test void invalidOrImplicitDesignIsNeverSilentlyRepaired()throws Exception{
        try(var input=open("baseline")){
            var opt=options("baseline",2,1_000_000,10_000_000);double[][] bad=new double[32][3];for(int i=0;i<32;i++)bad[i]=new double[]{1,i,i};
            assertThrows(IOException.class,()->GemmaKinship.calculate(input.data(),input.mask(),bad,opt));
            bad[0]=new double[]{0,0,0};assertThrows(IOException.class,()->GemmaKinship.calculate(input.data(),input.mask(),bad,opt));
            assertThrows(IOException.class,()->GemmaKinship.calculate(input.data(),new boolean[32],input.design(),opt));
            input.design()[0][1]=Double.NaN;assertThrows(IOException.class,()->GemmaKinship.calculate(input.data(),input.mask(),input.design(),opt));
        }
    }
}
