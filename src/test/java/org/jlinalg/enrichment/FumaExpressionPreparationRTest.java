package org.jlinalg.enrichment;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Independent published-method base-R controls; not original FUMA runtime parity. */
class FumaExpressionPreparationRTest {
    private static List<String[]> table(Map<String,byte[]> files,String path){return new String(files.get(path),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
    @TestFactory Stream<DynamicTest> independentPublishedMethodRControls()throws Exception{
        byte[] raw;try(var in=getClass().getResourceAsStream("fuma-expression-preparation/independent-R-controls.zip")){assertNotNull(in);raw=in.readNBytes(131072);assertEquals(-1,in.read());}assertEquals(52405,raw.length);assertEquals("ae92c5a9f1c63ea778fabf8db9bf5d325213cc2e1f07781b0eb76ce6e252423a",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        Map<String,byte[]> files=new HashMap<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){long bytes=0;for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()){assertFalse(e.isDirectory());assertFalse(e.getName().contains(".."));assertFalse(e.getName().contains("\\"));byte[] b=zip.readNBytes(1048577);bytes+=b.length;assertTrue(b.length<=1048576&&bytes<4194304);assertNull(files.put(e.getName(),b));}}
        assertTrue(new String(files.get("receipt.json"),StandardCharsets.UTF_8).contains("not original FUMA"));List<String> cases=new ArrayList<>();for(int n:new int[]{3,4,5,63,64,65,129,200})for(String prefix:List.of("complete-","subset-"))cases.add(prefix+n);return cases.stream().map(name->DynamicTest.dynamicTest(name,()->compare(files,name)));
    }
    private static void close(double expected,double actual){assertTrue(Double.isFinite(expected));assertTrue(Double.isFinite(actual));assertEquals(expected,actual,5e-14+1e-12*Math.abs(expected));}
    private static void compare(Map<String,byte[]> files,String name){var input=table(files,name+"/input.tsv");List<String> ids=List.copyOf(Arrays.asList(input.get(0)).subList(1,input.get(0).length));List<FumaExpressionPreparation.Sample> selected=table(files,name+"/samples.tsv").stream().skip(1).map(r->new FumaExpressionPreparation.Sample(r[0],r[1])).toList();List<String> labels=table(files,name+"/labels.tsv").stream().skip(1).map(r->r[0]).toList();var frame=FumaExpressionPreparation.frame(ids,selected,labels,name.startsWith("subset-"),FumaExpressionPreparation.Limits.defaults(),()->{});var expected=table(files,name+"/expected.tsv");assertEquals(13,input.size());assertEquals(input.size(),expected.size());assertEquals(5+3*labels.size(),expected.get(0).length);
        for(int i=1;i<input.size();i++){String[] r=input.get(i),e=expected.get(i);assertEquals(r[0],e[0]);double[] values=new double[ids.size()];for(int j=0;j<values.length;j++)values[j]=Double.parseDouble(r[j+1]);var actual=FumaExpressionPreparation.prepare(r[0],values,frame,()->{});assertEquals(e[1],actual.disposition().name(),name+" "+r[0]+": strict raw eligibility differs");assertEquals(Integer.parseInt(e[2]),actual.cappedSelectedValues());close(Double.parseDouble(e[3]),actual.sampleMeanLog2());close(Double.parseDouble(e[4]),actual.labelMeanLog2());double[][] matrices={actual.rawMeans(),actual.log2Means(),actual.centeredMeans()};for(int k=0;k<3;k++)for(int j=0;j<labels.size();j++){
            String source=e[5+k*labels.size()+j];
            if(source.equals("Inf")){
                // Retain the actual base-R overflow. Only the constructed,
                // constant maximum-double raw row admits this exact departure.
                assertEquals(0,k);assertEquals("ENSG7.7",r[0]);for(double value:values)assertEquals(Double.MAX_VALUE,value);assertEquals(Double.MAX_VALUE,matrices[k][j]);
            }else close(Double.parseDouble(source),matrices[k][j]);
        }}
    }
}
