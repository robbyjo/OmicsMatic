/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.genetics.ShapeitCommonVariants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ShapeitJoinedRecordsTest {
    private static final Map<String,byte[]> CAPTURE=capture();
    private static final List<Map<String,Object>> CASES=readCases();
    @TempDir Path temporary;
    static Stream<String> cases(){return CASES.stream().map(c->text(c.get("name")));}
    static Stream<String> selectionCases(){return cases().filter(name->name.startsWith("selection-"));}
    private static Map<String,Object> caseNamed(String name){return CASES.stream().filter(c->name.equals(c.get("name"))).findFirst().orElseThrow();}
    private static ShapeitBcf.Limits rawLimits(){var d=ShapeitBcf.Limits.defaults();return new ShapeitBcf.Limits(d.samples(),d.records(),d.genotypeCells(),d.inputBytes(),d.decodedBytes(),d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),30000,d.formatFields(),d.genotypeWidth(),d.valuesPerRecord());}
    private record Prepared(Path protocol,String digest,String region,List<ShapeitJoinedRecords.Panel> panels,List<ShapeitJoinedRecords.Resource> resources,List<ShapeitRawIndex.Store> stores) { }
    private Prepared prepare(String name) throws Exception {
        Map<String,Object> c=caseNamed(name);Path directory=Files.createDirectory(temporary.resolve("prepared-"+UUID.randomUUID()));
        var resources=new ArrayList<ShapeitJoinedRecords.Resource>();
        for(Object item:list(c.get("bindings"))){var b=map(item);String member=text(b.get("member"));Path local=directory.resolve(Path.of(member).getFileName().toString());Files.write(local,CAPTURE.get(member));resources.add(ShapeitJoinedRecords.Resource.bind(text(b.get("kind")),local,text(b.get("peer_path")),ShapeitJoinedRecords.Limits.defaults().resourceBytes(),()->{}));}
        var panels=new ArrayList<ShapeitJoinedRecords.Panel>();var stores=new ArrayList<ShapeitRawIndex.Store>();
        for(int i=0;i<list(c.get("roles")).size();i++){
            int index=i;var bound=new ArrayList<ShapeitJoinedRecords.Resource>();var bindings=list(c.get("bindings"));
            for(int j=0;j<bindings.size();j++)if(((Number)map(bindings.get(j)).get("panel")).intValue()==index)bound.add(resources.get(j));
            Path bcf=bound.stream().filter(r->r.kind().equals("B")).findFirst().orElseThrow().localPath();
            var store=ShapeitRawIndex.build(bcf,directory.resolve("spool-"+i),rawLimits(),ShapeitRawIndex.Limits.defaults(),()->{});stores.add(store);
            panels.add(ShapeitJoinedRecords.Panel.bind(ShapeitJoinedRecords.Role.valueOf(text(list(c.get("roles")).get(i))),store,bound,ShapeitJoinedRecords.Limits.defaults(),()->{}));
        }
        Path protocol=directory.resolve("original.wire.tsv");Files.write(protocol,CAPTURE.get(text(c.get("protocol_member"))));
        return new Prepared(protocol,text(c.get("protocol_sha256")),text(c.get("region")),panels,resources,stores);
    }
    private ShapeitJoinedRecords.Report link(Prepared p,ShapeitJoinedRecords.Visitor visitor) throws IOException {
        return ShapeitJoinedRecords.link(p.protocol(),p.digest(),p.region(),p.panels(),temporary.resolve("linked-"+UUID.randomUUID()),ShapeitJoinedRecords.Limits.defaults(),visitor,()->{});
    }
    @ParameterizedTest @MethodSource("cases")
    void everyOriginalCaptureClosesAgainstTheUntouchedRawPopulation(String name) throws Exception {
        var p=prepare(name);var events=new ArrayList<ShapeitJoinedRecords.Event>();var c=caseNamed(name);
        if(!Boolean.TRUE.equals(c.get("complete"))){IOException failure=assertThrows(IOException.class,()->link(p,events::add));assertTrue(failure.getMessage().contains("SKIPPED"));return;}
        var result=link(p,events::add);assertTrue(result.complete());
        var expected=Files.readAllLines(p.protocol()).stream().filter(line->line.startsWith("J\t")).map(line->line.split("\t",-1)).toList();assertEquals(expected.size(),result.events());assertEquals(expected.size(),events.size());
        var originals=new ArrayList<Map<Long,ShapeitBcf.Row>>();
        for(var store:p.stores()){var rows=new HashMap<Long,ShapeitBcf.Row>();assertTrue(ShapeitBcf.read(store.source(),rawLimits(),r->rows.put(r.ordinal(),r),()->{}).complete());originals.add(rows);}
        long[] visits=new long[p.panels().size()];
        for(int i=0;i<events.size();i++){
            var event=events.get(i);assertEquals(i,event.ordinal());
            for(int f=0;f<visits.length;f++){
                var row=event.rawRows().get(f);String digest=expected.get(i)[4+5*f];
                if(digest.equals("-")){assertNull(row);continue;}
                assertEquals(digest,row.rawWireSha256());visits[f]++;same(originals.get(f).get(row.ordinal()),row);
            }
        }
        assertEquals(Arrays.stream(visits).boxed().toList(),result.physicalVisits());
        assertThrows(UnsupportedOperationException.class,()->result.physicalVisits().clear());
        if(!events.isEmpty())assertThrows(UnsupportedOperationException.class,()->events.get(0).rawRows().clear());
    }
    @ParameterizedTest @MethodSource("selectionCases")
    void rawLinkedEventsReproduceEveryOriginalScannerMask(String name) throws Exception {
        String sourceName=name.substring("selection-".length());
        var settings=selectionMember("cases.tsv").lines().skip(1).map(line->line.split("\t",-1)).filter(f->f[0].equals(sourceName)).findFirst().orElseThrow();
        Double threshold=switch(settings[6]){case "default"->null;case "inf"->Double.POSITIVE_INFINITY;case "-inf"->Double.NEGATIVE_INFINITY;case "nan"->Double.NaN;default->Double.valueOf(settings[6]);};
        var options=ShapeitCommonVariants.Options.fromDouble(settings[5].equals("1"),threshold);
        var p=prepare(name);ShapeitCommonVariants.Audit[] audit=new ShapeitCommonVariants.Audit[1];
        var result=link(p,new ShapeitJoinedRecords.Visitor(){
            @Override public void header(ShapeitJoinedRecords.Headers h){audit[0]=new ShapeitCommonVariants.Audit(h.target(),h.reference(),h.scaffold(),options,ShapeitCommonVariants.Limits.defaults());}
            @Override public void event(ShapeitJoinedRecords.Event event){audit[0].accept(event.scannerEvent());}
        });
        var selected=audit[0].finish(result.events());assertEquals(result.events(),selected.decisions().size());
        var oracle=selectionMember("capture/"+settings[9]).lines().toList();
        assertEquals(oracle.stream().filter(line->line.startsWith("J\t")).map(line->line.split("\t",-1)[2].equals("1")).toList(),selected.decisions().stream().map(ShapeitCommonVariants.Decision::kept).toList());
        var expectedSites=oracle.stream().filter(line->line.startsWith("K\t")).map(line->{String[] f=line.split("\t",-1);var alleles=new ArrayList<String>();alleles.add(f[5]);if(!f[6].isEmpty())alleles.addAll(Arrays.asList(f[6].split(",",-1)));return new ShapeitCommonVariants.Site(f[2],Integer.parseInt(f[3]),f[4],alleles);}).toList();
        assertEquals(expectedSites,selected.selectedSites());
    }
    static Stream<String> corruptions(){return Stream.of("old-version","query","before-resource","after-resource","sample","header","raw-identity","count","record-type","biallelic","ordinal","missing-footer","trailing","omitted-coordinate");}
    @ParameterizedTest @MethodSource("corruptions")
    void tamperedOrIncompleteStreamsCannotReturnACompleteReport(String mode) throws Exception {
        var p=prepare("selection-frequency-0");var lines=new ArrayList<>(Files.readAllLines(p.protocol()));
        switch(mode){
            case "old-version"->lines.set(0,lines.get(0).replace("WIRE-2","WIRE-1"));
            case "query"->lines.set(1,"U\t"+Base64.getEncoder().encodeToString("different".getBytes(StandardCharsets.UTF_8)));
            case "before-resource"->change(lines,"F\tB\t",f-> {f[5]="0".repeat(64);return f;});
            case "after-resource"->change(lines,"F\tA\t",f-> {f[5]="0".repeat(64);return f;});
            case "sample"->change(lines,"S\t",f->{f[3]=Base64.getEncoder().encodeToString("wrong".getBytes(StandardCharsets.UTF_8));return f;});
            case "header"->change(lines,"N\t",f->{f[3]="49";return f;});
            case "raw-identity"->change(lines,"J\t",f->{f[4]="0".repeat(64);return f;});
            case "count"->change(lines,"J\t",f->{f[7]=Long.toString(Long.parseLong(f[7])+1);return f;});
            case "record-type"->change(lines,"J\t",f->{f[6]="2";return f;});
            case "biallelic"->change(lines,"J\t",f->{f[5]=f[5].equals("1")?"0":"1";return f;});
            case "ordinal"->change(lines,"J\t",f->{f[1]="1";return f;});
            case "missing-footer"->{int end=index(lines,"E\t");lines.subList(end,lines.size()).clear();}
            case "trailing"->lines.add("Z");
            case "omitted-coordinate"->{lines.remove(index(lines,"J\t"));int n=0;for(int i=0;i<lines.size();i++)if(lines.get(i).startsWith("J\t")){String[] f=lines.get(i).split("\t",-1);f[1]=Integer.toString(n++);lines.set(i,String.join("\t",f));}final int count=n;change(lines,"E\t",f->{f[1]=Integer.toString(count);f[2]=Integer.toString(count);return f;});}
            default->throw new AssertionError(mode);
        }
        Files.writeString(p.protocol(),String.join("\n",lines)+"\n");var changed=new Prepared(p.protocol(),hash(Files.readAllBytes(p.protocol())),p.region(),p.panels(),p.resources(),p.stores());
        Path output=temporary.resolve("failed");IOException failure=assertThrows(IOException.class,()->ShapeitJoinedRecords.link(changed.protocol(),changed.digest(),changed.region(),changed.panels(),output,ShapeitJoinedRecords.Limits.defaults(),event->{},()->{}));
        assertTrue(failure.getMessage().contains("SKIPPED"));assertTrue(Files.readString(output.resolve("status.txt")).startsWith("FAILED"));
    }
    @Test void changedSourceCompanionOrCaptureAndCallbackFailureInvalidateTheLink() throws Exception {
        for(String target:List.of("source","companion","capture","callback")){
            var p=prepare("selection-frequency-0");boolean[] changed={false};
            IOException error=assertThrows(IOException.class,()->link(p,event->{if(changed[0])return;changed[0]=true;
                if(target.equals("callback"))throw new IOException("intentional callback failure");
                Path path=target.equals("source")?p.stores().get(0).source():target.equals("capture")?p.protocol():p.resources().stream().filter(r->r.kind().equals("I")).findFirst().orElseThrow().localPath();
                byte[] b=Files.readAllBytes(path);b[b.length-1]^=1;Files.write(path,b);
            }));assertTrue(changed[0]);assertTrue(error.getMessage().contains("SKIPPED"));
        }
    }
    @Test void captureDigestQueryCapacitiesAndExistingWorkspacesAreChecked() throws Exception {
        var p=prepare("selection-frequency-0");var d=ShapeitJoinedRecords.Limits.defaults();
        for(var limit:List.of(new ShapeitJoinedRecords.Limits(1,d.lineBytes(),d.intervals(),d.samples(),d.resourceBytes()),new ShapeitJoinedRecords.Limits(d.protocolBytes(),8,d.intervals(),d.samples(),d.resourceBytes())))
            assertThrows(IOException.class,()->ShapeitJoinedRecords.link(p.protocol(),p.digest(),p.region(),p.panels(),temporary.resolve("limit-"+UUID.randomUUID()),limit,event->{},()->{}));
        assertThrows(IOException.class,()->ShapeitJoinedRecords.link(p.protocol(),"0".repeat(64),p.region(),p.panels(),temporary.resolve("digest"),d,event->{},()->{}));
        Path out=Files.createDirectory(temporary.resolve("existing"));Files.writeString(out.resolve("keep"),"untouched");assertThrows(FileAlreadyExistsException.class,()->ShapeitJoinedRecords.link(p.protocol(),p.digest(),p.region(),p.panels(),out,d,event->{},()->{}));assertEquals("untouched",Files.readString(out.resolve("keep")));
    }
    @Test void whitespaceOnlyFamBoundaryIsRejectedBeforeCallingTheOriginalReader() throws Exception {
        var p=prepare("fam-name-semantics");var bound=new ArrayList<ShapeitJoinedRecords.Resource>();
        for(var resource:p.resources()){
            if(resource.kind().equals("M")){Files.writeString(resource.localPath()," \t\n");bound.add(ShapeitJoinedRecords.Resource.bind("M",resource.localPath(),resource.peerPath(),10000,()->{}));}else bound.add(resource);
        }
        IOException failure=assertThrows(IOException.class,()->ShapeitJoinedRecords.Panel.bind(ShapeitJoinedRecords.Role.TARGET,p.stores().get(0),bound,ShapeitJoinedRecords.Limits.defaults(),()->{}));assertTrue(failure.getMessage().contains("SOURCE_FAM_WHITESPACE_BOUNDARY"));
    }
    private static int index(List<String> lines,String prefix){for(int i=0;i<lines.size();i++)if(lines.get(i).startsWith(prefix))return i;throw new AssertionError(prefix);}
    private static void change(List<String> lines,String prefix,UnaryOperator<String[]> mutation){int i=index(lines,prefix);lines.set(i,String.join("\t",mutation.apply(lines.get(i).split("\t",-1))));}
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String text(Object value){return assertInstanceOf(String.class,value);}
    private static String selectionMember(String name) throws IOException {
        try(var input=ShapeitJoinedRecordsTest.class.getResourceAsStream("/org/jlinalg/genetics/shapeit-selection/original-controls.zip")){
            assertNotNull(input);try(var zip=new ZipInputStream(input)){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())if(e.getName().equals(name)){byte[] raw=zip.readNBytes(8388609);assertTrue(raw.length<=8388608);return new String(raw,StandardCharsets.UTF_8);}}
        }throw new IOException("Missing original scanner fixture: "+name);
    }
    private static List<Map<String,Object>> readCases(){try{return list(SimpleJson.parse(new String(CAPTURE.get("cases.json"),StandardCharsets.UTF_8))).stream().map(ShapeitJoinedRecordsTest::map).toList();}catch(IOException error){throw new ExceptionInInitializerError(error);}}
    private static List<?> list(Object value){return assertInstanceOf(List.class,value);}
    private static Map<String,Object> map(Object value){Map<?,?> raw=assertInstanceOf(Map.class,value);var result=new LinkedHashMap<String,Object>();raw.forEach((k,v)->result.put(text(k),v));return result;}
    private static void same(ShapeitBcf.Row a,ShapeitBcf.Row b){assertNotNull(a);assertEquals(a.contig(),b.contig());assertEquals(a.position(),b.position());assertEquals(a.referenceLength(),b.referenceLength());assertEquals(a.id(),b.id());assertEquals(a.reference(),b.reference());assertEquals(a.alternates(),b.alternates());assertEquals(a.qualityBits(),b.qualityBits());assertEquals(a.filters(),b.filters());assertEquals(a.rawWireSha256(),b.rawWireSha256());assertEquals(new ArrayList<>(a.info().keySet()),new ArrayList<>(b.info().keySet()));for(String key:a.info().keySet())same(a.info().get(key),b.info().get(key));assertEquals(a.formats().size(),b.formats().size());for(int i=0;i<a.formats().size();i++)same(a.formats().get(i),b.formats().get(i));}
    private static void same(ShapeitBcf.Field a,ShapeitBcf.Field b){assertEquals(a.name(),b.name());assertEquals(a.type(),b.type());assertEquals(a.width(),b.width());assertEquals(a.cells(),b.cells());assertArrayEquals(a.payload(),b.payload());}
    private static Map<String,byte[]> capture(){
        try(var input=ShapeitJoinedRecordsTest.class.getResourceAsStream("/org/jlinalg/cli/shapeit-joined/original-controls.zip")){
            assertNotNull(input);var members=new HashMap<String,byte[]>();try(var zip=new ZipInputStream(input)){int total=0;for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()){assertFalse(e.isDirectory());assertFalse(members.containsKey(e.getName()));byte[] bytes=zip.readNBytes(16777217);total+=bytes.length;assertTrue(total<=16777216);members.put(e.getName(),bytes);}}
            var sums=new String(members.get("SHA256SUMS"),StandardCharsets.UTF_8).lines().toList();assertEquals(members.size()-1,sums.size());for(String line:sums)assertEquals(line.substring(0,64),hash(members.get(line.substring(66))),line.substring(66));
            try(var fixture=ShapeitJoinedRecordsTest.class.getResourceAsStream("/org/jlinalg/cli/shapeit-joined/original-controls.zip")){assertNotNull(fixture);assertEquals("fc63451afeb22f5c0c45d8a46af7634687ea85f6c25eb431d73e609cc6baad79",hash(fixture.readAllBytes()));}
            return members;
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
}
