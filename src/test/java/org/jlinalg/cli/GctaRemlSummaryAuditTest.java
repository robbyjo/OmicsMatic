/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class GctaRemlSummaryAuditTest {
    @TempDir Path temp;
    private record Invocation(int code,String error) { }
    private record Bad(String name,String model,String key,Double value,String requiredError) { }

    @Test void completeOriginalExportsPreserveUnconstrainedAndRoundedZeroResults() throws Exception {
        var originals=fixture(temp.resolve("originals"));
        for(String model:List.of("reml-positive","reml-unconstrained","reml-multi","reml-tiny","reml-large")) {
            var run=verify(originals.resolve(model));assertEquals(0,run.code,model+": "+run.error);
        }
        var unconstrained=objects(originals.resolve("reml-unconstrained"));
        assertTrue(((Number)((List<?>)unconstrained.get("V(G)/Vp")).get(0)).doubleValue()<0);
        var tiny=objects(originals.resolve("reml-tiny"));
        assertEquals(0,((Number)((List<?>)tiny.get("Vp")).get(0)).doubleValue());
        assertTrue(((Number)((List<?>)tiny.get("V(G)/Vp")).get(0)).doubleValue()>0);
    }

    @Test void resealedMetadataCannotHideMissingOrImpossibleSourceSummaryFields() throws Exception {
        var checks=new ArrayList<Executable>();
        for(var bad:List.of(
                new Bad("invalid-P","reml-positive","Pval",2.0,"REML likelihood-test"),
                new Bad("missing-h2","reml-positive","V(G)/Vp",null,"REML summary schema"),
                new Bad("missing-P","reml-positive","Pval",null,"REML summary schema"),
                new Bad("wrong-h2","reml-positive","V(G)/Vp",.2,"REML variance ratio"),
                new Bad("wrong-negative-h2","reml-unconstrained","V(G)/Vp",-.8,"REML variance ratio"),
                new Bad("wrong-total-h2","reml-multi","Sum of V(G)/Vp",.1,"REML variance ratio"),
                new Bad("wrong-LRT","reml-positive","LRT",20.0,"REML likelihood-test"),
                new Bad("wrong-df","reml-positive","df",2.0,"REML likelihood-test"),
                new Bad("wrong-total-SE","reml-positive","Vp",99.0,"REML total variance SE"))) {
            checks.add(()->{
                Path root=fixture(temp.resolve(bad.name)).resolve(bad.model);
                String text=Files.readString(root.resolve("worker/result.hsq"));var lines=new ArrayList<String>();
                for(String line:text.lines().toList()) {
                    if(line.startsWith(bad.key+"\t")) {
                        if(bad.value==null)continue;
                        String[] fields=line.split("\t",-1);fields[bad.key.equals("Vp")?2:1]=bad.value.toString();line=String.join("\t",fields);
                    }
                    lines.add(line);
                }
                Files.writeString(root.resolve("worker/result.hsq"),String.join("\n",lines)+"\n",StandardCharsets.UTF_8);
                var report=SeuratDifferentialContracts.read(root.resolve("model-objects.json"));
                var values=SeuratDifferentialContracts.object(report.get("hsq"),"hsq");
                if(bad.value==null)values.remove(bad.key);
                else {var row=new ArrayList<Object>(SeuratDifferentialContracts.list(values.get(bad.key),bad.key));row.set(bad.key.equals("Vp")?1:0,bad.value);values.put(bad.key,row);}
                report.put("hsq",values);SeuratDifferentialContracts.json(root.resolve("model-objects.json"),report);GctaWorkflowCli.seal(root);
                var result=verify(root);assertEquals(2,result.code,bad.name+" must not admit an internally impossible complete model");assertTrue(result.error.contains(bad.requiredError),bad.name+": "+result.error);
            });
        }
        assertAll(checks);
    }

    private static Map<String,Object> objects(Path root) throws Exception {
        return SeuratDifferentialContracts.object(SeuratDifferentialContracts.read(root.resolve("model-objects.json")).get("hsq"),"hsq");
    }
    private Path fixture(Path folder) throws Exception {
        Files.createDirectories(folder);try(var stream=getClass().getResourceAsStream("/org/jlinalg/cli/gcta1941-reml-captures.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)) {
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    String name=entry.getName();if(!name.startsWith("cases/"))continue;
                    Path target=folder.resolve(name.substring(6)).normalize();assertTrue(target.startsWith(folder));Files.createDirectories(target.getParent());Files.write(target,zip.readAllBytes(),StandardOpenOption.CREATE_NEW);
                }
            }
        }return folder;
    }
    private static Invocation verify(Path root) throws Exception {
        var out=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();
        int code=GctaWorkflowCli.run(new String[]{"--verify",root.toString()},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));
        return new Invocation(code,error.toString(StandardCharsets.UTF_8));
    }
}
