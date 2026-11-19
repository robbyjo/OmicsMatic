/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.coloc;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ColocAbfTest {
    static Path fixture,nativeOutput;
    @BeforeAll static void capture() throws Exception {
        byte[] raw;
        try (var input=ColocAbfTest.class.getResourceAsStream("/org/jlinalg/coloc/abf-original603.zip")) {
            assertNotNull(input);raw=input.readAllBytes();
        }
        assertEquals("de00e6dfa5f7057fe16c8d59651868b9df46aa34365a6d8e4e451f9c677ed533",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        Path parent=Path.of(System.getProperty("jlinalg.test.artifacts","build/coloc-abf-tests")).toAbsolutePath();
        Files.createDirectories(parent);int i=1;while(Files.exists(parent.resolve("attempt-"+i)))i++;
        Path attempt=parent.resolve("attempt-"+i);Files.createDirectory(attempt);fixture=attempt.resolve("fixtures");Files.createDirectory(fixture);
        int count=0;long bytes=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))) {
            for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()) {
                Path p=fixture.resolve(e.getName()).normalize();assertTrue(p.startsWith(fixture));assertFalse(e.isDirectory());
                byte[] member=zip.readAllBytes();bytes+=member.length;assertTrue(bytes<=64L*1024*1024);assertTrue(member.length<=16*1024*1024);
                Files.createDirectories(p.getParent());Files.write(p,member);count++;
            }
        }
        assertEquals(265,count);nativeOutput=attempt.resolve("native");
        ColocAbfReferenceProbe.main(new String[]{fixture.resolve("inputs").toString(),nativeOutput.toString()});
    }
    @TestFactory Stream<DynamicTest> installedOriginalPackageReferences() throws Exception {
        var rows=table(fixture.resolve("inputs/cases.tsv"));
        assertEquals(31,rows.size());
        return rows.subList(1,rows.size()).stream().map(row->DynamicTest.dynamicTest(row[0],()->{
            Path actual=nativeOutput.resolve(row[0]), expected=fixture.resolve("original").resolve(row[0]);
            int code=Integer.parseInt(Files.readString(actual.resolve("STATUS.txt")).strip());
            assertEquals(Integer.parseInt(row[1]),code);
            if(code!=0) {assertFalse(Files.exists(actual.resolve("summary.tsv")));return;}
            assertEquals("FINITE",Files.readString(expected.resolve("STATUS.txt")).strip());
            for(String name:List.of("summary.tsv","priors.tsv","variants.tsv")) {
                var a=table(actual.resolve(name));var b=table(expected.resolve(name));assertEquals(b.size(),a.size());
                for(int r=1;r<a.size();r++)for(int c=0;c<a.get(0).length;c++) {
                    String column=a.get(0)[c];int bc=Arrays.asList(b.get(0)).indexOf(column);assertTrue(bc>=0);
                    String x=b.get(r)[bc],y=a.get(r)[c];
                    if(column.equals("snp")||column.equals("field"))assertEquals(x,y);
                    else {double value=Double.parseDouble(x);assertEquals(value,Double.parseDouble(y),2e-12*Math.max(1,Math.abs(value)),row[0]+" "+column);}
                }
            }
        }));
    }
    private static List<String[]> table(Path p) throws Exception {
        var rows=new ArrayList<String[]>();
        for(String line:Files.readAllLines(p,StandardCharsets.UTF_8)) {
            String[] fields=line.split("\t",-1);
            for(int c=0;c<fields.length;c++)if(fields[c].startsWith("\"")&&fields[c].endsWith("\""))
                fields[c]=fields[c].substring(1,fields[c].length()-1).replace("\"\"","\"");
            rows.add(fields);
        }
        return rows;
    }
    private static ColocAbfInput estimates(List<String> ids,double[] beta,double[] v) {
        return new ColocAbfInput(ColocAbfInput.TraitType.QUANTITATIVE,ids,beta,v,null,null,null,1.0,null,null);
    }
    @Test void agreesWithIndependentEnumerationOfAllCausalConfigurations() {
        var ids=List.of("a","b","c");double[] a={.2,.1,.3},b={-.1,.4,.2},va={.01,.04,.09},vb={.02,.03,.04};
        var options=new ColocAbfOptions(.01,.02,.001,null,null);
        var result=ColocAbf.analyze(estimates(ids,a,va),estimates(ids,b,vb),options);
        double[] first=new double[3],second=new double[3],h={1,0,0,0,0};double w=.15*.15;
        for(int i=0;i<3;i++) {
            first[i]=Math.sqrt(va[i]/(va[i]+w))*Math.exp(-a[i]*a[i]/(2*(va[i]+w))+a[i]*a[i]/(2*va[i]));
            second[i]=Math.sqrt(vb[i]/(vb[i]+w))*Math.exp(-b[i]*b[i]/(2*(vb[i]+w))+b[i]*b[i]/(2*vb[i]));
            h[1]+=.01*first[i];h[2]+=.02*second[i];h[4]+=.001*first[i]*second[i];
        }
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)if(i!=j)h[3]+=.01*.02*first[i]*second[j];
        double sum=Arrays.stream(h).sum();for(int i=0;i<5;i++)h[i]/=sum;
        assertArrayEquals(h,result.hypothesisPosteriors(),2e-15);
    }
    @Test void preservesInputAndReturnedArrays() {
        double[] beta={.1,.2},v={.01,.02},weights={1,2};var ids=new ArrayList<>(List.of("a","b"));
        var input=estimates(ids,beta,v);var options=new ColocAbfOptions(1e-4,1e-4,1e-5,weights,null);
        beta[0]=100;v[0]=100;weights[0]=100;ids.set(0,"changed");
        input.beta()[0]=50;options.trait1Weights()[0]=50;
        assertEquals(.1,input.beta()[0]);assertEquals(.01,input.variance()[0]);assertEquals(1,options.trait1Weights()[0]);assertEquals("a",input.variants().get(0));
        var result=ColocAbf.analyze(input,input,options);double old=result.hypothesisPosteriors()[4];result.hypothesisPosteriors()[4]=0;
        assertEquals(old,result.hypothesisPosteriors()[4]);assertThrows(UnsupportedOperationException.class,()->result.variants().clear());
    }
    @Test void rejectsDuplicateBlankNullAndMalformedIdentifiers() {
        for(var ids:List.of(Arrays.asList("a","a"),Arrays.asList("a"," "),Arrays.asList("a",null),Arrays.asList("a","\ud800")))
            assertThrows(IllegalArgumentException.class,()->estimates(ids,new double[]{1,2},new double[]{1,1}));
    }
    @Test void enforcesCapacityBeforeCopyingHugeInput() {
        var ids=Collections.nCopies(ColocAbfInput.MAX_VARIANTS+1,"a");
        assertThrows(IllegalArgumentException.class,()->estimates(ids,new double[0],new double[0]));
    }
    @Test void keepsSourceUnweightedConditionalColumnWithWeightedRegionalPriors() {
        var input=estimates(List.of("a","b","c"),new double[]{.1,.3,.2},new double[]{.01,.01,.01});
        var ordinary=ColocAbf.analyze(input,input);var weighted=ColocAbf.analyze(input,input,new ColocAbfOptions(1e-4,1e-4,1e-5,new double[]{10,1,1},null));
        for(int i=0;i<3;i++)assertEquals(ordinary.variants().get(i).sourceConditionalSharedPosterior(),weighted.variants().get(i).sourceConditionalSharedPosterior());
        assertNotEquals(ordinary.hypothesisPosteriors()[4],weighted.hypothesisPosteriors()[4]);
        assertTrue(weighted.warnings().stream().anyMatch(s->s.startsWith("SOURCE_SNP_PP_H4_UNWEIGHTED")));
    }
    @Test void doesNotConvertAllZeroWeightsIntoUniformPriors() {
        var input=estimates(List.of("a","b"),new double[]{.1,.2},new double[]{.01,.01});
        assertThrows(IllegalArgumentException.class,()->ColocAbf.analyze(input,input,new ColocAbfOptions(1e-4,1e-4,1e-5,new double[]{0,0},null)));
    }
    @Test void reportsWeakAssociationWithoutFilteringRows() {
        var input=estimates(List.of("a","b"),new double[]{0,0},new double[]{1,1});var result=ColocAbf.analyze(input,input);
        assertEquals(2,result.variants().size());assertTrue(result.warnings().stream().anyMatch(s->s.startsWith("NO_P_BELOW_1E_6")));
    }
    @Test void estimatesScaleUsingAllOriginalVariants() {
        double n=1000;double[] f={.1,.2,.3};double[] v=Arrays.stream(f).map(x->1/(2*n*x*(1-x))).toArray();
        var input=new ColocAbfInput(ColocAbfInput.TraitType.QUANTITATIVE,List.of("a","b","c"),new double[]{.1,.2,.3},v,null,f,n,null,null,null);
        var result=ColocAbf.analyze(input,input);assertEquals(1,result.trait1StandardDeviation(),3e-15);
        assertTrue(result.warnings().stream().anyMatch(s->s.startsWith("SDY_ESTIMATED")));
    }
    @Test void sharedPriorMayExceedTraitPriorAsInOriginalAbf() {
        var input=estimates(List.of("a","b"),new double[]{.1,.2},new double[]{.01,.01});
        var result=ColocAbf.analyze(input,input,new ColocAbfOptions(1e-4,1e-4,.003,null,null));
        assertEquals(.003,result.effectivePriors()[2]);
    }
    @Test void rejectsInvalidProbabilitiesWithoutClamping() {
        assertThrows(IllegalArgumentException.class,()->new ColocAbfInput(ColocAbfInput.TraitType.QUANTITATIVE,List.of("a"),null,null,new double[]{1.5},new double[]{.2},1000.0,null,null,null));
        assertThrows(IllegalArgumentException.class,()->new ColocAbfInput(ColocAbfInput.TraitType.QUANTITATIVE,List.of("a"),null,null,new double[]{0},new double[]{.2},1000.0,null,null,null));
    }
}
