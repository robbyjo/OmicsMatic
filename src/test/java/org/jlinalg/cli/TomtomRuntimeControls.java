/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.TomtomResults;

/** Actual-JAR synthetic native admission controls; no public analysis or forged/copied ticket. */
public final class TomtomRuntimeControls {
    private TomtomRuntimeControls() { }
    /**
     * @param args exact candidate JAR, original managed home, original source/build evidence directory, NEW Linux root
     * @throws Exception on incomplete native admission or synthetic control
     */
    public static void main(String[] args)throws Exception {
        if(args.length!=4||!System.getProperty("os.name").equals("Linux"))throw new IOException("Use Linux Java, exact candidate, existing original home/build evidence and NEW private evidence root");
        Path jar=Path.of(args[0]),home=Path.of(args[1]),build=Path.of(args[2]),root=Path.of(args[3]);for(Class<?> type:List.of(TomtomRuntime.class,MemeBuildEvidence.class,MemePerl.class,TomtomRecipe.class,TomtomResults.class))if(!type.getProtectionDomain().getCodeSource().getLocation().toURI().equals(jar.toUri()))throw new IOException("Wrong actual runtime JAR: "+type);
        TomtomRecipe.nativePath(root.toString());if(Files.exists(root)||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Preserve earlier controls; use a NEW canonical native root");Files.createDirectory(root,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try{run(jar,home,build,root);}catch(Exception failure){MemeRuntimeFiles.write(root.resolve("FAILURE.json"),Map.of("status","FAILED_NATIVE_RUNTIME_CONTROLS_NO_USER_ANALYSIS","error",failure.toString(),"required_action",TomtomRuntime.ACTION));throw failure;}
    }
    private static void run(Path jar,Path home,Path build,Path root)throws Exception {
        MemeRuntimeFiles.write(root.resolve("OWNER.json"),Map.of("candidate",MemeRuntimeFiles.pin(NextflowFiles.pin(jar,268435456,()->{})),"home",home.toString(),"build_evidence",build.toString(),"scope","Fresh native original source/payload/host/Perl/health and downstream synthetic process controls. No public user analysis or catalog credit."));
        var lock=MemeRuntimeFiles.lock();Path inputs=root.resolve("original-inputs");Files.createDirectory(inputs);Path motifs=inputs.resolve("original motifs α with spaces.meme");Files.write(motifs,MemeRuntimeFiles.resource(MemeRuntimeFiles.RESOURCE+"health-motifs.meme",NextflowFiles.fingerprint(lock.get("health_motifs"),2097152)),StandardOpenOption.CREATE_NEW);
        var receipts=new ArrayList<Map<String,Object>>();
        for(var mode:TomtomRecipe.Mode.values()){
            System.out.println("ADMIT_START "+mode);var ticket=TomtomRuntime.admit(TomtomRecipe.PROFILE,mode,home,build,root.resolve("admission-"+mode),TomtomRuntime.Limits.defaults(),()->{});ticket.verify(()->{});require(!ticket.userAnalysisAdmitted()&&ticket.mode()==mode,"Runtime ticket incorrectly admits user analysis/capability");
            var host=NextflowFiles.object(NextflowFiles.json(ticket.work().resolve("metadata/host.json"),67108864));var hostFiles=NextflowFiles.object(host.get("complete_host_files"));var requested=NextflowFiles.strings(hostFiles.get("requestedFiles"));
            if(mode==TomtomRecipe.Mode.TEXT){require(host.get("Perl_report_capability")==null&&!requested.contains("/usr/bin/perl"),"Text-only native runtime acquired an unrequested Perl capability");}
            else{require(host.get("Perl_report_capability")!=null&&requested.contains("/usr/bin/perl"),"Full report native runtime omitted its current Perl capability");var moduleRoots=NextflowFiles.strings(NextflowFiles.object(host.get("Perl_report_capability")).get("ordered_module_roots"));var capturedRoots=NextflowFiles.strings(hostFiles.get("requestedRoots"));for(String name:moduleRoots){Path path=Path.of(name);require(capturedRoots.contains(name),"Configured Perl module root omitted");if(Files.exists(path))require(capturedRoots.contains(path.toRealPath().toString()),"Canonical Perl root alias target omitted");}}
            Path folder=root.resolve("downstream-"+mode);Files.createDirectory(folder);Path recipe=folder.resolve("request.json");var spec=TomtomPreparedInputsTest.recipe(motifs,List.of(motifs));spec.put("output_mode",mode.name());MemeRuntimeFiles.write(recipe,spec);var request=TomtomRecipe.read(recipe,()->{});var captured=TomtomInputs.capture(folder.resolve("prepared"),request,()->{});Path process=folder.resolve("process");
            var run=ticket.execute(request,captured,process,()->{});require(run.ownedLiveAfterCleanup().isEmpty(),"Owned Tomtom synthetic workers survived cleanup");String output=mode==TomtomRecipe.Mode.TEXT?null:process.resolve("result").toString();var command=request.arguments(home.resolve("bin/tomtom").toString(),captured.query().toString(),captured.targets(),captured.backgroundFile(),output);var expected=captured.expected(command);int rows,pairs;
            if(mode==TomtomRecipe.Mode.TEXT){var audit=TomtomResults.readText(run.stdout(),expected,request.outputLimits(),()->{});rows=audit.matches().size();pairs=audit.pairs().size();}
            else{var audit=TomtomResults.read(Path.of(output).resolve("tomtom.xml"),Path.of(output).resolve("tomtom.tsv"),expected,request.outputLimits(),()->{});rows=audit.matches().size();pairs=audit.pairs().size();}
            require(rows==9&&pairs==9,"Incomplete downstream current-ticket synthetic population");ticket.verify(()->{});
            Path extra=ticket.work().resolve("unrecorded-empty-directory");Files.createDirectory(extra);boolean refused=false;try{ticket.verify(()->{});}catch(IOException invalid){refused=true;}require(refused,"Changed runtime evidence namespace was accepted");Files.delete(extra);ticket.verify(()->{});
            var wrong=new java.util.LinkedHashMap<>(spec);wrong.put("output_mode",mode==TomtomRecipe.Mode.TEXT?"FULL_REPORT":"TEXT");Path wrongRecipe=folder.resolve("wrong-mode.json");MemeRuntimeFiles.write(wrongRecipe,wrong);var different=TomtomRecipe.read(wrongRecipe,()->{});var differentInputs=TomtomInputs.capture(folder.resolve("wrong-mode-inputs"),different,()->{});Path forbidden=folder.resolve("not-launched");boolean skipped=false;try{ticket.execute(different,differentInputs,forbidden,()->{});}catch(TomtomRuntime.Unavailable unavailable){skipped=true;}require(skipped&&!Files.exists(forbidden),"Runtime capability mismatch launched a process or changed output mode");
            receipts.add(Map.of("mode",mode.name(),"runtime_admitted_current_host",true,"fresh_health_fits",2,"downstream_synthetic_fits",1,"complete_downstream_pairs",pairs,"complete_downstream_rows",rows,"owned_live_after_cleanup",run.ownedLiveAfterCleanup(),"stale_evidence_rejected",refused,"mode_mismatch_skipped_before_launch",skipped,"user_analysis_admitted",false));System.out.println("ADMIT_AND_SYNTHETIC_CONTROL_COMPLETE "+mode);
        }
        Path missing=root.resolve("missing-home-admission");boolean skipped=false;try{TomtomRuntime.admit(TomtomRecipe.PROFILE,TomtomRecipe.Mode.TEXT,root.resolve("absent-home"),build,missing,TomtomRuntime.Limits.defaults(),()->{});}catch(TomtomRuntime.Unavailable unavailable){skipped=true;}
        require(skipped&&Files.exists(missing.resolve("FAILURE.json"))&&!Files.exists(missing.resolve("COMPLETED.json")),"Unavailable native home did not retain a prominent named skip without a completion ticket");
        MemeRuntimeFiles.write(root.resolve("COMPLETED.json"),Map.of("status","PASSED_NATIVE_TOMTOM_RUNTIME_SOURCE_HOST_AND_FRESH_HEALTH_CONTROLS","profiles",receipts,"native_admissions",2,"fresh_health_fits",4,"downstream_synthetic_fits",2,"missing_home_skipped",true,"user_analysis_admitted",false,"public_execution_adapter_available",false,"implementation_credit",false));
        System.out.println("COMPLETE_TOMTOM_NATIVE_RUNTIME_CONTROLS ADMISSIONS2 HEALTH4 DOWNSTREAM2 NO_USER_ANALYSIS_OR_CATALOG_CREDIT");
    }
    private static void require(boolean condition,String message)throws IOException {MemeRuntimeFiles.require(condition,message);}
}
