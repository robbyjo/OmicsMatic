/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original-package controls; no R execution or implementation-derived oracle during tests. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SignacAnnotationControlsTest {
    @TempDir static Path root;
    private Map<String,Object> cases,original;
    private final AtomicInteger scratch=new AtomicInteger();
    private final SignacFragments.Limits defaults=SignacFragments.Limits.defaults();
    @BeforeAll void fixture()throws Exception{
        byte[] raw;try(var in=getClass().getResourceAsStream("/org/jlinalg/sequence/signac-annotations/original-controls.zip")){assertNotNull(in);raw=in.readAllBytes();}
        assertEquals("abefe970bb2ed1cb7418629c2a8af3aa982c198df4d7ea0755c7b924c38f207f",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){Path path=root.resolve(entry.getName()).normalize();assertTrue(path.startsWith(root));Files.createDirectories(path.getParent());Files.write(path,zip.readAllBytes());}}
        cases=object(SimpleJson.parse(Files.readString(root.resolve("cases.json"),StandardCharsets.UTF_8)));original=object(SimpleJson.parse(Files.readString(root.resolve("original.json"),StandardCharsets.UTF_8)));
    }
    @TestFactory Stream<DynamicTest> originalTss(){
        List<DynamicTest> tests=new ArrayList<>();for(Object value:list(cases.get("tss_cases"))){var c=object(value);var result=result("tss",text(c,"id"));for(String format:List.of(".tsv",".gz",".bgz"))tests.add(DynamicTest.dynamicTest(text(c,"id")+format,()->{
            var positions=positions(c);var sources=sources(c,format);var cells=strings(c.get("cells"));var settings=tssOptions(c,20_000_000);
            if(result.containsKey("error")){assertThrows(IOException.class,()->SignacTssEnrichment.enrich(sources,positions,cells,settings,defaults,()->false));return;}
            var actual=SignacTssEnrichment.enrich(sources,positions,cells,settings,defaults,()->false);compareScores(actual.scores(),result);assertEquals(cells,actual.scores().stream().map(SignacTssEnrichment.Score::cell).toList());
            if(bool(c,"fast"))assertNull(actual.profile());else{
                var profile=actual.profile();assertNotNull(profile);var expected=object(result.get("profile"));assertEquals(strings(expected.get("cells")),profile.cells());assertEquals(strings(expected.get("positions")),profile.positions().stream().map(Object::toString).toList());compareDoubles(profile.normalized(),expected.get("values"));var raw=object(result.get("raw_profile"));compareLongs(profile.raw(),raw.get("values"));
            }
            assertEquals(2*sources.size(),actual.passes().size());assertTrue(actual.warnings().stream().anyMatch(w->w.code().equals("LEGACY_TSS_SOURCE_BOUNDARIES")));
        }));}return tests.stream();
    }
    @TestFactory Stream<DynamicTest> originalGenes(){
        List<DynamicTest> tests=new ArrayList<>();for(Object value:list(cases.get("gene_cases"))){var c=object(value);var expected=result("gene_activity",text(c,"id"));for(String format:List.of(".tsv",".gz",".bgz"))for(int memory:new int[]{16,100000})tests.add(DynamicTest.dynamicTest(text(c,"id")+format+"-sort"+memory,()->{
            Path temp=root.resolve("gene-work-"+scratch.incrementAndGet());var limits=new SignacFragments.Limits(defaults.features(),defaults.cells(),defaults.nonzeroValues(),defaults.records(),defaults.contributions(),defaults.decodedBytes(),defaults.lineBytes(),memory);
            var options=new SignacGeneActivity.Options(nullableStrings(c.get("features")),nullableStrings(c.get("biotypes")),c.get("max_width")==null?null:number(c.get("max_width")),integer(c,"upstream"),integer(c,"downstream"),integer(c,"process_n"),bool(c,"gene_id"));
            if(expected.containsKey("error")){assertThrows(IOException.class,()->SignacGeneActivity.geneActivity(sources(c,format),annotations(c),strings(c.get("cells")),options,temp,limits,()->false));assertFalse(Files.exists(temp));return;}
            var actual=SignacGeneActivity.geneActivity(sources(c,format),annotations(c),strings(c.get("cells")),options,temp,limits,()->false);compareMatrix(actual.matrix(),object(expected.get("matrix")));assertFalse(Files.exists(temp));
        }));}return tests.stream();
    }
    @TestFactory Stream<DynamicTest> originalAnnotationReduction(){
        List<DynamicTest> tests=new ArrayList<>();for(Object value:list(cases.get("gene_cases"))){var c=object(value);var expected=result("gene_activity",text(c,"id"));if(expected.containsKey("error"))continue;
            tests.add(DynamicTest.dynamicTest("collapse-"+text(c,"id"),()->{var actual=SignacGeneActivity.collapse(annotations(c),true,()->false);var want=object(object(expected.get("collapsed")).get("value"));assertEquals(strings(want.get("chromosomes")),actual.stream().map(SignacGeneActivity.Transcript::chromosome).toList());assertEquals(ints(want.get("start")),actual.stream().map(SignacGeneActivity.Transcript::start).toList());assertEquals(ints(want.get("end")),actual.stream().map(SignacGeneActivity.Transcript::end).toList());assertEquals(strings(want.get("strand")),actual.stream().map(SignacGeneActivity.Transcript::strand).toList());var metadata=object(want.get("metadata"));assertEquals(strings(metadata.get("gene_id")),actual.stream().map(SignacGeneActivity.Transcript::geneId).toList());assertEquals(strings(metadata.get("gene_name")),actual.stream().map(SignacGeneActivity.Transcript::geneName).toList());assertEquals(strings(metadata.get("gene_biotype")),actual.stream().map(SignacGeneActivity.Transcript::biotype).toList());}));
            tests.add(DynamicTest.dynamicTest("TSS-from-annotation-"+text(c,"id"),()->{var actual=SignacGeneActivity.tssPositions(annotations(c),true,()->false);var want=object(object(expected.get("tss_positions")).get("value"));assertEquals(strings(want.get("chromosomes")),actual.stream().map(SignacTssEnrichment.Position::chromosome).toList());assertEquals(ints(want.get("start")),actual.stream().map(SignacTssEnrichment.Position::start).toList());assertEquals(ints(want.get("end")),actual.stream().map(SignacTssEnrichment.Position::end).toList());assertEquals(strings(want.get("strand")),actual.stream().map(SignacTssEnrichment.Position::strand).toList());assertTrue(actual.stream().allMatch(p->p.sequenceLength()==null));}));
        }return tests.stream();
    }
    @Test void originalExtensionBoundaries(){
        for(Object value:list(cases.get("tss_cases"))){var c=object(value);var output=result("tss",text(c,"id"));if(output.containsKey("error"))continue;int first=c.get("n")==null?Integer.MAX_VALUE:integer(c,"n");List<SignacTssEnrichment.Position> selected=positions(c).stream().limit(first).filter(p->!Set.of("chrM","Mt","MT").contains(p.chromosome())).toList();
            var expected=object(output.get("extensions"));int extension=integer(c,"extension");for(String key:List.of("upstream","downstream","centers","full")){int up=key.equals("upstream")?extension:key.equals("downstream")?-(extension-99):key.equals("centers")?500:extension;int down=key.equals("downstream")?extension:key.equals("upstream")?-(extension-99):key.equals("centers")?500:extension;var want=object(object(expected.get(key)).get("value"));var actual=selected.stream().map(p->SignacTssEnrichment.extend(p,up,down,true)).toList();assertEquals(ints(want.get("start")),actual.stream().map(SignacTssEnrichment.Window::start).toList(),text(c,"id")+key);assertEquals(ints(want.get("end")),actual.stream().map(SignacTssEnrichment.Window::end).toList(),text(c,"id")+key);}
        }
    }
    @Test void rawAndNormalizedProfilesAreImmutable()throws Exception{var c=find("tss_cases","endpoints-False-2000");var result=SignacTssEnrichment.enrich(sources(c,".tsv"),positions(c),strings(c.get("cells")),tssOptions(c,20_000_000),defaults,()->false);long[][] raw=result.profile().raw();double[][] normalized=result.profile().normalized();long old=raw[0][0];double previous=normalized[0][0];raw[0][0]=999;normalized[0][0]=999;assertEquals(old,result.profile().raw()[0][0]);assertEquals(previous,result.profile().normalized()[0][0]);assertThrows(UnsupportedOperationException.class,()->result.profile().cells().add("changed"));}
    @Test void cancellationBeforeAnyWork(){assertThrows(CancellationException.class,()->SignacTssEnrichment.enrich(List.of(),List.of(),List.of(),SignacTssEnrichment.Options.defaults(),defaults,()->true));assertThrows(CancellationException.class,()->SignacGeneActivity.collapse(List.of(),true,()->true));}
    @Test void cancellationDuringActualScanning()throws Exception{var c=find("tss_cases","random-True-2000");AtomicInteger calls=new AtomicInteger();assertThrows(CancellationException.class,()->SignacTssEnrichment.enrich(sources(c,".tsv"),positions(c),strings(c.get("cells")),tssOptions(c,20_000_000),defaults,()->calls.incrementAndGet()>30));assertTrue(calls.get()>30);}
    @Test void explicitProfileCapacityFails()throws Exception{var c=find("tss_cases","endpoints-False-2000");assertThrows(IOException.class,()->SignacTssEnrichment.enrich(sources(c,".tsv"),positions(c),strings(c.get("cells")),tssOptions(c,10),defaults,()->false));}
    @Test void endpointContributionCapacityFails()throws Exception{var c=find("tss_cases","endpoints-True-2000");var limited=new SignacFragments.Limits(defaults.features(),defaults.cells(),defaults.nonzeroValues(),defaults.records(),1,defaults.decodedBytes(),defaults.lineBytes(),100);assertThrows(IOException.class,()->SignacTssEnrichment.enrich(sources(c,".tsv"),positions(c),strings(c.get("cells")),tssOptions(c,20_000_000),limited,()->false));}
    @Test void invalidOptionsAndIdsFail(){assertThrows(IllegalArgumentException.class,()->new SignacTssEnrichment.Options(true,0,1000,null,null,10));assertThrows(IllegalArgumentException.class,()->new SignacTssEnrichment.Options(true,1,1000,0,null,10));assertThrows(IllegalArgumentException.class,()->new SignacTssEnrichment.Position("chr1",0,1,"+",null));assertThrows(IllegalArgumentException.class,()->new SignacGeneActivity.Annotation("chr1",1,2,"?","g","g","protein_coding"));assertThrows(IllegalArgumentException.class,()->new SignacGeneActivity.Options(null,null,Double.NaN,0,0,1,false));}
    @Test void rUniqueNamesReserveExistingSuffixes(){assertEquals(List.of("a","a.3","a.1","a.4","a.2"),SignacGeneActivity.makeUnique(List.of("a","a","a.1","a","a.2")));}
    @Test void inconsistentChromosomeLengthsFail(){assertThrows(IllegalArgumentException.class,()->SignacTssEnrichment.enrich(List.of(new SignacFragments.Source(root.resolve("input/endpoints.tsv"),null)),List.of(new SignacTssEnrichment.Position("chr1",3000,3000,"+",null),new SignacTssEnrichment.Position("chr1",3500,3500,"+",7000)),List.of("A","B"),SignacTssEnrichment.Options.defaults(),defaults,()->false));}
    private Map<String,Object> find(String key,String id){return list(cases.get(key)).stream().map(SignacAnnotationControlsTest::object).filter(v->text(v,"id").equals(id)).findFirst().orElseThrow();}
    private Map<String,Object> result(String key,String id){var wrapper=list(original.get(key)).stream().map(SignacAnnotationControlsTest::object).filter(v->text(v,"id").equals(id)).findFirst().orElseThrow();return object(object(wrapper.get("result")).get("value"));}
    private List<SignacFragments.Source> sources(Map<String,Object> c,String format){List<SignacFragments.Source> result=new ArrayList<>();for(Object value:list(c.get("sources"))){var source=object(value);List<SignacFragments.Cell> map=null;if(source.get("map")!=null){map=new ArrayList<>();for(Object pair:list(source.get("map"))){var item=object(pair);map.add(new SignacFragments.Cell(text(item,"cell"),text(item,"barcode")));}}result.add(new SignacFragments.Source(root.resolve("input/"+text(source,"file")+format),map));}return result;}
    private static SignacTssEnrichment.Options tssOptions(Map<String,Object> c,long capacity){return new SignacTssEnrichment.Options(bool(c,"fast"),integer(c,"process_n"),integer(c,"extension"),c.get("n")==null?null:integer(c,"n"),nullableStrings(c.get("selected_cells")),capacity);}
    private static List<SignacTssEnrichment.Position> positions(Map<String,Object> c){Map<String,Object> lengths=c.get("seqlengths")==null?Map.of():object(c.get("seqlengths"));List<SignacTssEnrichment.Position> result=new ArrayList<>();for(Object value:list(c.get("positions"))){var r=object(value);String chr=text(r,"chromosome");result.add(new SignacTssEnrichment.Position(chr,integer(r,"start"),integer(r,"end"),text(r,"strand"),lengths.get(chr)==null?null:integer(lengths,chr)));}return result;}
    private static List<SignacGeneActivity.Annotation> annotations(Map<String,Object> c){List<SignacGeneActivity.Annotation> result=new ArrayList<>();for(Object value:list(c.get("annotation"))){var r=object(value);result.add(new SignacGeneActivity.Annotation(text(r,"chromosome"),integer(r,"start"),integer(r,"end"),text(r,"strand"),text(r,"gene_id"),(String)r.get("gene_name"),text(r,"gene_biotype")));}return result;}
    private static void compareScores(List<SignacTssEnrichment.Score> actual,Map<String,Object> expected){List<?> score=array(expected.get("score")),kind=array(expected.get("score_kind")),percent=array(expected.get("percentile")),pkind=array(expected.get("percentile_kind"));assertEquals(strings(expected.get("cells")),actual.stream().map(SignacTssEnrichment.Score::cell).toList());for(int i=0;i<actual.size();i++){var row=actual.get(i);assertEquals(kind.get(i),row.enrichment().kind().name());assertEquals(pkind.get(i),row.percentile().kind().name());if(row.enrichment().kind()==SignacFragmentQc.NumberKind.FINITE)assertEquals(number(score.get(i)),row.enrichment().value(),2e-12*Math.max(1,Math.abs(number(score.get(i)))));if(row.percentile().kind()==SignacFragmentQc.NumberKind.FINITE)assertEquals(number(percent.get(i)),row.percentile().value());}}
    private static void compareMatrix(SignacFragments.Matrix actual,Map<String,Object> expected){assertEquals(strings(expected.get("features")),actual.features());assertEquals(strings(expected.get("cells")),actual.cells());assertArrayEquals(ints(expected.get("p")).stream().mapToInt(Integer::intValue).toArray(),actual.pointers());assertArrayEquals(ints(expected.get("i")).stream().mapToInt(Integer::intValue).toArray(),actual.rows());assertArrayEquals(array(expected.get("x")).stream().mapToLong(v->((Number)v).longValue()).toArray(),actual.values());}
    private static void compareDoubles(double[][] actual,Object expected){List<?> rows=array(expected);assertEquals(rows.size(),actual.length);for(int row=0;row<actual.length;row++){List<?> values=array(rows.get(row));assertEquals(values.size(),actual[row].length);for(int col=0;col<actual[row].length;col++)assertEquals(number(values.get(col)),actual[row][col],2e-12*Math.max(1,Math.abs(number(values.get(col)))));}}
    private static void compareLongs(long[][] actual,Object expected){List<?> rows=array(expected);assertEquals(rows.size(),actual.length);for(int row=0;row<actual.length;row++)assertArrayEquals(array(rows.get(row)).stream().mapToLong(v->((Number)v).longValue()).toArray(),actual[row]);}
    private static List<?> array(Object value){return value==null?List.of():value instanceof List<?> l?l:List.of(value);}
    private static List<?> list(Object value){return (List<?>)value;}
    private static Map<String,Object> object(Object value){Map<String,Object> result=new LinkedHashMap<>();for(var e:((Map<?,?>)value).entrySet())result.put((String)e.getKey(),e.getValue());return result;}
    private static List<String> strings(Object value){return array(value).stream().map(String.class::cast).toList();}
    private static List<String> nullableStrings(Object value){return value==null?null:strings(value);}
    private static List<Integer> ints(Object value){return array(value).stream().map(v->((Number)v).intValue()).toList();}
    private static String text(Map<String,Object> value,String key){return (String)value.get(key);}
    private static int integer(Map<String,Object> value,String key){return ((Number)value.get(key)).intValue();}
    private static boolean bool(Map<String,Object> value,String key){return (Boolean)value.get(key);}
    private static double number(Object value){return ((Number)value).doubleValue();}
}
