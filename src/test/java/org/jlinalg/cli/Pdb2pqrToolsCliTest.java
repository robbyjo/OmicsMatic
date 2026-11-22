/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Pdb2pqrToolsCliTest {
    @TempDir Path root;
    static Map<String,String> model() {
        String[] fields={"force-field","PARSE","mode","prepare","protonation","forcefield","debump","false","optimize","false","water","keep","repair","error","unparameterized","error","neutral-n","false","neutral-c","false","state","1","alternate","error","length-units","angstrom","max-heavy-shift",".01"};var options=new LinkedHashMap<String,String>();for(int i=0;i<fields.length;i+=2)options.put(fields[i],fields[i+1]);return options;
    }
    private record Run(int code,String out,String err) { }
    private static Run run(String... args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=Pdb2pqrToolsCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private static Run run(Map<String,String> options){var args=new ArrayList<String>();options.forEach((k,v)->{args.add("--"+k);args.add(v);});return run(args.toArray(String[]::new));}
    private Map<String,String> options(String name)throws Exception {var options=model();Path input=root.resolve("source.pdb");if(!Files.exists(input))Files.writeString(input,Pdb2pqrInputTest.atom(1,"N",' ',"ALA","A",1,' ',0,"","N"));options.put("input",input.toString());options.put("out",root.resolve(name).toString());return options;}
    private static void status(Map<String,String> options,String expected)throws Exception {Path out=Path.of(options.get("out"));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains(expected));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action:"));assertFalse(Files.exists(out.resolve("prepared.pqr")));assertFalse(Files.exists(out.resolve("results.json")));}
    @Test void allModelPoliciesAreRequiredWithoutScientificDefaults() {
        var full=model();for(String key:full.keySet()){var options=new LinkedHashMap<>(full);options.remove(key);assertThrows(IllegalArgumentException.class,()->Pdb2pqrToolsCli.choices(options),key);}
    }
    @Test void allSixForceFieldsUseCanonicalNamesAndFixedLocalOutputs() {
        for(String ff:List.of("AMBER","CHARMM","PARSE","TYL06","PEOEPB","SWANSON")){var options=model();options.put("force-field",ff);var flags=Pdb2pqrToolsCli.choices(options).arguments();assertEquals(ff,flags.get(1));assertTrue(flags.contains("--keep-chain"));assertTrue(flags.contains("--whitespace"));assertTrue(flags.contains("prepared.pqr"));assertFalse(flags.contains("--ffout"));}
    }
    @Test void phIsRequiredOnlyForPropkaAndCannotBeSilentlyIgnored() {
        var options=model();options.put("ph","7");assertThrows(IllegalArgumentException.class,()->Pdb2pqrToolsCli.choices(options));options.put("protonation","propka");assertTrue(Pdb2pqrToolsCli.choices(options).arguments().contains("--with-ph"));for(String ph:List.of("NaN","Infinity","-1","15")){options.put("ph",ph);assertThrows(IllegalArgumentException.class,()->Pdb2pqrToolsCli.choices(options));}options.remove("ph");assertThrows(IllegalArgumentException.class,()->Pdb2pqrToolsCli.choices(options));
    }
    @Test void assignOnlyRejectsEveryIgnoredRepairOptimizationOrPhChoice() {
        var base=model();base.put("mode","assign-only");assertTrue(Pdb2pqrToolsCli.choices(base).arguments().contains("--assign-only"));for(String[] bad:new String[][]{{"debump","true"},{"optimize","true"},{"protonation","propka"},{"repair","allow"},{"unparameterized","omit"}}){var options=new LinkedHashMap<>(base);options.put(bad[0],bad[1]);if(bad[0].equals("protonation"))options.put("ph","7");assertThrows(IllegalArgumentException.class,()->Pdb2pqrToolsCli.choices(options));}
    }
    @Test void neutralTerminalFlagsRequireParseAndInverseFlagsMatchPositiveDecisions() {
        var options=model();options.put("neutral-n","true");assertTrue(Pdb2pqrToolsCli.choices(options).arguments().contains("--neutraln"));options.put("force-field","AMBER");assertThrows(IllegalArgumentException.class,()->Pdb2pqrToolsCli.choices(options));options.put("force-field","PARSE");options.put("debump","true");options.put("optimize","true");var flags=Pdb2pqrToolsCli.choices(options).arguments();assertFalse(flags.contains("--nodebump"));assertFalse(flags.contains("--noopt"));
    }
    @Test void unsupportedUnitsModesAndNonfiniteDisplacementsFail() {
        for(String[] pair:new String[][]{{"force-field","parse"},{"mode","clean"},{"water","guess"},{"repair","yes"},{"debump","yes"},{"state","0"},{"alternate","guess"},{"length-units","nanometer"},{"max-heavy-shift","NaN"},{"max-heavy-shift","-1"}}){var options=model();options.put(pair[0],pair[1]);assertThrows(IllegalArgumentException.class,()->Pdb2pqrToolsCli.choices(options));}
    }
    @Test void missingHomeSkipsWithRetainedSelectionAndConcreteInstallationAction()throws Exception {
        var options=options("skip");var run=run(options);assertEquals(3,run.code(),run.err());status(options,"skipped-unavailable-dependency");Path out=Path.of(options.get("out"));assertTrue(Files.exists(out.resolve("partial/selected.pdb")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("--allow-install"));assertTrue(run.err().contains("PREPARATION SKIPPED"));
    }
    @Test void unadmittedRuntimeSkipsWithoutLaunchingAnyExecutable()throws Exception {
        var options=options("unadmitted");Path home=Files.createDirectory(root.resolve("runtime"));Files.writeString(home.resolve(".jlinalg-pdb2pqr.json"),"{}");options.put("home",home.toString());assertEquals(3,run(options).code());status(options,"skipped-unavailable-dependency");assertFalse(Files.readString(Path.of(options.get("out"),"session.json")).contains("\"pid\""));
    }
    @Test void malformedPdbAndAlternateAmbiguityFailBeforeDependencyChecks()throws Exception {
        var options=options("bad-pdb");Files.writeString(Path.of(options.get("input")),"ATOM invalid\n");assertEquals(2,run(options).code());status(options,"failed");Files.writeString(Path.of(options.get("input")),Pdb2pqrInputTest.atom(1,"N",'A',"ALA","A",1,' ',0,"","N"));options.put("out",root.resolve("alternate").toString());assertEquals(2,run(options).code());status(options,"failed");
    }
    @Test void chosenStateOrdinalAndSourceHashesAreRetainedInSkippedBundle()throws Exception {
        var options=options("state");Files.writeString(Path.of(options.get("input")),"MODEL        9\n"+Pdb2pqrInputTest.atom(1,"N",' ',"ALA","A",1,' ',1,"","N")+"ENDMDL\nMODEL      100\n"+Pdb2pqrInputTest.atom(1,"N",' ',"ALA","A",1,' ',4,"","N")+"ENDMDL\n");options.put("state","2");assertEquals(3,run(options).code());assertTrue(Files.readString(Path.of(options.get("out"),"partial/selected.pdb")).contains("   4.000"));String session=Files.readString(Path.of(options.get("out"),"session.json"));assertTrue(session.contains("selected_pdb_sha256"));assertTrue(session.contains("selected_state_ordinal\":2"));
    }
    @Test void byteAtomAndPolicyBoundsFailWithProminentRecovery()throws Exception {
        var options=options("bytes");options.put("max-bytes","1");assertEquals(2,run(options).code());status(options,"failed");options.remove("max-bytes");options.put("out",root.resolve("atoms").toString());options.put("max-atoms","1");Files.writeString(Path.of(options.get("input")),Pdb2pqrInputTest.atom(1,"N",' ',"ALA","A",1,' ',0,"","N")+Pdb2pqrInputTest.atom(2,"CA",' ',"ALA","A",1,' ',1,"","C"));assertEquals(2,run(options).code());status(options,"failed");
    }
    @Test void unicodeRecipeOverridesAndExistingOutputsArePreserved()throws Exception {
        var options=options("unused");Path input=root.resolve("输入.pdb");Files.copy(Path.of(options.get("input")),input);options.put("input",input.toString());Path recipe=root.resolve("options.json");Files.writeString(recipe,RegenieRuntime.json(options));Path out=root.resolve("结果");assertEquals(3,run("--options-file",recipe.toString(),"--out",out.toString()).code());assertFalse(Files.exists(Path.of(options.get("out"))));assertTrue(Files.readString(out.resolve("session.json")).contains("recipe-sha256"));Path existing=Files.createDirectory(root.resolve("existing"));Files.writeString(existing.resolve("marker"),"keep");options.put("out",existing.toString());assertEquals(2,run(options).code());assertEquals("keep",Files.readString(existing.resolve("marker")));assertTrue(run("--help").out().contains("water"));assertEquals(2,run("--out","a","--out","b").code());assertEquals(2,run("--unknown","x").code());
    }
}
