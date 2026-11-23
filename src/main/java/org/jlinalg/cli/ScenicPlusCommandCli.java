/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Exact source-profile routing for the qualified SCENIC+ component workflows. */
public final class ScenicPlusCommandCli {
    private ScenicPlusCommandCli() { }
    private static final String ACTION="SKIP3: restore the exact permitted private runtime/input or retain the skipped dependent analysis. FAIL2: preserve raw/partial/failed evidence, correct the named cause and retry a NEW output. Inspect checks saved closure; VERIFY and REPLAY execute fresh original computation. Use the exact producer JAR for existing captures. Keep licensed runtimes, trusted fitted objects and source-bearing traces private under accepted internal academic copying/transfer terms. Unknown profiles, versions and author settings are never inferred.";

    public static void main(String[] args) {
        int code=run(args,System.out,System.err);
        if(code!=0)System.exit(code);
    }

    static int run(String[] args,PrintStream out,PrintStream error) {
        try {
            if(Arrays.equals(args,new String[]{"--help"})) {
                out.println(help());
                return 0;
            }
            require(args.length==2&&(args[0].equals("--request")||args[0].equals("--inspect")),
                    "Use scenicplus-tools --request JSON, --inspect CAPTURE or --help");
            String profile;
            if(args[0].equals("--inspect"))profile=captureProfile(Path.of(args[1]));
            else {
                Path source=Path.of(args[1]).toAbsolutePath().normalize();
                Map<String,Object> request=readRoutingRequest(source);
                String mode=text(request.get("mode"),"mode");
                if(mode.equals("RUN"))profile=text(request.get("profile"),"profile");
                else {
                    require(Set.of("VERIFY","REPLAY").contains(mode),"Unknown SCENIC+ mode");
                    profile=captureProfile(source.getParent().resolve(text(request.get("analysis"),"analysis")));
                }
            }
            Class<?> target=switch(profile) {
                case ScenicPlusPrepareCli.PROFILE -> ScenicPlusPrepareCli.class;
                case ScenicPlusMotifsCli.PROFILE -> ScenicPlusMotifsCli.class;
                case ScenicPlusToolsCli.PROFILE -> ScenicPlusToolsCli.class;
                case ScenicPlusNetworkCli.PROFILE -> ScenicPlusNetworkCli.class;
                default -> throw new IOException("Unknown SCENIC+ source profile: "+profile+". No fallback or RNA-only SCENIC substitution is performed.");
            };
            sameJar(target);
            out.println("SCENIC+ selected source profile: "+profile);
            return switch(profile) {
                case ScenicPlusPrepareCli.PROFILE -> ScenicPlusPrepareCli.run(args,out,error);
                case ScenicPlusMotifsCli.PROFILE -> ScenicPlusMotifsCli.run(args,out,error);
                case ScenicPlusToolsCli.PROFILE -> ScenicPlusToolsCli.run(args,out,error);
                case ScenicPlusNetworkCli.PROFILE -> ScenicPlusNetworkCli.run(args,out,error);
                default -> throw new IOException("Unroutable SCENIC+ profile");
            };
        } catch(Exception failure) {
            int code=failure instanceof ScenicWorkflowContracts.Unavailable?3:2;
            error.println((code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED: ":"FAIL2: NO COMPLETE RESULT: ")+failure+"\n"+ACTION);
            return code;
        }
    }

    private static Map<String,Object> readRoutingRequest(Path source)throws IOException {
        require(Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS),"A regular existing request is required");
        return object(GprofilerJson.read(ScenicWorkflowFiles.bytes(source,16_777_216)));
    }

    private static String captureProfile(Path capture)throws IOException {
        Path root=capture.toAbsolutePath().normalize();
        require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)&&root.toRealPath().equals(root),
                "A physical existing capture is required; incomplete/aliased evidence is not a complete result");
        // This is only a routing hint. The selected component checks its complete
        // seal, inventory, source profile and producer before accepting a capture.
        return text(readRoutingRequest(root.resolve("canonical-request.json")).get("profile"),"captured profile");
    }

    private static Path origin(Class<?> type)throws IOException,URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
    }

    private static void sameJar(Class<?> target)throws IOException,URISyntaxException {
        Path jar=origin(ScenicPlusCommandCli.class);
        if(!Files.isRegularFile(jar)||!jar.toString().endsWith(".jar"))
            throw new ScenicWorkflowContracts.Unavailable("Use a complete source-built JAR for SCENIC+ execution/inspection");
        require(jar.equals(origin(JLinAlgCli.class))&&jar.equals(origin(target)),
                "Mixed SCENIC+ main dispatcher/router/component class origins; restore one complete producer JAR");
    }

    private static String help() {
        return "scenicplus-tools --request JSON | --inspect CAPTURE | --help\n"
            +"Schema1 source-profile requests use RUN, VERIFY or REPLAY. Exact routes:\n"
            +"  UNPAIRED preparation: "+ScenicPlusPrepareCli.PROFILE+"\n"
            +"  MOTIFS cisTarget/DEM and cistromes: "+ScenicPlusMotifsCli.PROFILE+"\n"
            +"  REGION_TO_GENE: "+ScenicPlusToolsCli.PROFILE+"\n"
            +"  NETWORK TF2G/R2G/eGRN/activity: "+ScenicPlusNetworkCli.PROFILE+"\n"
            +"The component receives the original arguments and validates the full request. VERIFY/REPLAY route from the saved canonical profile and require a NEW disjoint witness. No software installation, reference download, normalization or inferred cell pairing is performed. Unpaired preparation requires a trusted original cisTopic pickle; loading untrusted pickles executes code. Motif preparation uses explicit local prepared databases. ClusterBuster scanning is SKIP3 pending confirmed permission. Original empty-category motif merging remains FAIL2, and original unbalanced DEM may ignore a requested background cap; read actual outcomes and remedies. Paired preparation, annotation/search-space generation and genome scanning are outside these prepared workflows.\n"
            +"Docs: SCENICPLUS_GLOBAL_WORKFLOWS.md and the individual preparation/motif/network contracts.\n"+ACTION;
    }
}
