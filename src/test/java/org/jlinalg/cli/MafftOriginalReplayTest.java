/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.MafftAddMap;
import org.jlinalg.sequence.MafftSequences;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MafftOriginalReplayTest {
    record Fixture(String version,String name,int exit,MafftSequences.Alphabet alphabet,MafftSequences.Symbols symbols,String prefix) {
        String id(){return version+":"+name;}
    }
    private static final Map<String,byte[]> FILES=files();
    private static final List<Fixture> CASES=casesList();
    private static String text(byte[] bytes){return new String(bytes,StandardCharsets.UTF_8);}
    private static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    private static Map<String,byte[]> files(){
        try{
            String prefix="/org/jlinalg/sequence/mafft-original/";byte[] raw;
            try(var in=MafftOriginalReplayTest.class.getResourceAsStream(prefix+"original-controls.zip")){assertNotNull(in);raw=in.readNBytes(8388609);assertTrue(raw.length<=8388608);}
            try(var in=MafftOriginalReplayTest.class.getResourceAsStream(prefix+"original-controls.sha256")){assertNotNull(in);assertEquals(text(in.readNBytes(256)).trim(),sha(raw));}
            var files=new HashMap<String,byte[]>();long decoded=0;
            try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                    assertFalse(entry.isDirectory());byte[] value=zip.readNBytes(16777217);assertTrue(value.length<=16777216);decoded+=value.length;assertTrue(decoded<=33554432);
                    assertNull(files.putIfAbsent(entry.getName(),value));
                }
            }
            var pinned=new HashSet<String>();
            for(String line:text(files.get("SHA256SUMS")).lines().toList()){
                String[] row=line.split("  ",2);assertEquals(2,row.length);assertTrue(pinned.add(row[1]));assertEquals(row[0],sha(files.get(row[1])));
            }
            var expected=new HashSet<>(files.keySet());expected.remove("SHA256SUMS");assertEquals(expected,pinned);return Map.copyOf(files);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    private static List<Fixture> casesList(){
        var result=new ArrayList<Fixture>();
        for(String line:text(FILES.get("cases.tsv")).lines().skip(1).toList()){
            String[] row=line.split("\t",-1);assertEquals(6,row.length);
            result.add(new Fixture(row[0],row[1],Integer.parseInt(row[2]),MafftSequences.Alphabet.valueOf(row[3]),MafftSequences.Symbols.valueOf(row[4]),row[5]));
        }
        assertEquals(60,result.size());return List.copyOf(result);
    }
    static Stream<String> cases(){return CASES.stream().map(Fixture::id);}
    private static MafftSequences.Data data(Fixture fixture,String suffix,MafftSequences.Layout layout)throws IOException {
        return MafftSequences.readFasta(new StringReader(text(FILES.get(fixture.prefix()+suffix))),fixture.alphabet(),fixture.symbols(),MafftSequences.Headers.WHOLE_HEADER,layout,MafftSequences.Gaps.ALLOW,MafftSequences.Limits.defaults(),()->{});
    }
    @ParameterizedTest(name="Complete original MAFFT control {0}") @MethodSource("cases")
    void everyOriginalFitRefusalAndDeletionFamilyIsAuditedWithoutAnUpstreamInstallation(String id)throws Exception {
        var fixture=CASES.stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();
        String output=text(FILES.get(fixture.prefix()+"process.stdout")),errors=text(FILES.get(fixture.prefix()+"process.stderr"));
        if(fixture.name().equals("version")){assertTrue((output+errors).contains("v"+fixture.version()));return;}
        if(fixture.exit()!=0){assertEquals(1,fixture.exit());assertTrue(output.isEmpty());assertTrue(errors.contains("Illegal character U"));assertThrows(IOException.class,()->data(fixture,"input.fasta",MafftSequences.Layout.SEQUENCES));return;}
        assertFalse(output.isEmpty());boolean add=fixture.name().startsWith("AA-add"),keep=fixture.name().equals("AA-add-keep-map");
        var primary=data(fixture,"input.fasta",add?MafftSequences.Layout.ALIGNMENT:MafftSequences.Layout.SEQUENCES);
        var aligned=data(fixture,"process.stdout",MafftSequences.Layout.ALIGNMENT);
        var added=add?data(fixture,"added.fasta",MafftSequences.Layout.SEQUENCES):null;
        var original=add?MafftSequences.combine(primary,added,MafftSequences.Limits.defaults()):primary;
        var names=new LinkedHashMap<String,String>();for(String taxon:original.taxa())names.put(taxon,taxon);
        assertEquals(original.taxa().size(),MafftSequences.checkIdentities(original,aligned,names,MafftSequences.OutputOrder.INPUT).size());
        if(keep){
            var backbone=MafftSequences.auditBackbone(primary,aligned,names,MafftSequences.Backbone.KEEP_LENGTH);
            assertEquals(primary.columns(),backbone.outputColumns());assertTrue(backbone.gapOnlyColumnIdentityInferred());
            var rows=new ArrayList<MafftAddMap.Mapping>();
            var map=MafftAddMap.read(new StringReader(text(FILES.get(fixture.prefix()+"added.fasta.map"))),added,aligned,names,MafftAddMap.Limits.defaults(),rows::add,()->{});
            assertEquals(12,map.residues());assertEquals(7,map.mappedResidues());assertEquals(5,map.deletedResidues());assertEquals(12,rows.size());
            assertTrue(errors.contains("5 letters were DELETED"));
        }else{
            var audit=MafftSequences.audit(original,aligned,names,MafftSequences.OutputOrder.INPUT);
            assertEquals(original.statistics().residues(),audit.originalResidues());assertEquals(aligned.statistics().cells(),audit.alignedCells());
            if(add){
                var backbone=MafftSequences.auditBackbone(primary,aligned,names,MafftSequences.Backbone.EXPAND);
                assertEquals(1,backbone.originalGapOnlyColumns());assertFalse(backbone.gapOnlyColumnIdentityInferred());
            }
        }
    }
}
