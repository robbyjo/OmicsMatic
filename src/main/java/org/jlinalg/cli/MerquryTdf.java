/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Complete original TDF decoding and deterministic IGV coverage reconstruction from audited BED bytes. */
final class MerquryTdf {
    private MerquryTdf() { }
    static List<Object> replay(Path captured,Path scratch,MerquryRequest.Spec spec,MerquryRuntime.Context runtime,NextflowProcess.Check integrity,Runnable check)throws IOException {
        var results=new ArrayList<Object>();Path helper=scratch.resolve("TdfDump.java");Files.write(helper,MerquryProfile.resource("TdfDump.java"),StandardOpenOption.CREATE_NEW);
        Path work=captured.resolve("engine/work"),igv=Path.of(runtime.programs().get("igvtools")).getParent();var inputs=list(object(json(captured.resolve("effective.json"),1048576)).get("inputs"));
        for(int i=1;i<inputs.size();i++) {
            String asm="asm"+i;Path folder=Files.createDirectory(scratch.resolve(asm)),rebuild=Files.createDirectory(folder.resolve("rebuild"));String staged=text(object(inputs.get(i)).get("staged"));
            Path bed=work.resolve(asm+"_only.bed"),fai=work.resolve(staged+".fai");Files.copy(bed,rebuild.resolve(bed.getFileName()));Files.copy(fai,rebuild.resolve(fai.getFileName()));
            Pin originalBed=pin(bed,spec.capacity().outputBytes(),check),originalFai=pin(fai,spec.capacity().outputBytes(),check);
            require(originalBed.equals(pin(rebuild.resolve(bed.getFileName()),spec.capacity().outputBytes(),check))&&originalFai.equals(pin(rebuild.resolve(fai.getFileName()),spec.capacity().outputBytes(),check)),"Original TDF coverage inputs changed during staging");
            NextflowProcess.executeSession(List.of(runtime.programs().get("igvtools"),"count",bed.getFileName().toString(),asm+"_only.tdf",fai.getFileName().toString()),runtime.executionEnvironment(),folder.resolve("count-process"),rebuild,scratch,spec.process(),integrity,check);
            for(String which:List.of("original","reconstructed")) {
                Path tdf=which.equals("original")?work.resolve(asm+"_only.tdf"):rebuild.resolve(asm+"_only.tdf"),output=folder.resolve(which+".tsv");
                NextflowProcess.executeSession(List.of(runtime.programs().get("java"),"-Xmx1g","-Djava.awt.headless=true","--module-path",igv.resolve("lib").toString(),"--add-modules","org.igv","--add-exports","org.igv/org.broad.igv.tdf=ALL-UNNAMED",helper.toString(),tdf.toString(),output.toString()),runtime.executionEnvironment(),folder.resolve(which+"-process"),folder,scratch,spec.process(),integrity,check);
            }
            Pin semantic=pin(folder.resolve("original.tsv"),spec.capacity().outputBytes(),check);require(semantic.bytes()>0&&semantic.equals(pin(folder.resolve("reconstructed.tsv"),spec.capacity().outputBytes(),check)),"Complete original TDF dataset/tile/interval/name/numeric population differs from reconstructed validated BED coverage");
            require(originalBed.equals(pin(bed,spec.capacity().outputBytes(),check))&&originalFai.equals(pin(fai,spec.capacity().outputBytes(),check)),"TDF original BED/FAI changed during replay");results.add(map("assembly",asm,"complete_tdf_semantics",semantic,"validated_bed",originalBed,"original_fai",originalFai,"all_datasets_and_values_compared",true,"scientific_model_refit",false));
        }
        return List.copyOf(results);
    }
}
