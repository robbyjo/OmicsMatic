/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jlinalg.genetics.SmartpcaAnalysis;
import org.jlinalg.genetics.SmartpcaOutliers;
import org.jlinalg.genetics.SmartpcaPrepared;
import org.jlinalg.genetics.SmartpcaShrinkage;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;

/** Unchanged original source/license resources and one exact packaged scientific producer. */
final class SmartpcaWorkflowSource {
    private SmartpcaWorkflowSource() { }
    static final String DESCRIPTOR="4c6f694a668fe8bbf759e91744acd09b695d9ecd9dcaadfdd936009422e1a972";
    static final String ARCHIVE="8472006f0ae402fd8f564a02720b4d7b9f2a66a6b81a44498cb569a02eb5e1f5";
    private static final String RESOURCE="/org/jlinalg/omics/smartpca-4f7e9f0/";
    static Map<String,Object> producer()throws IOException {
        try {
            Path jar=Path.of(SmartpcaWorkflowSource.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(jar)||!jar.getFileName().toString().endsWith(".jar"))throw new SmartpcaWorkflowContracts.Unavailable("Supply the exact complete project producer JAR containing this qualified profile");
            for(Class<?> type:List.of(SmartpcaPrepared.class,SmartpcaOutliers.class,SmartpcaShrinkage.class,SmartpcaAnalysis.class,
                    SmartpcaWorkflowData.class,SmartpcaWorkflowContracts.class,SmartpcaWorkflowFiles.class,GprofilerJson.class,
                    jdistlib.accelerator.CpuComputeBackend.class,org.jlinalg.compute.BackendContext.class)) {
                Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
                require(origin.equals(jar),"Scientific/runtime classes must load from this one exact producer: "+type.getName());
            }
            return map("kind","EXACT_PACKAGED_JAR","identity",pin(jar),"source_profile_sha256",DESCRIPTOR,
                "java_version",System.getProperty("java.version"),"os",System.getProperty("os.name"),"backend","PORTABLE_JAVA_CPU_FP64");
        }catch(URISyntaxException invalid){throw new IOException("Cannot bind exact native producer",invalid);}
    }
    static void sameProducer(Map<String,Object> saved)throws IOException {
        var current=producer();require(equal(saved.get("identity"),current.get("identity"))&&Objects.equals(saved.get("source_profile_sha256"),DESCRIPTOR),
            "Use the exact complete producer JAR recorded by this capture. No producer substitution or saved hash refresh is allowed.");
    }
    static void sources(Path root,boolean verify)throws IOException {
        byte[] descriptor=resource("SOURCE.json");require(sha(descriptor).equals(DESCRIPTOR),"Different packaged source descriptor");
        var source=object(GprofilerJson.read(descriptor));require(source.get("commit").equals("4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1")&&source.get("tag").equals("v7.2.1"),"Different source package identity");
        require(list(source.get("members")).size()==159,"Different source member population");
        for(String name:List.of("SOURCE.json","LICENSE.txt","original-source.tar.gz")) {
            byte[] bytes=name.equals("SOURCE.json")?descriptor:resource(name);
            if(name.equals("original-source.tar.gz"))require(sha(bytes).equals(ARCHIVE)&&equal(pin(bytes),source.get("archive")),"Different original source archive");
            if(name.equals("LICENSE.txt")) {
                Object wanted=null;for(Object member:list(source.get("members"))){var row=object(member);if(name.equals(row.get("path")))wanted=map("bytes",row.get("bytes"),"sha256",row.get("sha256"));}
                require(wanted!=null&&equal(pin(bytes),wanted),"Different original BSD notice");
            }
            Path target=root.resolve("source").resolve(name);
            if(verify)require(Arrays.equals(bytes,SmartpcaWorkflowFiles.bytes(target,1_048_576)),"Captured source/license bytes differ: "+name);
            else {Files.createDirectories(target.getParent());Files.write(target,bytes,StandardOpenOption.CREATE_NEW);}
        }
    }
    private static byte[] resource(String name)throws IOException {
        try(var in=SmartpcaWorkflowSource.class.getResourceAsStream(RESOURCE+name)) {
            if(in==null)throw new SmartpcaWorkflowContracts.Unavailable("Missing original source resource; supply the complete exact producer: "+name);return in.readAllBytes();
        }
    }
}
