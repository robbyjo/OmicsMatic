/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Public family router; scientific choices remain explicit in each source-profile request. */
public final class SmartpcaCommandCli {
    private SmartpcaCommandCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream error) {
        try {
            if(args.length==1&&args[0].equals("--help")){out.println("smartpca-tools: prepared native PCA and source fstonly profiles. Use --request JSON or --verify CAPTURE.\n");SmartpcaToolsCli.run(args,out,error);out.println();return SmartpcaFstCli.run(args,out,error);}
            String profile;
            if(args.length==2&&args[0].equals("--verify"))profile=captureProfile(Path.of(args[1]));
            else {
                require(args.length==2&&args[0].equals("--request"),"Use smartpca-tools --request JSON, --verify CAPTURE or --help");
                Path path=Path.of(args[1]).toAbsolutePath().normalize();var request=object(GprofilerJson.read(SmartpcaWorkflowFiles.bytes(path,16_777_216)));
                String mode=text(request.get("mode"),"mode");
                if(Set.of("VERIFY","REPLAY").contains(mode))profile=captureProfile(Path.of(SmartpcaWorkflowContracts.resolve(SmartpcaWorkflowContracts.address(path.getParent()),text(request.get("analysis"),"analysis"),SmartpcaWorkflowContracts.style())));
                else profile=text(request.get("profile"),"profile");
            }
            require(Set.of(SmartpcaWorkflowContracts.PROFILE,SmartpcaFstContracts.PROFILE).contains(profile),"Unknown or contradictory smartpca source profile");
            return profile.equals(SmartpcaFstContracts.PROFILE)?SmartpcaFstCli.run(args,out,error):SmartpcaToolsCli.run(args,out,error);
        }catch(Exception failure){int code=failure instanceof SmartpcaWorkflowContracts.Unavailable?3:2;error.println((code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED: ":"FAIL2: NO COMPLETE RESULT: ")+failure+"\n"+SmartpcaWorkflowContracts.ACTION);return code;}
    }
    private static String captureProfile(Path capture)throws IOException {
        Path root=SmartpcaWorkflowAudit.existing(capture);Path canonical=root.resolve("canonical-request.json");
        require(Files.isRegularFile(canonical,LinkOption.NOFOLLOW_LINKS),"Existing capture has no regular canonical request; incomplete evidence admits no complete result");
        return text(SmartpcaWorkflowFiles.read(canonical).get("profile"),"captured profile");
    }
}
