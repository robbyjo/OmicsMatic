package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReadQualityTrimmerTest {
    private ReadQualityTrimmer.Pipeline pipeline(String text){return new ReadQualityTrimmer.Pipeline(Arrays.stream(text.split(";")).map(s->{String[] f=s.split(":",-1);return new ReadQualityTrimmer.Step(f[0],Arrays.asList(f).subList(1,f.length));}).toList());}
    @Test void nativeResultsMatch4236ActualOriginalStepAndPipelineReferences()throws Exception{
        Path data=Path.of("src/test/resources/trimmomatic");var cases=Files.readAllLines(data.resolve("cases.tsv"));for(String version:List.of("0.39","0.41")){var expected=Files.readAllLines(data.resolve("reference-"+version+".tsv"));assertEquals(cases.size(),expected.size());for(int i=1;i<cases.size();i++){String[] c=cases.get(i).split("\t",-1),e=expected.get(i).split("\t",-1);assertEquals(c[0],e[0]);var r=pipeline(c[3]).apply(new FastqIO.Read("@"+c[0],c[4],c[5]),Integer.parseInt(c[1]),c[2].equals("2"));assertEquals(Boolean.parseBoolean(e[1]),r.retained(),version+":"+c[0]+":"+c[3]);if(r.retained()){assertEquals(e[2],r.read().sequence(),c[0]);assertEquals(e[3],r.read().quality(),c[0]);assertEquals(Integer.parseInt(e[4]),r.phredOffset(),c[0]);assertEquals(Integer.parseInt(e[5]),r.first(),c[0]);assertEquals(Integer.parseInt(e[6]),r.end(),c[0]);}}}
    }
    @Test void documentedQualityAndOrderedWindowBoundariesHaveIndependentKnownValues(){
        var read=new FastqIO.Read("@N","NnACGT","IIII!!");var result=pipeline("LEADING:20").apply(read,33,false);assertEquals("nACGT",result.read().sequence());assertEquals(1,result.first());assertFalse(pipeline("TRAILING:20").apply(new FastqIO.Read("@one","A","I"),33,false).retained());assertEquals("ACGT",pipeline("SLIDINGWINDOW:2:20").apply(new FastqIO.Read("@w","ACGTAA","IIII!!"),33,false).read().sequence());
        var a=pipeline("MINLEN:5;HEADCROP:3").apply(new FastqIO.Read("@o","ACGTAC","IIIIII"),33,false);var b=pipeline("HEADCROP:3;MINLEN:5").apply(new FastqIO.Read("@o","ACGTAC","IIIIII"),33,false);assertTrue(a.retained());assertEquals("TAC",a.read().sequence());assertFalse(b.retained());
    }
    @Test void explicitCropCapsAndMateSpecificSettingsUseOriginalCoordinateSpans(){
        var read=new FastqIO.Read("@r","ACGTACGTAC","IIIIIIIIII");var p=pipeline("HEADCROP:2:5:0:10");var a=p.apply(read,33,false);assertEquals("CGTAC",a.read().sequence());assertEquals(5,a.first());assertEquals("ACGTACGTAC",p.apply(read,33,true).read().sequence());assertEquals("ACGTA",pipeline("TAILCROP:2:5").apply(read,33,false).read().sequence());
    }
    @Test void invalidParametersEncodingAndUnsupportedLongMaxinfoFailWithoutGuessing(){
        for(String text:List.of("CROP:0","SLIDINGWINDOW:0:20","LEADING:NaN","MAXINFO:40:1.1","BASECOUNT:N:4:2","ILLUMINACLIP:a:2:30:10"))assertThrows(IllegalArgumentException.class,()->pipeline(text));assertThrows(IllegalArgumentException.class,()->pipeline("LEADING:1").apply(new FastqIO.Read("@q","A","!"),64,false));assertThrows(IllegalArgumentException.class,()->pipeline("MAXINFO:40:.5").apply(new FastqIO.Read("@long","A".repeat(1001),"I".repeat(1001)),33,false));assertEquals(1000,pipeline("CROP:1000;MAXINFO:40:.5").apply(new FastqIO.Read("@long","A".repeat(1001),"I".repeat(1001)),33,false).read().sequence().length());
    }
}
