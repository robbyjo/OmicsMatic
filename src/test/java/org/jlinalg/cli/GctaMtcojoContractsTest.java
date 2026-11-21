/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GctaMtcojoContractsTest {
    @TempDir Path temp;
    @Test void sourceIntegerFdrFlagAndCausalEstimatorCannotSilentlyChange() throws Exception {
        var raw=request("GSMR");var effective=GctaWorkflowContracts.validate(raw);var command=GctaWorkflowRuntime.command(effective,false);
        assertFalse(command.contains("--gsmr-ld-fdr"));assertEquals(.05,effective.get("ld_fdr"));
        raw.put("ld_fdr",.01);assertTrue(assertThrows(IOException.class,()->GctaWorkflowContracts.validate(raw)).getMessage().contains("integer conversion"));
        raw.put("ld_fdr",0);command=GctaWorkflowRuntime.command(GctaWorkflowContracts.validate(raw),false);assertEquals("0",command.get(command.indexOf("--gsmr-ld-fdr")+1));
        var supplied=request("supplied");supplied.put("causal_effects",Map.of("X",100));assertTrue(assertThrows(IOException.class,()->GctaWorkflowContracts.validate(supplied)).getMessage().contains("invokes GSMR"));
        supplied.put("causal_effects",Map.of("X",0));supplied.put("clump_r2",.05);assertThrows(IOException.class,()->GctaWorkflowContracts.validate(supplied));supplied.remove("clump_r2");assertEquals(0.0,SeuratDifferentialContracts.object(GctaWorkflowContracts.validate(supplied).get("causal_effects"),"effects").get("X"));
        var traits=new ArrayList<Map<String,Object>>();traits.add(new LinkedHashMap<>(Map.of("name","Y","summary","y.ma","sample_prevalence",.25,"population_prevalence",.01)));traits.add(new LinkedHashMap<>(Map.of("name","X","summary","x.ma")));supplied.put("traits",traits);
        String list=new String(GctaMtcojoContracts.traitList(GctaWorkflowContracts.validate(supplied)),StandardCharsets.UTF_8);assertTrue(list.contains("Y\t../inputs/mt-trait0.ma\t0.25\t0.01\n"));
        supplied.put("effect_scale","liability");assertThrows(IOException.class,()->GctaWorkflowContracts.validate(supplied));
    }
    @Test void gzipCrcAndFinalRecordTruncationAreRefusedBeforeSourceExecution() throws Exception {
        var raw=request("supplied");var traits=new ArrayList<Map<String,Object>>();traits.add(new LinkedHashMap<>(Map.of("name","Y","summary","y.ma")));traits.add(new LinkedHashMap<>(Map.of("name","X","summary","x.ma.gz","compression","gzip")));raw.put("traits",traits);var r=GctaWorkflowContracts.validate(raw);
        Path root=temp.resolve("capture");Files.createDirectories(root.resolve("worker"));String text="SNP\tA1\tA2\tfreq\tb\tse\tp\tN\nrs1\tA\tG\t0.25\t0.1\t0.1\t0.3173105\t100\nrs2\tA\tG\t0.25\t0.2\t0.1\t0.04550026\t100\n";
        Path y=root.resolve("inputs/mt-trait0.ma"),x=root.resolve("inputs/mt-trait1.ma.gz");Files.createDirectories(y.getParent());Files.writeString(y,text);gzip(x,text);
        for(int chr=1;chr<=22;chr++) {
            for(String kind:List.of("ref","weight")){Path p=root.resolve("inputs/mt-"+kind+"/"+chr+".l2.ldscore");Files.createDirectories(p.getParent());Files.writeString(p,"CHR\tSNP\tBP\tL2\n"+(chr==1?"1\trs1\t1\t-0.2\n1\trs2\t2\t2\n":""));}
            Files.writeString(root.resolve("inputs/mt-ref/"+chr+".l2.M_5_50"),chr==1?"2\n":"0\n");
        }
        var reference=List.of(new GctaWorkflowFiles.Variant(1,"rs1",1,"A","G",0),new GctaWorkflowFiles.Variant(1,"rs2",2,"A","G",1));
        var prepared=GctaMtcojoFiles.prepare(root,r,reference,new long[]{0},false);assertEquals(2,prepared.common().size());assertEquals(.384375,prepared.sourceDerivedVp().get(0),1e-15);
        byte[] valid=Files.readAllBytes(x);valid[valid.length-1]^=1;Files.write(x,valid);assertThrows(IOException.class,()->GctaMtcojoFiles.prepare(root,r,reference,new long[]{0},true));
        gzip(x,text.substring(0,text.length()-1));assertTrue(assertThrows(IOException.class,()->GctaMtcojoFiles.prepare(root,r,reference,new long[]{0},true)).getMessage().contains("unterminated final record"));
        gzip(x,text);Path duplicate=root.resolve("inputs/mt-ref/2.l2.ldscore");Files.writeString(duplicate,"CHR\tSNP\tBP\tL2\n2\trs1\t10\t1\n");assertTrue(assertThrows(IOException.class,()->GctaMtcojoFiles.prepare(root,r,reference,new long[]{0},true)).getMessage().contains("Duplicate LD-score"));
    }
    private Map<String,Object> request(String mode) {
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("profile","GCTA-1.94.1");r.put("engine","original");r.put("model","MTCOJO");r.put("runtime","unused.exe");r.put("out",temp.resolve("unused-output").toString());r.put("preparation_note","Synthetic prepared inputs, no participant data");r.put("effect_scale","as_supplied");r.put("causal_effect_mode",mode);
        r.put("genotypes",Map.of("bed","input.bed","bim","input.bim","fam","input.fam"));r.put("traits",List.of(Map.of("name","Y","summary","y.ma"),Map.of("name","X","summary","x.ma")));if(mode.equals("supplied"))r.put("causal_effects",Map.of("X",.3));
        var ref=new ArrayList<Map<String,Object>>();var weight=new ArrayList<Map<String,Object>>();for(int chr=1;chr<=22;chr++){ref.add(Map.of("chromosome",chr,"scores",chr+".ldscore","markers",chr+".M"));weight.add(Map.of("chromosome",chr,"scores",chr+".weight"));}r.put("reference_ld",ref);r.put("weight_ld",weight);return r;
    }
    private static void gzip(Path path,String text) throws IOException {try(var stream=new GZIPOutputStream(Files.newOutputStream(path))){stream.write(text.getBytes(StandardCharsets.UTF_8));}}
}
