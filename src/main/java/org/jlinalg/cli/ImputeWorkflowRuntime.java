/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Exact unmodified original static runtime; health is an actual scientific operation. */
final class ImputeWorkflowRuntime {
    private ImputeWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static final String BINARY_SHA="411409f9f3e7ad2f0d5c7bcdeac39804bba6139067c2d8fbefa83f80325bf94c";
    static final String LICENSE_SHA="b37e95a7d7da476d36e1e411da303a1156e3de4390cd5067ad0906012b3bc420";
    static Map<String,Object> identity(Map<String,Object> request)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))
            throw new Unavailable("Original IMPUTE2 v2.3.2 static profile needs Linux x86_64 Java; on Windows use Java in WSL. No alternate edition is substituted");
        Path home=Path.of(text(request.get("runtime_home"),"runtime_home"));
        if(!Files.isDirectory(home)||!Files.isRegularFile(home.resolve("impute2"))||!Files.isRegularFile(home.resolve("LICENCE")))
            throw new Unavailable("Restore the permitted private original v2.3.2 home with impute2 and bundled LICENCE; analysis never downloads or installs");
        require(home.toRealPath().equals(home),"Use a physical runtime home without symlink/junction aliases");
        var members=map();
        for(String name:List.of("impute2","LICENCE")) {
            Path path=home.resolve(name);require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&path.toRealPath().equals(path),"Runtime member is not physical regular original file");
            var identity=pin(path);String expected=name.equals("impute2")?BINARY_SHA:LICENSE_SHA;
            if(!expected.equals(identity.get("sha256")))throw new Unavailable("Wrong original v2.3.2 "+name+" identity; restore the accepted archive rather than refreshing its hash");members.put(name,identity);
        }
        if(!Files.isExecutable(home.resolve("impute2")))throw new Unavailable("Private original impute2 is not executable; restore its archive executable permission if permitted");
        return map("profile",ImputeWorkflowContracts.PROFILE,"members",members,"host",map("os",System.getProperty("os.name"),"arch",System.getProperty("os.arch")),"help_is_health_probe",false,"emulator_used",false,"license_acceptance",request.get("license_accepted"));
    }
    static List<String> command(Path root,Map<String,Object> request)throws IOException {
        Path worker=root.resolve("worker");var options=object(request.get("options"));String operation=ImputeWorkflowContracts.operation(request);
        var result=new ArrayList<String>(List.of(Path.of(text(request.get("runtime_home"),"runtime_home")).resolve("impute2").toString(),"-m",ImputeWorkflowContracts.input(root,"map").toString(),"-int",Long.toString(number(options,"interval_start")),Long.toString(number(options,"interval_end")),"-Ne",Long.toString(number(options,"Ne")),"-seed",Long.toString(number(options,"seed"))));
        if(operation.equals("PREPHASE"))result.addAll(List.of("-prephase_g","-g",ImputeWorkflowContracts.input(root,"study_genotypes").toString()));
        else {
            result.add("-h");result.add(ImputeWorkflowContracts.input(root,"reference_haps").toString());
            if(operation.equals("MERGE_REFERENCE"))result.add(ImputeWorkflowContracts.input(root,"second_reference_haps").toString());
            result.add("-l");result.add(ImputeWorkflowContracts.input(root,"reference_legend").toString());
            if(operation.equals("MERGE_REFERENCE"))result.add(ImputeWorkflowContracts.input(root,"second_reference_legend").toString());
            if(operation.equals("MERGE_REFERENCE"))result.addAll(List.of("-merge_ref_panels_output_ref",worker.resolve("merged").toString()));
            else {
                if(operation.equals("IMPUTE_PREPHASED"))result.addAll(List.of("-use_prephased_g","-known_haps_g",ImputeWorkflowContracts.input(root,"study_haps").toString()));
                else {
                    result.addAll(List.of("-g",ImputeWorkflowContracts.input(root,"study_genotypes").toString()));
                    switch(text(options.get("typed_genotype_policy"),"typed_genotype_policy")) {
                        case "reimpute-all" -> result.add("-pgs");
                        case "fill-missing-at-source-threshold" -> result.add("-pgs_miss");
                        case "preserve-input" -> { /* Original default retains typed input calls, including missing ones. */ }
                        default -> throw new IOException("Unknown typed policy");
                    }
                }
                result.addAll(List.of("-strand_g",ImputeWorkflowContracts.input(root,"strand").toString()));
            }
        }
        result.addAll(List.of("-o",worker.resolve("result").toString()));return List.copyOf(result);
    }
    static void execute(Path root,Map<String,Object> request,Map<String,Object> process,ImputeWorkflowProcess.Check check)throws Exception {
        var before=identity(request);write(root.resolve("runtime-before.json"),before);
        var environment=Map.of("LC_ALL","C","TZ","UTC","PATH","/usr/bin:/bin");process.put("environment",environment);
        int exit;
        try{exit=ImputeWorkflowProcess.execute(command(root,request),environment,root,request,process,check);}
        finally{write(root.resolve("worker-process.json"),process);}
        require(same(before,identity(request)),"Original binary/license/runtime profile changed during operation");write(root.resolve("runtime-after.json"),identity(request));
        require(exit==0,"Original IMPUTE2 returned "+exit+"; retain worker stdout/stderr and partial scientific files");
        String stdout=new String(bytes(root.resolve("worker/stdout.log"),16777216),java.nio.charset.StandardCharsets.UTF_8);
        require(stdout.contains("IMPUTE version 2.3.2"),"Original scientific invocation did not identify exact v2.3.2");
        for(String name:ImputeWorkflowData.scientificFiles(ImputeWorkflowContracts.operation(request)))require(Files.isRegularFile(root.resolve("worker/"+name),LinkOption.NOFOLLOW_LINKS),"Original process did not return expected "+name);
    }
}
