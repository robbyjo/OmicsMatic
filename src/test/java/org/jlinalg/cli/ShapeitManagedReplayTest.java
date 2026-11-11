/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.genetics.ShapeitCommonVariants;
import org.jlinalg.genetics.ShapeitGeneticMap;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Portable complete data/output replay of actual managed fits; no upstream process is launched. */
final class ShapeitManagedReplayTest {
    private static final Map<String,byte[]> CAPTURE=capture();
    private static final List<?> CASES=cases();
    @TempDir Path temp;
    static Stream<String> controls(){return CASES.stream().map(row->text(map(row).get("name")));}
    @ParameterizedTest @MethodSource("controls")
    void everyActualManagedFitReplaysCompleteInputsAdmissionAndOutputOnThisJvm(String name)throws Exception{
        var c=CASES.stream().map(ShapeitManagedReplayTest::map).filter(row->name.equals(row.get("name"))).findFirst().orElseThrow();var p=map(c.get("parameters"));var bindings=list(c.get("bindings"));var roles=list(c.get("roles"));var inputs=new ArrayList<ShapeitCommonData.Input>();
        for(int i=0;i<roles.size();i++){
            var resources=new ArrayList<ShapeitJoinedRecords.Resource>();Path bcf=null;
            for(Object item:bindings){var b=map(item);if(number(b,"panel")!=i)continue;String kind=text(b.get("kind"));Path path=temp.resolve("panel-"+i+".bcf"+(kind.equals("I")?".csi":""));byte[] raw=CAPTURE.get(text(b.get("member")));assertEquals(text(b.get("sha256")),digest(raw));Files.write(path,raw);if(kind.equals("B"))bcf=path;resources.add(ShapeitJoinedRecords.Resource.bind(kind,path,text(b.get("peer_path")),1073741824,()->{}));}
            var store=ShapeitRawIndex.build(Objects.requireNonNull(bcf),temp.resolve("spool-"+i),ShapeitBcf.Limits.defaults(),ShapeitRawIndex.Limits.defaults(),()->{});
            inputs.add(new ShapeitCommonData.Input(ShapeitJoinedRecords.Role.valueOf(text(roles.get(i))),store,resources));
        }
        Path protocol=temp.resolve("original.join.tsv");Files.write(protocol,CAPTURE.get(text(c.get("protocol_member"))));Path geneticMap=null;
        if(c.get("map_member")!=null){geneticMap=temp.resolve("genetic.map.tsv");Files.write(geneticMap,CAPTURE.get(text(c.get("map_member"))));}
        var spec=new ShapeitCommonData.Spec(text(p.get("contig")),text(p.get("region")),Boolean.TRUE.equals(p.get("snpsOnly")),p.get("minimumMaf")==null?null:((Number)p.get("minimumMaf")).doubleValue(),geneticMap,ShapeitCommonVariants.Limits.defaults(),ShapeitGeneticMap.Limits.defaults(),ShapeitCommonCalls.Limits.defaults(),ShapeitJoinedRecords.Limits.defaults(),1073741824);
        var prepared=ShapeitCommonData.prepare(spec,inputs,protocol,text(c.get("protocol_sha256")),temp.resolve("prepared"),()->{});
        var admission=ShapeitCommonPhasing.admit(prepared,ShapeitCommonPhasing.CountPolicy.valueOf(text(p.get("counts"))),ShapeitCommonPhasing.MissingPolicy.valueOf(text(p.get("missing"))),temp.resolve("input-count-review.tsv"),1073741824,()->{});
        var expected=map(c.get("admission"));assertEquals(number(expected,"selectedRows"),admission.selectedRows());assertEquals(number(expected,"selectedTargetCells"),admission.selectedTargetCells());assertEquals(number(expected,"selectedReferenceCells"),admission.selectedReferenceCells());assertEquals(number(expected,"appliedScaffoldConstraints"),admission.appliedScaffoldConstraints());assertEquals(number(expected,"declaredCountMismatchRows"),admission.declaredCountMismatchRows());assertEquals(number(expected,"partiallyMissingTargetCalls"),admission.partiallyMissingTargetCalls());
        assertArrayEquals(CAPTURE.get(text(c.get("input_counts_member"))),Files.readAllBytes(admission.countReview()));
        Path output=temp.resolve("original.phased.bcf");Files.write(output,CAPTURE.get(text(c.get("output_member"))));
        var audited=ShapeitCommonOutputs.compare(prepared,output,ShapeitBcf.Limits.defaults(),temp.resolve("audit"),1073741824,()->{});
        assertArrayEquals(CAPTURE.get(text(c.get("target_counts_member"))),Files.readAllBytes(audited.targetCounts()));assertEquals(admission.appliedScaffoldConstraints(),audited.appliedScaffoldConstraintsChecked());assertEquals(number(c,"source_AN_includes_reference_rows"),audited.sourceANIncludesReferenceRows());prepared.verify(()->{});
        if(!prepared.callsHeader().reference().isEmpty())assertTrue(audited.warnings().stream().anyMatch(w->w.contains("AN")&&w.contains("reference")));
    }
    private static Map<String,byte[]> capture(){
        try(var input=ShapeitManagedReplayTest.class.getResourceAsStream("/org/jlinalg/cli/shapeit-managed/original-controls.zip")){
            assertNotNull(input);byte[] raw=input.readNBytes(8388609);assertTrue(raw.length<8388609);assertEquals("b04cd57720d67b4f7f5a6a242bca88326b68d692e1e262660c496d3f7bec97d4",digest(raw));var entries=new HashMap<String,byte[]>();long total=0;
            try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){byte[] value=zip.readNBytes(8388609);total+=value.length;assertTrue(total<=8388608);assertNull(entries.put(entry.getName(),value));}}
            for(String line:new String(entries.get("SHA256SUMS"),StandardCharsets.UTF_8).split("\n")){if(line.isEmpty())continue;assertEquals(line.substring(0,64),digest(entries.get(line.substring(66))));}return Map.copyOf(entries);
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    private static List<?> cases(){try{return list(SimpleJson.parse(new String(CAPTURE.get("cases.json"),StandardCharsets.UTF_8)));}catch(IOException error){throw new ExceptionInInitializerError(error);}}
    private static Map<?,?> map(Object value){return assertInstanceOf(Map.class,value);}
    private static List<?> list(Object value){return assertInstanceOf(List.class,value);}
    private static String text(Object value){return assertInstanceOf(String.class,value);}
    private static long number(Map<?,?> value,String key){return assertInstanceOf(Number.class,value.get(key)).longValue();}
    private static String digest(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
}
