/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.MrPressoWorkflowFiles.*;

/** Full bounded native recomputation, source/input binding and replay evidence. */
final class MrPressoWorkflowAudit {
    private MrPressoWorkflowAudit() { }
    static Map<String,Object> verify(Path capture)throws IOException {
        Path root=capture.toAbsolutePath().normalize().toRealPath();Map<String,Object> manifest=read(root.resolve("capture-files.json"));
        require(!Files.exists(root.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS),"This retained failed capture admits no complete scientific result");
        require(equal(manifest,inventory(root)),"Capture has changed/missing/extra bytes or directories");
        var completed=read(root.resolve("COMPLETED.json"));
        require(equal(completed,map("profile",MrPressoWorkflowContracts.PROFILE,"results_admitted",true,"seal_identity",pin(root.resolve("capture-files.json")))),"Completion marker contradicts capture seal");
        MrPressoWorkflowSource.sources(root,true);MrPressoWorkflowSource.sameProducer(read(root.resolve("producer.json")));
        namespace(root,manifest);
        var canonical=read(root.resolve("canonical-request.json"));binding(root,canonical);
        var evaluated=MrPressoWorkflowEvaluation.evaluate(root.resolve("inputs/associations.tsv"),canonical);
        var saved=read(root.resolve("native-result.json"));MrPressoWorkflowEvaluation.compare(saved,evaluated,"native-result");
        for(var table:MrPressoWorkflowTables.tables(saved).entrySet())require(Arrays.equals(bytes(root.resolve(table.getKey()),67_108_864),table.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Result table contradicts typed source-profile result: "+table.getKey());
        require(Boolean.TRUE.equals(evaluated.get("results_admitted")),"Capture carries no admitted complete result");
        require(equal(read(root.resolve("analysis-status.json")),status(evaluated)),"Admission status contradicts full native recomputation");
        require(Arrays.equals(bytes(root.resolve("WARNINGS.md"),1_048_576),warnings(evaluated).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Warning/action text contradicts native result");
        if(Files.exists(root.resolve("replay.json"),LinkOption.NOFOLLOW_LINKS))replay(root,canonical,saved);
        return evaluated;
    }
    static void binding(Path root,Map<String,Object> canonical)throws IOException {
        require(equal(read(root.resolve("canonical-request.json")),canonical),"Captured canonical request changed");
        var binding=read(root.resolve("request-binding.json"));var raw=read(root.resolve("source-request.json"));
        require(equal(binding.get("source_request"),raw)&&equal(binding.get("canonical_request"),canonical),"Original/canonical request binding differs");
        String mode=text(raw.get("mode"),"mode"),base=text(binding.get("source_base"),"source_base"),style=text(binding.get("path_style"),"path_style");
        Map<String,Object> expected;
        if(mode.equals("RUN"))expected=MrPressoWorkflowContracts.validate(raw,base,style);
        else{
            require(mode.equals("REPLAY"),"Unknown source request mode");keys(raw,Set.of("schema_version","mode","analysis","out"));integer(raw.get("schema_version"),1,1);
            var replay=read(root.resolve("replay.json"));String parent=MrPressoWorkflowContracts.resolveAddress(base,text(raw.get("analysis"),"analysis"),style);
            require(parent.equals(replay.get("parent_address")),"Replay source address differs from request");
            expected=new LinkedHashMap<>(read(root.resolve("replay-parent-canonical.json")));var inputs=new LinkedHashMap<>(object(expected.get("inputs")));
            inputs.put("associations",parent+"/inputs/associations.tsv");expected.put("inputs",inputs);
            expected.put("out",MrPressoWorkflowContracts.resolveAddress(base,text(raw.get("out"),"out"),style));expected=MrPressoWorkflowContracts.validate(expected,base,style);
        }
        require(equal(expected,canonical),"Canonical scientific options/paths differ from the original request");
        require(Objects.equals(binding.get("creation_address"),canonical.get("out")),"Creation/output address binding differs");
        var input=read(root.resolve("input-binding.json"));
        require(Objects.equals(input.get("logical"),object(canonical.get("inputs")).get("associations"))&&equal(input.get("identity"),pin(root.resolve("inputs/associations.tsv"))),"Captured input differs from its declared binding");
        require(equal(read(root.resolve("PROVISIONAL.json")),map("phase","RUN_START_NO_RESULT_ADMITTED","profile",MrPressoWorkflowContracts.PROFILE,"original_R_executed",false)),"Initial no-result marker changed");
    }
    private static void replay(Path root,Map<String,Object> canonical,Map<String,Object> child)throws IOException {
        var record=read(root.resolve("replay.json"));var manifest=read(root.resolve("replay-parent-capture-files.json"));var files=object(manifest.get("files"));
        require(equal(record.get("parent_seal_identity"),pin(root.resolve("replay-parent-capture-files.json"))),"Parent seal binding differs");
        for(var pair:Map.of("canonical-request.json","replay-parent-canonical.json","native-result.json","replay-parent-result.json","producer.json","replay-parent-producer.json").entrySet())
            require(equal(files.get(pair.getKey()),pin(root.resolve(pair.getValue()))),"Copied replay parent evidence differs: "+pair.getKey());
        require(equal(files.get("inputs/associations.tsv"),pin(root.resolve("inputs/associations.tsv"))),"Replay input bytes differ from captured parent");
        var marker=read(root.resolve("replay-parent-COMPLETED.json"));require(equal(marker,map("profile",MrPressoWorkflowContracts.PROFILE,"results_admitted",true,"seal_identity",record.get("parent_seal_identity"))),"Parent admission marker contradicts its saved seal");
        require(equal(record.get("parent_completion_identity"),pin(root.resolve("replay-parent-COMPLETED.json"))),"Parent completion-marker bytes differ");
        MrPressoWorkflowSource.sameProducer(read(root.resolve("replay-parent-producer.json")));
        var parent=read(root.resolve("replay-parent-canonical.json"));var comparable=new LinkedHashMap<>(parent);comparable.put("out",canonical.get("out"));
        var inputs=new LinkedHashMap<>(object(comparable.get("inputs")));inputs.put("associations",object(canonical.get("inputs")).get("associations"));comparable.put("inputs",inputs);
        require(equal(comparable,canonical),"Replay changed scientific options, policies, RNG or limits");
        MrPressoWorkflowEvaluation.compare(read(root.resolve("replay-parent-result.json")),child,"replay-scientific-result");
        require(equal(read(root.resolve("replay-comparison.json")),map("parent_input_bytes_equal",true,"scientific_fields_equal",true,"integer_counts_ids_rng_exact",true,"finite_cell_relative_tolerance",3e-12,"finite_cell_ulp_tolerance",16,"numeric_zero_exact",true)),"Replay comparison policy/result differs");
    }
    private static void namespace(Path root,Map<String,Object> manifest)throws IOException {
        var expected=new HashSet<>(List.of("source-request.json","canonical-request.json","request-binding.json","PROVISIONAL.json","producer.json", "input-binding.json","inputs/associations.tsv","native-result.json","WARNINGS.md","analysis-status.json","source/SOURCE.json"));
        expected.addAll(MrPressoWorkflowTables.tables(read(root.resolve("native-result.json"))).keySet());
        for(String name:object(read(root.resolve("source/SOURCE.json")).get("files")).keySet())expected.add("source/"+name);
        if(Files.exists(root.resolve("replay.json"),LinkOption.NOFOLLOW_LINKS))expected.addAll(List.of("replay.json","replay-parent-capture-files.json","replay-parent-canonical.json", "replay-parent-result.json","replay-parent-producer.json","replay-parent-COMPLETED.json","replay-comparison.json"));
        require(object(manifest.get("files")).keySet().equals(expected),"Unexpected/absent admitted capture files");
        require(new HashSet<>(list(manifest.get("directories"))).equals(Set.of("inputs","source","source/R","source/man")),"Unexpected/absent admitted capture directories");
    }
    static Map<String,Object> status(Map<String,Object> result)throws IOException {
        boolean admitted=Boolean.TRUE.equals(result.get("results_admitted")),unreportable=Boolean.TRUE.equals(result.get("contains_unreportable_fields"));
        return map("schema_version",1,"profile",MrPressoWorkflowContracts.PROFILE,"status",!admitted?"FAIL2_NO_COMPLETE_RESULT":unreportable?"COMPLETED_WITH_UNREPORTABLE_FIELDS":list(result.get("warnings")).isEmpty()?"COMPLETED":"COMPLETED_WITH_WARNINGS", "results_admitted",admitted,"original_R_executed",false);
    }
    static String warnings(Map<String,Object> result){return "# Warnings and required actions\n\n"+MrPressoWorkflowContracts.SCOPE+"\n\n"+MrPressoWorkflowContracts.ACTION+"\n\nNative/source-profile warnings: "+result.get("warnings")+"\n\nResults admitted: "+result.get("results_admitted")+"\n\nContains unreportable fields: "+result.get("contains_unreportable_fields")+". Conditional NA corrected estimates are explicitly marked SOURCE_NOT_ESTIMATED.\n";}
}
