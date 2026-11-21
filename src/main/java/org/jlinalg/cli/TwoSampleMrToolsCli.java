/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Exact-profile global delegation to the independently qualified original workflows. */
public final class TwoSampleMrToolsCli {
    private TwoSampleMrToolsCli() { }
    private static final long JSON_CAP=2_000_000;
    private static final String ACTION="SKIP3: DEPENDENT ANALYSIS SKIPPED; restore the named exact permitted runtime, package/profile or input, or retain the skip. Downloads/private installation are allowed where permitted; if prohibited, explain the missing prerequisite and skip dependent analysis. FAIL2: NO COMPLETE RESULT; preserve inputs, original returns/errors, partials and logs, correct the named data/design, numerical, integrity, capacity or process cause and retry NEW output. Read WARNINGS.md, analysis-status.json and original logs. No silent version, identifier, population, threshold, estimator/provider or nonfinite repair. Historical dependencies, author execution commits and whole-paper reproduction remain separate qualifications.";
    @FunctionalInterface private interface Command {int run(String[] args,PrintStream out,PrintStream err);}
    private record Route(String engine,Set<String> profiles,Command command) { }
    private static final List<Route> ROUTES=List.of(
        new Route(TwoSampleMrWorkflowCli.ENGINE,TwoSampleMrWorkflowContracts.PROFILES,TwoSampleMrWorkflowCli::run),
        new Route(TwoSampleMrClumpCli.ENGINE,TwoSampleMrClumpContracts.PROFILES,TwoSampleMrClumpCli::run),
        new Route(TwoSampleMrHistoricalCli.ENGINE,TwoSampleMrHistoricalContracts.PROFILES,TwoSampleMrHistoricalCli::run),
        new Route(TwoSampleMrHistoricalClumpCli.ENGINE,TwoSampleMrHistoricalClumpContracts.PROFILES,TwoSampleMrHistoricalClumpCli::run));
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")) {
                emit(out,"Usage: twosamplemr-tools --request UTF8_JSON | --verify COMPLETE_BUNDLE\n"
                    +"RUN/REPLAY execute the original package with explicit runtime paths. VERIFY checks saved captures without executing R.\n"
                    +"Prepared workflows: original0.7.14 or tagged0.4.9/0.5.6/0.5.7/0.6.8; historical FORMAT is also available. Local CLUMP: original0.7.14 or tagged0.6.8 with exact ieugwasr/PLINK.\n"
                    +"Choose the exact profile in the request. VERIFY/REPLAY use the capture's engine/profile; delegated schemas, warnings, return codes and semantics are unchanged. Original R execution is WindowsR4.6.1; Linux Java saved verification is separately qualified.\n"
                    +"Profiles:\n"+String.join("\n",ROUTES.stream().flatMap(r->r.profiles().stream()).sorted().toList())+"\n"
                    +"Guides: docs/literature/TWOSAMPLEMR_TOOLS.md and examples/twosamplemr-global/.\n"+ACTION);return 0;
            }
            Route target;
            if(args.length==2&&args[0].equals("--verify"))target=capture(Path.of(args[1]));
            else if(args.length==2&&args[0].equals("--request")) {
                Path path=Path.of(args[1]).toAbsolutePath().normalize();var request=readJson(path);String mode=text(request,"mode");
                if(mode.equals("RUN")) {
                    String profile=text(request,"profile");target=ROUTES.stream().filter(r->r.profiles().contains(profile)).findFirst().orElse(null);
                    if(target==null){emit(err,"SKIP3 — DEPENDENT ANALYSIS SKIPPED: exact TwoSampleMR profile is not qualified: "+profile+"\n"+ACTION);return 3;}
                }else if(mode.equals("VERIFY")||mode.equals("REPLAY"))target=capture(resolve(path.getParent(),text(request,"analysis")));
                else throw new IOException("Unknown TwoSampleMR mode; require RUN, VERIFY or REPLAY");
            }else throw new IOException("Require --request JSON, --verify BUNDLE or --help");
            return target.command().run(args,out,err);
        }catch(OutOfMemoryError error){emit(err,"FAIL2 — NO COMPLETE RESULT: routing JSON exhausted the Java heap; preserve diagnostics and use explicit capacities/NEW output. "+ACTION);return 2;}
        catch(Exception error){emit(err,"FAIL2 — NO COMPLETE RESULT: "+error+"\n"+ACTION);return 2;}
    }
    private static Route capture(Path root)throws IOException {
        Map<String,Object> manifest=readJson(root.toAbsolutePath().normalize().resolve("manifest.json"));String engine=text(manifest,"engine"),profile=text(manifest,"profile");
        Route target=ROUTES.stream().filter(r->r.engine().equals(engine)).findFirst().orElseThrow(()->new IOException("Capture has no supported original TwoSampleMR engine: "+engine));
        if(!target.profiles().contains(profile))throw new IOException("Capture engine/profile conflict; restore the original complete capture without relabeling editions");return target;
    }
    private static Map<String,Object> readJson(Path path)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>JSON_CAP)throw new IOException("Require bounded regular UTF8 request/manifest: "+path);
        byte[] raw;try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){raw=input.readNBytes((int)JSON_CAP+1);}
        if(raw.length>JSON_CAP)throw new IOException("Request/manifest grew beyond the routing JSON capacity");
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();
        return object(SimpleJson.parse(text),"TwoSampleMR request/manifest");
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));stream.flush();}
}
