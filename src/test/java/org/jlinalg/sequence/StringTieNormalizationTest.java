/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StringTieNormalizationTest {
    private static final String PREFIX="/org/jlinalg/sequence/";
    private static final Map<String,String> ARITHMETIC=fixture("stringtie-normalization/original-normalization-arithmetic.zip","801f59a33b3cd20dfd7658cff5e1ec0f73544d41c5f6ed2a0cf8bd22dc0b91d4",65);
    private static final Map<String,String> TABLES=fixture("stringtie-tables/original-table-controls.zip","ef1939713a3356d8a49ee7c784c4b19940b446e932acfd8a4476618ac44df71a",421);
    private static final String BASE="baseline/3.0.3/estimate";
    private static final StringTieGtf.Limits GTF_LIMITS=new StringTieGtf.Limits(10000,10000,1000,100,100,16384,1048576);
    private static final StringTieTables.Limits TABLE_LIMITS=new StringTieTables.Limits(10000,16384,1048576,1000,1000000);

    private static Map<String,String> fixture(String path,String sha,int expected) {
        try {
            byte[] raw;try(var input=StringTieNormalizationTest.class.getResourceAsStream(PREFIX+path)){assertNotNull(input);raw=input.readAllBytes();}
            assertEquals(sha,java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            var files=new HashMap<String,String>();
            try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){java.util.zip.ZipEntry e;
                while((e=zip.getNextEntry())!=null){assertFalse(e.isDirectory());assertFalse(e.getName().contains(".."));byte[] bytes=zip.readNBytes(1048577);assertTrue(bytes.length<=1048576);assertNull(files.put(e.getName(),new String(bytes,StandardCharsets.UTF_8)));}}
            assertEquals(expected,files.size());return Map.copyOf(files);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    private static List<String> versions(){return List.of("1.3.0","1.3.6","2.1.2","2.1.7","2.2.1","3.0.0","3.0.3");}
    static Stream<Arguments> extractedSourceCases(){
        List<String[]> cases=ARITHMETIC.get("cases.tsv").lines().skip(1).map(l->l.split("\t",-1)).toList();assertEquals(144,cases.size());
        var rows=new ArrayList<Arguments>();
        for(String version:versions()){
            List<String[]> outputs=ARITHMETIC.get(version+"/original-values.tsv").lines().map(l->l.split("\t",-1)).toList();assertEquals(144,outputs.size());
            for(int i=0;i<cases.size();i++){String[] c=cases.get(i),v=outputs.get(i);assertEquals(c[0],v[0]);assertEquals(7,v.length);rows.add(Arguments.of(version,c[0],c[1],Double.valueOf(c[2]),Double.valueOf(c[3]),v));}}
        assertEquals(1008,rows.size());return rows.stream();
    }
    private static double original(String raw){return switch(raw){case "nan","-nan"->Double.NaN;case "inf"->Double.POSITIVE_INFINITY;case "-inf"->Double.NEGATIVE_INFINITY;default->Double.valueOf(raw);};}
    private static void sameSourceNumber(String hex,String fixed,StringTieNormalization.Value actual){
        double expected=original(hex);
        if(Double.isNaN(expected)){assertEquals(StringTieNormalization.NumberClass.NAN,actual.numberClass());assertTrue(Double.isNaN(actual.value()));assertNull(actual.fixed6());}
        else if(!Double.isFinite(expected)){assertEquals(expected,actual.value());assertEquals(expected>0?StringTieNormalization.NumberClass.POSITIVE_INFINITY:StringTieNormalization.NumberClass.NEGATIVE_INFINITY,actual.numberClass());assertNull(actual.fixed6());}
        else {assertEquals(StringTieNormalization.NumberClass.FINITE,actual.numberClass());assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(actual.value()));assertEquals(fixed,actual.fixed6());}
    }
    @ParameterizedTest(name="source {0} case {1}") @MethodSource("extractedSourceCases")
    void all1008UnmodifiedExtractedSourceArithmeticCasesMatch(String version,String id,String coverage,double fragments,double covSum,String[] values){
        var result=StringTieNormalization.finalizeCoverage(StringTieNormalization.Edition.of(version),coverage,fragments,covSum);
        assertEquals(coverage,result.coverageToken());sameSourceNumber(values[1],values[4],result.readbackCoverage());sameSourceNumber(values[2],values[5],result.fpkm());sameSourceNumber(values[3],values[6],result.tpm());
    }
    private static Map<String,String> family(String path){
        var result=new HashMap<String,String>();for(String name:List.of("guide.gtf","transcripts.gtf","t_data.ctab","e_data.ctab","i_data.ctab","e2t.ctab","i2t.ctab"))result.put(name,TABLES.get(path+"/"+name));
        result.put("genes",TABLES.get(path+(path.startsWith("baseline")?"/gene-abundance.tsv":"/genes.tsv")));assertTrue(result.values().stream().allMatch(java.util.Objects::nonNull));return result;
    }
    private static StringTieGtf.Document gtf(String raw,StringTieGtf.Profile profile)throws IOException{return StringTieGtf.read(new StringReader(raw),Map.of("chrA",12000L,"chrB",10000L),profile,GTF_LIMITS,()->{});}
    private static StringTieTables.Audit tables(Map<String,String> family,StringTieGtf.PopulationPolicy policy)throws IOException{
        return StringTieTables.auditEstimation(new StringTieTables.Inputs(new StringReader(family.get("genes")),new StringReader(family.get("t_data.ctab")),new StringReader(family.get("e_data.ctab")),new StringReader(family.get("i_data.ctab")),new StringReader(family.get("e2t.ctab")),new StringReader(family.get("i2t.ctab"))),
                gtf(family.get("guide.gtf"),StringTieGtf.Profile.ANNOTATION),gtf(family.get("transcripts.gtf"),StringTieGtf.Profile.ESTIMATION),policy,TABLE_LIMITS,()->{});
    }
    private static StringTieNormalization.Audit audit(Map<String,String> family,String version)throws IOException{
        return StringTieNormalization.auditEstimation(StringTieNormalization.Edition.of(version),gtf(family.get("transcripts.gtf"),StringTieGtf.Profile.ESTIMATION),tables(family,StringTieGtf.PopulationPolicy.COMPLETE),()->{});
    }
    static Stream<Arguments> originalFamilies(){return versions().stream().flatMap(v->Stream.of(Arguments.of(v,"baseline/"+v+"/estimate"),Arguments.of(v,"baseline/"+v+"/empty-estimate"),
            Arguments.of(v,"boundaries/"+v+"/unknown_first"),Arguments.of(v,"boundaries/"+v+"/known_first"),Arguments.of(v,"wildcard/"+v)));}
    @ParameterizedTest @MethodSource("originalFamilies")
    void all35ActualWholeToolFamiliesPreserveReadbackAndHaveCompatibleCommonFactors(String version,String path)throws Exception{
        var result=audit(family(path),version);assertEquals(4,result.rows().size());assertTrue(result.omittedTranscriptIds().isEmpty());
        for(var row:result.rows())assertEquals(row.sourceCoverage().fixed6(),row.originalTableCoverage());
        boolean empty=path.contains("empty-estimate")||path.startsWith("wildcard");
        if(empty){assertEquals(StringTieNormalization.Resolution.NO_POSITIVE_NUMERATOR,result.fpkmDenominator().resolution());assertEquals(StringTieNormalization.Resolution.NO_POSITIVE_NUMERATOR,result.tpmDenominator().resolution());assertNull(result.fpkmDenominator().activeBranchBounds());}
        else {assertEquals(StringTieNormalization.Resolution.CONSTRAINED,result.fpkmDenominator().resolution());assertEquals(StringTieNormalization.Resolution.CONSTRAINED,result.tpmDenominator().resolution());
            assertTrue(result.fpkmDenominator().activeBranchBounds().contains(path.startsWith("baseline")?28000:14000));assertNotNull(result.tpmDenominator().activeBranchBounds().maximum());}
    }
    @Test void zeroDenominatorGuardsAndOldNonfiniteResultsStayDifferent(){
        var old=StringTieNormalization.finalizeCoverage(StringTieNormalization.Edition.V1_3_0,"0.000000",0,0);
        var current=StringTieNormalization.finalizeCoverage(StringTieNormalization.Edition.V3_0_3,"0.000000",0,0);
        assertEquals(StringTieNormalization.NumberClass.NAN,old.fpkm().numberClass());assertNull(old.fpkm().fixed6());assertEquals("0.000000",current.fpkm().fixed6());
    }
    @Test void negativeZeroClampDependsOnTheExactEdition(){
        var old=StringTieNormalization.finalizeCoverage(StringTieNormalization.Edition.V1_3_0,"-0.000000",1,1);
        var patch=StringTieNormalization.finalizeCoverage(StringTieNormalization.Edition.V1_3_6,"-0.000000",1,1);
        var current=StringTieNormalization.finalizeCoverage(StringTieNormalization.Edition.V3_0_3,"-0.000000",1,1);
        assertEquals("-0.000000",old.readbackCoverage().fixed6());assertEquals("0.000000",patch.readbackCoverage().fixed6());assertEquals("-0.000000",current.readbackCoverage().fixed6());
    }
    @ParameterizedTest @ValueSource(strings={"coverage","fpkm","matched-fpkm","tpm","zero-with-positive-output"})
    void corruptReadbackAndIncompatibleTranscriptFactorsRejectNormalization(String mutation)throws Exception{
        var f=family(BASE);
        switch(mutation){case "coverage"->f.put("t_data.ctab",f.get("t_data.ctab").replace("21.337809","21.337808"));
            case "fpkm"->f.put("t_data.ctab",f.get("t_data.ctab").replace("762064.607143","862064.607143"));
            case "matched-fpkm"->{f.put("t_data.ctab",f.get("t_data.ctab").replace("762064.607143","862064.607143"));f.put("transcripts.gtf",f.get("transcripts.gtf").replace("762064.607143","862064.607143"));}
            case "tpm"->f.put("transcripts.gtf",f.get("transcripts.gtf").replace("216849.625203","316849.625203"));
            case "zero-with-positive-output"->{f.put("t_data.ctab",f.get("t_data.ctab").replace("21.337809","0.000000"));f.put("transcripts.gtf",f.get("transcripts.gtf").replace("21.337809","0.000000"));}
            default->throw new AssertionError();}
        assertThrows(IOException.class,()->audit(f,"3.0.3"));
    }
    @Test void uniformUnknownFactorIsNotClaimedAsTheRecoveredTrueDenominator()throws Exception{
        var f=family(BASE);String raw=f.get("transcripts.gtf");var originalRows=audit(family(BASE),"3.0.3").rows();
        List<String[]> inputs=ARITHMETIC.get("cases.tsv").lines().skip(1).map(l->l.split("\t",-1)).toList();
        List<String[]> outputs=ARITHMETIC.get("3.0.3/original-values.tsv").lines().map(l->l.split("\t",-1)).toList();
        for(var row:originalRows){if(row.sourceCoverage().value()==0)continue;
            int index=java.util.stream.IntStream.range(0,inputs.size()).filter(i->inputs.get(i)[1].equals(row.originalCoverage())&&Double.valueOf(inputs.get(i)[3])==46.666666666666664).findFirst().orElseThrow();
            raw=raw.replace("TPM \""+row.originalTpm()+"\"","TPM \""+outputs.get(index)[6]+"\"");}
        f.put("transcripts.gtf",raw);
        var old=audit(family(BASE),"3.0.3");var changed=audit(f,"3.0.3");
        assertEquals(StringTieNormalization.Resolution.CONSTRAINED,changed.tpmDenominator().resolution());
        assertTrue(changed.tpmDenominator().activeBranchBounds().maximum().compareTo(old.tpmDenominator().activeBranchBounds().minimum())<0);
    }
    @Test void allZeroWithPositiveCoverageRemainsAnUnresolvedConditionalBranch()throws Exception{
        var f=family(BASE);f.put("transcripts.gtf",f.get("transcripts.gtf").replaceAll("FPKM \"[^\"]+\"","FPKM \"0.000000\"").replaceAll("TPM \"[^\"]+\"","TPM \"0.000000\""));
        String[] lines=f.get("t_data.ctab").split("\n",-1);for(int i=1;i<lines.length-1;i++){String[] c=lines[i].split("\t",-1);c[11]="0.000000";lines[i]=String.join("\t",c);}f.put("t_data.ctab",String.join("\n",lines));
        var result=audit(f,"3.0.3");assertEquals(StringTieNormalization.Resolution.ALL_ZERO_UNRESOLVED,result.fpkmDenominator().resolution());assertTrue(result.fpkmDenominator().positiveNumerators()>0);
        assertFalse(result.fpkmDenominator().activeBranchBounds().contains(28000));
    }
    @Test void literalZeroCannotMasqueradeAsPositiveCoverageShortcut()throws Exception{
        var f=family(BASE);f.put("transcripts.gtf",f.get("transcripts.gtf").replace("TPM \"216849.625203\"","TPM \"0.0\""));assertThrows(IOException.class,()->audit(f,"3.0.3"));
    }
    @Test void exactOriginalPrintedTpmAboveOneMillionRemainsUnchanged()throws Exception{
        var result=audit(family("boundaries/3.0.3/unknown_first"),"3.0.3");assertEquals("1000000.007143",result.rows().stream().filter(r->r.transcriptId().equals("t.plus")).findFirst().orElseThrow().originalTpm());
    }
    @Test void missingOriginalGtfIdentityNeverReceivesAQuantitativeNormalizationRow()throws Exception{
        var f=family(BASE);f.put("transcripts.gtf",f.get("transcripts.gtf").lines().filter(l->!l.contains("tc.uncovered")).collect(java.util.stream.Collectors.joining("\n","","\n")));
        var result=StringTieNormalization.auditEstimation(StringTieNormalization.Edition.V3_0_3,gtf(f.get("transcripts.gtf"),StringTieGtf.Profile.ESTIMATION),tables(f,StringTieGtf.PopulationPolicy.RETAIN_OMISSIONS),()->{});
        assertEquals(3,result.rows().size());assertEquals(List.of("tc.uncovered"),result.omittedTranscriptIds());
    }
    @Test void normalizationCannotRebindAChangedInternalExonPopulationWithTheSameSpanAndLength()throws Exception{
        var original=family(BASE);var checked=tables(original,StringTieGtf.PopulationPolicy.COMPLETE);
        String gtf=original.get("transcripts.gtf").replace("exon\t101\t250","exon\t101\t249").replace("exon\t401\t550","exon\t400\t550");
        assertThrows(IOException.class,()->StringTieNormalization.auditEstimation(StringTieNormalization.Edition.V3_0_3,gtf(gtf,StringTieGtf.Profile.ESTIMATION),checked,()->{}));
    }
    @Test void normalizationCannotLoseAnEmittedIdentityOrChangeOriginalEmissionOrder()throws Exception{
        var f=family(BASE);var checked=tables(f,StringTieGtf.PopulationPolicy.COMPLETE);
        String changed=f.get("transcripts.gtf").lines().filter(l->!l.contains("tc.uncovered")).collect(java.util.stream.Collectors.joining("\n","","\n"));
        assertThrows(IOException.class,()->StringTieNormalization.auditEstimation(StringTieNormalization.Edition.V3_0_3,gtf(changed,StringTieGtf.Profile.ESTIMATION),checked,()->{}));
    }
    @ParameterizedTest @ValueSource(strings={"","v3.0.3","latest","2.2.3","3.0.2"})
    void editionIsExplicitAndNeverGuessed(String edition){assertThrows(IllegalArgumentException.class,()->StringTieNormalization.Edition.of(edition));}
    @Test void invalidDenominatorsAndSourceTokensFailBeforeArithmetic(){
        for(double denominator:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->StringTieNormalization.finalizeCoverage(StringTieNormalization.Edition.V3_0_3,"1.000000",denominator,1));
        for(String token:List.of("1e5","NaN","Infinity"," 1.0","+1.0","01.0","1".repeat(1025)))assertThrows(IllegalArgumentException.class,()->StringTieNormalization.finalizeCoverage(StringTieNormalization.Edition.V3_0_3,token,1,1));
    }
    @Test void normalizationCancellationAndResultImmutabilityAreExplicit()throws Exception{
        var f=family(BASE);var checked=tables(f,StringTieGtf.PopulationPolicy.COMPLETE);var parsed=gtf(f.get("transcripts.gtf"),StringTieGtf.Profile.ESTIMATION);
        assertThrows(CancellationException.class,()->StringTieNormalization.auditEstimation(StringTieNormalization.Edition.V3_0_3,parsed,checked,()->{throw new CancellationException();}));
        var result=StringTieNormalization.auditEstimation(StringTieNormalization.Edition.V3_0_3,parsed,checked,()->{});assertThrows(UnsupportedOperationException.class,()->result.rows().clear());assertThrows(UnsupportedOperationException.class,()->result.omittedTranscriptIds().clear());
    }
    @Test void denominatorIntervalsValidateTheirDomainWithoutInventingAnUpperLimit(){
        var range=new StringTieNormalization.Bounds(BigDecimal.ONE,null);assertTrue(range.contains(2));assertFalse(range.contains(0));assertFalse(range.contains(Double.NaN));assertFalse(range.contains(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->new StringTieNormalization.Bounds(BigDecimal.ONE,BigDecimal.ZERO));
    }
}
