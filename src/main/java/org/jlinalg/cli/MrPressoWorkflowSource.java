/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import org.jlinalg.mrpresso.MrPressoOriginal;
import org.jlinalg.mrpresso.MrPressoRandom;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.MrPressoWorkflowFiles.*;

/** Fixed original-source resources and exact packaged-producer binding. */
final class MrPressoWorkflowSource {
    private MrPressoWorkflowSource() { }
    private static final String RESOURCE="/org/jlinalg/omics/mrpresso-3e3c92d/";
    private static final String DESCRIPTOR_SHA="04d2a349b54c4f5c44a23309ded00fde42a18e5caeb98f5934c3198b3fd97191";
    static Map<String,Object> producer()throws IOException {
        try{
            Path jar=Path.of(MrPressoWorkflowSource.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(jar)||!jar.getFileName().toString().endsWith(".jar"))throw new MrPressoWorkflowContracts.Unavailable("Use the exact complete packaged producer JAR for native RUN/VERIFY/REPLAY");
            for(Class<?> type:List.of(MrPressoOriginal.class,MrPressoRandom.class,GprofilerJson.class,jdistlib.Normal.class,jdistlib.T.class,jdistlib.accelerator.CpuComputeBackend.class,
                    org.jlinalg.compute.BackendContext.class)){
                Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();require(origin.equals(jar),"Native scientific classes must load from this one captured producer JAR: "+type.getName());
            }
            return map("kind","EXACT_PACKAGED_JAR","identity",pin(jar),"source_profile_sha256",DESCRIPTOR_SHA,"java_version",System.getProperty("java.version"),"os",System.getProperty("os.name"));
        }catch(URISyntaxException invalid){throw new IOException("Cannot bind the packaged producer",invalid);}
    }
    static void sameProducer(Map<String,Object> captured)throws IOException {
        var current=producer();require(equal(captured.get("identity"),current.get("identity"))&&Objects.equals(captured.get("source_profile_sha256"),DESCRIPTOR_SHA),
            "Use the exact producer JAR recorded by this capture; current scientific producer differs. No fallback or hash refresh is performed.");
    }
    static void sources(Path root,boolean verify)throws IOException {
        byte[] descriptor=resource("SOURCE.json");require(sha(descriptor).equals(DESCRIPTOR_SHA),"Packaged MR-PRESSO source descriptor differs");var profile=object(GprofilerJson.read(descriptor));
        require(profile.get("commit").equals(MrPressoOriginal.SOURCE_COMMIT)&&profile.get("profile").equals(MrPressoWorkflowContracts.PROFILE),"Different original MR-PRESSO source profile");
        var members=object(profile.get("files"));require(members.keySet().equals(Set.of("DESCRIPTION","NAMESPACE","README.md","R/MR-PRESSO-internal.R","R/mr_presso.R", "man/MRPRESSO-package.Rd","man/SummaryStats.Rd","man/mr_presso.Rd","COPYING-GPL-3.0.txt")),"Original source member population differs");
        var names=new ArrayList<>(members.keySet());names.add("SOURCE.json");
        for(String name:names){
            byte[] raw=name.equals("SOURCE.json")?descriptor:resource(name);if(!name.equals("SOURCE.json"))require(equal(pin(raw),members.get(name)),"Packaged original source bytes differ: "+name);
            Path target=root.resolve("source").resolve(name);
            if(verify)require(Arrays.equals(raw,bytes(target,1_048_576)),"Captured original source differs: "+name);
            else{Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);}
        }
    }
    private static byte[] resource(String name)throws IOException {
        try(var input=MrPressoWorkflowSource.class.getResourceAsStream(RESOURCE+name)){
            if(input==null)throw new MrPressoWorkflowContracts.Unavailable("Supply complete qualified JAR; missing "+RESOURCE+name);return input.readAllBytes();
        }
    }
}
