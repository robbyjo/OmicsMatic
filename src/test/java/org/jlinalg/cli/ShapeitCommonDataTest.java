/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import org.jlinalg.genetics.ShapeitCommonVariants;
import org.jlinalg.genetics.ShapeitGeneticMap;
import org.jlinalg.genetics.ShapeitGenotypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ShapeitCommonDataTest {
    private static final Map<String,byte[]> CAPTURE=capture();
    private static final List<Map<String,Object>> CASES=casesJson();
    @TempDir Path temporary;
    static Stream<String> cases(){return CASES.stream().map(c->text(c.get("name")));}
    static Stream<String> corruptions(){return Stream.of("dosage","first-phase-bit","position","sample-order","truncated","extra-record","reference-AN-retagged","scaffold-flipped");}
    private record Control(Map<String,Object> metadata,List<ShapeitCommonData.Input> inputs,ShapeitCommonData.Spec spec,Path protocol,String protocolSha,Path output) { }
    private Control extract(String name) throws Exception {
        var c=CASES.stream().filter(row->row.get("name").equals(name)).findFirst().orElseThrow();Path dir=Files.createDirectory(temporary.resolve("case-"+UUID.randomUUID()));
        var resources=new ArrayList<ShapeitJoinedRecords.Resource>();var bindings=list(c.get("bindings"));
        for(Object item:bindings){var b=map(item);Path path=dir.resolve(Path.of(text(b.get("member"))).getFileName().toString());Files.write(path,CAPTURE.get(text(b.get("member"))));resources.add(ShapeitJoinedRecords.Resource.bind(text(b.get("kind")),path,text(b.get("peer_path")),1073741824L,()->{}));}
        var inputs=new ArrayList<ShapeitCommonData.Input>();
        for(int i=0;i<list(c.get("roles")).size();i++){
            var selected=new ArrayList<ShapeitJoinedRecords.Resource>();for(int j=0;j<bindings.size();j++)if(((Number)map(bindings.get(j)).get("panel")).intValue()==i)selected.add(resources.get(j));
            var source=selected.stream().filter(r->r.kind().equals("B")).findFirst().orElseThrow().localPath();var store=ShapeitRawIndex.build(source,dir.resolve("spool-"+i),ShapeitBcf.Limits.defaults(),ShapeitRawIndex.Limits.defaults(),()->{});
            inputs.add(new ShapeitCommonData.Input(ShapeitJoinedRecords.Role.valueOf(text(list(c.get("roles")).get(i))),store,selected));
        }
        Path protocol=dir.resolve("source.join.tsv");Files.write(protocol,CAPTURE.get(text(c.get("join_member"))));Path output=null,map=null;
        if(c.get("output_member")!=null){output=dir.resolve("original-output.bcf");Files.write(output,CAPTURE.get(text(c.get("output_member"))));}
        if(c.get("map_member")!=null){map=dir.resolve("source.map.tsv");Files.write(map,CAPTURE.get(text(c.get("map_member"))));}
        Double maf=text(c.get("maf")).equals("default")?null:Double.valueOf(text(c.get("maf")));
        var spec=new ShapeitCommonData.Spec("20",text(c.get("region")),Boolean.TRUE.equals(c.get("snp")),maf,map,ShapeitCommonVariants.Limits.defaults(),ShapeitGeneticMap.Limits.defaults(),ShapeitCommonCalls.Limits.defaults(),ShapeitJoinedRecords.Limits.defaults(),16777216);
        return new Control(c,inputs,spec,protocol,text(c.get("join_sha256")),output);
    }
    private ShapeitCommonData.Prepared prepare(Control c) throws IOException {return ShapeitCommonData.prepare(c.spec(),c.inputs(),c.protocol(),c.protocolSha(),temporary.resolve("prepared-"+UUID.randomUUID()),()->{});}
    private ShapeitCommonOutputs.Result compare(ShapeitCommonData.Prepared p,Path output) throws IOException {return ShapeitCommonOutputs.compare(p,output,ShapeitBcf.Limits.defaults(),temporary.resolve("audit-"+UUID.randomUUID()),16777216,()->{});}

    @ParameterizedTest @MethodSource("cases")
    void completeRawPreparationAndOutputAuditMatchActualOriginalReaderAndFitting(String name) throws Exception {
        var c=extract(name);
        if(!Boolean.TRUE.equals(c.metadata().get("complete"))){IOException failure=assertThrows(IOException.class,()->prepare(c));String expected=name.endsWith("missing")?"REFERENCE_LITERAL_MISSING":"REFERENCE_UNPHASED";assertTrue(failure.getMessage().contains(expected),failure.getMessage());return;}
        var p=prepare(c);var initial=new String(CAPTURE.get(text(c.metadata().get("initial_member"))),StandardCharsets.UTF_8).lines().map(line->line.split("\t",-1)).toList();
        String[] h=initial.stream().filter(f->f[0].equals("H")).findFirst().orElseThrow();int ns=Integer.parseInt(h[1]),nr=Integer.parseInt(h[2]),variants=Integer.parseInt(h[3]);
        assertEquals(variants,p.selection().selectedSites().size());assertEquals(ns,p.callsHeader().target().size());assertEquals(nr,p.callsHeader().reference().size());
        var sites=initial.stream().filter(f->f[0].equals("R")).toList();var states=new HashMap<String,Integer>();var references=new HashMap<String,Boolean>();
        for(String[] f:initial){if(f[0].equals("G"))states.put(f[1]+":"+f[2],Integer.valueOf(f[3]));if(f[0].equals("A")){assertTrue(f[3].equals("\u0000")||f[3].equals("\u0001"),"The original matrix getter was emitted as its uint8_t byte, not an ASCII digit");references.put(f[1]+":"+f[2],f[3].equals("\u0001"));}}
        var readers=new EnumMap<ShapeitJoinedRecords.Role,ShapeitRawIndex.Reader>(ShapeitJoinedRecords.Role.class);
        try(var ledger=new DataInputStream(Files.newInputStream(p.ledger()))){
            for(var input:p.inputs())readers.put(input.role(),input.store().open());
            for(int v=0;v<variants;v++){
                long[] ord={ledger.readLong(),ledger.readLong(),ledger.readLong()};var target=ShapeitCommonData.row(readers,ShapeitJoinedRecords.Role.TARGET,ord[0]);var reference=ShapeitCommonData.row(readers,ShapeitJoinedRecords.Role.REFERENCE,ord[1]);var scaffold=ShapeitCommonData.row(readers,ShapeitJoinedRecords.Role.SCAFFOLD,ord[2]);
                var row=ShapeitCommonCalls.decode(p.callsHeader(),ShapeitCommonData.calls(target,ns),reference==null?List.of():ShapeitCommonData.calls(reference,nr),scaffold==null||scaffold.alternates().size()!=1?null:ShapeitCommonData.calls(scaffold,p.callsHeader().scaffold().size()));
                for(int i=0;i<ns;i++)assertEquals(states.get(v+":"+i).intValue(),row.target().get(i).scaffoldPacked(),name+"/"+v+"/"+i);
                for(int i=0;i<nr*2;i++)assertEquals(references.get(v+":"+i),row.referenceHaplotypes().get(i),name+"/ref/"+v+"/"+i);
                String[] expected=sites.get(v);assertEquals(Long.parseLong(expected[7]),row.counts().cref());assertEquals(Long.parseLong(expected[8]),row.counts().calt());assertEquals(Long.parseLong(expected[9]),row.counts().cmis());
            }
            assertEquals(-1,ledger.read());
        }finally{for(var r:readers.values())r.close();}
        String[] count=initial.stream().filter(f->f[0].equals("C")).findFirst().orElseThrow();var total=p.totals();
        assertEquals(Arrays.stream(count).skip(1).mapToLong(Long::parseLong).boxed().toList(),List.of(total.homozygousReference(),total.heterozygous(),total.homozygousAlternate(),total.missing(),total.scaffoldApplied(),total.referenceAlleles0(),total.referenceAlleles1()));
        assertEquals(variants,p.mapping().relativeCentiMorgan().length);assertEquals(0.0,p.mapping().relativeCentiMorgan()[0]);
        var output=compare(p,c.output());assertTrue(output.rawOutput().complete());assertEquals(variants,output.genotypes().rows());assertEquals((long)variants*ns,output.genotypes().calls());assertEquals(total.scaffoldApplied(),output.appliedScaffoldConstraintsChecked());
        assertEquals(output.genotypes().calls(),output.rawOutput().firstAllelePhaseBits());assertEquals(0,output.rawOutput().vectorEnds());assertEquals(0,output.rawOutput().typedMissingGtValues());
        var counts=Files.readAllLines(output.targetCounts());assertEquals(variants+1,counts.size());for(String line:counts.subList(1,counts.size())){String[] f=line.split("\t",-1);assertEquals(ns*2,Integer.parseInt(f[7]));assertEquals((ns+nr)*2,Integer.parseInt(f[9]));}
        if(nr>0){assertEquals(variants,output.sourceANIncludesReferenceRows());assertTrue(output.warnings().stream().anyMatch(s->s.startsWith("SOURCE AN INCLUDES REFERENCE")));}else assertEquals(0,output.sourceANIncludesReferenceRows());
        if(name.equals("scaffold-unused")){assertEquals(0,total.scaffoldApplied());assertTrue(output.warnings().stream().anyMatch(s->s.startsWith("NO SCAFFOLD CONSTRAINTS")));}
        if(name.equals("cohort-wire-departures")){assertTrue(output.genotypes().missingAlleleReadAsReferenceCalls()>0);assertTrue(output.warnings().stream().anyMatch(s->s.startsWith("UPSTREAM PHASE-MARKED MISSING")));}
        assertTrue(Files.readString(output.targetCounts().getParent().resolve("WARNINGS.md")).contains("Required action"));
        assertThrows(UnsupportedOperationException.class,()->output.warnings().clear());p.verify(()->{});
    }

    @ParameterizedTest @MethodSource("corruptions")
    void alteredOutputsCannotPassTheCompleteOriginalContract(String mode) throws Exception {
        var c=extract(mode.equals("reference-AN-retagged")?"reference-default":mode.equals("scaffold-flipped")?"scaffold-default":"cohort-default");var p=prepare(c);byte[] raw;
        try(var gzip=new GZIPInputStream(Files.newInputStream(c.output()))){raw=gzip.readAllBytes();}
        var bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);int first=9+bytes.getInt(5),recordEnd=first+8+bytes.getInt(first)+bytes.getInt(first+4);
        var rows=new ArrayList<ShapeitBcf.Row>();ShapeitBcf.read(c.output(),ShapeitBcf.Limits.defaults(),rows::add,()->{});
        if(mode.equals("dosage")||mode.equals("first-phase-bit")){int at=unique(raw,first,recordEnd,rows.get(0).format("GT").payload());raw[at]=mode.equals("dosage")?(byte)(raw[at]==3?5:3):(byte)(raw[at]&~1);}
        else if(mode.equals("position"))bytes.putInt(first+12,bytes.getInt(first+12)+1);
        else if(mode.equals("sample-order")){byte[] old="\tM0\tM1\t".getBytes(StandardCharsets.UTF_8),changed="\tM1\tM0\t".getBytes(StandardCharsets.UTF_8);int at=unique(raw,9,first,old);System.arraycopy(changed,0,raw,at,changed.length);}
        else if(mode.equals("truncated"))raw=Arrays.copyOf(raw,raw.length-1);
        else if(mode.equals("extra-record")){byte[] added=Arrays.copyOf(raw,raw.length+recordEnd-first);System.arraycopy(raw,first,added,raw.length,recordEnd-first);raw=added;}
        else if(mode.equals("reference-AN-retagged")){int at=unique(raw,first,recordEnd,rows.get(0).info().get("AN").payload());raw[at]=20;}
        else if(mode.equals("scaffold-flipped")){
            boolean changed=false;int offset=first;
            for(int v=0;v<rows.size()&&!changed;v++){
                int end=offset+8+bytes.getInt(offset)+bytes.getInt(offset+4);
                try(var ledger=new DataInputStream(Files.newInputStream(p.ledger()))){ledger.skipNBytes(24L*v);long[] ord={ledger.readLong(),ledger.readLong(),ledger.readLong()};var readers=new EnumMap<ShapeitJoinedRecords.Role,ShapeitRawIndex.Reader>(ShapeitJoinedRecords.Role.class);
                    try{for(var input:p.inputs())readers.put(input.role(),input.store().open());var target=ShapeitCommonData.row(readers,ShapeitJoinedRecords.Role.TARGET,ord[0]);var scaf=ShapeitCommonData.row(readers,ShapeitJoinedRecords.Role.SCAFFOLD,ord[2]);var state=ShapeitCommonCalls.decode(p.callsHeader(),ShapeitCommonData.calls(target,p.callsHeader().target().size()),List.of(),scaf==null?null:ShapeitCommonData.calls(scaf,p.callsHeader().scaffold().size()));
                        for(int i=0;i<state.target().size();i++)if((state.target().get(i).scaffoldPacked()&3)==3){var gt=rows.get(v).format("GT");assertEquals(1,gt.type());int at=unique(raw,offset,end,gt.payload())+2*i;byte tmp=raw[at];raw[at]=raw[at+1];raw[at+1]=tmp;changed=true;break;}
                    }finally{for(var reader:readers.values())reader.close();}
                }offset=end;
            }assertTrue(changed);
        }else fail(mode);
        Path changed=temporary.resolve("changed.bcf");Files.write(changed,raw);IOException failure=assertThrows(IOException.class,()->compare(p,changed));assertTrue(failure.getMessage().contains("no result is admitted"));
    }
    @Test void incompleteMapOrLedgerCapacitiesFailWithoutDroppingVariants() throws Exception {
        var c=extract("cohort-map");Files.writeString(c.spec().map(),"position rate cM\n0 1 0\n1000 1 0.001\n");assertThrows(IOException.class,()->prepare(c));
        var d=extract("cohort-default");var s=d.spec();var limited=new ShapeitCommonData.Spec(s.contig(),s.region(),s.snpsOnly(),s.minimumMaf(),s.map(),s.selectionLimits(),s.mapLimits(),s.callLimits(),s.joinLimits(),24);
        assertThrows(IOException.class,()->ShapeitCommonData.prepare(limited,d.inputs(),d.protocol(),d.protocolSha(),temporary.resolve("limited"),()->{}));
    }
    @Test void changedPreparedDataAndTargetTableCapacityCannotYieldSuccess() throws Exception {
        var c=extract("cohort-default");var p=prepare(c);assertThrows(IOException.class,()->ShapeitCommonOutputs.compare(p,c.output(),ShapeitBcf.Limits.defaults(),temporary.resolve("small-table"),1,()->{}));
        byte[] b=Files.readAllBytes(p.ledger());b[b.length-1]^=1;Files.write(p.ledger(),b);assertThrows(IOException.class,()->compare(p,c.output()));
    }
    private static int unique(byte[] bytes,int start,int end,byte[] needle){int found=-1;for(int i=start;i<=end-needle.length;i++)if(Arrays.mismatch(bytes,i,i+needle.length,needle,0,needle.length)==-1){assertEquals(-1,found,"Mutation payload must be unique within its physical record/header");found=i;}assertTrue(found>=0);return found;}
    private static String text(Object value){return assertInstanceOf(String.class,value);}
    private static List<?> list(Object value){return assertInstanceOf(List.class,value);}
    private static Map<String,Object> map(Object value){Map<?,?> input=assertInstanceOf(Map.class,value);var output=new LinkedHashMap<String,Object>();input.forEach((k,v)->output.put(text(k),v));return output;}
    private static List<Map<String,Object>> casesJson(){try{return list(SimpleJson.parse(new String(CAPTURE.get("cases.json"),StandardCharsets.UTF_8))).stream().map(ShapeitCommonDataTest::map).toList();}catch(IOException error){throw new ExceptionInInitializerError(error);}}
    private static Map<String,byte[]> capture(){
        try(var input=ShapeitCommonDataTest.class.getResourceAsStream("/org/jlinalg/cli/shapeit-common/original-controls.zip")){
            assertNotNull(input);byte[] raw=input.readNBytes(16777217);assertEquals("81523e3096bf076dea54138f56db752fb51b463e4b9810ee47ba8756fa35bff4",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            var members=new HashMap<String,byte[]>();try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){int bytes=0;for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()){assertFalse(e.isDirectory());assertFalse(members.containsKey(e.getName()));byte[] b=zip.readNBytes(16777217);bytes+=b.length;assertTrue(bytes<=16777216);members.put(e.getName(),b);}}
            var checksums=new String(members.get("SHA256SUMS"),StandardCharsets.UTF_8).lines().toList();assertEquals(members.size()-1,checksums.size());for(String line:checksums)assertEquals(line.substring(0,64),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(members.get(line.substring(66)))));return members;
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
}
