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
import org.jlinalg.protein.PqrStructure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApbsToolsCliTest {
    @TempDir Path root;
    private static Map<String,String> model() {
        var options=new LinkedHashMap<String,String>();String[] entries={"length-units","angstrom","charge-model","explicit one-charge test,not a protein force field","equation","lpbe","boundary","sdh","nodes","65,65,65","lengths","20,20,20","center","0,0,0","solute-dielectric","2","solvent-dielectric","80","probe-radius","1.4","surface-density","10","temperature","298.15","salt-molarity","0"};
        for(int i=0;i<entries.length;i+=2)options.put(entries[i],entries[i+1]);return options;
    }
    private record Run(int code,String out,String err) { }
    private static Run run(Map<String,String> options) {
        var args=new ArrayList<String>();options.forEach((key,value)->{args.add("--"+key);args.add(value);});return run(args.toArray(String[]::new));
    }
    private static Run run(String... args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=ApbsToolsCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private Map<String,String> options(String name)throws Exception {
        var options=model();Path input=root.resolve("source.pqr");if(!Files.exists(input))Files.writeString(input,"ATOM 1 ION ION 1 0 0 0 1 2\n");options.put("input",input.toString());options.put("out",root.resolve(name).toString());return options;
    }
    private static void assertStatus(Map<String,String> options,String status)throws Exception {
        Path out=Path.of(options.get("out"));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains(status));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action:"));assertFalse(Files.exists(out.resolve("results.json")));assertFalse(Files.exists(out.resolve("potential.dx")));
    }
    @Test void everyModelChoiceIsRequiredWithoutDefaultInference() {
        var complete=model();for(String key:complete.keySet()){var missing=new LinkedHashMap<>(complete);missing.remove(key);assertThrows(IllegalArgumentException.class,()->ApbsToolsCli.parameters(missing),key);}
    }
    @Test void finiteBoundedGridVectorsAndIntegersAreRequired() {
        for(String[] bad:new String[][]{{"nodes","65.5,65,65"},{"nodes","64,65,65"},{"nodes","257,257,257"},{"nodes","NaN,65,65"},{"center","0,0"},{"center","Infinity,0,0"},{"lengths","-20,20,20"},{"lengths","1001,20,20"},{"lengths",".00001,20,20"}}){var options=model();options.put(bad[0],bad[1]);assertThrows(IllegalArgumentException.class,()->ApbsToolsCli.parameters(options),List.of(bad).toString());}
    }
    @Test void explicitPositiveSaltRequiresRadiusAndZeroSaltForbidsIt() {
        var salt=model();salt.put("salt-molarity",".15");assertThrows(IllegalArgumentException.class,()->ApbsToolsCli.parameters(salt));salt.put("ion-radius","2");assertTrue(ApbsToolsCli.parameters(salt).script().contains("ion charge -1 conc 0.15 radius 2.0"));salt.put("salt-molarity","0");assertThrows(IllegalArgumentException.class,()->ApbsToolsCli.parameters(salt));
    }
    @Test void unsupportedEquationsUnitsBoundariesAndNonfiniteModelValuesFail() {
        for(String[] bad:new String[][]{{"equation","pbsa\nquit"},{"boundary","focus"},{"length-units","nanometer"},{"solute-dielectric","NaN"},{"solvent-dielectric","0"},{"temperature","0"},{"salt-molarity","-1"},{"probe-radius","-1"},{"surface-density","0"},{"charge-model","line\nline"}}){var options=model();options.put(bad[0],bad[1]);assertThrows(IllegalArgumentException.class,()->ApbsToolsCli.parameters(options));}
    }
    @Test void coveredGridIncludesRadiusProbeAndOneCellMargin()throws Exception {
        var structure=PqrStructure.read(new java.io.StringReader("ATOM 1 H ION 1 0 0 0 1 0\n"),10);ApbsToolsCli.parameters(model()).covers(structure);
        var bad=model();bad.put("lengths","2,2,2");assertThrows(java.io.IOException.class,()->ApbsToolsCli.parameters(bad).covers(structure));
    }
    @Test void scriptUsesOnlyTypedScientificParametersAndFixedRelativeNames() {
        var parameters=ApbsToolsCli.parameters(model());String script=parameters.script();assertTrue(script.contains("mol pqr source.pqr"));assertTrue(script.contains("mg-manual\n dime 65 65 65"));assertTrue(script.contains("srfm mol"));assertTrue(script.contains("chgm spl2"));assertTrue(script.contains("write pot dx potential"));assertFalse(script.contains("ion charge"));parameters.nodes()[0]=129;parameters.lengths()[0]=999;assertEquals(274625,parameters.points());assertEquals(20,parameters.lengths()[0]);
    }
    @Test void missingHomeSkipsWithRetainedInputAndConcreteRecovery()throws Exception {
        var options=options("skip");var result=run(options);assertEquals(3,result.code(),result.err());assertStatus(options,"skipped-unavailable-dependency");assertTrue(result.err().contains("ANALYSIS SKIPPED"));assertTrue(Files.exists(Path.of(options.get("out"),"partial/source.pqr")));assertTrue(Files.readString(Path.of(options.get("out"),"WARNINGS.md")).contains("--allow-install"));
    }
    @Test void unadmittedRuntimeSkipsWithoutLaunchingAnyProcess()throws Exception {
        var options=options("unadmitted");Path home=Files.createDirectory(root.resolve("runtime"));Files.writeString(home.resolve(".jlinalg-apbs.json"),"{}");options.put("home",home.toString());assertEquals(3,run(options).code());assertStatus(options,"skipped-unavailable-dependency");String session=Files.readString(Path.of(options.get("out"),"session.json"));assertFalse(session.contains("\"pid\""));
    }
    @Test void malformedPqrAndGridCoverageFailBeforeDependencyCheck()throws Exception {
        var options=options("bad-pqr");Files.writeString(Path.of(options.get("input")),"ATOM 1 H ION 1 0 0 0 NaN 0\n");assertEquals(2,run(options).code());assertStatus(options,"failed");Files.writeString(Path.of(options.get("input")),"ATOM 1 H ION 1 99 0 0 1 0\n");options.put("out",root.resolve("coverage").toString());assertEquals(2,run(options).code());assertStatus(options,"failed");
    }
    @Test void unicodeRecipesAndExistingOutputPreservation()throws Exception {
        var options=options("未安装");Path recipe=root.resolve("recipe.json");Files.writeString(recipe,RegenieRuntime.json(options));assertEquals(3,run("--options-file",recipe.toString()).code());assertStatus(options,"skipped-unavailable-dependency");assertTrue(Files.readString(Path.of(options.get("out"),"session.json")).contains("recipe-sha256"));
        Path existing=Files.createDirectory(root.resolve("existing"));Files.writeString(existing.resolve("marker"),"keep");options.put("out",existing.toString());assertEquals(2,run(options).code());assertEquals("keep",Files.readString(existing.resolve("marker")));assertTrue(run("--help").out().contains("NaN maps fail even after exit0"));assertEquals(2,run("--out","a","--out","b").code());
    }
}
