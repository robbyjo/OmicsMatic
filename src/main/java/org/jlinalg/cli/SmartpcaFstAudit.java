/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;

/** Read-only full scientific recomputation and complete, portable capture admission. */
final class SmartpcaFstAudit {
    private SmartpcaFstAudit() { }
    static final Map<String,String> PARENT_FILES=Map.of(
        "capture-files.json","replay-parent-capture-files.json","COMPLETED.json","replay-parent-COMPLETED.json",
        "canonical-request.json","replay-parent-canonical.json","native-result.json","replay-parent-result.json","producer.json","replay-parent-producer.json");
    static Path existing(Path capture)throws IOException {
        Path root=capture.toAbsolutePath().normalize();
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new SmartpcaWorkflowContracts.Unavailable("Supply an existing completed smartpca capture: "+root);
        require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)&&root.toRealPath().equals(root),"Require physical capture directory; existing aliased/non-directory evidence is not admitted");return root;
    }
    static SmartpcaFstEvaluation.Evaluated verify(Path capture)throws IOException {
        Path root=existing(capture);
        // Once a capture exists, absent members are integrity failures, never prerequisite skips.
        try{return verifyExisting(root);}
        catch(SmartpcaWorkflowContracts.Unavailable missing){throw new IOException("Existing smartpca capture cannot be admitted: "+missing.getMessage(),missing);}
    }
    private static SmartpcaFstEvaluation.Evaluated verifyExisting(Path root)throws IOException {
        require(Files.isRegularFile(root.resolve("capture-files.json"),LinkOption.NOFOLLOW_LINKS)&&Files.isRegularFile(root.resolve("COMPLETED.json"),LinkOption.NOFOLLOW_LINKS),
            "Existing capture lacks its regular completion/seal; incomplete evidence admits no result");
        var manifest=read(root.resolve("capture-files.json"));
        require(equal(manifest,inventory(root)),"Capture bytes, files or directories differ from seal");
        require(equal(read(root.resolve("COMPLETED.json")),completion(root)),"Completion marker contradicts seal/profile/admission");
        var saved=read(root.resolve("native-result.json"));namespace(root,manifest,saved);
        SmartpcaWorkflowSource.sources(root,true);producerRecord(read(root.resolve("producer.json")));producerOrigins();
        var canonical=read(root.resolve("canonical-request.json"));binding(root,canonical);
        var evaluated=SmartpcaFstEvaluation.evaluate(root,canonical,null);checkOutputs(root,evaluated);
        require(Boolean.TRUE.equals(evaluated.result().get("results_admitted")),"Declared policy admits no complete result");
        require(equal(read(root.resolve("analysis-status.json")),status(evaluated.result())),"Status contradicts recomputed scientific admission");
        require(Arrays.equals(bytes(root.resolve("WARNINGS.md"),1_048_576),warnings(evaluated.result()).getBytes(StandardCharsets.UTF_8)),"Warnings/remedies contradict recomputation");
        process(root,canonical);outputBytes(manifest,canonical);
        if(Files.exists(root.resolve("replay.json"),LinkOption.NOFOLLOW_LINKS))replay(root,canonical,evaluated.result());
        // Detect changes during the read-only calculation as well as changes before it.
        require(equal(manifest,inventory(root))&&equal(read(root.resolve("COMPLETED.json")),completion(root)),"Capture changed during VERIFY");
        return evaluated;
    }
    static void producerOrigins()throws IOException {
        var expected=SmartpcaWorkflowSource.class.getProtectionDomain().getCodeSource().getLocation();
        for(Class<?> type:List.of(SmartpcaFstAudit.class,SmartpcaFstCli.class,SmartpcaFstContracts.class,SmartpcaFstEvaluation.class,SmartpcaFstTables.class,SmartpcaCommandCli.class,org.jlinalg.genetics.SmartpcaFst.class))
            require(type.getProtectionDomain().getCodeSource().getLocation().equals(expected),"Workflow/scientific classes must come from one exact producer: "+type.getName());
    }
    static void producerRecord(Map<String,Object> value)throws IOException {
        require(value.keySet().equals(Set.of("kind","identity","source_profile_sha256","java_version","os","backend"))&&"EXACT_PACKAGED_JAR".equals(value.get("kind"))&&"PORTABLE_JAVA_CPU_FP64".equals(value.get("backend")),"Producer kind/backend/provenance contradicts this native profile");
        require(!text(value.get("java_version"),"java_version").isBlank()&&!text(value.get("os"),"os").isBlank(),"Saved producer platform/version is absent");
        SmartpcaWorkflowSource.sameProducer(value);
    }
    static Map<String,Object> completion(Path root)throws IOException {
        return map("schema_version",1,"profile",SmartpcaFstContracts.PROFILE,"results_admitted",true,"seal_identity",pin(root.resolve("capture-files.json")));
    }
    static Map<String,Object> provisional(){return map("phase","RUN_START_NO_RESULT_ADMITTED","profile",SmartpcaFstContracts.PROFILE,"original_C_executed",false);}
    static void binding(Path root,Map<String,Object> canonical)throws IOException {
        require(equal(read(root.resolve("canonical-request.json")),canonical),"Canonical request changed");
        var record=read(root.resolve("request-binding.json"));
        require(record.keySet().equals(Set.of("source_request","canonical_request","source_base","path_style","creation_address")),"Unexpected request binding fields");
        var raw=read(root.resolve("source-request.json"));
        require(equal(record.get("source_request"),raw)&&equal(record.get("canonical_request"),canonical),"Original/canonical request binding differs");
        String base=text(record.get("source_base"),"source_base"),style=text(record.get("path_style"),"path_style");
        Map<String,Object> expected;
        if("RUN".equals(raw.get("mode")))expected=SmartpcaFstContracts.validate(raw,base,style);
        else {
            require("REPLAY".equals(raw.get("mode")),"Unknown original request mode");keys(raw,Set.of("schema_version","mode","analysis","out"));integer(raw.get("schema_version"),1,1);
            String parent=SmartpcaWorkflowContracts.resolve(base,text(raw.get("analysis"),"analysis"),style);
            require(Objects.equals(parent,read(root.resolve("replay.json")).get("parent_address")),"Replay source address differs from its request");
            expected=replayRequest(read(root.resolve("replay-parent-canonical.json")),parent,SmartpcaWorkflowContracts.resolve(base,text(raw.get("out"),"out"),style),base,style);
        }
        require(equal(expected,canonical)&&Objects.equals(record.get("creation_address"),canonical.get("out")),"Scientific choices or paths differ from original request");
        var bindings=read(root.resolve("input-binding.json"));require(bindings.keySet().equals(new HashSet<>(SmartpcaWorkflowContracts.INPUTS)),"Input binding population differs");
        for(String name:SmartpcaWorkflowContracts.INPUTS) {
            var value=object(bindings.get(name));require(value.keySet().equals(Set.of("logical","physical","identity")),"Unexpected input binding fields");
            require(Objects.equals(value.get("logical"),object(canonical.get("inputs")).get(name))&&equal(value.get("identity"),pin(root.resolve("inputs").resolve(SmartpcaWorkflowData.NAMES.get(name)))),"Input differs from declared captured binding: "+name);
            String physical=text(value.get("physical"),"physical");require(physical.equals(SmartpcaWorkflowContracts.resolve(base,physical,style)),"Physical address is not canonical for the recorded platform");
        }
        require(equal(read(root.resolve("PROVISIONAL.json")),provisional()),"Initial no-result marker differs");
    }
    static Map<String,Object> replayRequest(Map<String,Object> parent,String address,String out,String base,String style)throws IOException {
        var result=new LinkedHashMap<>(parent);var inputs=new LinkedHashMap<>(object(result.get("inputs")));
        for(String name:SmartpcaWorkflowContracts.INPUTS)inputs.put(name,address+"/inputs/"+SmartpcaWorkflowData.NAMES.get(name));
        result.put("inputs",inputs);result.put("out",out);return SmartpcaFstContracts.validate(result,base,style);
    }
    static void checkOutputs(Path root,SmartpcaFstEvaluation.Evaluated value)throws IOException {SmartpcaFstTables.verifyOutputs(root,value);}

    static void namespace(Path root,Map<String,Object> manifest,Map<String,Object> result)throws IOException {
        var expected=new HashSet<>(List.of("source-request.json","canonical-request.json","request-binding.json","PROVISIONAL.json","producer.json","input-binding.json","compute-process.json","input-summary.json","native-result.json","WARNINGS.md","analysis-status.json","source/SOURCE.json","source/LICENSE.txt","source/original-source.tar.gz","locus-contributions.tsv"));
        expected.addAll(SmartpcaFstTables.TABLES);for(String name:SmartpcaWorkflowData.NAMES.values())expected.add("inputs/"+name);
        if(Files.exists(root.resolve("replay.json"),LinkOption.NOFOLLOW_LINKS)){expected.addAll(PARENT_FILES.values());expected.addAll(Set.of("replay.json","replay-comparison.json"));}
        require(object(manifest.get("files")).keySet().equals(expected),"Unexpected/absent admitted FST files");
        var directories=list(manifest.get("directories"));require(directories.size()==2&&new HashSet<>(directories).equals(Set.of("inputs","source")),"Unexpected/absent FST capture directories");
    }

    static void outputBytes(Map<String,Object> manifest,Map<String,Object> request)throws IOException {
        long total=0;
        for(var entry:object(manifest.get("files")).entrySet()) {
            String name=entry.getKey();
            if(name.endsWith(".tsv")||Set.of("native-result.json","input-summary.json").contains(name))total=Math.addExact(total,number(object(entry.getValue()),"bytes"));
        }
        require(total<=SmartpcaWorkflowContracts.limit(request,"maximum_output_bytes"),"Complete numerical output exceeds declared byte capacity");
    }
    private static void process(Path root,Map<String,Object> request)throws IOException {
        var value=read(root.resolve("compute-process.json"));require(value.keySet().equals(Set.of("timeout_seconds","owned_thread","owned_thread_stopped","deadline_exceeded","calculation_returned","elapsed_nanoseconds")),"Unexpected native-process receipt fields");
        require(number(value,"timeout_seconds")==SmartpcaWorkflowContracts.limit(request,"timeout_seconds")&&Boolean.TRUE.equals(value.get("owned_thread_stopped"))&&Boolean.FALSE.equals(value.get("deadline_exceeded"))&&Boolean.TRUE.equals(value.get("calculation_returned")),"Native owner/deadline receipt does not admit completion");
        require(text(value.get("owned_thread"),"owned_thread").matches("omicsmatic-smartpca-fst-[0-9a-f-]{36}"),"Unknown native calculation owner");
        require(number(value,"elapsed_nanoseconds")<=(number(value,"timeout_seconds")+30)*1_000_000_000L,"Native execution/closure exceeded declared bounds");
    }
    static Map<String,Object> comparison(){return map("parent_input_bytes_equal",true,"scientific_fields_equal",true,"integer_counts_ids_exact",true,"finite_cell_relative_tolerance",3e-12,"finite_cell_ulp_tolerance",16,"numeric_zero_exact",true);}
    static void replay(Path root,Map<String,Object> canonical,Map<String,Object> child)throws IOException {
        var record=read(root.resolve("replay.json"));require(record.keySet().equals(Set.of("parent_address","parent_seal_identity","parent_completion_identity")),"Unexpected replay binding fields");
        var files=object(read(root.resolve("replay-parent-capture-files.json")).get("files"));
        require(equal(record.get("parent_seal_identity"),pin(root.resolve("replay-parent-capture-files.json")))&&equal(record.get("parent_completion_identity"),pin(root.resolve("replay-parent-COMPLETED.json"))),"Parent seal/completion bytes differ");
        require(equal(read(root.resolve("replay-parent-COMPLETED.json")),map("schema_version",1,"profile",SmartpcaFstContracts.PROFILE,"results_admitted",true,"seal_identity",record.get("parent_seal_identity"))),"Parent completion contradicts its saved seal");
        for(var pair:PARENT_FILES.entrySet())if(!Set.of("capture-files.json","COMPLETED.json").contains(pair.getKey()))require(equal(files.get(pair.getKey()),pin(root.resolve(pair.getValue()))),"Copied parent evidence differs: "+pair.getKey());
        for(String name:SmartpcaWorkflowData.NAMES.values())require(equal(files.get("inputs/"+name),pin(root.resolve("inputs").resolve(name))),"Child input differs from parent: "+name);
        producerRecord(read(root.resolve("replay-parent-producer.json")));
        var parent=read(root.resolve("replay-parent-canonical.json"));var comparable=new LinkedHashMap<>(parent);comparable.put("out",canonical.get("out"));
        var inputs=new LinkedHashMap<>(object(parent.get("inputs")));for(String name:SmartpcaWorkflowContracts.INPUTS)inputs.put(name,object(canonical.get("inputs")).get(name));comparable.put("inputs",inputs);
        require(equal(comparable,canonical),"Replay changed scientific options, population declarations, policies or limits");
        SmartpcaWorkflowEvaluation.compare(read(root.resolve("replay-parent-result.json")),child,"replay-scientific-result");
        require(equal(read(root.resolve("replay-comparison.json")),comparison()),"Replay scientific comparison receipt differs");
    }
    static Map<String,Object> status(Map<String,Object> result)throws IOException {
        boolean admitted=Boolean.TRUE.equals(result.get("results_admitted"));long invalid=number(result,"unreportable_estimate_rows");
        return map("schema_version",1,"profile",SmartpcaFstContracts.PROFILE,"status",!admitted?"FAIL2_NO_COMPLETE_RESULT":invalid>0?"COMPLETED_WITH_UNREPORTABLE_FIELDS":"COMPLETED_WITH_SCOPE_WARNINGS","results_admitted",admitted,"unreportable_estimate_rows",invalid,"original_C_executed",false);
    }

    static String warnings(Map<String,Object> result)throws IOException {
        return "# Warnings and required actions\n\n"+SmartpcaFstContracts.SCOPE+"\n\n"+SmartpcaFstContracts.ACTION+
            "\n\nOriginal paper parameters and historical editions are not recovered. The source package edition7.2.1 and program16000 differ.\n"+
            "\nGenetic-position convention applied by the source: "+result.get("position_convention")+". Read markers.tsv for the effective Morgans and every original position token. Genome build is a literal declaration, not inferred.\n"+
            "\nThe fstonly source path bypasses PCA normalization, minor-allele-count and maxmissing filters. Nonautosomal markers and markers with at most one observed selected sample are explicitly excluded; raw inputs remain captured.\n"+
            "\nBlock weights count all source-retained markers, including loci unavailable for a particular pair. The source1e-10 denominator term is retained; it does not make zero-denominator estimates identifiable.\n"+
            "\nNegative finite FST estimates are retained without clamping: "+result.get("negative_FST_estimates_retained")+". Zero source standard errors: "+result.get("zero_source_standard_errors")+". This profile provides no P values or Z-score inference.\n"+
            "\nUnreportable estimate rows: "+result.get("unreportable_estimate_rows")+". One-block diploid source zeros remain ineligible; one-block inbreeding follows the original refusal. Zero/nonpositive total denominator and nonfinite fields remain ineligible.\n"+
            "\nExplicit diagnostic retention policy: "+result.get("allow_unreportable_fields")+"; results admitted under that policy: "+result.get("results_admitted")+". Read every source_status and fields_admitted flag; retention never validates undefined inference.\n";
    }
}
