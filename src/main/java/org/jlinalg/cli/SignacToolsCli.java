/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** One explicit entry point for scoped native Signac methods and guarded original adapters. */
public final class SignacToolsCli {
    private SignacToolsCli() { }
    static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,"Usage: signac-tools --workflow NAME --request UTF8_JSON\n       signac-tools --workflow NAME --help\n       signac-tools --source-report\n\nWorkflows:\n  normalize   Native RunTFIDF / FindTopFeatures, independently of R or SVD\n  lsi         Native count preparation + guarded original Signac SVD\n  fragments   Native fragment counts, FeatureMatrix, legacy nucleosome QC, binarization\n  annotations Native TSS profiles/enrichment and gene activity\n  motifs      Native supplied-population motif enrichment\n  scan        Guarded original motif scanning / motif-object construction\n  links       Original background sampling + native peak/expression link audit\n  markers     Guarded original current Seurat Wilcoxon / logistic-regression tests\n  peaks       Native preparation/reduction + original MACS2 calling\n  coverage    Native accessibility tracks, gene/transcript models and SVG replay\n  chromvar    Existing chromvar-tools background/deviation workflow\n\nChoose the workflow explicitly for both analysis and VERIFY; request schemas remain workflow-specific. Relative paths resolve against the JSON file, not the current directory. Use NEW outputs and retain every attempt. Keep request filenames ASCII on legacy Windows launchers; exact UTF-8 data paths/identities belong inside JSON.\n\nNative methods need Java only; coverage also needs a full Java desktop/font/PNG runtime. Original LSI, scan, links, markers and chromVAR background require the exact declared permitted R/package profiles. MACS calling requires Linux Java and an exact private MACS/Python profile; Java-only VERIFY also works on Windows. No analysis-time installation or silent edition/method substitution occurs. Missing/forbidden requirements mean SKIP3 and skip the dependent analysis. FAIL2 admits no result, including partial files: read WARNINGS/status/logs and the concrete cause, correct it and retry into NEW paths.\n\nSource profiles and current dependency environments are explicit, not historical whole-package or whole-paper/biological equivalence. Markers retain partial-comparison warnings; untested groups are not negative results. VERIFY scope differs by workflow: some surrounding arithmetic replays natively, original statistical/RNG fits may not. Coverage SVG replays, PNG pixels do not. Hashes and consistency do not authenticate execution or biology. chromVAR is an existing integration and receives no duplicate tool credit.\n\nRead docs/literature/SIGNAC_TOOLS.md and the per-workflow example/validation guides. Older canonical/released JARs do not contain this new route.");return 0;}
            if(args.length==1&&args[0].equals("--source-report")){try(var in=SignacToolsCli.class.getResourceAsStream("/org/jlinalg/omics/signac-tools/WORKFLOWS.json")){if(in==null)throw new IOException("Packaged Signac workflow/source index unavailable");out.writeBytes(in.readAllBytes());}return 0;}
            if(args.length!=3&&args.length!=4||!args[0].equals("--workflow")||args.length==3&&!args[2].equals("--help")||args.length==4&&!args[2].equals("--request"))throw new IOException("Require --workflow NAME --request UTF8_JSON, --workflow NAME --help, --source-report or --help");
            String[] forwarded=args.length==3?new String[]{"--help"}:new String[]{"--request",args[3]};
            return switch(args[1]){
                case "normalize"->SignacNormalizationCli.run(forwarded,out,err);
                case "lsi"->SignacLsiRequestCli.run(forwarded,out,err);
                case "fragments"->SignacFragmentsRequestCli.run(forwarded,out,err);
                case "annotations"->SignacAnnotationsRequestCli.run(forwarded,out,err);
                case "motifs"->SignacMotifsRequestCli.run(forwarded,out,err);
                case "scan"->SignacScanRequestCli.run(forwarded,out,err);
                case "links"->SignacLinksRequestCli.run(forwarded,out,err);
                case "markers"->SeuratDifferentialCli.run(forwarded,out,err);
                case "peaks"->SignacPeaksCli.run(forwarded,out,err);
                case "coverage"->SignacCoverageCli.run(forwarded,out,err);
                case "chromvar"->ChromVarToolsCli.run(forwarded,out,err);
                default->throw new IOException("Unknown Signac workflow: "+args[1]);
            };
        }catch(Exception failure){emit(err,"FAIL2 — NO SIGNAC RESULT ADMITTED: "+failure+"\nChoose an explicit supported workflow and its exact request schema. Read SIGNAC_TOOLS.md and per-workflow WARNINGS/status/logs. Preserve attempts, correct the named cause and use NEW outputs. Missing/forbidden original dependencies must remain SKIP3; no automatic installation or statistical substitution occurs.");return 2;}
    }
    private static void emit(PrintStream out,String value){out.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
