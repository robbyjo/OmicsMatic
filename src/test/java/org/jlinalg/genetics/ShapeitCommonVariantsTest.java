/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ShapeitCommonVariantsTest {
    private static final Map<String,byte[]> CAPTURE=capture();
    private static final ShapeitCommonVariants.Limits LIMITS=ShapeitCommonVariants.Limits.defaults();
    private record Case(String name,String main,String reference,String scaffold,String region,boolean snp,String maf,String error,int exit,String result) { }
    private record Probe(int file,long ordinal,int headerSamples,int span,ShapeitCommonVariants.Site site,
                         ShapeitCommonVariants.CountValue ac,ShapeitCommonVariants.CountValue an,boolean gt) { }
    private static String text(String path){return new String(CAPTURE.get(path),StandardCharsets.UTF_8);}
    private static Stream<Case> cases(){
        var cases=text("cases.tsv").lines().skip(1).map(line->{String[] f=line.split("\t",-1);return new Case(f[0],f[1],f[2],f[3],f[4],f[5].equals("1"),f[6],f[7],Integer.parseInt(f[8]),f[9]);}).toList();
        assertEquals(50,cases.size());return cases.stream();
    }
    private static ShapeitCommonVariants.Header header(String fixture){
        if(fixture.equals("-"))return null;
        String[] f=text("fixtures.tsv").lines().skip(1).map(line->line.split("\t",-1)).filter(row->row[0].equals(fixture)).findFirst().orElseThrow();
        return new ShapeitCommonVariants.Header(Integer.parseInt(f[1]),ShapeitCommonVariants.FileKind.valueOf(f[2]));
    }
    private static ShapeitCommonVariants.Options options(Case c){
        Double threshold=switch(c.maf()){case "default"->null;case "inf"->Double.POSITIVE_INFINITY;case "-inf"->Double.NEGATIVE_INFINITY;case "nan"->Double.NaN;default->Double.valueOf(c.maf());};
        return ShapeitCommonVariants.Options.fromDouble(c.snp(),threshold);
    }
    private static ShapeitCommonVariants.Site site(String chr,String pos,String id,String ref,String alt){
        var alleles=new ArrayList<String>();alleles.add(ref);if(!alt.isEmpty())alleles.addAll(Arrays.asList(alt.split(",",-1)));
        return new ShapeitCommonVariants.Site(chr,Integer.parseInt(pos),id,alleles);
    }
    private static List<Probe> probes(List<String> lines){
        var result=new ArrayList<Probe>();
        for(String line:lines)if(line.startsWith("B\t")){
            String[] f=line.split("\t",-1);
            result.add(new Probe(Integer.parseInt(f[1]),Long.parseLong(f[2]),Integer.parseInt(f[3]),Integer.parseInt(f[6]),site(f[4],f[5],f[7],f[8],f[9]),
                new ShapeitCommonVariants.CountValue(Integer.parseInt(f[10]),f[11].equals("-")?0:Integer.parseInt(f[11])),
                new ShapeitCommonVariants.CountValue(Integer.parseInt(f[12]),f[13].equals("-")?0:Integer.parseInt(f[13])),f[14].equals("1")));
        }
        return result;
    }
    private static ShapeitCommonVariants.Event event(Case c,ShapeitCommonVariants.PanelRow[] rows){
        boolean ref=!c.reference().equals("-"),scaf=!c.scaffold().equals("-");
        return new ShapeitCommonVariants.Event(rows[0],ref?rows[1]:null,scaf?rows[ref?2:1]:null);
    }
    private static Map<Long,ShapeitCommonVariants.Event> events(Case c,List<String> lines,List<Probe> probes){
        int files=1+(c.reference().equals("-")?0:1)+(c.scaffold().equals("-")?0:1);
        var result=new LinkedHashMap<Long,ShapeitCommonVariants.Event>();
        for(String line:lines)if(line.startsWith("J\t")){
            String[] f=line.split("\t",-1);long ordinal=Long.parseLong(f[1]);var rows=new ShapeitCommonVariants.PanelRow[files];
            for(int panel=0;panel<files;panel++){
                int index=3+panel*5;boolean physical=f[index].equals("1");int sourcePanel=panel;
                var identities=lines.stream().filter(value->value.startsWith("R\t"+ordinal+"\t"+sourcePanel+"\t")).toList();assertEquals(physical?1:0,identities.size());
                if(!physical){assertEquals("0",f[index+1]);continue;}
                String[] r=identities.get(0).split("\t",-1);var identity=site(r[3],r[4],r[6],r[7],r[8]);
                var matches=probes.stream().filter(p->p.file()==sourcePanel&&p.site().equals(identity)&&p.span()==Integer.parseInt(r[5])).toList();assertEquals(1,matches.size());var p=matches.get(0);
                assertEquals(identity.alleles().size()==2,f[index+1].equals("1"));
                if(identity.alleles().size()==2){assertEquals(1,p.ac().returned());assertEquals(1,p.an().returned());assertEquals(Integer.toUnsignedLong(p.ac().first()),Long.parseLong(f[index+3]));assertEquals(Integer.toUnsignedLong(p.an().first()),Long.parseLong(f[index+4]));}
                rows[panel]=new ShapeitCommonVariants.PanelRow(identity,Integer.parseInt(f[index+2]),p.ac(),p.an(),p.gt());
            }
            result.put(ordinal,event(c,rows));
        }
        return result;
    }
    private static List<ShapeitCommonVariants.Event> simpleErrorEvents(Case c,List<Probe> probes){
        int files=1+(c.reference().equals("-")?0:1)+(c.scaffold().equals("-")?0:1);var grouped=new LinkedHashMap<String,ShapeitCommonVariants.PanelRow[]>();
        for(var p:probes.stream().sorted(Comparator.comparing((Probe p)->p.site().contig()).thenComparingInt(p->p.site().position())).toList()){
            String key=p.site().contig()+":"+p.site().position();var rows=grouped.computeIfAbsent(key,k->new ShapeitCommonVariants.PanelRow[files]);assertNull(rows[p.file()]);
            rows[p.file()]=new ShapeitCommonVariants.PanelRow(p.site(),p.site().alleles().size()==2&&p.headerSamples()>0?1:0,p.ac(),p.an(),p.gt());
        }
        return grouped.values().stream().map(rows->event(c,rows)).toList();
    }

    @ParameterizedTest @MethodSource("cases")
    void completeMasksSiteOrderCountersAndFailureOutcomesMatchActualOriginalScanner(Case c){
        var lines=text("capture/"+c.result()).lines().toList();var physical=probes(lines);
        if(!c.error().equals("-")){
            String expected=switch(c.error()){case "samples"->"FEWER_THAN_50_SAMPLES";case "source-empty-main"->"VOID_HEADER_GAP";case "no-variants"->"NO_VARIANTS";default->c.error()+"_INFO_CARDINALITY";};
            var error=assertThrows(ShapeitCommonVariants.SourceInputException.class,()->{
                var audit=new ShapeitCommonVariants.Audit(header(c.main()),header(c.reference()),header(c.scaffold()),options(c),LIMITS);
                var events=simpleErrorEvents(c,physical);for(var event:events)audit.accept(event);audit.finish(events.size());
            });
            assertEquals(expected,error.code(),c.name());assertNotEquals(0,c.exit());assertFalse(lines.stream().anyMatch(s->s.startsWith("E\t")));return;
        }
        assertEquals(0,c.exit());var joined=events(c,lines,physical);var settings=options(c);
        var audit=new ShapeitCommonVariants.Audit(header(c.main()),header(c.reference()),header(c.scaffold()),settings,LIMITS);
        String[] h=lines.stream().filter(s->s.startsWith("H\t")).findFirst().orElseThrow().split("\t",-1);
        assertEquals(header(c.main()).samples(),Integer.parseInt(h[1]));assertEquals(c.reference().equals("-")?0:header(c.reference()).samples(),Integer.parseInt(h[2]));
        int thresholdBits=Integer.parseUnsignedInt(h[5],16);
        if(Float.isNaN(Float.intBitsToFloat(thresholdBits)))assertTrue(Float.isNaN(settings.minimumMaf()));else assertEquals(thresholdBits,Float.floatToRawIntBits(settings.minimumMaf()));
        long index=0;
        for(String line:lines)if(line.startsWith("J\t")){
            String[] f=line.split("\t",-1);assertEquals(index,Long.parseLong(f[1]));var decision=audit.accept(joined.get(index));assertEquals(f[2].equals("1"),decision.kept(),c.name()+"/"+index);
            int from=3+5*(1+(c.reference().equals("-")?0:1)+(c.scaffold().equals("-")?0:1));
            if(f[from].equals("."))assertNull(decision.site());else assertEquals(site(f[from],f[from+1],f[from+2],f[from+3],f[from+4]),decision.site());index++;
        }
        String[] end=lines.get(lines.size()-1).split("\t",-1);assertEquals("E",end[0]);assertEquals(index,Long.parseLong(end[1]));
        var result=audit.finish(Long.parseLong(end[1]));assertEquals(Integer.parseInt(h[3]),result.decisions().size());assertEquals(Integer.parseInt(h[4]),result.selectedSites().size());
        var kept=lines.stream().filter(s->s.startsWith("K\t")).map(s->{var f=s.split("\t",-1);return site(f[2],f[3],f[4],f[5],f[6]);}).toList();assertEquals(kept,result.selectedSites());
        var logs=text("capture/reader-"+c.name()+".stdout");
        assertEquals(counter(logs,"sites removed in main panel \\[not in reference panel\\]"),result.reasonCounts().get(ShapeitCommonVariants.Reason.NO_REFERENCE_OVERLAP));
        assertEquals(counter(logs,"sites removed in main panel \\[multi-allelic\\]"),result.reasonCounts().get(ShapeitCommonVariants.Reason.ALL_NONBIALLELIC));
        assertEquals(counter(logs,"sites removed in main panel \\[not SNPs\\]"),result.reasonCounts().get(ShapeitCommonVariants.Reason.NOT_UPPERCASE_SNP));
        assertEquals(counter(logs,"sites removed in main panel \\[below MAF threshold\\]"),result.reasonCounts().get(ShapeitCommonVariants.Reason.BELOW_FLOAT32_MAF));
        assertEquals(counter(logs,"sites removed in reference panel \\[not in main panel\\]"),result.reasonCounts().get(ShapeitCommonVariants.Reason.REFERENCE_ONLY));
        assertEquals(counter(logs,"sites removed in scaffold panel \\[not in main panel\\]"),result.reasonCounts().get(ShapeitCommonVariants.Reason.SCAFFOLD_ONLY));
        assertEquals(counter(logs,"sites removed \\[record in main panel not in a supported format\\]"),result.reasonCounts().get(ShapeitCommonVariants.Reason.UNSUPPORTED_TARGET_FORMAT));
        assertEquals(counter(logs,"sites removed \\[record in reference panel not in a supported format\\]"),result.reasonCounts().get(ShapeitCommonVariants.Reason.UNSUPPORTED_REFERENCE_FORMAT));
        assertEquals(counter(logs,"sites removed \\[record in scaffold panel not in a supported format\\]"),result.unsupportedScaffoldFormats());
        assertEquals(counter(logs,"sites with scaffold data"),result.sourceWithScaffold());
        if(c.name().equals("binary-scaffold-formats")){assertEquals(9,result.selectedSites().size());assertEquals(7,result.unsupportedScaffoldFormats());assertEquals(2,result.selectedWithAcceptedScaffoldType());assertTrue(result.risks().contains(ShapeitCommonVariants.Risk.KEPT_WITH_UNSUPPORTED_SCAFFOLD));assertTrue(result.risks().contains(ShapeitCommonVariants.Risk.KEPT_SCAFFOLD_WITHOUT_GT));}
    }
    private static long counter(String text,String suffix){var match=Pattern.compile("([0-9]+) "+suffix).matcher(text);return match.find()?Long.parseLong(match.group(1)):0;}
    private static ShapeitCommonVariants.Header main(){return new ShapeitCommonVariants.Header(50,ShapeitCommonVariants.FileKind.BCF);}
    private static ShapeitCommonVariants.PanelRow good(){return new ShapeitCommonVariants.PanelRow(new ShapeitCommonVariants.Site("20",1000,"same",List.of("A","C")),1,new ShapeitCommonVariants.CountValue(1,2),new ShapeitCommonVariants.CountValue(1,100),true);}
    private static ShapeitCommonVariants.Audit audit(ShapeitCommonVariants.Limits limits){return new ShapeitCommonVariants.Audit(main(),null,null,ShapeitCommonVariants.Options.fromDouble(false,null),limits);}

    @Test void aMetadataFailureIsPermanentAndCannotBeRepairedIntoACompleteReport(){
        var audit=audit(LIMITS);audit.accept(new ShapeitCommonVariants.Event(good(),null,null));
        var bad=new ShapeitCommonVariants.PanelRow(good().site(),1,new ShapeitCommonVariants.CountValue(0,0),good().an(),true);
        var error=assertThrows(ShapeitCommonVariants.SourceInputException.class,()->audit.accept(new ShapeitCommonVariants.Event(bad,null,null)));assertEquals("AC_INFO_CARDINALITY",error.code());
        assertThrows(IllegalStateException.class,()->audit.finish(1));assertThrows(IllegalStateException.class,()->audit.accept(new ShapeitCommonVariants.Event(good(),null,null)));
    }
    @Test void completeJoinCensusCannotBeReplacedWithAValidPrefix(){
        var audit=audit(LIMITS);audit.accept(new ShapeitCommonVariants.Event(good(),null,null));
        var error=assertThrows(ShapeitCommonVariants.SourceInputException.class,()->audit.finish(2));assertEquals("INCOMPLETE_JOIN_STREAM",error.code());assertThrows(IllegalStateException.class,()->audit.finish(1));
    }
    @Test void capacitiesFailInsteadOfSamplingOrReturningACompletePrefix(){
        var capped=audit(new ShapeitCommonVariants.Limits(100,1,100000));capped.accept(new ShapeitCommonVariants.Event(good(),null,null));
        assertEquals("EVENT_CAPACITY",assertThrows(ShapeitCommonVariants.SourceInputException.class,()->capped.accept(new ShapeitCommonVariants.Event(good(),null,null))).code());assertThrows(IllegalStateException.class,()->capped.finish(1));
        var memory=audit(new ShapeitCommonVariants.Limits(100,100,1));assertEquals("RETAINED_CAPACITY",assertThrows(ShapeitCommonVariants.SourceInputException.class,()->memory.accept(new ShapeitCommonVariants.Event(good(),null,null))).code());
        assertEquals("SAMPLE_CAPACITY",assertThrows(ShapeitCommonVariants.SourceInputException.class,()->audit(new ShapeitCommonVariants.Limits(49,100,100000))).code());
    }
    @Test void incompatibleOrUnconfiguredPanelRowsInvalidateTheAudit(){
        var audit=new ShapeitCommonVariants.Audit(main(),main(),null,ShapeitCommonVariants.Options.fromDouble(false,null),LIMITS);
        var other=new ShapeitCommonVariants.PanelRow(new ShapeitCommonVariants.Site("21",1000,"other",List.of("A","C")),1,good().ac(),good().an(),true);
        assertEquals("JOIN_COORDINATE_MISMATCH",assertThrows(ShapeitCommonVariants.SourceInputException.class,()->audit.accept(new ShapeitCommonVariants.Event(good(),other,null))).code());assertThrows(IllegalStateException.class,()->audit.finish(0));
        var unconfigured=audit(LIMITS);assertEquals("UNCONFIGURED_PANEL",assertThrows(ShapeitCommonVariants.SourceInputException.class,()->unconfigured.accept(new ShapeitCommonVariants.Event(good(),good(),null))).code());
        var empty=audit(LIMITS);assertEquals("EMPTY_JOIN_EVENT",assertThrows(ShapeitCommonVariants.SourceInputException.class,()->empty.accept(new ShapeitCommonVariants.Event(null,null,null))).code());
    }
    @Test void duplicateIdenticalSiteIdentitiesRemainSeparateOriginalEvents(){
        var audit=audit(LIMITS);audit.accept(new ShapeitCommonVariants.Event(good(),null,null));audit.accept(new ShapeitCommonVariants.Event(good(),null,null));
        var result=audit.finish(2);assertEquals(List.of(good().site(),good().site()),result.selectedSites());assertEquals(2,result.decisions().size());assertEquals(1,result.decisions().get(1).ordinal());
        assertThrows(IllegalStateException.class,()->audit.finish(2));
    }
    @Test void reportIdentitiesMasksCountersAndWarningsAreImmutable(){
        var alleles=new ArrayList<>(List.of("A","C"));var site=new ShapeitCommonVariants.Site("20",1000,"source",alleles);alleles.clear();assertEquals(List.of("A","C"),site.alleles());
        var audit=audit(LIMITS);audit.accept(new ShapeitCommonVariants.Event(good(),null,null));var result=audit.finish(1);
        assertThrows(UnsupportedOperationException.class,()->result.selectedSites().clear());assertThrows(UnsupportedOperationException.class,()->result.decisions().clear());assertThrows(UnsupportedOperationException.class,()->result.reasonCounts().clear());assertThrows(UnsupportedOperationException.class,()->result.risks().add(ShapeitCommonVariants.Risk.ZERO_AN));
    }
    @Test void checkedOriginalEmptyTargetBoundaryIsRetainedAsAFailure(){
        assertTrue(text("source-boundary/empty-main.stderr").contains("__n < this->size()"));assertTrue(text("source-boundary/observed.json").contains("confirmed-original-void-file-gap-vector-boundary"));
    }
    private static Map<String,byte[]> capture(){
        try(var stream=ShapeitCommonVariantsTest.class.getResourceAsStream("/org/jlinalg/genetics/shapeit-selection/original-controls.zip")){
            assertNotNull(stream);var result=new LinkedHashMap<String,byte[]>();int total=0;
            try(var zip=new ZipInputStream(stream)){
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                    assertTrue(result.size()<1024);assertFalse(entry.isDirectory());assertFalse(result.containsKey(entry.getName()));var output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                    while((count=zip.read(buffer))>=0){total+=count;assertTrue(total<=8388608);output.write(buffer,0,count);}result.put(entry.getName(),output.toByteArray());
                }
            }
            var checks=new String(result.get("SHA256SUMS"),StandardCharsets.UTF_8).lines().toList();assertEquals(result.size()-1,checks.size());
            for(String line:checks)assertEquals(line.substring(0,64),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.get(line.substring(66)))),line.substring(66));
            return Map.copyOf(result);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
}
