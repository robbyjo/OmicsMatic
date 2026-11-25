/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.IqCompute.map;

class IqWorkflowTest {
    @TempDir Path temporary;
    Map<String,Object> request()throws IOException {
        var request=IqToolsCli.example();request.put("input",map("file","/original/path/not/read/during/replay.tsv","compression","none","separator","tab","columns",map("protein","P","sample","S","ion","I","quant","Q")));
        request.put("capacity",map("rows",100,"proteins",10,"samples",20,"ions_per_protein",100,"cells_per_protein",2000,"pairs_per_protein",200,"numeric_bytes",4194304,"work",1000000,"iterations",1000,"input_bytes",1048576,"decoded_bytes",1048576,"output_bytes",1048576,"wall_millis",60000));return request;
    }
    Path input(String name,String text)throws IOException {Path root=temporary.resolve(name);Files.createDirectory(root);Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("outputs"));Files.writeString(root.resolve("inputs/ions.table"),text,StandardCharsets.UTF_8);return root;}
    @Test void completeLiteralRowsMetadataAndProteinPopulationsReplay()throws Exception {
        Path root=input("complete","P\tS\tI\tQ\tunrelated\nP1\tA\ti1\t10\tliteral 1\nP1\tB\ti1\t11\tliteral 2\nP1\tC\ti1\t12\tliteral 3\nP1\tA\ti2\t20\tliteral 4\nP1\tB\ti2\t21\tliteral 5\nP1\tC\ti2\t22\tliteral 6\n");var spec=IqRequest.parse(request(),false);var audit=IqCompute.run(root,spec,false,()->{});
        assertEquals(6L,audit.get("input_rows"));assertEquals(1,audit.get("proteins"));assertEquals(3,audit.get("samples"));assertEquals(6,Files.readAllLines(root.resolve("ROWS.jsonl")).size());assertTrue(Files.readString(root.resolve("ROWS.jsonl")).contains("literal 6"));
        assertEquals(NextflowJson.json(audit),NextflowJson.json(IqCompute.run(root,spec,true,()->{})));
        String tsv=Files.readString(root.resolve("outputs/proteins.tsv"));assertTrue(tsv.startsWith("protein\tsample\tnormalized_log2\tcomponent_zero_based\tsource_annotation\n"));assertEquals(4,tsv.lines().count());
    }
    @Test void wrongUnitsEditionProcessAndExtraSettingsAreNotInferred()throws Exception {
        for(var edit:List.of(map("units","raw-linear"),map("edition","iq-1.9.12"),map("process",Map.of()),map("undeclared_filter",true))){var r=request();r.putAll(edit);assertThrows(IOException.class,()->IqRequest.parse(r,false));}
        var r=request();var input=new LinkedHashMap<String,Object>();object(r.get("input")).forEach((k,v)->input.put(k.toString(),v));input.put("columns",map("protein","P","sample","S","ion","I","quant","I"));r.put("input",input);assertThrows(IOException.class,()->IqRequest.parse(r,false));
    }
    @Test void validLog2ZeroAndDuplicateLinearSumsDoNotTriggerPreprocessing()throws Exception {
        Path root=input("duplicate","P\tS\tI\tQ\nP1\tA\ti1\t0\nP1\tA\ti1\t0\nP1\tB\ti1\t-2\n");var audit=IqCompute.run(root,IqRequest.parse(request(),false),false,()->{});assertEquals(1L,audit.get("duplicate_rows"));assertEquals(false,audit.get("normalization_performed"));String rows=Files.readString(root.resolve("ROWS.jsonl"));assertTrue(rows.contains("SOURCE_SUM_IN_LINEAR_SPACE"));assertTrue(Files.readString(root.resolve("outputs/proteins.tsv")).contains("P1\tA\t1.0\t0\t\n"));
    }
    @Test void missingQuantitiesAndCapacityFailuresCannotSilentlyDropRows()throws Exception {
        for(String value:List.of("NA","NaN","Infinity"," 1")){Path root=input("invalid"+value.hashCode(),"P\tS\tI\tQ\nP1\tA\ti1\t"+value+"\n");assertThrows(IOException.class,()->IqCompute.run(root,IqRequest.parse(request(),false),false,()->{}));}
        Path root=input("too-small","P\tS\tI\tQ\nP1\tA\ti1\t1\n");var r=request();var c=new LinkedHashMap<String,Object>();object(r.get("capacity")).forEach((k,v)->c.put(k.toString(),v));c.put("output_bytes",1);r.put("capacity",c);assertThrows(IOException.class,()->IqCompute.run(root,IqRequest.parse(r,false),false,()->{}));
    }
    @Test void sourceLabelsAreLiteralEvenWhenTheyMatchOutputHeaderNames()throws Exception {
        Path root=input("literal-identifiers","P\tS\tI\tQ\nNA\tprotein\ti1\t-1\nNA\tsample\ti1\t0\n");IqCompute.run(root,IqRequest.parse(request(),false),false,()->{});String text=Files.readString(root.resolve("outputs/proteins.tsv"));assertTrue(text.contains("NA\tprotein\t-1.0"));assertTrue(text.contains("NA\tsample\t0.0"));
    }
    @Test void completeNumericalReplayRejectsChangedUnusedInputMetadata()throws Exception {
        Path root=input("changed-metadata","P\tS\tI\tQ\tnote\nP1\tA\ti1\t1\toriginal\n");var spec=IqRequest.parse(request(),false);IqCompute.run(root,spec,false,()->{});Files.writeString(root.resolve("inputs/ions.table"),Files.readString(root.resolve("inputs/ions.table")).replace("original","changed"));assertThrows(IOException.class,()->IqCompute.run(root,spec,true,()->{}));
    }
}
