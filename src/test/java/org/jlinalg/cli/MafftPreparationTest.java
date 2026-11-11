/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MafftPreparationTest {
    @TempDir Path root;
    record Fixture(MafftRecipe.Spec spec,Path work,Path input) { }
    Fixture fixture(String text,boolean add,Map<String,Object> limits)throws Exception {
        Path input=root.resolve("source \u03b1 input.fasta");Files.writeString(input,text,StandardCharsets.UTF_8);
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("profile","linux-x86_64-mafft-7.526");r.put("operation",add?"ADD":"DE_NOVO");r.put("input",input.toString());r.put("alphabet","NUCLEOTIDE");r.put("symbols","BIOLOGICAL");r.put("headers","WHOLE_HEADER");r.put("input_gaps",add?"PRESERVE_ALIGNMENT":"REMOVE");r.put("strategy","AUTO");r.put("scoring",Map.of("source_defaults",true));r.put("parameters",Map.of("threads",1,"output_order","INPUT","keep_length",add));if(!limits.isEmpty())r.put("limits",limits);
        if(add){Path added=root.resolve("added.fasta");Files.writeString(added,">added \u03b2\nAC-GT\n");r.put("additions",added.toString());r.put("addition_gaps","REMOVE");}
        Path recipe=root.resolve("recipe.json");Files.writeString(recipe,MafftJson.json(r));Path work=root.resolve("work");MafftBundle.create(work);return new Fixture(MafftRecipe.read(recipe),work,input);
    }
    MafftAnalysis.Prepared prepare(Fixture f)throws IOException{return MafftAnalysis.prepare(f.spec(),f.work().resolve("inputs"),f.work().resolve("staged"),()->{});}
    @Test void completeUnicodeHeadersCaseAndOriginalBytesSurvivePreparation()throws Exception {
        String raw=">\u03b1 taxon full description\na-CgT\n>\u7b2c\u4e09\nACGTT\n";var f=fixture(raw,false,Map.of());var p=prepare(f);
        assertEquals(raw,Files.readString(f.work().resolve("inputs/input.original.fasta")));assertEquals(2,p.population().rows().size());assertTrue(p.originalToOpaque().containsKey("\u03b1 taxon full description"));
        String staged=Files.readString(p.preparedInput());assertTrue(staged.contains(">T000000001\naCgT\n"));assertFalse(staged.contains("\u03b1"));
        var map=(List<?>)SimpleJson.parse(Files.readString(f.work().resolve("taxon-map.json")));assertEquals(2,map.size());p.verify(f.work().resolve("inputs"),f.spec().limits(),()->{});
    }
    @Test void aSingleRowBackboneRemainsAlignedWhileAddedInputIsExplicitlyUngapped()throws Exception {var f=fixture(">reference \u03b4\nAc-GT\n",true,Map.of());var p=prepare(f);assertEquals(1,p.primary().rows().size());assertEquals(2,p.population().rows().size());assertTrue(Files.readString(p.preparedInput()).contains("Ac-GT"));assertTrue(Files.readString(p.preparedAdditions()).contains("ACGT"));assertFalse(Files.readString(p.preparedAdditions()).contains("AC-GT"));}
    @Test void malformedLaterInputIsNotAcceptedAsAValidPrefix()throws Exception {var f=fixture(">valid\nACGT\n>late\nACZT\n",false,Map.of());assertThrows(IOException.class,()->prepare(f));assertTrue(Files.readString(f.work().resolve("inputs/input.original.fasta")).contains("ACZT"));assertFalse(Files.exists(f.work().resolve("taxon-map.json")));}
    @Test void capacityAppliesToTheWholePopulationRatherThanASample()throws Exception {var f=fixture(">a\nACGT\n>b\nACGT\n",false,Map.of("fasta",Map.of("cells",7)));assertThrows(IOException.class,()->prepare(f));assertFalse(Files.exists(f.work().resolve("taxon-map.json")));}
    @Test void everyOriginalInputMutationIsDetectedAfterPreparation()throws Exception {var f=fixture(">a\nACGT\n>b\nACGTT\n",false,Map.of());var p=prepare(f);Files.writeString(f.input(),">changed\nACGT\n");assertThrows(IOException.class,()->p.verify(f.work().resolve("inputs"),f.spec().limits(),()->{}));}
    @Test void preparedByteMutationInvalidatesExecutionInputs()throws Exception {var f=fixture(">a\nACGT\n>b\nACGTT\n",false,Map.of());var p=prepare(f);Files.writeString(p.preparedInput(),">T000000001\nAAAA\n");assertThrows(IOException.class,()->p.verify(f.work().resolve("inputs"),f.spec().limits(),()->{}));}
    @Test void extraOriginalSnapshotFilesCannotBeHidden()throws Exception {var f=fixture(">a\nACGT\n>b\nACGTT\n",false,Map.of());var p=prepare(f);Files.writeString(f.work().resolve("inputs/extra"),"extra");assertThrows(IOException.class,()->p.verify(f.work().resolve("inputs"),f.spec().limits(),()->{}));}
    @Test void inputByteCapacityRejectsTheWholeFileBeforeParsing()throws Exception {var f=fixture(">a\nACGT\n>b\nACGTT\n",false,Map.of("inputBytes",5));assertThrows(IOException.class,()->prepare(f));assertFalse(Files.exists(f.work().resolve("taxon-map.json")));}
    @Test void taxonMapOutputCapacityCannotProduceAnAdmittedPrefix()throws Exception {var f=fixture(">a\nACGT\n>b\nACGTT\n",false,Map.of("process",Map.of("fileBytes",10)));assertThrows(IOException.class,()->prepare(f));assertTrue(Files.exists(f.work().resolve("taxon-map.json")));}
    @Test void anEmptyResidueRowIsRefusedRatherThanOmitted()throws Exception {var f=fixture(">all-gaps\n----\n>b\nACGT\n",false,Map.of());assertThrows(IOException.class,()->prepare(f));assertFalse(Files.exists(f.work().resolve("taxon-map.json")));}
}
