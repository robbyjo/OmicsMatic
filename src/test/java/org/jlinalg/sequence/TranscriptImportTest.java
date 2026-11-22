package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class TranscriptImportTest {
    @Test void independentGeneSumsLengthsAndZeroExpressionFallbacks() {
        var input=new TranscriptImport.Estimates(List.of("a","b","c"),List.of("S1","S2","S3"),
                new double[][]{{1.5,0,2},{2.5,0,4},{0,0,0}},new double[][]{{1,0,3},{3,0,1},{0,0,0}},
                new double[][]{{10,20,30},{30,40,50},{100,200,300}});
        var result=TranscriptImport.summarize(input,Map.of("a","A","b","A","c","B"),TranscriptImport.Level.GENE,TranscriptImport.Scaling.NO,TranscriptImport.IdPolicy.EXACT,TranscriptImport.Unmapped.ERROR);
        assertEquals(List.of("A","B"),result.estimates().features());assertArrayEquals(new double[]{4,0,6},result.estimates().counts()[0]);
        assertArrayEquals(new double[]{4,0,4},result.estimates().abundance()[0]);assertArrayEquals(new double[]{25,Math.sqrt(25*35),35},result.estimates().length()[0],1e-12);
        assertArrayEquals(new double[]{200,200,200},result.estimates().length()[1],1e-12);assertEquals(4,result.fallbacks().size());
        assertEquals("other-sample-geometric-mean",result.fallbacks().get(0).method());assertEquals("all-sample-isoform-arithmetic-mean",result.fallbacks().get(1).method());
    }
    @Test void countsScaleToRetainedLibraryAndDtuUsesCommonGeneMedian() {
        var input=new TranscriptImport.Estimates(List.of("a","b","c"),List.of("S"),new double[][]{{2},{3},{100}},new double[][]{{1},{3},{100}},new double[][]{{10},{30},{100}});
        var result=TranscriptImport.summarize(input,Map.of("a","A","b","A"),TranscriptImport.Level.GENE,TranscriptImport.Scaling.SCALED_TPM,TranscriptImport.IdPolicy.EXACT,TranscriptImport.Unmapped.DROP);
        assertEquals(5,result.estimates().counts()[0][0]);assertFalse(result.transcripts().get(2).retained());
        var dtu=TranscriptImport.summarize(input,Map.of("a","A","b","A","c","C"),TranscriptImport.Level.TRANSCRIPT,TranscriptImport.Scaling.DTU_SCALED_TPM,TranscriptImport.IdPolicy.EXACT,TranscriptImport.Unmapped.ERROR);
        double denominator=20+60+10000;assertArrayEquals(new double[]{20*105/denominator},dtu.estimates().counts()[0],1e-12);assertArrayEquals(new double[]{60*105/denominator},dtu.estimates().counts()[1],1e-12);
    }
    @Test void twoHundredFourActualOriginalRImportsAcrossAllFormatsAndModes()throws Exception {
        try(var stream=getClass().getResourceAsStream("/tximport/reference.tsv")) {
            assertNotNull(stream);try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                String[] headers=reader.readLine().split("\t",-1);String line;int cases=0,values=0;
                while((line=reader.readLine())!=null) {
                    String[] fields=line.split("\t",-1);var row=new HashMap<String,String>();for(int i=0;i<headers.length;i++)row.put(headers[i],fields[i]);
                    var tx=List.of(row.get("transcripts").split(",",-1));var samples=List.of(row.get("samples").split(",",-1));int columns=samples.size();
                    double[][] counts=matrix(row.get("counts"),columns),abundance=matrix(row.get("abundance"),columns),length=matrix(row.get("length"),columns);
                    if(row.get("format").equals("rsem"))for(double[] lengths:length)for(int column=0;column<columns;column++)lengths[column]=Math.max(1,lengths[column]);
                    var policy=switch(row.get("policy")){case "exact"->TranscriptImport.IdPolicy.EXACT;case "strip-version"->TranscriptImport.IdPolicy.STRIP_VERSION;default->TranscriptImport.IdPolicy.STRIP_BAR;};
                    var scaling=switch(row.get("mode")){case "no"->TranscriptImport.Scaling.NO;case "scaledTPM"->TranscriptImport.Scaling.SCALED_TPM;case "lengthScaledTPM"->TranscriptImport.Scaling.LENGTH_SCALED_TPM;default->TranscriptImport.Scaling.DTU_SCALED_TPM;};
                    String[] genes=row.get("genes").split(",",-1);var mapping=new LinkedHashMap<String,String>();for(int i=0;i<genes.length;i++)if(!genes[i].isEmpty())mapping.put(TranscriptImport.matchingId(tx.get(i),policy),genes[i]);
                    var result=TranscriptImport.summarize(new TranscriptImport.Estimates(tx,samples,counts,abundance,length),mapping,row.get("level").equals("gene")?TranscriptImport.Level.GENE:TranscriptImport.Level.TRANSCRIPT,scaling,policy,row.get("unmapped").equals("drop")?TranscriptImport.Unmapped.DROP:TranscriptImport.Unmapped.ERROR).estimates();
                    var expectedFeatures=List.of(row.get("expected_features").split(",",-1));assertEquals(new HashSet<>(expectedFeatures),new HashSet<>(result.features()),"case "+cases);
                    for(String type:List.of("counts","abundance","length")) {
                        double[][] expected=matrix(row.get("expected_"+type),columns),actual=switch(type){case "counts"->result.counts();case "abundance"->result.abundance();default->result.length();};
                        for(int i=0;i<expectedFeatures.size();i++)for(int j=0;j<columns;j++) {
                            double target=expected[i][j];assertEquals(target,actual[result.features().indexOf(expectedFeatures.get(i))][j],Math.max(1e-10,Math.abs(target)*2e-12),"case "+cases+" "+type+" "+i+","+j);values++;
                        }
                    }
                    cases++;
                }
                assertEquals(204,cases);assertEquals(15615,values);
            }
        }
    }
    @Test void invalidDimensionsDomainsMappingsAndZeroDenominatorsFail() {
        assertThrows(IllegalArgumentException.class,()->new TranscriptImport.Estimates(List.of("a"),List.of("S"),new double[][]{{Double.NaN}},new double[][]{{1}},new double[][]{{1}}));
        assertThrows(IllegalArgumentException.class,()->new TranscriptImport.Estimates(List.of("a"),List.of("S"),new double[][]{{1}},new double[][]{{1}},new double[][]{{0}}));
        var input=new TranscriptImport.Estimates(List.of("a.1","a.2"),List.of("S"),new double[][]{{0},{0}},new double[][]{{0},{0}},new double[][]{{1},{2}});
        assertThrows(IllegalArgumentException.class,()->TranscriptImport.summarize(input,Map.of("a","A"),TranscriptImport.Level.GENE,TranscriptImport.Scaling.NO,TranscriptImport.IdPolicy.STRIP_VERSION,TranscriptImport.Unmapped.ERROR));
        assertThrows(IllegalArgumentException.class,()->TranscriptImport.summarize(input,Map.of(),TranscriptImport.Level.TRANSCRIPT,TranscriptImport.Scaling.SCALED_TPM,TranscriptImport.IdPolicy.EXACT,TranscriptImport.Unmapped.ERROR));
        assertThrows(IllegalArgumentException.class,()->TranscriptImport.summarize(input,Map.of("a.1","A"),TranscriptImport.Level.TRANSCRIPT,TranscriptImport.Scaling.DTU_SCALED_TPM,TranscriptImport.IdPolicy.EXACT,TranscriptImport.Unmapped.DROP));
        assertThrows(IllegalArgumentException.class,()->TranscriptImport.summarize(input,Map.of("a.1","A"),TranscriptImport.Level.GENE,TranscriptImport.Scaling.NO,TranscriptImport.IdPolicy.EXACT,TranscriptImport.Unmapped.ERROR));
    }
    @Test void defensiveCopiesKeepCallerAndResultIsolated() {
        double[][] values={{1}};var input=new TranscriptImport.Estimates(List.of("001"),List.of("1"),values,values,values);values[0][0]=99;input.counts()[0][0]=42;
        var result=TranscriptImport.summarize(input,Map.of(),TranscriptImport.Level.TRANSCRIPT,TranscriptImport.Scaling.NO,TranscriptImport.IdPolicy.EXACT,TranscriptImport.Unmapped.ERROR);
        assertEquals(1,result.estimates().counts()[0][0]);result.estimates().counts()[0][0]=77;assertEquals(1,result.estimates().counts()[0][0]);assertThrows(UnsupportedOperationException.class,()->result.transcripts().clear());
    }
    private static double[][] matrix(String text,int columns){String[] values=text.split(",");double[][] result=new double[values.length/columns][columns];for(int i=0;i<values.length;i++)result[i/columns][i%columns]=Double.parseDouble(values[i]);return result;}
}
