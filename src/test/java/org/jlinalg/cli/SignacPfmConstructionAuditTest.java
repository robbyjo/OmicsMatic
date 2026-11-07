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

/** Independent checks against original TFBSTools constructor slots, not a replacement PFM constructor. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SignacPfmConstructionAuditTest {
    @TempDir Path root;
    private Map<String,Map<String,Object>> controls;
    private final AtomicInteger next=new AtomicInteger();
    @BeforeAll void fixture()throws Exception {
        byte[] raw;try(var in=getClass().getResourceAsStream("/org/jlinalg/sequence/signac-pfm-construction/original-controls.zip")){assertNotNull(in);raw=in.readAllBytes();}
        assertEquals("65f369aafa67df98c06822c7bf19c6f2dbb39abf591d45b6c2cfb3a95f30d44d",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        byte[] result=null;try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals("original.json"))result=zip.readAllBytes();}
        assertNotNull(result);controls=new LinkedHashMap<>();for(Object value:list(object(SimpleJson.parse(new String(result,StandardCharsets.UTF_8))).get("results"))){var c=object(value);controls.put((String)c.get("id"),c);}assertEquals(10,controls.size());
    }
    @TestFactory Stream<DynamicTest> originalEffectiveSlots(){return Stream.of("integer","fractional","fractional_under_one","maximum_integer").map(id->DynamicTest.dynamicTest(id,()->{
        var record=controls.get(id);var input=model(record);var result=SignacPfmConstructionAudit.audit(file(tsv(record)),List.of(input),()->false);
        assertEquals(1,result.motifs());assertEquals(2,result.positions());assertEquals(id.startsWith("fractional")?8:0,result.changedCounts());assertEquals(id.equals("fractional")?.25:id.equals("fractional_under_one")?.5:0,result.maximumCountChange());
        assertEquals("integer",object(record.get("value")).get("storage"));
    }));}
    @TestFactory Stream<DynamicTest> sourceIntegerOverflow(){return Stream.of("integer_overflow","large_count").map(id->DynamicTest.dynamicTest(id,()->{
        var record=controls.get(id);assertTrue(object(record.get("value")).containsKey("error"));var input=model(record);assertThrows(IOException.class,()->SignacPfmConstructionAudit.audit(file(tsv(controls.get("integer"))),List.of(input),()->false));
    }));}
    @TestFactory Stream<DynamicTest> alteredEffectiveModels(){return Stream.of("count","fractional-effective","background","id","name","position","missing","extra","NaN","bad-utf8","extra-column").map(id->DynamicTest.dynamicTest(id,()->{
        String value=tsv(controls.get("fractional"));String[] lines=value.split("\n");String[] row=lines[1].split("\t",-1);
        switch(id){case "count"->row[3]="10";case "fractional-effective"->row[3]="9.25";case "background"->row[7]="0.2";case "id"->row[0]="OTHER";case "name"->row[1]="OTHER";case "position"->row[2]="2";case "NaN"->row[3]="NaN";case "extra-column"->row=Arrays.copyOf(row,12);default->{}}
        if(id.equals("extra-column"))row[11]="unexpected";lines[1]=String.join("\t",row);value=String.join("\n",lines)+"\n";
        if(id.equals("missing"))value=lines[0]+"\n"+lines[1]+"\n";if(id.equals("extra"))value+=lines[2]+"\n";
        Path path=file(value);if(id.equals("bad-utf8"))Files.write(path,new byte[]{(byte)0xc3,0x28});
        assertThrows(IOException.class,()->SignacPfmConstructionAudit.audit(path,List.of(model(controls.get("fractional"))),()->false));
    }));}
    @TestFactory Stream<DynamicTest> originalIntegerBoundary()throws Exception {
        byte[] raw;try(var in=getClass().getResourceAsStream("/org/jlinalg/sequence/signac-pfm-construction/integer-boundary-original.json")){assertNotNull(in);raw=in.readAllBytes();}
        assertEquals("e04c5adab9057cf27b1636e6e59ed3b061a74b3e58912097d2ee02edf904e965",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));List<Object> records=list(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)));assertEquals(6,records.size());
        return records.stream().map(value->DynamicTest.dynamicTest("integer-boundary/"+object(value).get("input"),()->{
            var record=object(value);double declared=((Number)record.get("input")).doubleValue();var model=new SignacPfmIO.Model("M","factor",new double[][]{{declared,1,0,0}},new double[]{.1,.2,.3,.4});
            boolean succeeded=record.get("effective") instanceof List<?>;double effective=succeeded?((Number)list(record.get("effective")).get(0)).doubleValue():Math.floor(declared);
            String text=SignacPfmIO.HEADER+"\nM\tfactor\t1\t"+effective+"\t1\t0\t0\t0.1\t0.2\t0.3\t0.4\n";Path path=file(text);
            if(succeeded){var result=SignacPfmConstructionAudit.audit(path,List.of(model),()->false);assertEquals(declared==effective?0:1,result.changedCounts());assertEquals(declared-effective,result.maximumCountChange());}
            else{assertTrue(object(record.get("effective")).containsKey("error"));assertThrows(IOException.class,()->SignacPfmConstructionAudit.audit(path,List.of(model),()->false));}
        }));
    }
    @Test void cancellationStopsAudit()throws Exception {assertThrows(CancellationException.class,()->SignacPfmConstructionAudit.audit(file(tsv(controls.get("integer"))),List.of(model(controls.get("integer"))),()->true));}
    @Test void emptyModelPopulationRejected()throws Exception {assertThrows(IOException.class,()->SignacPfmConstructionAudit.audit(file(tsv(controls.get("integer"))),List.of(),()->false));}
    @Test void originalZeroEffectiveColumnsRemainZero()throws Exception {
        var result=SignacPfmConstructionAudit.audit(file(tsv(controls.get("fractional_under_one"))),List.of(model(controls.get("fractional_under_one"))),()->false);assertEquals(8,result.changedCounts());
    }
    private Path file(String value)throws IOException {Path path=root.resolve("effective-"+next.incrementAndGet()+".tsv");Files.writeString(path,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);return path;}
    private static SignacPfmIO.Model model(Map<String,Object> record){var raw=list(record.get("declared"));double[][] rows=new double[2][4];for(int base=0;base<4;base++)for(int position=0;position<2;position++)rows[position][base]=((Number)list(raw.get(base)).get(position)).doubleValue();return new SignacPfmIO.Model("M","factor",rows,new double[]{.1,.2,.3,.4});}
    private static String tsv(Map<String,Object> record){var value=object(record.get("value"));var raw=list(value.get("effective"));StringBuilder text=new StringBuilder(SignacPfmIO.HEADER+"\n");for(int p=0;p<2;p++){text.append("M\tfactor\t").append(p+1);for(int b=0;b<4;b++)text.append('\t').append(((Number)list(raw.get(b)).get(p)).doubleValue());for(double bg:new double[]{.1,.2,.3,.4})text.append('\t').append(bg);text.append('\n');}return text.toString();}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object value){return (List<Object>)value;}
}
