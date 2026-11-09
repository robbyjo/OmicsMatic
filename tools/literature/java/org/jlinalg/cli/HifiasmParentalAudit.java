/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.jlinalg.assembly.HifiasmParentalKmers;
import org.jlinalg.assembly.HifiasmParentalKmers.Entry;
import org.jlinalg.assembly.HifiasmParentalKmers.Table;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Execute the production native component against actual original-source and independent controls. */
public final class HifiasmParentalAudit {
    private HifiasmParentalAudit(){ }
    @FunctionalInterface private interface Rejection{void run()throws Exception;}
    private static int rejected;
    private static void reject(Rejection action)throws Exception{try{action.run();}catch(IllegalArgumentException|java.io.IOException expected){rejected++;return;}throw new AssertionError("Invalid/corrupt family was accepted");}
    private static byte[] hex(Object value)throws Exception{return HexFormat.of().parseHex(text(value));}
    private static int number(Object value)throws Exception{return Math.toIntExact(integer(value,0,Integer.MAX_VALUE));}
    private static Map<Long,Integer> entries(Table table){var result=new HashMap<Long,Integer>();for(Entry e:table.entries())result.put(e.hash(),e.count());return result;}
    private static int[] numbers(Object raw)throws Exception{var values=list(raw);int[] result=new int[values.size()];for(int i=0;i<result.length;i++)result[i]=number(values.get(i));return result;}
    public static void main(String[] args)throws Exception{
        require(args.length==2,"Require exact original cases and NEW output");Path file=Path.of(args[0]).toAbsolutePath().normalize(),out=Path.of(args[1]).toAbsolutePath().normalize();require(!Files.exists(out),"Require NEW output");Files.createDirectory(out);var root=GseapyExecutionRuntime.read(file,67108864);int hashes=0,classifications=0,reads=0;var tables=new LinkedHashMap<String,Table>();var tablePins=new LinkedHashMap<String,Object>();
        for(Object raw:list(root.get("hashes"))){var r=object(raw);long actual=HifiasmParentalKmers.hash(hex(r.get("hex")));require(Long.toUnsignedString(actual).equals(text(r.get("expected"))),"Original/independent unsigned hash differs");hashes++;}
        for(Object raw:list(root.get("classifications"))){var r=object(raw);char actual=HifiasmParentalKmers.classifyCounts(number(r.get("k")),numbers(r.get("counts")),numbers(r.get("runs")),finite(r.get("ratio"),0,Double.MAX_VALUE));require(Character.toString(actual).equals(text(r.get("expected"))),"Original source strict vote/tie classification differs");classifications++;}
        for(Object raw:list(root.get("tables"))){var r=object(raw);String id=text(r.get("id"));var sequences=new ArrayList<byte[]>();long bases=0;for(Object value:list(r.get("sequences_hex"))){byte[] sequence=hex(value);sequences.add(sequence);bases+=sequence.length;}var table=HifiasmParentalKmers.count(sequences,number(r.get("k")),number(r.get("prefix")),bases,100000);var expected=new HashMap<Long,Integer>();for(Object item:list(r.get("entries"))){var e=object(item);expected.put(Long.parseUnsignedLong(text(e.get("hash"))),number(e.get("count")));}require(entries(table).equals(expected),"Complete independent parental count family differs");require(table.windows().orElseThrow()==integer(r.get("windows"),0,Long.MAX_VALUE)&&table.ambiguousBases().orElseThrow()==integer(r.get("ambiguous_bases"),0,Long.MAX_VALUE)&&table.saturatedWindows().orElseThrow()==integer(r.get("saturated_windows"),0,Long.MAX_VALUE),"Window/ambiguity/saturation provenance differs");var output=new ByteArrayOutputStream();HifiasmParentalKmers.write(table,output);byte[] bytes=output.toByteArray();Path path=out.resolve(id+".yak");Files.write(path,bytes,java.nio.file.StandardOpenOption.CREATE_NEW);var decoded=HifiasmParentalKmers.read(new ByteArrayInputStream(bytes),bytes.length,100000);require(entries(decoded).equals(expected)&&decoded.windows().isEmpty()&&decoded.ambiguousBases().isEmpty()&&decoded.saturatedWindows().isEmpty(),"Source YAK read fabricated provenance or changed counts");tables.put(id,table);tablePins.put(id,GseapyExecutionRuntime.pinObject(NextflowFiles.pin(path,67108864,()->{})));}
        for(Object raw:list(root.get("read_checks"))){var r=object(raw);var result=HifiasmParentalKmers.classify(hex(r.get("source_recovered_hex")),tables.get(text(r.get("paternal"))),tables.get(text(r.get("maternal"))),number(r.get("minimum")),number(r.get("repeat")),finite(r.get("ratio"),0,Double.MAX_VALUE));int label=result.sourceClass()=='p'?1:result.sourceClass()=='m'?2:0;require(label==number(r.get("expected_source_label")),"Actual original compressed-read worker class differs");int[] copy=result.flagCounts();if(copy.length>0){int old=copy[0];copy[0]++;require(result.flagCounts()[0]==old,"Classification flags are mutable");}reads++;}
        Table mini=new Table(31,10,List.of(new Entry(1,2),new Entry(1025,3)),OptionalLong.empty(),OptionalLong.empty(),OptionalLong.empty());var stream=new ByteArrayOutputStream();HifiasmParentalKmers.write(mini,stream);byte[] valid=stream.toByteArray();var mutations=new ArrayList<byte[]>();byte[] changed=valid.clone();changed[0]='X';mutations.add(changed);changed=valid.clone();changed[4]=64;mutations.add(changed);changed=valid.clone();changed[8]=9;mutations.add(changed);changed=valid.clone();changed[12]=12;mutations.add(changed);changed=java.util.Arrays.copyOf(valid,valid.length-1);mutations.add(changed);changed=java.util.Arrays.copyOf(valid,valid.length+1);mutations.add(changed);changed=valid.clone();changed[24]=1;mutations.add(changed);changed=valid.clone();System.arraycopy(changed,32,changed,40,8);mutations.add(changed);changed=valid.clone();changed[39]=(byte)128;mutations.add(changed);for(byte[] corrupt:mutations)reject(()->HifiasmParentalKmers.read(new ByteArrayInputStream(corrupt),corrupt.length,100));
        reject(()->HifiasmParentalKmers.read(new ByteArrayInputStream(valid),valid.length,1));reject(()->HifiasmParentalKmers.read(new ByteArrayInputStream(valid),valid.length-1,100));reject(()->HifiasmParentalKmers.count(List.of(new byte[]{'A'}),1,10,0,1));reject(()->HifiasmParentalKmers.count(List.of(new byte[]{'A','C'}),1,10,2,1));reject(()->HifiasmParentalKmers.count(List.of(new byte[]{'A'}),64,10,1,1));reject(()->HifiasmParentalKmers.hash(new byte[]{'N'}));reject(()->new Table(31,10,List.of(new Entry(1,2),new Entry(1,3)),OptionalLong.empty(),OptionalLong.empty(),OptionalLong.empty()));reject(()->new Entry(1,1024));reject(()->HifiasmParentalKmers.classifyCounts(31,new int[15],new int[2],1));reject(()->HifiasmParentalKmers.classifyCounts(31,new int[16],new int[2],Double.NaN));reject(()->HifiasmParentalKmers.classifyCounts(31,new int[16],new int[2],-1));
        require(rejected==20,"Complete native rejection family differs");NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.ofEntries(Map.entry("status","passed-production-native-parental-component-only"),Map.entry("hash_cases",hashes),Map.entry("classification_cases",classifications),Map.entry("table_families",tables.size()),Map.entry("source_trio_reads",reads),Map.entry("invalid_families_rejected",rejected),Map.entry("table_pins",tablePins),Map.entry("analysis_admitted",false),Map.entry("full_adapter_admitted",false)));System.out.println("Passed "+hashes+" hashes, "+classifications+" vote cases, "+tables.size()+" count families, "+reads+" actual source-worker read classes and "+rejected+" rejections");
    }
}
