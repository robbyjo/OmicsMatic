/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.jlinalg.proteomics.FragmentAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SpectralAnnotationCompute.map;

class SpectralAnnotationTest {
    @TempDir Path temporary;
    Map<String,Object> request(){var r=SpectralAnnotationCli.example();r.put("input",map("file","/foreign/source/not/read.tsv","annotation_column","Annotation","format","literal-utf8-tsv"));r.put("capacity",map("rows",100,"input_bytes",4194304,"output_bytes",4194304,"wall_millis",60000,"fragment_charge",16));return r;}
    SpectralAnnotationRequest.Spec spec()throws IOException{return SpectralAnnotationRequest.parse(request(),false);}
    Path input(String name,String text)throws IOException{Path root=temporary.resolve(name);Files.createDirectory(root);Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("outputs"));Files.writeString(root.resolve("inputs/library.tsv"),text,StandardCharsets.UTF_8);return root;}

    @Test void literalPhysicalAnnotationRetainsItsChargeAndIonNumber(){
        assertEquals(new FragmentAnnotation('b',2,1),FragmentAnnotation.parse("b2^1"));assertEquals(new FragmentAnnotation('y',12,3),FragmentAnnotation.parse("y12^3"));assertEquals("noloss",FragmentAnnotation.parse("y12^3").lossType());
        assertEquals(Integer.MAX_VALUE,FragmentAnnotation.parse("b2147483647^1").number());assertThrows(IllegalArgumentException.class,()->new FragmentAnnotation('a',2,1));
    }
    @Test void missingAndUnsupportedInformationNeverDefaultsToChargeOne(){
        for(String raw:List.of("","b2","b2^","b2^0","b0^1","b02^1","b2^01","B2^1","a2^1","y2-H2O^1","y2^1-NH3"," y2^1","y2^1 ","y2^+1","y٢^1","y2^1\n","b2147483648^1","b2^2147483648"))assertThrows(IllegalArgumentException.class,()->FragmentAnnotation.parse(raw),raw);
        assertThrows(NullPointerException.class,()->FragmentAnnotation.parse(null));
    }
    @Test void allOriginalCellsStayLiteralIncludingUnicodeNegativeScalesAndUnusedTokens()throws Exception{
        String original="Annotation\tProteinId\tRT\tIntensity\tunused\nb2^1\tβ😀\t-29.090593859067077\t5921.397725\tNaN\ny12^3\tNA\t0\t0.636213\tInf\n";
        Path root=input("literal",original);byte[] before=Files.readAllBytes(root.resolve("inputs/library.tsv"));var audit=SpectralAnnotationCompute.run(root,spec(),false,()->{});
        assertEquals(2L,audit.get("input_records"));assertEquals(10L,audit.get("original_cells_preserved"));assertEquals(false,audit.get("normalization_or_scientific_values_modified"));
        String out=Files.readString(root.resolve("outputs/library.tsv"));assertTrue(out.contains("b2^1\tβ😀\t-29.090593859067077\t5921.397725\tNaN\t1\tb\t2\tnoloss\n"));assertTrue(out.contains("y12^3\tNA\t0\t0.636213\tInf\t3\ty\t12\tnoloss\n"));assertArrayEquals(before,Files.readAllBytes(root.resolve("inputs/library.tsv")));
        assertEquals(NextflowJson.json(audit),NextflowJson.json(SpectralAnnotationCompute.run(root,spec(),true,()->{})));assertEquals(2,Files.readAllLines(root.resolve("ROWS.jsonl")).size());
    }
    @Test void replayRejectsChangedUnusedCellsAndChangedAnnotationLedger()throws Exception{
        for(String member:List.of("outputs/library.tsv","ROWS.jsonl")){
            Path root=input(member.startsWith("outputs")?"changed-cell":"changed-ledger","Annotation\tunused\ny4^2\tliteral\n");SpectralAnnotationCompute.run(root,spec(),false,()->{});Path path=root.resolve(member);String before=Files.readString(path);Files.writeString(path,member.startsWith("outputs")?before.replace("literal","different"):before.replace("\"charge\":2","\"charge\":1"));assertNotEquals(before,Files.readString(path));assertThrows(IOException.class,()->SpectralAnnotationCompute.run(root,spec(),true,()->{}));
        }
    }
    @Test void originalBomCrLfAndUnterminatedFinalRecordAreCapturedWithoutCellChanges()throws Exception{
        Path root=input("line-endings","\ufeffAnnotation\tother\r\nb2^4\tquoted\"literal\r\ny3^2\tlast");byte[] original=Files.readAllBytes(root.resolve("inputs/library.tsv"));SpectralAnnotationCompute.run(root,spec(),false,()->{});String output=Files.readString(root.resolve("outputs/library.tsv"));assertFalse(output.contains("\r"));assertFalse(output.startsWith("\ufeff"));assertTrue(output.endsWith("y3^2\tlast\t2\ty\t3\tnoloss\n"));assertTrue(output.contains("quoted\"literal"));assertArrayEquals(original,Files.readAllBytes(root.resolve("inputs/library.tsv")));
    }
    @Test void collidingExplicitColumnsAreRefusedInsteadOfOverwritten()throws Exception{
        int n=0;for(String collision:List.of("FragmentCharge","fragment_charge","Fragment-Charge","Fragment Charge","FragmentType","FragmentSeriesNumber","FragmentLossType")){
            Path root=input("collision"+(n++),"Annotation\t"+collision+"\nb2^1\t2\n");assertThrows(IOException.class,()->SpectralAnnotationCompute.run(root,spec(),false,()->{}));
        }
    }
    @Test void malformedRecordsUtf8AndBlankRowsAreNeverDropped()throws Exception{
        int n=0;for(String source:List.of("Annotation\tx\nb2^1\n","Annotation\tx\nb2^1\tz\n\ny2^2\tz\n","Annotation\nunknown\n","Annotation\n", "Annotation\nb2^1\u0000\n")){
            Path root=input("invalid"+(n++),source);assertThrows(IOException.class,()->SpectralAnnotationCompute.run(root,spec(),false,()->{}));
        }
        Path invalid=input("invalid-utf8","Annotation\n");Files.write(invalid.resolve("inputs/library.tsv"),new byte[]{(byte)0xc3,0x28},StandardOpenOption.APPEND);assertThrows(IOException.class,()->SpectralAnnotationCompute.run(invalid,spec(),false,()->{}));
    }
    @Test void fullPopulationByteRowChargeAndCancellationBoundsFail()throws Exception{
        int n=0;for(var cap:List.of(map("rows",1),map("input_bytes",1),map("output_bytes",1),map("fragment_charge",1))){
            var r=request();var c=new LinkedHashMap<String,Object>();NextflowFiles.object(r.get("capacity")).forEach((k,v)->c.put(k.toString(),v));c.putAll(cap);r.put("capacity",c);Path root=input("capacity"+(n++),"Annotation\nb2^2\ny3^1\n");assertThrows(IOException.class,()->SpectralAnnotationCompute.run(root,SpectralAnnotationRequest.parse(r,false),false,()->{}));
        }
        Path root=input("cancelled","Annotation\nb2^1\n");assertThrows(CancellationException.class,()->SpectralAnnotationCompute.run(root,spec(),false,()->{throw new CancellationException("requested");}));
        Path longRow=input("long-row","Annotation\tunused\nb2^1\t"+"x".repeat(1048576)+"\n");assertThrows(IOException.class,()->SpectralAnnotationCompute.run(longRow,spec(),false,()->{}));
    }
    @Test void typedPoliciesAndColumnMappingAreExplicit()throws Exception{
        for(var edit:List.of(map("annotation_syntax","guess"),map("engine","diann"),map("process",Map.of()),map("filter_bad_rows",true))){var r=request();r.putAll(edit);assertThrows(IOException.class,()->SpectralAnnotationRequest.parse(r,false));}
        var r=request();r.put("input",map("file","relative.tsv","annotation_column","Annotation","format","literal-utf8-tsv"));assertThrows(IOException.class,()->SpectralAnnotationRequest.parse(r,true));
        Path root=input("case-sensitive","annotation\nb2^1\n");assertThrows(IOException.class,()->SpectralAnnotationCompute.run(root,spec(),false,()->{}));
    }
    @Test void commandReportsMissingSourcesAsSkippedAndDoesNotRunAnUpstreamProcess()throws Exception{
        var r=request();r.put("input",map("file",temporary.resolve("missing.tsv").toString(),"annotation_column","Annotation","format","literal-utf8-tsv"));Path file=temporary.resolve("request.json");Files.writeString(file,NextflowJson.json(r));var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(3,SpectralAnnotationCli.run(new String[]{"--request",file.toString(),"--out",temporary.resolve("no-capture").toString()},new PrintStream(out),new PrintStream(err)));assertTrue(err.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertFalse(Files.exists(temporary.resolve("no-capture")));
        out.reset();err.reset();assertEquals(0,SpectralAnnotationCli.run(new String[]{"--profiles"},new PrintStream(out),new PrintStream(err)));assertTrue(out.toString(StandardCharsets.UTF_8).contains("\"raw_MS_analysis_admitted\":false"));assertEquals(2,SpectralAnnotationCli.run(new String[]{"--unknown"},new PrintStream(out),new PrintStream(err)));
    }
}
