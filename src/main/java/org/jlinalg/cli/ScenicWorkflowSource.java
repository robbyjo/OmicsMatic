/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.enrichment.CisTargetRecovery;
import org.jlinalg.enrichment.PyScenicAucell;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Bind scientific code and the controller to one complete source-built JAR. */
final class ScenicWorkflowSource {
    private ScenicWorkflowSource(){ }
    static Map<String,Object> producer()throws IOException {
        try {
            Path jar=Path.of(ScenicWorkflowSource.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(jar)||!jar.getFileName().toString().endsWith(".jar"))throw new ScenicWorkflowContracts.Unavailable("Use a complete source-built project JAR for this profile, not loose/mixed class directories");
            var visited=new java.util.HashSet<Class<?>>();for(Class<?> type:List.of(ScenicToolsCli.class,ScenicWorkflowSource.class,ScenicWorkflowRuntime.class,ScenicWorkflowProcess.class,
                    ScenicWorkflowFiles.class,ScenicWorkflowContracts.class,ScenicWorkflowActivity.class,ScenicWorkflowAudit.class,
                    PyScenicAucell.class,CisTargetRecovery.class,GprofilerJson.class,OwnedProcesses.class,GprofilerWorkflowContracts.class,MrPressoWorkflowContracts.class)) {
                origins(type,jar,visited);
            }
            return map("kind","EXACT_PACKAGED_JAR","identity",pin(jar),"source_profile",ScenicWorkflowContracts.PROFILE,
                "source_profile_identity",pin(ScenicWorkflowRuntime.resource("SOURCE-PROFILE.json")),"java_version",System.getProperty("java.version"),"os",System.getProperty("os.name"),"authentication",false);
        }catch(URISyntaxException error){throw new IOException("Cannot resolve exact SCENIC producer",error);}
    }
    private static void origins(Class<?> type,Path jar,java.util.Set<Class<?>> visited)throws IOException,URISyntaxException {
        if(!visited.add(type))return;Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();require(jar.equals(origin),"Require one exact scientific/controller producer including nested classes: "+type.getName());
        for(Class<?> member:type.getNestMembers())origins(member,jar,visited);for(Class<?> child:type.getDeclaredClasses())origins(child,jar,visited);
    }
    static void sameProducer(Map<String,Object> saved)throws IOException {
        var current=producer();require("EXACT_PACKAGED_JAR".equals(saved.get("kind"))&&ScenicWorkflowContracts.PROFILE.equals(saved.get("source_profile"))&&Boolean.FALSE.equals(saved.get("authentication")),"Saved producer kind/profile/authentication scope differs");
        require(same(saved.get("identity"),current.get("identity"))&&same(saved.get("source_profile_identity"),current.get("source_profile_identity")),"Use the exact complete producer JAR that created the capture; do not refresh saved hashes to permit substitution");
    }
}
