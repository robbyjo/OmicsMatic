/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Public request router for native deviations and guarded original background preparation. */
public final class ChromVarToolsCli {
    private ChromVarToolsCli() { }
    static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==1&&args[0].equals("--help")){
                emit(out,"Usage: chromvar-tools --request UTF8_JSON\n       chromvar-tools --source-report\n\nBACKGROUND: original R sampling from prepared bias or native reference GC.\nDEVIATIONS: native Java scoring from an explicit prepared background matrix.\nVERIFY: Java-only captured consistency checks; no R execution.\n\nRequest paths resolve against the JSON file directory. Outputs must be NEW.\nSupported request profiles: 1.16.0-source and 1.34.1-package, on explicitly recorded current dependencies. --source-report retains additional paper-edition source comparisons without inferring an unstated paper commit.\n\nBACKGROUND requires R4.6.1, chromVAR1.34.1, Matrix1.7.5, nabor0.5.0, Rcpp1.1.2, RcppArmadillo15.6.2-1 and jsonlite2.0.0, through the four declared private runtime paths. Missing/forbidden requirements mean SKIP3 and skip dependent analysis. No automatic installation occurs.\n\nFAIL2 means no result admitted: inspect WARNINGS.md, status, manifest and worker logs; preserve attempts, correct the named input/reference/source/capacity/process error and use NEW paths.\n\nVERIFY recomputes native count/GC preparation and checks capture consistency; it does not rerun sampling or independently generate RNG. Hashes do not authenticate execution or biology. Read docs/literature/CHROMVAR_TOOLS.md and examples/chromvar-background/README.md.");return 0;
            }
            if(args.length==1&&args[0].equals("--source-report")){
                try(var source=ChromVarToolsCli.class.getResourceAsStream("/org/jlinalg/omics/chromvar-paper-sources/SOURCE-PROFILES.json")){if(source==null)throw new IOException("Paper source correspondence resource unavailable");out.writeBytes(source.readAllBytes());}return 0;
            }
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request UTF8_JSON, --source-report or --help");
            Path request=Path.of(args[1]).toAbsolutePath().normalize();Map<String,Object> value=read(request);Object mode=value.get("mode");
            if("BACKGROUND".equals(mode))return ChromVarBackgroundRequestCli.run(args,out,err);
            if("DEVIATIONS".equals(mode))return ChromVarRequestCli.run(args,out,err);
            if("VERIFY".equals(mode)){
                Object a=value.get("analysis");if(!(a instanceof String name)||name.isBlank())throw new IOException("VERIFY requires an analysis path");Path analysis=Path.of(name);if(!analysis.isAbsolute())analysis=request.getParent().resolve(analysis);Object engine=read(analysis.resolve("manifest.json")).get("engine");
                if("upstream-chromVAR-R".equals(engine))return ChromVarBackgroundRequestCli.run(args,out,err);
                if("native-java".equals(engine))return ChromVarRequestCli.run(args,out,err);
                throw new IOException("Capture has no supported completed chromVAR engine; inspect its WARNINGS.md and analysis-status.json");
            }
            throw new IOException("Unsupported mode; require BACKGROUND, DEVIATIONS or VERIFY");
        }catch(Exception failure){emit(err,"FAIL2 — NO CHROMVAR RESULT ADMITTED: "+failure+"\nRead CHROMVAR_TOOLS.md and captured WARNINGS/status/logs. Preserve attempts, correct the named cause and use NEW paths. Missing/forbidden R requirements must leave dependent analyses SKIP3; no automatic installation or statistical substitution occurs.");return 2;}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> read(Path path)throws IOException{
        if(!Files.isRegularFile(path)||Files.size(path)>4000000)throw new IOException("JSON unavailable or exceeds 4000000 bytes: "+path);Object value=SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8));if(!(value instanceof Map<?,?> m)||m.keySet().stream().anyMatch(k->!(k instanceof String)))throw new IOException("Require a JSON object");return (Map<String,Object>)m;
    }
    private static void emit(PrintStream out,String message){out.writeBytes((message+"\n").getBytes(StandardCharsets.UTF_8));}
}
