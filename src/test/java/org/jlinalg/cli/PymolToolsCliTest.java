/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PymolToolsCliTest {
    @TempDir Path root;
    private Path source;
    @BeforeEach void load()throws Exception {PymolAuditTest.extract(root);source=root.resolve("pdb-measure/inputs/mobile.pdb");}
    private record Run(int code,String out,String err) { }
    private static Run run(String... args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=PymolToolsCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    private Map<String,Object> recipe(){return new LinkedHashMap<>(Map.of("schema_version",1,"structures",List.of(Map.of("id","protein","path",source.toString(),"format","pdb","state",1,"cif_namespace","author")),"steps",List.of(Map.of("id","distance","operation","measure","kind","distance","atoms",List.of(Map.of("structure","protein","chain","A","resi","1","name","N"),Map.of("structure","protein","chain","A","resi","1","name","CA"))))));}
    private Run invoke(Map<String,Object> recipe,String label,String... extra)throws Exception {Path path=root.resolve(label+".json");Files.writeString(path,RegenieRuntime.json(recipe),StandardCharsets.UTF_8);var args=new java.util.ArrayList<>(List.of("--recipe",path.toString(),"--out",root.resolve(label).toString(),"--home",root.resolve("missing-runtime").toString()));args.addAll(List.of(extra));return run(args.toArray(String[]::new));}
    private void failed(String label,String status)throws Exception {Path out=root.resolve(label);assertTrue(Files.readString(out.resolve("analysis-status.json")).contains(status));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action:"));assertFalse(Files.exists(out.resolve("original-results.json")));}
    @Test void helpNamesLinuxDependencyTypedScopeAndExitCodes(){var result=run("--help");assertEquals(0,result.code());assertTrue(result.out().contains("Linux Java inside WSL"));assertTrue(result.out().contains("exit3"));assertTrue(result.out().contains("No implicit alternate"));}
    @Test void validInputWithMissingOrUnsupportedRuntimeSkipsWithActionableWarnings()throws Exception {var result=invoke(recipe(),"missing");assertEquals(3,result.code(),result.err());failed("missing","skipped-dependency-required");String warnings=Files.readString(root.resolve("missing/WARNINGS.md"));assertTrue(warnings.contains("install_pymol.py"));assertTrue(warnings.contains("Native molecular-geometry"));assertTrue(result.err().contains("ANALYSIS SKIPPED"));}
    @Test void arbitraryScriptsAndUnknownOperationsFailBeforeRuntimeAdmission()throws Exception {var recipe=recipe();recipe.put("steps",List.of(Map.of("id","script","operation","run","script","print('bad')")));assertEquals(2,invoke(recipe,"script").code());failed("script","failed");}
    @Test void rawSelectionExpressionsFailBeforeAdmission()throws Exception {var recipe=recipe();recipe.put("steps",List.of(Map.of("id","fit","operation","align","mobile",Map.of("structure","protein","expression","all"),"target",Map.of("structure","protein"))));assertEquals(2,invoke(recipe,"selector").code());failed("selector","failed");}
    @Test void invalidStateAndCombinedResourceBoundsFail()throws Exception {var recipe=recipe();recipe.put("structures",List.of(Map.of("id","protein","path",source.toString(),"format","pdb","state",2,"cif_namespace","author")));assertEquals(2,invoke(recipe,"state").code());failed("state","failed");assertEquals(2,invoke(recipe(),"bytes","--max-bytes","1").code());failed("bytes","failed");assertEquals(2,invoke(recipe(),"atoms","--max-atoms","1").code());failed("atoms","failed");}
    @Test void duplicateInputsAndUnknownFieldsAreRejected()throws Exception {var recipe=recipe();Object input=PymolAudit.list(recipe.get("structures")).get(0);recipe.put("structures",List.of(input,input));assertEquals(2,invoke(recipe,"duplicate").code());failed("duplicate","failed");var unknown=recipe();unknown.put("python","anything");assertEquals(2,invoke(unknown,"unknown").code());failed("unknown","failed");}
    @Test void unsafeMutationSettingsAreRejectedBeforeAdmission()throws Exception {var recipe=recipe();recipe.put("steps",List.of(Map.of("id","mutate","operation","mutate","residue",Map.of("structure","protein","resi","1"),"to","GLN","library","automatic-best","rotamer",1,"hydrogens","auto","max_backbone_shift",.1)));assertEquals(2,invoke(recipe,"mutation").code());failed("mutation","failed");}
    @Test void existingOutputsAndDuplicateOptionsArePreserved()throws Exception {Path out=Files.createDirectory(root.resolve("existing"));Files.writeString(out.resolve("keep"),"unchanged");assertEquals(2,run("--recipe","absent","--out",out.toString()).code());assertEquals("unchanged",Files.readString(out.resolve("keep")));assertEquals(2,run("--out","a","--out","b").code());}
}
