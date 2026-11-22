package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.genetics.PlinkAssociations.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkConfidenceIntervalsTest {
    @TempDir Path root;
    private Row row(Engine engine,Model model,Map<String,String> extra,boolean reportable) {
        var raw=new LinkedHashMap<String,String>();raw.put(engine==Engine.PLINK1?"SNP":"ID","rs1");raw.put(model==Model.LINEAR?"BETA":"OR",model==Model.LINEAR?".3823":"1.397");raw.put(engine==Engine.PLINK2 && model!=Model.LINEAR?"LOG(OR)_SE":"SE",".2112");raw.putAll(extra);
        return new Row("rs1","C","A",true,128,model==Model.FIRTH,reportable?.3823:Double.NaN,model==Model.LINEAR?Double.NaN:1.397,reportable?.2112:Double.NaN,reportable?1.81:Double.NaN,reportable?.07271:Double.NaN,reportable?1.138:Double.NaN,reportable?Status.VALID:Status.UNREPORTABLE_UPSTREAM_ERROR,reportable?".":"UNFINISHED",raw);
    }
    @Test void betaBoundsRetainOriginalValuesAndFractionalRequestedLevel() throws Exception {
        var value=PlinkConfidenceIntervals.read(row(Engine.PLINK1,Model.LINEAR,Map.of("L95","-.03166","U95",".7962"),true),Engine.PLINK1,Model.LINEAR,.955);
        assertEquals(.955,value.requestedLevel());assertEquals("L95",value.lowerColumn());assertEquals(PlinkConfidenceIntervals.Scale.BETA,value.scale());assertEquals(-.03166,value.lower());assertEquals(.7962,value.upper());assertTrue(value.reportable());
    }
    @Test void binaryLimitsKeepOddsRatioScaleAndDoNotBecomeBetaLimits() throws Exception {
        var value=PlinkConfidenceIntervals.read(row(Engine.PLINK2,Model.LOGISTIC,Map.of("L95.5",".571912","U95.5","3.41114"),true),Engine.PLINK2,Model.LOGISTIC,.955);
        assertEquals(PlinkConfidenceIntervals.Scale.ODDS_RATIO,value.scale());assertEquals(.571912,value.lower());assertEquals(3.41114,value.upper());assertNotEquals(Math.log(.571912),value.lower());
    }
    @Test void engineSpecificRoundedHeadersDoNotReplaceTheRequestedLevel() throws Exception {
        for(double level:new double[]{.29,.58,.01,.999}) {
            String label=Integer.toString((int)(level*100+1e-8));var value=PlinkConfidenceIntervals.read(row(Engine.PLINK1,Model.LINEAR,Map.of("L"+label,"-.1","U"+label,".9"),true),Engine.PLINK1,Model.LINEAR,level);assertEquals(level,value.requestedLevel());
        }
        var rounded=PlinkConfidenceIntervals.read(row(Engine.PLINK2,Model.LINEAR,Map.of("L100","-.1","U100",".9"),true),Engine.PLINK2,Model.LINEAR,.9999999);assertEquals(.9999999,rounded.requestedLevel());
        assertTrue(PlinkConfidenceIntervals.read(row(Engine.PLINK2,Model.LINEAR,Map.of("L12.3457","-.1","U12.3457",".9"),true),Engine.PLINK2,Model.LINEAR,.123456789123).reportable());
        assertThrows(IOException.class,()->PlinkConfidenceIntervals.read(row(Engine.PLINK1,Model.LINEAR,Map.of("L95","-.1","U95",".9"),true),Engine.PLINK1,Model.LINEAR,.9));
    }
    @Test void missingAmbiguousOrMismatchedConfidencePairsFail() {
        for(Map<String,String> fields:java.util.List.of(Map.of("L95","0"),Map.of("L95","0","U90","1"),Map.of("L95","0","U95","1","L90","0","U90","1"))) assertThrows(IOException.class,()->PlinkConfidenceIntervals.read(row(Engine.PLINK2,Model.LINEAR,fields,true),Engine.PLINK2,Model.LINEAR,.95));
        assertThrows(IOException.class,()->PlinkConfidenceIntervals.read(row(Engine.PLINK2,Model.LINEAR,Map.of("L95","0","U95","1"),true),Engine.PLINK1,Model.LINEAR,.95));
        assertThrows(IOException.class,()->PlinkConfidenceIntervals.read(row(Engine.PLINK2,Model.LINEAR,Map.of("L95","0","U95","1"),true),Engine.PLINK2,Model.LOGISTIC,.95));
    }
    @Test void unavailableReversedOrZeroOddsBoundsDoNotInvalidateUsableCoreEffects() throws Exception {
        for(Map<String,String> fields:java.util.List.of(Map.of("L95","0","U95","1"),Map.of("L95","2","U95","1"),Map.of("L95","NA","U95","1"),Map.of("L95","1","U95","inf"))) {
            Row row=row(Engine.PLINK2,Model.LOGISTIC,fields,true);var value=PlinkConfidenceIntervals.read(row,Engine.PLINK2,Model.LOGISTIC,.95);assertEquals(PlinkConfidenceIntervals.Status.UNREPORTABLE_BOUNDS,value.status());assertTrue(Double.isNaN(value.lower()));assertTrue(Double.isNaN(value.upper()));assertTrue(row.effectReportable());
        }
    }
    @Test void upstreamErrorSuppressesFiniteProvisionalIntervals() throws Exception {
        var value=PlinkConfidenceIntervals.read(row(Engine.PLINK2,Model.FIRTH,Map.of("L95","1.1","U95","10000"),false),Engine.PLINK2,Model.FIRTH,.95);assertEquals(PlinkConfidenceIntervals.Status.UNREPORTABLE_ESTIMATE,value.status());assertTrue(Double.isNaN(value.lower()));assertTrue(Double.isNaN(value.upper()));
    }
    @Test void malformedLimitsAndMissingReportedSeFailStructurally() {
        assertThrows(IOException.class,()->PlinkConfidenceIntervals.read(row(Engine.PLINK2,Model.LINEAR,Map.of("L95","nonsense","U95","1"),true),Engine.PLINK2,Model.LINEAR,.95));
        var raw=Map.of("SNP","rs1","BETA",".5","L95","0","U95","1");Row missing=new Row("rs1","C","A",true,128,false,.5,Double.NaN,Double.NaN,1,.3,.5,Status.VALID_WITHOUT_REPORTED_SE,".",raw);assertThrows(IOException.class,()->PlinkConfidenceIntervals.read(missing,Engine.PLINK1,Model.LINEAR,.95));
    }
    @Test void originalLevelDomainAndIntervalConstructionAreChecked() {
        for(double level:new double[]{0,.009,1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->PlinkConfidenceIntervals.validateLevel(level));
        PlinkConfidenceIntervals.validateLevel(.01);PlinkConfidenceIntervals.validateLevel(Math.nextDown(1.0));
        assertThrows(IllegalArgumentException.class,()->new PlinkConfidenceIntervals.Interval(.95,PlinkConfidenceIntervals.Scale.BETA,Double.NaN,1,"L95","U95",PlinkConfidenceIntervals.Status.REPORTED));
        assertThrows(IllegalArgumentException.class,()->new PlinkConfidenceIntervals.Interval(.95,PlinkConfidenceIntervals.Scale.BETA,0,1,"L95","U95",PlinkConfidenceIntervals.Status.UNREPORTABLE_BOUNDS));
    }
    private Path unpack(String name) throws Exception {
        Path target=root.resolve(name.replace(".gz",""));try(var raw=getClass().getResourceAsStream("/org/jlinalg/genetics/plink-confidence/"+name)) {assertNotNull(raw);try(var zipped=new GZIPInputStream(raw)){Files.copy(zipped,target);}}return target;
    }
    @Test void all2880ActualOriginalRowsPreserveLevelsScalesBoundsAndErrorSuppression() throws Exception {
        var alleles=new LinkedHashMap<String,Alleles>();
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/genetics/plink-associations/variants.tsv.gz")) {assertNotNull(raw);try(var gzip=new GZIPInputStream(raw)){String[] lines=new String(gzip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\n");for(int i=1;i<lines.length;i++){String[] f=lines[i].strip().split("\t");alleles.put(f[0],new Alleles(f[1],f[2]));}}}
        String index;try(var raw=getClass().getResourceAsStream("/org/jlinalg/genetics/plink-confidence/cases.tsv")){assertNotNull(raw);index=new String(raw.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        long[] counts={0,0,0};int cases=0;
        for(String line:index.split("\n")) {
            if(line.startsWith("name\t") || line.isBlank())continue;String[] fields=line.strip().split("\t");Engine engine=Engine.valueOf(fields[1]);Model model=Model.valueOf(fields[2]);double level=Double.parseDouble(fields[3]);cases++;
            var scan=PlinkAssociations.scan(unpack(fields[0]),engine,model,alleles,128,Limits.defaults(),row->{
                try {var interval=PlinkConfidenceIntervals.read(row,engine,model,level);assertEquals(level,interval.requestedLevel());assertEquals(model==Model.LINEAR?PlinkConfidenceIntervals.Scale.BETA:PlinkConfidenceIntervals.Scale.ODDS_RATIO,interval.scale());counts[0]++;
                    if(!row.effectReportable()){assertEquals(PlinkConfidenceIntervals.Status.UNREPORTABLE_ESTIMATE,interval.status());assertTrue(Double.isNaN(interval.lower()));assertTrue(Double.isNaN(interval.upper()));counts[1]++;if(row.upstreamError().equals("UNFINISHED")){assertTrue(Double.isFinite(Double.parseDouble(row.rawFields().get(interval.lowerColumn()))));counts[2]++;}}
                    else {assertTrue(interval.reportable());assertEquals(Double.parseDouble(row.rawFields().get(interval.lowerColumn())),interval.lower());assertEquals(Double.parseDouble(row.rawFields().get(interval.upperColumn())),interval.upper());assertTrue(Double.isFinite(row.standardError()));}
                }catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
            });assertEquals(96,scan.rows());
        }
        assertEquals(30,cases);assertArrayEquals(new long[]{2880,6,4},counts);
    }
}
